# SpringBoot MCP Server 示例

[English](./README_EN.md) | 中文

这是一个基于Spring Boot WebFlux实现的MCP (Model Context Protocol) Server示例项目。

## 📖 什么是MCP？

**MCP (Model Context Protocol，模型上下文协议)** 是规范应用程序向大语言模型提供上下文的开放协议。

### MCP的核心组件

```
┌─────────────────────────────────────────────────────────┐
│                     MCP Host                             │
│  (AI客户端，如Cherry Studio、通义千问、IDEA LAB等)       │
│                                                          │
│  ┌─────────────┐         ┌─────────────────────────┐    │
│  │   用户问题   │ ──────> │      MCP Client          │    │
│  └─────────────┘         │  (集成在Host中)          │    │
│                          └───────────┬─────────────┘    │
└──────────────────────────────────────┼──────────────────┘
                                       │
                    MCP协议（SSE + JSON-RPC 2.0）
                                       │
                                       ▼
                    ┌──────────────────────────────────┐
                    │         MCP Server               │
                    │    (本项目实现的部分)            │
                    │                                  │
                    │  提供工具供AI调用：              │
                    │  - hello_world                   │
                    │  - get_time                      │
                    │  - echo                          │
                    └──────────────────────────────────┘
```

### MCP协议的"两通道、四步骤"

**两通道：**
1. **SSE通道** (GET /sse) - 服务器 → 客户端，用于推送消息
2. **HTTP POST通道** (POST /message/{sessionId}) - 客户端 → 服务器，用于发送请求

**四步骤：**
1. **连** - 客户端建立SSE连接
2. **取** - 服务器发送endpoint事件，提供POST端点地址
3. **握** - 客户端发送initialize请求，服务器返回能力信息
4. **用** - 调用tools/list获取工具，调用tools/call执行工具

## 🚀 快速开始

### 前置要求

- JDK 17+
- Maven 3.6+
- Node.js (用于MCP Inspector测试，可选)

### 运行项目

```bash
# 进入项目目录
cd springboot-mcp-server

# 使用Maven运行
mvn spring-boot:run

# 或者先编译再运行
mvn clean package
java -jar target/springboot-mcp-server-1.0.0.jar
```

### 测试方法

#### 方法1：使用内置测试页面

1. 启动服务器后，打开浏览器访问：http://localhost:8080/mcp-test.html
2. 点击"连接到 MCP Server"
3. 点击"Initialize (握手)"
4. 点击"List Tools"查看可用工具
5. 调用各种工具

#### 方法2：使用MCP Inspector

```bash
# 安装并运行MCP Inspector
npx @modelcontextprotocol/inspector@0.9
```

1. 在浏览器中打开Inspector界面
2. 选择 "SSE Transport"
3. 输入URL: `http://localhost:8080/sse`
4. 点击 "Connect"
5. 执行Initialize、List Tools、Call Tools等操作

## 📁 项目结构

```
springboot-mcp-server/
├── pom.xml                                    # Maven配置
├── README.md                                  # 项目说明
├── src/
│   ├── main/
│   │   ├── java/com/example/
│   │   │   ├── HelloWorldApplication.java     # 主应用程序入口
│   │   │   ├── config/
│   │   │   │   └── McpConfig.java             # CORS配置
│   │   │   └── mcp/
│   │   │       ├── protocol/                  # JSON-RPC 2.0协议
│   │   │       │   ├── McpRequest.java        # 请求格式
│   │   │       │   └── McpResponse.java       # 响应格式
│   │   │       ├── transport/                 # 传输层
│   │   │       │   └── SseTransport.java      # SSE和HTTP POST端点
│   │   │       ├── server/                    # 协议处理
│   │   │       │   └── McpServerHandler.java  # MCP协议处理器
│   │   │       └── tools/                     # 工具管理
│   │   │           └── McpToolRegistry.java   # 工具注册表
│   │   └── resources/
│   │       ├── application.properties         # 应用配置
│   │       └── static/
│   │           └── mcp-test.html              # 测试页面
│   └── test/
│       └── java/                              # 测试代码
```

## 🛠️ 实现的MCP工具

| 工具名 | 描述 | 参数 |
|--------|------|------|
| `hello_world` | 返回问候语 | `name` (可选) - 要问候的名字 |
| `get_time` | 获取服务器当前时间 | 无 |
| `echo` | 回显输入的消息 | `message` - 要回显的消息 |

## 📚 核心代码说明

### 1. SSE端点 (SseTransport.java)

```java
// 建立SSE长连接，用于服务器向客户端推送消息
@GetMapping(value = "/sse", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
public Flux<ServerSentEvent<String>> sseEndpoint(...) {
    // 1. 创建消息发射器
    // 2. 发送endpoint事件（告诉客户端POST端点地址）
    // 3. 返回SSE流
}
```

### 2. HTTP POST端点 (SseTransport.java)

```java
// 接收客户端的JSON-RPC请求
@PostMapping(value = "/message/{sessionId}")
public Mono<Void> handleSessionMessage(...) {
    // 1. 解析JSON-RPC请求
    // 2. 调用McpServerHandler处理
    // 3. 通过SSE发送响应
}
```

### 3. 协议处理 (McpServerHandler.java)

```java
// 根据method分发到不同的处理方法
public McpResponse handleRequest(McpRequest request) {
    switch (request.getMethod()) {
        case "initialize": return handleInitialize(...);
        case "tools/list": return handleListTools(...);
        case "tools/call": return handleCallTool(...);
        // ...
    }
}
```

### 4. 工具注册 (McpToolRegistry.java)

```java
// 注册工具定义和实现
toolDefinitions.put("hello_world", toolDef);
toolImplementations.put("hello_world", (args) -> {
    String name = (String) args.getOrDefault("name", "World");
    return "Hello, " + name + "!";
});
```

## 🔗 相关链接

- [MCP官方文档](https://modelcontextprotocol.io/)
- [Spring WebFlux文档](https://docs.spring.io/spring-framework/docs/current/reference/html/web-reactive.html)
- [JSON-RPC 2.0规范](https://www.jsonrpc.org/specification)

## 📝 许可证

MIT License
