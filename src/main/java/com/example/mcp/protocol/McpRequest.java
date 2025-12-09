package com.example.mcp.protocol;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Map;

/**
 * MCP 请求类 - JSON-RPC 2.0 请求格式
 * 
 * 【JSON-RPC 2.0 协议简介】
 * JSON-RPC 2.0 是一种无状态、轻量级的远程过程调用(RPC)协议。
 * MCP协议使用JSON-RPC 2.0作为消息格式，定义了Client和Server之间的通信格式。
 * 
 * 【请求格式规范】
 * 每个请求必须包含以下字段：
 * {
 *   "jsonrpc": "2.0",           // 必须：协议版本，固定为"2.0"
 *   "method": "方法名",          // 必须：要调用的方法名称
 *   "params": {...},            // 可选：方法参数（对象或数组）
 *   "id": "唯一标识"             // 必须（除通知外）：请求的唯一标识符
 * }
 * 
 * 【MCP常用方法】
 * - "initialize"：初始化握手，协商协议版本和能力
 * - "notifications/initialized"：客户端初始化完成通知
 * - "tools/list"：获取可用工具列表
 * - "tools/call"：调用具体工具
 * 
 * 【示例请求】
 * 初始化请求：
 * {
 *   "jsonrpc": "2.0",
 *   "method": "initialize",
 *   "params": {
 *     "protocolVersion": "2024-11-05",
 *     "capabilities": {},
 *     "clientInfo": { "name": "mcp-inspector", "version": "0.9.0" }
 *   },
 *   "id": 0
 * }
 */
public class McpRequest {

    /**
     * JSON-RPC协议版本
     * 必须为"2.0"，这是JSON-RPC 2.0规范的要求
     */
    @JsonProperty("jsonrpc")
    private String jsonrpc = "2.0";

    /**
     * 请求方法名
     * 
     * MCP协议定义了以下标准方法：
     * - initialize：初始化握手
     * - notifications/initialized：初始化完成通知
     * - tools/list：列出可用工具
     * - tools/call：调用工具
     * - resources/list：列出可用资源（本示例未实现）
     * - prompts/list：列出可用提示（本示例未实现）
     */
    @JsonProperty("method")
    private String method;

    /**
     * 请求参数
     * 
     * 不同方法有不同的参数结构：
     * - initialize: { protocolVersion, capabilities, clientInfo }
     * - tools/call: { name: "工具名", arguments: {...} }
     * 
     * 使用Map<String, Object>可以灵活处理各种参数结构
     */
    @JsonProperty("params")
    private Map<String, Object> params;

    /**
     * 请求唯一标识符
     * 
     * 【作用】
     * - 用于匹配请求和响应
     * - 服务器返回的响应中会包含相同的id
     * - 客户端通过id知道哪个响应对应哪个请求
     * 
     * 【注意】
     * - 如果是通知（notification），可以没有id
     * - 通知是不需要响应的消息，如"notifications/initialized"
     */
    @JsonProperty("id")
    private String id;

    // ============== Getter 和 Setter 方法 ==============

    public String getJsonrpc() {
        return jsonrpc;
    }

    public void setJsonrpc(String jsonrpc) {
        this.jsonrpc = jsonrpc;
    }

    public String getMethod() {
        return method;
    }

    public void setMethod(String method) {
        this.method = method;
    }

    public Map<String, Object> getParams() {
        return params;
    }

    public void setParams(Map<String, Object> params) {
        this.params = params;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    @Override
    public String toString() {
        return "McpRequest{" +
                "jsonrpc='" + jsonrpc + '\'' +
                ", method='" + method + '\'' +
                ", params=" + params +
                ", id='" + id + '\'' +
                '}';
    }
}
