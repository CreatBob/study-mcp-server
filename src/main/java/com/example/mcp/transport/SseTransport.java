package com.example.mcp.transport;

import com.example.mcp.protocol.McpRequest;
import com.example.mcp.protocol.McpResponse;
import com.example.mcp.server.McpServerHandler;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;

import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * SSE 传输层 - MCP协议的"两通道"实现
 * 
 * 【MCP协议的两通道架构】
 * MCP协议定义了Server和Client之间需要两个通信通道：
 * 
 * 1. SSE通道（服务器→客户端）：
 *    - 端点：GET /sse
 *    - 作用：服务器向客户端推送数据
 *    - 技术：Server-Sent Events (SSE)
 *    - 特点：长连接，单向推送
 * 
 * 2. HTTP POST通道（客户端→服务器）：
 *    - 端点：POST /message/{sessionId}
 *    - 作用：客户端向服务器发送请求
 *    - 技术：普通HTTP POST请求
 *    - 特点：短连接，请求-响应模式
 * 
 * 【SSE（Server-Sent Events）简介】
 * SSE是一种基于HTTP协议实现的Web技术，用于服务器向客户端实时推送数据。
 * 
 * SSE的工作原理：
 * - 客户端通过普通HTTP GET请求建立连接
 * - 服务器保持连接打开，不断发送数据
 * - 数据格式：event: 事件名\ndata: 数据内容\n\n
 * - 响应头：Content-Type: text/event-stream
 * 
 * SSE vs WebSocket：
 * - SSE：单向（服务器→客户端），基于HTTP，简单易用
 * - WebSocket：双向，独立协议，更复杂但功能更强
 * - MCP选择SSE是因为它更简单，且HTTP POST已满足客户端→服务器的需求
 * 
 * 【数据格式（text/event-stream）】
 * SSE数据包含4个固定字段：
 * - event: 事件类型（可选，默认为message）
 * - data: 实际数据内容
 * - id: 事件ID（用于断线续传）
 * - retry: 重连时间（毫秒）
 * 
 * 示例：
 * event: endpoint
 * data: /message?sessionId=xxx
 * 
 * event: message
 * data: {"jsonrpc":"2.0","id":"0","result":{...}}
 */
@RestController
public class SseTransport {

    /**
     * 客户端连接映射表
     * 
     * Key: sessionId（会话标识符）
     * Value: Sinks.Many（消息发射器，用于向该客户端推送消息）
     * 
     * 【为什么使用ConcurrentHashMap？】
     * - 支持并发访问，多个客户端可以同时连接
     * - 线程安全，避免竞态条件
     * 
     * 【Sinks.Many是什么？】
     * - Reactor提供的消息发射器
     * - 可以手动向其中添加消息（tryEmitNext）
     * - 订阅者可以接收所有发送的消息
     * - 类似于RxJava的Subject或传统的EventEmitter
     */
    private final Map<String, Sinks.Many<ServerSentEvent<String>>> clientSinks = new ConcurrentHashMap<>();

    /**
     * JSON序列化工具
     * 用于将Java对象转换为JSON字符串
     */
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * MCP协议处理器
     * 负责处理各种MCP请求（initialize、tools/list、tools/call等）
     */
    private final McpServerHandler serverHandler;

    /**
     * 允许的Origin列表
     * 用于CORS安全验证，防止DNS重绑定攻击
     */
    private final List<String> allowedOrigins = Arrays.asList(
            "http://localhost",
            "http://127.0.0.1",
            "null" // 本地文件访问时Origin为null
    );

    /**
     * 构造函数
     * 
     * @param serverHandler MCP协议处理器（由Spring自动注入）
     */
    public SseTransport(McpServerHandler serverHandler) {
        this.serverHandler = serverHandler;
    }

    // ==================== 通道1：SSE端点 ====================

