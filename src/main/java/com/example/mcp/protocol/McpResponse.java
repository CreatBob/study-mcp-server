package com.example.mcp.protocol;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * MCP 响应类 - JSON-RPC 2.0 响应格式
 * 
 * 【JSON-RPC 2.0 响应规范】
 * 响应必须包含以下字段：
 * 
 * 成功响应：
 * {
 *   "jsonrpc": "2.0",    // 必须：协议版本
 *   "id": "请求ID",       // 必须：与请求中的id相同
 *   "result": {...}      // 必须：调用结果
 * }
 * 
 * 错误响应：
 * {
 *   "jsonrpc": "2.0",    // 必须：协议版本
 *   "id": "请求ID",       // 必须：与请求中的id相同
 *   "error": {           // 必须：错误信息
 *     "code": -32600,    // 错误代码
 *     "message": "错误描述",
 *     "data": {...}      // 可选：附加错误数据
 *   }
 * }
 * 
 * 【重要】result 和 error 互斥，不能同时存在！
 * 
 * 【标准错误代码】
 * -32700: Parse error（解析错误）
 * -32600: Invalid Request（无效请求）
 * -32601: Method not found（方法未找到）
 * -32602: Invalid params（无效参数）
 * -32603: Internal error（内部错误）
 */
@JsonInclude(JsonInclude.Include.NON_NULL) // 不序列化null字段，确保result和error互斥
public class McpResponse {

    /**
     * JSON-RPC协议版本
     * 必须为"2.0"
     */
    @JsonProperty("jsonrpc")
    private String jsonrpc = "2.0";

    /**
     * 请求标识符
     * 必须与对应请求的id相同，用于客户端匹配请求和响应
     */
    @JsonProperty("id")
    private String id;

    /**
     * 成功时的返回结果
     * 
     * 【不同方法的result结构】
     * 
     * initialize响应：
     * {
     *   "protocolVersion": "2024-11-05",
     *   "capabilities": { "tools": { "listChanged": true } },
     *   "serverInfo": { "name": "SpringBoot MCP Server", "version": "1.0.0" }
     * }
     * 
     * tools/list响应：
     * {
     *   "tools": [
     *     { "name": "hello_world", "description": "...", "inputSchema": {...} },
     *     ...
     *   ]
     * }
     * 
     * tools/call响应：
     * {
     *   "content": [
     *     { "type": "text", "text": "工具执行结果" }
     *   ]
     * }
     */
    @JsonProperty("result")
    private Object result;

    /**
     * 失败时的错误信息
     * 当请求处理失败时，返回error而不是result
     */
    @JsonProperty("error")
    private McpError error;

    // ============== Getter 和 Setter 方法 ==============

    public String getJsonrpc() {
        return jsonrpc;
    }

    public void setJsonrpc(String jsonrpc) {
        this.jsonrpc = jsonrpc;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public Object getResult() {
        return result;
    }

    public void setResult(Object result) {
        this.result = result;
    }

    public McpError getError() {
        return error;
    }

    public void setError(McpError error) {
        this.error = error;
    }

    /**
     * MCP 错误类 - JSON-RPC 2.0 错误对象
     * 
     * 【错误对象结构】
     * {
     *   "code": 错误代码,      // 整数，标识错误类型
     *   "message": "错误描述", // 字符串，简短错误描述
     *   "data": {...}        // 可选，附加错误信息
     * }
     * 
     * 【预定义错误代码】
     * -32700: Parse error - 服务端收到无效JSON
     * -32600: Invalid Request - JSON不是有效的请求对象
     * -32601: Method not found - 方法不存在或不可用
     * -32602: Invalid params - 方法参数无效
     * -32603: Internal error - 服务端内部错误
     * -32000 to -32099: Server error - 服务端自定义错误
     */
    public static class McpError {
        
        /**
         * 错误代码
         * 使用JSON-RPC 2.0预定义的错误代码
         */
        @JsonProperty("code")
        private int code;

        /**
         * 错误消息
         * 简短的错误描述
         */
        @JsonProperty("message")
        private String message;

        /**
         * 附加错误数据
         * 可选字段，提供更详细的错误信息
         */
        @JsonProperty("data")
        @JsonInclude(JsonInclude.Include.NON_NULL)
        private Object data;

        /**
         * 默认构造函数
         */
        public McpError() {
        }

        /**
         * 带参数的构造函数
         * 
         * @param code    错误代码
         * @param message 错误消息
         */
        public McpError(int code, String message) {
            this.code = code;
            this.message = message;
        }

        /**
         * 带所有参数的构造函数
         * 
         * @param code    错误代码
         * @param message 错误消息
         * @param data    附加数据
         */
        public McpError(int code, String message, Object data) {
            this.code = code;
            this.message = message;
            this.data = data;
        }

        // Getter 和 Setter
        public int getCode() {
            return code;
        }

        public void setCode(int code) {
            this.code = code;
        }

        public String getMessage() {
            return message;
        }

        public void setMessage(String message) {
            this.message = message;
        }

        public Object getData() {
            return data;
        }

        public void setData(Object data) {
            this.data = data;
        }
    }

    @Override
    public String toString() {
        return "McpResponse{" +
                "jsonrpc='" + jsonrpc + '\'' +
                ", id='" + id + '\'' +
                ", result=" + result +
                ", error=" + error +
                '}';
    }
}
