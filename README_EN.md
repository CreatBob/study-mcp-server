# SpringBoot MCP Server Example

English | [中文](./README.md)

A sample MCP (Model Context Protocol) Server implementation based on Spring Boot WebFlux.

## 📖 What is MCP?

**MCP (Model Context Protocol)** is an open protocol that standardizes how applications provide context to Large Language Models.

### Core Components of MCP

```
┌─────────────────────────────────────────────────────────┐
│                     MCP Host                            │
│  (AI clients like Cherry Studio, Tongyi Qianwen, etc.)  │
│                                                         │
│  ┌─────────────┐         ┌─────────────────────────┐   │
│  │ User Query  │ ──────> │      MCP Client         │   │
│  └─────────────┘         │  (integrated in Host)   │   │
│                          └───────────┬─────────────┘   │
└──────────────────────────────────────┼─────────────────┘
                                       │
                    MCP Protocol (SSE + JSON-RPC 2.0)
                                       │
                                       ▼
                    ┌──────────────────────────────────┐
                    │         MCP Server               │
                    │    (implemented by this project) │
                    │                                  │
                    │  Tools provided for AI:          │
                    │  - hello_world                   │
                    │  - get_time                      │
                    │  - echo                          │
                    └──────────────────────────────────┘
```

### MCP Protocol: Two Channels, Four Steps

**Two Channels:**
1. **SSE Channel** (GET /sse) - Server → Client, for pushing messages
2. **HTTP POST Channel** (POST /message/{sessionId}) - Client → Server, for sending requests

**Four Steps:**
1. **Connect** - Client establishes SSE connection
2. **Receive** - Server sends endpoint event with POST endpoint address
3. **Handshake** - Client sends initialize request, server returns capability info
4. **Use** - Call tools/list to get tools, call tools/call to execute tools

## 🚀 Quick Start

### Prerequisites

- JDK 17+
- Maven 3.6+
- Node.js (optional, for MCP Inspector testing)

### Running the Project

```bash
# Navigate to project directory
cd springboot-mcp-server

# Run with Maven
mvn spring-boot:run

# Or build and run
mvn clean package
java -jar target/springboot-mcp-server-1.0.0.jar
```

### Testing Methods

#### Method 1: Using Built-in Test Page

1. After starting the server, open browser and visit: http://localhost:8080/mcp-test.html
2. Click "Connect to MCP Server"
3. Click "Initialize (Handshake)"
4. Click "List Tools" to view available tools
5. Call various tools

#### Method 2: Using MCP Inspector

```bash
# Install and run MCP Inspector
npx @modelcontextprotocol/inspector@0.9
```

1. Open Inspector interface in browser
2. Select "SSE Transport"
3. Enter URL: `http://localhost:8080/sse`
4. Click "Connect"
5. Execute Initialize, List Tools, Call Tools, etc.

## 📁 Project Structure

```
springboot-mcp-server/
├── pom.xml                                    # Maven configuration
├── README.md                                  # Project documentation
├── src/
│   ├── main/
│   │   ├── java/com/example/
│   │   │   ├── HelloWorldApplication.java     # Main application entry
│   │   │   ├── config/
│   │   │   │   └── McpConfig.java             # CORS configuration
│   │   │   └── mcp/
│   │   │       ├── protocol/                  # JSON-RPC 2.0 protocol
│   │   │       │   ├── McpRequest.java        # Request format
│   │   │       │   └── McpResponse.java       # Response format
│   │   │       ├── transport/                 # Transport layer
│   │   │       │   └── SseTransport.java      # SSE and HTTP POST endpoints
│   │   │       ├── server/                    # Protocol handling
│   │   │       │   └── McpServerHandler.java  # MCP protocol handler
│   │   │       └── tools/                     # Tool management
│   │   │           └── McpToolRegistry.java   # Tool registry
│   │   └── resources/
│   │       ├── application.properties         # Application config
│   │       └── static/
│   │           └── mcp-test.html              # Test page
│   └── test/
│       └── java/                              # Test code
```

## 🛠️ Implemented MCP Tools

| Tool Name | Description | Parameters |
|-----------|-------------|------------|
| `hello_world` | Returns a greeting | `name` (optional) - Name to greet |
| `get_time` | Gets server current time | None |
| `echo` | Echoes back input message | `message` - Message to echo |

## 📚 Core Code Explanation

### 1. SSE Endpoint (SseTransport.java)

```java
// Establish SSE long connection for server-to-client message pushing
@GetMapping(value = "/sse", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
public Flux<ServerSentEvent<String>> sseEndpoint(...) {
    // 1. Create message emitter
    // 2. Send endpoint event (tells client the POST endpoint address)
    // 3. Return SSE stream
}
```

### 2. HTTP POST Endpoint (SseTransport.java)

```java
// Receive JSON-RPC requests from client
@PostMapping(value = "/message/{sessionId}")
public Mono<Void> handleSessionMessage(...) {
    // 1. Parse JSON-RPC request
    // 2. Call McpServerHandler for processing
    // 3. Send response via SSE
}
```

### 3. Protocol Handling (McpServerHandler.java)

```java
// Dispatch to different handlers based on method
public McpResponse handleRequest(McpRequest request) {
    switch (request.getMethod()) {
        case "initialize": return handleInitialize(...);
        case "tools/list": return handleListTools(...);
        case "tools/call": return handleCallTool(...);
        // ...
    }
}
```

### 4. Tool Registration (McpToolRegistry.java)

```java
// Register tool definitions and implementations
toolDefinitions.put("hello_world", toolDef);
toolImplementations.put("hello_world", (args) -> {
    String name = (String) args.getOrDefault("name", "World");
    return "Hello, " + name + "!";
});
```

## 🔗 Related Links

- [MCP Official Documentation](https://modelcontextprotocol.io/)
- [Spring WebFlux Documentation](https://docs.spring.io/spring-framework/docs/current/reference/html/web-reactive.html)
- [JSON-RPC 2.0 Specification](https://www.jsonrpc.org/specification)

## 📝 License

MIT License