    /**
     * 【MCP协议 - 通道1】SSE端点
     * 
     * 这是MCP协议"四步骤"中的第一步："连"
     * 客户端通过GET请求建立SSE长连接
     * 
     * 【工作流程】
     * 1. 客户端发送 GET /sse 请求
     * 2. 服务器创建SSE流，保持连接打开
     * 3. 服务器发送 event: endpoint 事件，告诉客户端POST端点地址
     * 4. 服务器持续发送心跳保持连接活跃
     * 5. 当有消息时，通过此通道推送给客户端
     * 
     * 【produces = TEXT_EVENT_STREAM_VALUE】
     * 告诉Spring这个端点返回SSE流
     * 响应头会自动设置：Content-Type: text/event-stream
     * 
     * @param clientId 客户端标识（可选）
     * @param request  HTTP请求对象
     * @return Flux<ServerSentEvent<String>> SSE事件流
     */
    @GetMapping(value = "/sse", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<String>> sseEndpoint(
            @RequestParam(required = false) String clientId,
            ServerHttpRequest request) {

        // ========== 安全检查：验证Origin头防止DNS重绑定攻击 ==========
        String origin = request.getHeaders().getFirst("Origin");
        if (origin != null && !isValidOrigin(origin)) {
            System.err.println("Invalid origin rejected: " + origin);
            return Flux.error(new SecurityException("Invalid origin"));
        }

        // ========== 生成会话ID ==========
        // 如果客户端提供了clientId则使用，否则自动生成
        String sessionId = clientId != null ? clientId : "client-" + System.currentTimeMillis();

        // ========== 创建消息发射器 ==========
        // Sinks.many().multicast().onBackpressureBuffer():
        // - multicast: 支持多个订阅者
        // - onBackpressureBuffer: 当消费者处理不过来时，缓存消息
        Sinks.Many<ServerSentEvent<String>> sink = Sinks.many().multicast().onBackpressureBuffer();
        clientSinks.put(sessionId, sink);

        System.out.println("MCP SSE client connected: " + sessionId + " from origin: " + origin);

        // ========== 创建心跳流 ==========
        // 每30秒发送一次心跳，保持连接活跃
        // 防止连接因为长时间没有数据而被中间代理或防火墙关闭
        Flux<ServerSentEvent<String>> heartbeat = Flux.interval(Duration.ofSeconds(30))
                .map(tick -> ServerSentEvent.<String>builder()
                        .event("ping")  // 心跳事件类型
                        .data("{\"type\":\"ping\"}")
                        .build());

        // ========== 合并消息流和心跳流 ==========
        return Flux.merge(
                sink.asFlux(),  // 主消息流
                heartbeat       // 心跳流
        )
        .doOnSubscribe(subscription -> {
            // 当客户端订阅时执行
            // 这是MCP协议"四步骤"中的第二步："取"
            try {
                // 1. 构建POST端点URI
                // MCP协议要求服务器告诉客户端应该把请求发送到哪里
                String endpointUri = getBaseUrl(request) + "/message/" + sessionId;

                // 2. 发送 endpoint 事件
                // 这是MCP协议规定的必须事件，客户端收到后知道往哪里发送请求
                ServerSentEvent<String> endpointEvent = ServerSentEvent.<String>builder()
                        .event("endpoint")  // 事件类型：endpoint
                        .data(endpointUri)  // 数据：POST端点URI
                        .build();

                sink.tryEmitNext(endpointEvent);
                System.out.println("Sent endpoint event to client: " + sessionId + " with URI: " + endpointUri);

            } catch (Exception e) {
                System.err.println("Error sending endpoint event: " + e.getMessage());
            }
        })
        .doOnCancel(() -> {
            // 当客户端断开连接时执行（正常关闭）
            System.out.println("MCP SSE client disconnected: " + sessionId);
            clientSinks.remove(sessionId);
            sink.tryEmitComplete();
        })
        .doOnError(error -> {
            // 当发生错误时执行
            System.err.println("MCP SSE error for client " + sessionId + ": " + error.getMessage());
            clientSinks.remove(sessionId);
        })
        .onErrorResume(error -> {
            // 错误恢复，返回空流而不是让整个流失败
            System.err.println("SSE stream error, attempting to recover: " + error.getMessage());
            return Flux.empty();
        });
    }

    // ==================== 通道2：HTTP POST端点 ====================

    /**
     * 【MCP协议 - 通道2】HTTP POST端点（带会话ID）
     * 
     * 这是客户端向服务器发送消息的通道
     * 客户端从endpoint事件中获取这个URI后，向此端点发送JSON-RPC请求
     * 
     * 【工作流程】
     * 1. 客户端发送 POST /message/{sessionId} 请求
     * 2. 请求体是JSON-RPC 2.0格式的消息
     * 3. 服务器处理请求，生成响应
     * 4. 通过SSE通道将响应推送给客户端
     * 
     * @param sessionId   会话标识符（从URL路径中获取）
     * @param messageJson 请求体（JSON格式的MCP请求）
     * @param request     HTTP请求对象
     * @return Mono<Void> 异步完成信号
     */
    @PostMapping(value = "/message/{sessionId}", consumes = MediaType.APPLICATION_JSON_VALUE)
    public Mono<Void> handleSessionMessage(
            @PathVariable String sessionId,
            @RequestBody String messageJson,
            ServerHttpRequest request) {
        return handleMessageInternal(sessionId, messageJson, request);
    }

    /**
     * 【备用端点】HTTP POST端点（通用）
     * 
     * 这是一个备用端点，支持通过查询参数传递sessionId
     * 某些MCP客户端可能使用这种方式
     * 
     * @param sessionId   会话标识符（从查询参数中获取）
     * @param messageJson 请求体
     * @param request     HTTP请求对象
     * @return Mono<Void> 异步完成信号
     */
    @PostMapping(value = "/message", consumes = MediaType.APPLICATION_JSON_VALUE)
    public Mono<Void> handleMessage(
            @RequestParam(required = false) String sessionId,
            @RequestBody String messageJson,
            ServerHttpRequest request) {
        // 如果没有提供sessionId，使用默认值
        String actualSessionId = sessionId != null ? sessionId : "default";
        return handleMessageInternal(actualSessionId, messageJson, request);
    }

    /**
     * 内部消息处理方法
     * 
     * 【处理流程详解】
     * 1. 安全检查：验证Origin头
     * 2. 解析请求：将JSON字符串解析为McpRequest对象
     * 3. 分发处理：调用McpServerHandler处理不同类型的请求
     * 4. 发送响应：通过SSE通道将响应推送给客户端
     * 
     * 【为什么响应通过SSE发送而不是直接返回？】
     * - MCP协议规定响应通过SSE通道发送
     * - 这样可以支持异步处理，服务器处理完后再推送
     * - 也支持服务器主动推送通知
     * 
     * @param sessionId   会话标识符
     * @param messageJson 请求体JSON
     * @param request     HTTP请求对象
     * @return Mono<Void> 异步完成信号
     */
    private Mono<Void> handleMessageInternal(String sessionId, String messageJson, ServerHttpRequest request) {
        return Mono.fromRunnable(() -> {
            try {
                // ========== 1. 安全检查 ==========
                String origin = request.getHeaders().getFirst("Origin");
                if (origin != null && !isValidOrigin(origin)) {
                    System.err.println("Invalid origin rejected for message: " + origin);
                    return;
                }

                // ========== 2. 解析JSON-RPC请求 ==========
                McpRequest mcpRequest = objectMapper.readValue(messageJson, McpRequest.class);
                System.out.println("Received MCP request: " + mcpRequest.getMethod() +
                        " (id: " + mcpRequest.getId() + ") from session: " + sessionId);

                // ========== 3. 调用协议处理器处理请求 ==========
                // McpServerHandler会根据method分发到不同的处理方法
                McpResponse response = serverHandler.handleRequest(mcpRequest);

                // ========== 4. 通过SSE发送响应 ==========
                // 注意：通知类型的请求（如notifications/initialized）不需要响应
                if (response != null) {
                    sendMessageToClient(sessionId, response);
                }

            } catch (Exception e) {
                System.err.println("Error processing MCP message: " + e.getMessage());
                e.printStackTrace();

                // 发送错误响应
                try {
                    McpRequest mcpReq = objectMapper.readValue(messageJson, McpRequest.class);
                    McpResponse errorResponse = new McpResponse();
                    errorResponse.setId(mcpReq.getId());
                    errorResponse.setError(new McpResponse.McpError(-32603, "Internal error: " + e.getMessage()));
                    sendMessageToClient(sessionId, errorResponse);
                } catch (Exception ex) {
                    System.err.println("Error sending error response: " + ex.getMessage());
                }
            }
        });
    }

    // ==================== 工具方法 ====================

    /**
     * 向指定客户端发送MCP消息
     * 
     * 【实现细节】
     * 1. 根据sessionId找到对应的Sink
     * 2. 将消息对象序列化为JSON
     * 3. 包装为ServerSentEvent
     * 4. 通过Sink发送
     * 
     * @param sessionId 会话标识符
     * @param message   要发送的消息对象（会被序列化为JSON）
     */
    public void sendMessageToClient(String sessionId, Object message) {
        Sinks.Many<ServerSentEvent<String>> sink = clientSinks.get(sessionId);
        if (sink != null) {
            try {
                // 将消息对象序列化为JSON字符串
                String jsonData = objectMapper.writeValueAsString(message);

                // 创建SSE事件
                // event类型为"message"，这是MCP协议规定的
                ServerSentEvent<String> event = ServerSentEvent.<String>builder()
                        .event("message")  // 事件类型
                        .data(jsonData)    // JSON数据
                        .build();

                // 发送到SSE流
                sink.tryEmitNext(event);
                System.out.println("Sent MCP message to client: " + sessionId);
            } catch (Exception e) {
                System.err.println("Error sending message to client " + sessionId + ": " + e.getMessage());
            }
        } else {
            System.err.println("No active connection found for session: " + sessionId);
        }
    }

    /**
     * 验证Origin是否有效
     * 
     * 【安全说明】
     * DNS重绑定攻击可以让恶意网站绕过同源策略
     * 验证Origin头可以防止这种攻击
     * 
     * @param origin 请求的Origin头
     * @return true如果Origin有效
     */
    private boolean isValidOrigin(String origin) {
        if (origin == null || origin.isEmpty()) {
            return true; // 允许没有Origin的请求（如直接访问）
        }
        // 检查是否在允许列表中
        return allowedOrigins.stream().anyMatch(origin::startsWith);
    }

    /**
     * 获取服务器基础URL
     * 
     * 用于构建POST端点的完整URI
     * 例如：http://localhost:8080
     * 
     * @param request HTTP请求对象
     * @return 基础URL字符串
     */
    private String getBaseUrl(ServerHttpRequest request) {
        // 获取scheme（http或https）
        String scheme = request.getURI().getScheme();
        // 获取host和port
        String host = request.getURI().getHost();
        int port = request.getURI().getPort();

        // 构建基础URL
        if (port == -1 || port == 80 || port == 443) {
            return scheme + "://" + host;
        }
        return scheme + "://" + host + ":" + port;
    }
}
