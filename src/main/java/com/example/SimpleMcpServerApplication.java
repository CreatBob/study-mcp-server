package com.example;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * MCP Server 主应用程序入口类
 * 
 * 【MCP简介】
 * MCP (Model Context Protocol，模型上下文协议) 是规范应用程序向大语言模型提供上下文的开放协议。
 * 
 * MCP的核心组件：
 * 1. MCP Host - AI客户端（如Cherry Studio、通义千问等），负责接收用户问题并调用工具
 * 2. MCP Client - 集成在MCP Host中，负责发送请求/接收响应
 * 3. MCP Server - 我们要实现的部分，处理请求并返回上下文数据
 * 
 * 本项目实现了一个简单的MCP Server，演示MCP协议的工作原理：
 * - 使用SSE (Server-Sent Events) 实现服务器向客户端的实时推送
 * - 使用JSON-RPC 2.0格式进行消息交换
 * - 实现了MCP协议的"两通道、四步骤"通信机制
 * 
 * @author MCP Learning Example
 */
@SpringBootApplication
public class SimpleMcpServerApplication {

    /**
     * 应用程序主入口
     * 
     * 启动后，MCP Server将：
     * 1. 在端口8080启动HTTP服务
     * 2. 提供 GET /sse 端点用于SSE连接（服务器推送通道）
     * 3. 提供 POST /message/{sessionId} 端点用于接收客户端请求（客户端请求通道）
     * 
     * @param args 命令行参数
     */
    public static void main(String[] args) {
        SpringApplication.run(SimpleMcpServerApplication.class, args);
        System.out.println("========================================");
        System.out.println("MCP Server 已启动!");
        System.out.println("========================================");
        System.out.println("SSE端点: http://localhost:8080/sse");
        System.out.println("测试页面: http://localhost:8080/mcp-test.html");
        System.out.println("========================================");
        System.out.println("使用MCP Inspector测试:");
        System.out.println("1. 运行: npx @modelcontextprotocol/inspector@0.9");
        System.out.println("2. 选择 SSE Transport");
        System.out.println("3. 输入URL: http://localhost:8080/sse");
        System.out.println("4. 点击 Connect");
        System.out.println("========================================");
    }
}
