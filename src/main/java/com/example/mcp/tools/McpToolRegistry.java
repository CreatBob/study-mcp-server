package com.example.mcp.tools;

import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.function.Function;

/**
 * MCP 工具注册表 - 管理所有可用的MCP工具
 * 
 * 【什么是MCP工具？】
 * MCP工具是服务器提供给客户端（AI）调用的功能。
 * 通过MCP协议，AI可以：
 * 1. 通过 tools/list 获取可用工具列表
 * 2. 通过 tools/call 调用具体工具
 * 3. 获取工具执行结果，用于回答用户问题
 * 
 * 【工具定义（Tool Definition）】
 * 每个工具需要提供以下信息：
 * - name: 工具名称（唯一标识）
 * - description: 工具描述（AI用这个来理解工具的用途）
 * - inputSchema: 输入参数的JSON Schema（告诉AI需要提供什么参数）
 * 
 * 【inputSchema详解】
 * 使用JSON Schema格式描述工具的输入参数：
 * {
 *   "type": "object",           // 参数是一个对象
 *   "properties": {             // 对象的属性定义
 *     "参数名": {
 *       "type": "string",       // 参数类型
 *       "description": "参数描述"
 *     }
 *   },
 *   "required": ["参数名"]       // 必填参数列表（可选）
 * }
 * 
 * 【本类实现的示例工具】
 * 1. hello_world - 返回问候语
 * 2. get_time - 获取当前服务器时间
 * 3. echo - 回显用户输入的消息
 */
@Component
public class McpToolRegistry {

    /**
     * 工具定义映射表
     * Key: 工具名称
     * Value: 工具定义（包含name, description, inputSchema）
     * 
     * 这个Map用于响应 tools/list 请求
     */
    private final Map<String, Map<String, Object>> toolDefinitions = new LinkedHashMap<>();

    /**
     * 工具实现映射表
     * Key: 工具名称
     * Value: 工具实现函数（接收参数Map，返回结果字符串）
     * 
     * 这个Map用于响应 tools/call 请求
     */
    private final Map<String, Function<Map<String, Object>, String>> toolImplementations = new HashMap<>();

    /**
     * 构造函数 - 注册所有工具
     * 
     * 在构造函数中注册所有可用的工具
     * 你可以在这里添加更多自定义工具
     */
    public McpToolRegistry() {
        // ========== 注册工具1: hello_world ==========
        registerHelloWorldTool();

        // ========== 注册工具2: get_time ==========
        registerGetTimeTool();

        // ========== 注册工具3: echo ==========
        registerEchoTool();

        System.out.println("MCP Tool Registry initialized with " + toolDefinitions.size() + " tools");
    }

    /**
     * 注册 hello_world 工具
     * 
     * 【功能】返回一个问候语
     * 【参数】name - 要问候的名字（可选）
     * 【返回】"Hello, {name}!" 或 "Hello, World!"
     */
    private void registerHelloWorldTool() {
        // 1. 定义工具的输入参数Schema
        Map<String, Object> inputSchema = new LinkedHashMap<>();
        inputSchema.put("type", "object");  // 参数是一个对象

        // 定义properties（参数列表）
        Map<String, Object> properties = new LinkedHashMap<>();
        Map<String, Object> nameProperty = new LinkedHashMap<>();
        nameProperty.put("type", "string");
        nameProperty.put("description", "Name to greet (optional)");
        properties.put("name", nameProperty);

        inputSchema.put("properties", properties);
        // 注意：这里没有设置required，表示name是可选参数

        // 2. 创建工具定义
        Map<String, Object> toolDef = new LinkedHashMap<>();
        toolDef.put("name", "hello_world");
        toolDef.put("description", "Returns a Hello World message");
        toolDef.put("inputSchema", inputSchema);

        // 3. 注册工具定义
        toolDefinitions.put("hello_world", toolDef);

        // 4. 注册工具实现
        toolImplementations.put("hello_world", (args) -> {
            // 获取name参数，如果没有提供则使用"World"
            String name = (String) args.getOrDefault("name", "World");
            if (name == null || name.isEmpty()) {
                name = "World";
            }
            return "Hello, " + name + "!";
        });
    }

