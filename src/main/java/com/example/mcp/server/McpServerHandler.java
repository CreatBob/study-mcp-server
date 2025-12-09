package com.example.mcp.server;

import com.example.mcp.protocol.McpRequest;
import com.example.mcp.protocol.McpResponse;
import com.example.mcp.tools.McpToolRegistry;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * MCP 协议处理器 - 实现MCP协议的"四步骤"
 * 
 * 【MCP协议的四步骤】
 * MCP协议定义了Client和Server之间通信的四个步骤：
 * 
 * 步骤一：连（Connect）
 * - 客户端通过GET请求建立SSE连接
 * - 这一步在SseTransport中实现
 * 
 * 步骤二：取（Get Endpoint）
 * - 服务器通过SSE发送endpoint事件，告诉客户端POST端点地址
 * - 这一步也在SseTransport中实现
 * 
 * 步骤三：握（Initialize）【本类实现】
 * - 客户端发送 initialize 请求
 * - 服务器返回能力信息（capabilities）
 * - 客户端发送 notifications/initialized 通知
 * - 握手完成，进入正常会话阶段
 * 
 * 步骤四：用（Use）【本类实现】
 * - 客户端调用 tools/list 获取可用工具
 * - 客户端调用 tools/call 执行具体工具
 * - 服务器返回工具执行结果
 * 
 * 【本类职责】
 * 接收JSON-RPC请求，根据method分发到不同的处理方法，返回响应
 */
@Component
public class McpServerHandler {

    /**
     * MCP协议版本
     * 这是当前实现支持的协议版本
     * 客户端和服务器需要协商使用相同的协议版本
     */
    private static final String PROTOCOL_VERSION = "2024-11-05";

    /**
     * 服务器名称
     * 在initialize响应中返回，用于标识服务器
     */
    private static final String SERVER_NAME = "SpringBoot MCP Server";

    /**
     * 服务器版本
     */
    private static final String SERVER_VERSION = "1.0.0";

    /**
     * 初始化状态标志
     * 只有在完成初始化握手后，才能调用工具
     */
    private boolean initialized = false;

    /**
     * 工具注册表
     * 管理所有可用的MCP工具
     */
    private final McpToolRegistry toolRegistry;

    /**
     * 构造函数
     * 
     * @param toolRegistry 工具注册表（由Spring自动注入）
     */
    public McpServerHandler(McpToolRegistry toolRegistry) {
        this.toolRegistry = toolRegistry;
    }

    /**
     * 处理MCP请求的主入口方法
     * 
     * 【工作流程】
     * 1. 获取请求的method
     * 2. 根据method分发到对应的处理方法
     * 3. 返回处理结果
     * 
     * 【支持的方法】
     * - initialize：初始化握手
     * - notifications/initialized：初始化完成通知
     * - tools/list：列出可用工具
     * - tools/call：调用工具
     * 
     * @param request MCP请求对象
     * @return MCP响应对象（通知类型的请求返回null）
     */
    public McpResponse handleRequest(McpRequest request) {
        String method = request.getMethod();
        String id = request.getId();
        Map<String, Object> params = request.getParams();

        try {
            switch (method) {
                // ========== 步骤三：握手阶段 ==========
                case "initialize":
                    // 处理初始化请求，返回服务器能力
                    return handleInitialize(id, params);

                case "notifications/initialized":
                    // 处理初始化完成通知
                    // 这是一个通知（notification），不需要响应
                    this.initialized = true;
                    System.out.println("Client initialization completed - MCP握手成功！");
                    return null;

                // ========== 步骤四：使用阶段 ==========
                case "tools/list":
                    // 列出所有可用工具
                    return handleListTools(id);

                case "tools/call":
                    // 调用具体工具
                    return handleCallTool(id, params);

                // ========== 未知方法 ==========
                default:
                    // 返回"方法未找到"错误
                    McpResponse errorResponse = new McpResponse();
                    errorResponse.setId(id);
                    errorResponse.setError(new McpResponse.McpError(
                            -32601,  // JSON-RPC 2.0: Method not found
                            "Method not found: " + method
                    ));
                    return errorResponse;
            }
        } catch (Exception e) {
            System.err.println("Error handling request " + method + ": " + e.getMessage());
            // 返回内部错误
            McpResponse errorResponse = new McpResponse();
            errorResponse.setId(id);
            errorResponse.setError(new McpResponse.McpError(
                    -32603,  // JSON-RPC 2.0: Internal error
                    "Internal error: " + e.getMessage()
            ));
            return errorResponse;
        }
    }

