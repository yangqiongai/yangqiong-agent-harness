/*
 * Copyright 2026 yangqiongtech.com
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.yangqiong.agent.harness.example.helloworld;

import com.yangqiong.agent.harness.mcp.McpClient;
import com.yangqiong.agent.harness.mcp.McpConfigLoader;
import com.yangqiong.agent.harness.mcp.McpPromptDescriptor;
import com.yangqiong.agent.harness.mcp.McpPromptResult;
import com.yangqiong.agent.harness.mcp.McpResourceContent;
import com.yangqiong.agent.harness.mcp.McpResourceDescriptor;
import com.yangqiong.agent.harness.mcp.McpResourceTemplate;
import com.yangqiong.agent.harness.mcp.McpServerConfig;
import com.yangqiong.agent.harness.mcp.McpToolCallResult;
import com.yangqiong.agent.harness.mcp.McpToolDescriptor;
import com.yangqiong.agent.harness.mcp.McpToolkit;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * MCP官方测试服务接入示例
 * <p>
 * 通过McpConfigLoader加载JSON配置，command使用裸npx。McpConfigLoader在加载期
 * 自动解析本机node.exe与npx-cli.js并重写为直连形式，用户无需显式配置node路径。
 * 拉起@modelcontextprotocol/server-everything后调用echo与get-sum工具
 * </p>
 * @author yangqiong
 */
public class McpExample {

    private McpExample() {
    }

    /**
     * 示例入口
     * @param args
     */
    public static void main(String[] args) {
        // 提前预热MCP：启动期统一完成各服务端握手与工具发现
        McpToolkit toolkit = preloadMcpToolkit();
        // stdio方式接入本地MCP服务
        runStdioEverythingExample(toolkit);
        // 用everything服务端验证Resources/Prompts能力
        runStdioEverythingResourcesPromptsExample(toolkit);
        // uvx方式接入Python生态MCP服务
        runStdioUvxTimeExample(toolkit);
        // streamable-http方式接入远程MCP服务
        runStreamableHttpExample(toolkit);
        // 释放全部MCP连接
        toolkit.close().block();
    }

    /**
     * 预热MCP：并行完成全部服务端握手与工具发现
     * <p>
     * 使用一个共享的McpToolkit统一管理多个服务端，启动期提前初始化，
     * 后续方法直接复用其客户端，避免各自重复握手。预热日志会逐个打印
     * 每个服务端接入可用状态，便于启动期发现配置或网络问题。
     * </p>
     * @return
     */
    private static McpToolkit preloadMcpToolkit() {
        String everythingJson = """
                {
                  "mcpServers": {
                    "everything": {
                      "command": "npx",
                      "args": ["-y", "@modelcontextprotocol/server-everything"],
                      "env": { "DEFAULT_MINIMUM_TOKENS": "10000" },
                      "initTimeout": "3m",
                      "requestTimeout": "2m"
                    }
                  }
                }""";
        String endpoint = System.getenv().getOrDefault("MCP_HTTP_ENDPOINT", "https://mcp.context7.com/mcp");
        String context7Yaml = """
                mcpServers:
                  context7:
                    transport: streamable-http
                    endpoint: ENDPOINT_PLACEHOLDER
                    requestTimeout: 2m
                """;
        // uvx方式接入Python生态的mcp-server-time（需本机已安装uv）
        String timeJson = """
                {
                  "mcpServers": {
                    "Time": {
                      "command": "uvx",
                      "args": ["mcp-server-time", "--local-timezone=Asia/Shanghai"],
                      "env": {},
                      "initTimeout": "3m",
                      "requestTimeout": "2m"
                    }
                  }
                }""";
        List<McpServerConfig> configs = new ArrayList<>();
        configs.addAll(McpConfigLoader.fromJson(everythingJson));
        configs.addAll(McpConfigLoader.fromYaml(context7Yaml.replace("ENDPOINT_PLACEHOLDER", endpoint)));
        configs.addAll(McpConfigLoader.fromJson(timeJson));
        McpToolkit toolkit = new McpToolkit(configs);
        System.out.println("开始预热MCP，共" + configs.size() + "个服务端");
        toolkit.initializeAll().block(Duration.ofMinutes(3));
        System.out.println("MCP预热完成，可用服务端: " + toolkit.getServerNames());
        return toolkit;
    }