    /**
     * 注册 get_time 工具
     * 
     * 【功能】获取当前服务器时间
     * 【参数】无
     * 【返回】当前时间字符串
     */
    private void registerGetTimeTool() {
        // 1. 定义输入参数Schema（无参数）
        Map<String, Object> inputSchema = new LinkedHashMap<>();
        inputSchema.put("type", "object");
        inputSchema.put("properties", new LinkedHashMap<>()); // 空的properties表示无参数

        // 2. 创建工具定义
        Map<String, Object> toolDef = new LinkedHashMap<>();
        toolDef.put("name", "get_time");
        toolDef.put("description", "Returns current server time");
        toolDef.put("inputSchema", inputSchema);

        // 3. 注册工具定义
        toolDefinitions.put("get_time", toolDef);

        // 4. 注册工具实现
        toolImplementations.put("get_time", (args) -> {
            // 获取当前时间并格式化
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
            return "Current server time: " + LocalDateTime.now().format(formatter);
        });
    }

    /**
     * 注册 echo 工具
     * 
     * 【功能】回显用户输入的消息
     * 【参数】message - 要回显的消息
     * 【返回】"Echo: {message}"
     */
    private void registerEchoTool() {
        // 1. 定义输入参数Schema
        Map<String, Object> inputSchema = new LinkedHashMap<>();
        inputSchema.put("type", "object");

        Map<String, Object> properties = new LinkedHashMap<>();
        Map<String, Object> messageProperty = new LinkedHashMap<>();
        messageProperty.put("type", "string");
        messageProperty.put("description", "Message to echo back");
        properties.put("message", messageProperty);

        inputSchema.put("properties", properties);
        // 可以设置required来标记必填参数
        // inputSchema.put("required", Arrays.asList("message"));

        // 2. 创建工具定义
        Map<String, Object> toolDef = new LinkedHashMap<>();
        toolDef.put("name", "echo");
        toolDef.put("description", "Echoes back the provided message");
        toolDef.put("inputSchema", inputSchema);

        // 3. 注册工具定义
        toolDefinitions.put("echo", toolDef);

        // 4. 注册工具实现
        toolImplementations.put("echo", (args) -> {
            String message = (String) args.getOrDefault("message", "");
            if (message == null || message.isEmpty()) {
                return "Echo: (empty message)";
            }
            return "Echo: " + message;
        });
    }

    // ==================== 公共方法 ====================

    /**
     * 获取所有工具的定义列表
     * 
     * 用于响应 tools/list 请求
     * 
     * @return 工具定义列表
     */
    public List<Map<String, Object>> getAllToolDefinitions() {
        return new ArrayList<>(toolDefinitions.values());
    }

    /**
     * 调用指定的工具
     * 
     * 用于响应 tools/call 请求
     * 
     * @param toolName  工具名称
     * @param arguments 工具参数
     * @return 工具执行结果
     */
    public String callTool(String toolName, Map<String, Object> arguments) {
        // 查找工具实现
        Function<Map<String, Object>, String> implementation = toolImplementations.get(toolName);

        if (implementation == null) {
            // 工具不存在
            return "Error: Tool '" + toolName + "' not found";
        }

        try {
            // 执行工具
            return implementation.apply(arguments);
        } catch (Exception e) {
            // 工具执行出错
            return "Error executing tool '" + toolName + "': " + e.getMessage();
        }
    }

    /**
     * 检查工具是否存在
     * 
     * @param toolName 工具名称
     * @return true如果工具存在
     */
    public boolean hasTool(String toolName) {
        return toolDefinitions.containsKey(toolName);
    }

    /**
     * 获取工具数量
     * 
     * @return 已注册的工具数量
     */
    public int getToolCount() {
        return toolDefinitions.size();
    }

    // ==================== 动态添加工具的方法（高级用法） ====================

    /**
     * 动态注册新工具
     * 
     * 【使用场景】
     * 在运行时动态添加新工具，例如：
     * - 从配置文件加载工具
     * - 根据用户权限提供不同的工具
     * - 插件系统
     * 
     * @param name           工具名称
     * @param description    工具描述
     * @param inputSchema    输入参数Schema
     * @param implementation 工具实现函数
     */
    public void registerTool(String name, String description,
                             Map<String, Object> inputSchema,
                             Function<Map<String, Object>, String> implementation) {
        // 创建工具定义
        Map<String, Object> toolDef = new LinkedHashMap<>();
        toolDef.put("name", name);
        toolDef.put("description", description);
        toolDef.put("inputSchema", inputSchema);

        // 注册
        toolDefinitions.put(name, toolDef);
        toolImplementations.put(name, implementation);

        System.out.println("Registered new tool: " + name);
    }

    /**
     * 注销工具
     * 
     * @param toolName 要注销的工具名称
     */
    public void unregisterTool(String toolName) {
        toolDefinitions.remove(toolName);
        toolImplementations.remove(toolName);
        System.out.println("Unregistered tool: " + toolName);
    }
}
