package com.example.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.reactive.CorsWebFilter;
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource;

/**
 * MCP Server 配置类
 * 
 * 【为什么需要CORS配置？】
 * 当MCP Client（如MCP Inspector或其他Web客户端）从浏览器访问我们的MCP Server时，
 * 由于它们通常不在同一个域名下，浏览器会进行跨域资源共享(CORS)检查。
 * 
 * 如果不配置CORS：
 * - 浏览器会阻止跨域请求
 * - MCP Inspector无法连接到我们的Server
 * - 会出现"Access-Control-Allow-Origin"相关错误
 * 
 * CORS配置允许来自不同源的请求访问我们的API
 */
@Configuration
public class McpConfig {

    /**
     * 配置CORS过滤器
     * 
     * 【CORS工作原理】
     * 1. 浏览器在发送跨域请求前，会先发送一个OPTIONS预检请求
     * 2. 服务器通过响应头告诉浏览器是否允许该跨域请求
     * 3. 如果允许，浏览器才会发送真正的请求
     * 
     * @return CorsWebFilter CORS过滤器Bean
     */
    @Bean
    public CorsWebFilter corsWebFilter() {
        CorsConfiguration config = new CorsConfiguration();
        
        // 允许所有来源的请求访问
        // 在生产环境中，应该限制为特定的域名，例如：
        // config.addAllowedOrigin("https://your-mcp-client.com");
        config.addAllowedOriginPattern("*");
        
        // 允许所有HTTP方法（GET, POST, PUT, DELETE等）
        // MCP协议主要使用GET（SSE连接）和POST（发送消息）
        config.addAllowedMethod("*");
        
        // 允许所有请求头
        // MCP协议需要Content-Type等头部
        config.addAllowedHeader("*");
        
        // 允许发送Cookie和认证信息
        // 某些MCP Client可能需要使用Cookie进行会话管理
        config.setAllowCredentials(true);
        
        // 暴露响应头给客户端
        // 这些头部在SSE连接中很重要
        config.addExposedHeader("Content-Type");
        config.addExposedHeader("Cache-Control");
        
        // 预检请求的缓存时间（秒）
        // 在这个时间内，浏览器不会重复发送OPTIONS预检请求
        config.setMaxAge(3600L);

        // 创建基于URL的CORS配置源
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        
        // 将CORS配置应用到所有路径
        // "/**" 表示匹配所有URL路径
        source.registerCorsConfiguration("/**", config);

        return new CorsWebFilter(source);
    }
}