    /**
     * stdio传输方式接入everything测试服务
     * <p>
     * 复用预热阶段已握手的everything客户端，调用echo与get-sum工具验证端到端可用。
     * </p>
     */
    private static void runStdioEverythingExample(McpToolkit toolkit) {
        McpClient client = toolkit.getClient("everything");
        if (client == null) {
            System.err.println("everything服务端未成功接入，跳过stdio演示");
            return;
        }
        try {
            if (client.getServerCapabilities() != null) {
                System.out.println("服务端能力: " + client.getServerCapabilities().getCapabilities());
            }

            List<McpToolDescriptor> tools = client.listTools().block();
            System.out.println("发现工具数量: " + (tools == null ? 0 : tools.size()));
            if (tools != null) {
                tools.forEach(tool -> System.out.println("  - " + tool.getName()));
            }

            // 调用echo工具回显文本
            McpToolCallResult echo = client.callTool("echo", Map.of("message", "你好，MCP")).block();
            System.out.println("echo返回: " + (echo == null ? "(空)" : echo.extractText()));

            // 调用get-sum工具做整数加法
            McpToolCallResult add = client.callTool("get-sum", Map.of("a", 2, "b", 40)).block();
            System.out.println("sum(2+40)返回: " + (add == null ? "(空)" : add.extractText()));
        } catch (Exception e) {
            System.err.println("MCP接入失败（请确认已安装node/npx且可联网）: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * 用everything服务端验证Resources与Prompts能力
     * <p>
     * 复用预热阶段已握手的everything客户端，列出资源与资源模板并读取样例资源，
     * 列出提示词模板并展开simple_prompt，验证自研客户端对Resources/Prompts的完整支持。
     * </p>
     */
    private static void runStdioEverythingResourcesPromptsExample(McpToolkit toolkit) {
        McpClient client = toolkit.getClient("everything");
        if (client == null) {
            System.err.println("everything服务端未成功接入，跳过Resources/Prompts演示");
            return;
        }
        try {
            List<McpResourceDescriptor> resources = client.listResources().block();
            System.out.println("资源数量: " + (resources == null ? 0 : resources.size()));
            if (resources != null) {
                resources.forEach(r -> System.out.println("  - " + r.uri() + " | " + r.name()
                        + " | " + r.mimeType() + " | " + r.description()));
            }
            List<McpResourceTemplate> templates = client.listResourceTemplates().block();
            System.out.println("资源模板数量: " + (templates == null ? 0 : templates.size()));
            if (templates != null) {
                templates.forEach(t -> System.out.println("  - " + t.uriTemplate() + " | " + t.name()));
            }
            // 读取资源验证resources/read：优先取listResources首个静态资源，否则展开首个模板
            String readUri = null;
            McpResourceContent content = null;
            if (resources != null && !resources.isEmpty()) {
                readUri = resources.get(0).uri();
                content = client.readResource(readUri).block();
            } else if (templates != null && !templates.isEmpty()) {
                readUri = templates.get(0).uriTemplate().replaceAll("\\{[^}]+}", "1");
                content = client.readResource(readUri).block();
            }
            if (content != null) {
                System.out.println("readResource(" + readUri + ")返回: " + (content.text() == null
                        ? "(二进制 " + content.blob().length + "字节)" : content.text()));
            }
            List<McpPromptDescriptor> prompts = client.listPrompts().block();
            System.out.println("提示词数量: " + (prompts == null ? 0 : prompts.size()));
            if (prompts != null) {
                prompts.forEach(p -> System.out.println("  - " + p.name() + " | " + p.description()
                        + " | 参数: " + p.arguments().stream()
                        .map(a -> a.name() + (a.required() ? "(必填)" : ""))
                        .toList()));
            }
            // 展开首个提示词模板验证prompts/get，按声明参数填充示例值
            if (prompts != null && !prompts.isEmpty()) {
                McpPromptDescriptor prompt = prompts.get(0);
                Map<String, Object> promptArgs = new java.util.HashMap<>();
                prompt.arguments().forEach(a -> {
                    if (a.required()) {
                        promptArgs.put(a.name(), a.name());
                    }
                });
                McpPromptResult result = client.getPrompt(prompt.name(), promptArgs).block();
                if (result != null && result.messages() != null) {
                    System.out.println("prompts/get(" + prompt.name() + ")展开消息数量: " + result.messages().size());
                    result.messages().forEach(m -> System.out.println("  [" + m.role() + "] " + m.content()));
                }
            }
        } catch (Exception e) {
            System.err.println("Resources/Prompts接入失败（请确认已安装node/npx且可联网）: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * uvx传输方式接入mcp-server-time
     * <p>
     * 复用预热阶段已握手的Time客户端，调用get_current_time获取指定时区当前时间。
     * 运行需本机已安装uv，首次执行uvx会联网拉取mcp-server-time。
     * </p>
     */
    private static void runStdioUvxTimeExample(McpToolkit toolkit) {
        McpClient client = toolkit.getClient("Time");
        if (client == null) {
            System.err.println("Time服务端未成功接入（请确认已安装uv且可联网），跳过uvx演示");
            return;
        }
        try {
            List<McpToolDescriptor> tools = client.listTools().block();
            System.out.println("Time服务发现工具数量: " + (tools == null ? 0 : tools.size()));
            if (tools != null) {
                tools.forEach(tool -> System.out.println("  - " + tool.getName()));
            }
            // 查询Asia/Shanghai时区当前时间
            McpToolCallResult now = client.callTool("get_current_time",
                    Map.of("timezone", "Asia/Shanghai")).block();
            System.out.println("get_current_time(Asia/Shanghai)返回: "
                    + (now == null ? "(空)" : now.extractText()));
        } catch (Exception e) {
            System.err.println("uvx MCP接入失败（请确认已安装uv且可联网）: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * streamable-http传输方式接入示例
     * <p>
     * 复用预热阶段已握手的context7客户端，经HTTP端点查询Vue2文档。
     * </p>
     */
    private static void runStreamableHttpExample(McpToolkit toolkit) {
        McpClient client = toolkit.getClient("context7");
        if (client == null) {
            System.err.println("context7服务端未成功接入，跳过streamable-http演示");
            return;
        }
        try {
            List<McpToolDescriptor> tools = client.listTools().block();
            System.out.println("发现工具数量: " + (tools == null ? 0 : tools.size()));
            if (tools != null) {
                tools.forEach(tool -> System.out.println("  - " + tool.getName()));
            }
            // 先解析库标识，再查询Vue2文档中computed计算属性特性的API说明
            McpToolCallResult lib = client.callTool("resolve-library-id", Map.of(
                    "libraryName", "vuejs/vue",
                    "query", "computed properties")).block();
            String libraryId = lib == null ? "" : extractLibraryId(lib.extractText());
            // resolve未取到合法标识时回退到vue仓库的标准ID，保证query-docs能以合法格式入参
            if (!libraryId.startsWith("/")) {
                System.out.println("resolve未取到库标识，回退到/vuejs/vue: " + (lib == null ? "(空)" : lib.extractText()));
                libraryId = "/vuejs/vue";
            }
            System.out.println("resolve-library-id解析结果: " + libraryId);
            McpToolCallResult result = client.callTool("query-docs", Map.of(
                    "libraryId", libraryId,
                    "query", "computed properties",
                    "num_results", "3")).block();
            System.out.println("查询结果(截取前2000字符):");
            if (result != null) {
                String text = result.extractText();
                System.out.println(text == null || text.isBlank() ? "(空)" : text.substring(0, Math.min(2000, text.length())));
            }
        } catch (Exception e) {
            System.err.println("streamable-http接入失败（请确认MCP_HTTP_ENDPOINT可访问且已联网）: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * 从resolve-library-id返回中提取库标识
     * @param text
     * @return
     */
    private static String extractLibraryId(String text) {
        if (text == null || text.isBlank()) {
            return "";
        }
        // 优先从JSON中取libraryId字段
        if (text.trim().startsWith("{")) {
            try {
                String id = new com.fasterxml.jackson.databind.ObjectMapper()
                        .readTree(text).path("libraryId").asText("");
                if (!id.isBlank()) {
                    return id;
                }
            } catch (Exception ignored) {
                // fallthrough到正则提取
            }
        }
        // 取形如/owner/repo或/owner/repo/version的token
        java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("/[A-Za-z0-9._-]+(?:/[A-Za-z0-9._@-]+)+")
                .matcher(text);
        return matcher.find() ? matcher.group() : text.trim();
    }
}