    /**
     * 处理 initialize 请求 - MCP握手的核心
     * 
     * 【initialize请求的作用】
     * 1. 协商协议版本：确保Client和Server使用兼容的协议版本
     * 2. 交换能力信息：告诉对方自己支持哪些功能
     * 3. 建立会话：为后续通信做准备
     * 
     * 【请求格式】
     * {
     *   "jsonrpc": "2.0",
     *   "method": "initialize",
     *   "params": {
     *     "protocolVersion": "2024-11-05",
     *     "capabilities": { ... },
     *     "clientInfo": { "name": "xxx", "version": "xxx" }
     *   },
     *   "id": 0
     * }
     * 
     * 【响应格式】
     * {
     *   "jsonrpc": "2.0",
     *   "id": 0,
     *   "result": {
     *     "protocolVersion": "2024-11-05",
     *     "capabilities": {
     *       "tools": { "listChanged": true }
     *     },
     *     "serverInfo": { "name": "xxx", "version": "xxx" }
     *   }
     * }
     * 
     * @param id     请求ID
     * @param params 请求参数
     * @return 初始化响应
     */
    private McpResponse handleInitialize(String id, Map<String, Object> params) {
        System.out.println("Processing initialize request...");

        // 构建响应结果
        Map<String, Object> result = new HashMap<>();

        // 1. 协议版本 - 告诉客户端我们使用的协议版本
        result.put("protocolVersion", PROTOCOL_VERSION);

        // 2. 服务器能力（capabilities）
        // 告诉客户端我们支持哪些功能
        Map<String, Object> capabilities = new HashMap<>();

        // tools能力：表示我们支持工具功能
        Map<String, Object> toolsCapability = new HashMap<>();
        toolsCapability.put("listChanged", true);  // 支持工具列表变更通知
        capabilities.put("tools", toolsCapability);

        // 注意：我们这个简单实现不支持resources和prompts
        // 如果支持，可以添加：
        // capabilities.put("resources", resourcesCapability);
        // capabilities.put("prompts", promptsCapability);

        result.put("capabilities", capabilities);

        // 3. 服务器信息
        Map<String, Object> serverInfo = new HashMap<>();
        serverInfo.put("name", SERVER_NAME);
        serverInfo.put("version", SERVER_VERSION);
        result.put("serverInfo", serverInfo);

        // 构建响应
        McpResponse response = new McpResponse();
        response.setId(id);
        response.setResult(result);

        System.out.println("Initialize response sent - 等待客户端发送 notifications/initialized");
        return response;
    }

    /**
     * 处理 tools/list 请求 - 列出所有可用工具
     * 
     * 【作用】
     * 让客户端知道服务器提供了哪些工具
     * 每个工具包含：名称、描述、输入参数Schema
     * 
     * 【响应格式】
     * {
     *   "jsonrpc": "2.0",
     *   "id": "1",
     *   "result": {
     *     "tools": [
     *       {
     *         "name": "hello_world",
     *         "description": "Returns a Hello World message",
     *         "inputSchema": {
     *           "type": "object",
     *           "properties": {
     *             "name": { "type": "string", "description": "Name to greet" }
     *           }
     *         }
     *       },
     *       ...
     *     ]
     *   }
     * }
     * 
     * @param id 请求ID
     * @return 工具列表响应
     */
    private McpResponse handleListTools(String id) {
        System.out.println("Processing tools/list request...");

        // 构建响应结果
        Map<String, Object> result = new HashMap<>();

        // 从工具注册表获取所有工具的定义
        result.put("tools", toolRegistry.getAllToolDefinitions());

        // 构建响应
        McpResponse response = new McpResponse();
        response.setId(id);
        response.setResult(result);

        System.out.println("Returned " + toolRegistry.getAllToolDefinitions().size() + " tools");
        return response;
    }

    /**
     * 处理 tools/call 请求 - 调用具体工具
     * 
     * 【作用】
     * 执行客户端指定的工具，并返回执行结果
     * 
     * 【请求格式】
     * {
     *   "jsonrpc": "2.0",
     *   "method": "tools/call",
     *   "params": {
     *     "name": "hello_world",       // 工具名称
     *     "arguments": { "name": "张三" } // 工具参数
     *   },
     *   "id": "2"
     * }
     * 
     * 【响应格式】
     * {
     *   "jsonrpc": "2.0",
     *   "id": "2",
     *   "result": {
     *     "content": [
     *       { "type": "text", "text": "Hello, 张三!" }
     *     ]
     *   }
     * }
     * 
     * @param id     请求ID
     * @param params 请求参数（包含工具名和参数）
     * @return 工具调用结果响应
     */
    @SuppressWarnings("unchecked")
    private McpResponse handleCallTool(String id, Map<String, Object> params) {
        // 获取工具名称
        String toolName = (String) params.get("name");

        // 获取工具参数（可能为null）
        Map<String, Object> arguments = (Map<String, Object>) params.get("arguments");
        if (arguments == null) {
            arguments = new HashMap<>();
        }

        System.out.println("Processing tools/call request: " + toolName + " with arguments: " + arguments);

        // 调用工具
        String toolResult = toolRegistry.callTool(toolName, arguments);

        // 构建响应结果
        // MCP规定工具返回结果必须是content数组
        Map<String, Object> result = new HashMap<>();
        result.put("content", java.util.Collections.singletonList(
                Map.of(
                        "type", "text",    // 内容类型：文本
                        "text", toolResult // 实际内容
                )
        ));

        // 构建响应
        McpResponse response = new McpResponse();
        response.setId(id);
        response.setResult(result);

        System.out.println("Tool " + toolName + " executed successfully");
        return response;
    }

    /**
     * 检查是否已完成初始化
     * 
     * @return true如果已初始化
     */
    public boolean isInitialized() {
        return initialized;
    }
}
