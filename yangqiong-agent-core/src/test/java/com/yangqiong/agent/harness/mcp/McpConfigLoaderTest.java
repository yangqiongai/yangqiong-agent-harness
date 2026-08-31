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
package com.yangqiong.agent.harness.mcp;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * MCP服务器配置YAML加载测试
 * @author yangqiong
 */
class McpConfigLoaderTest {

    /**
     * 验证多服务mcpServers形态解析
     */
    @Test
    void loadMultipleServers() {
        String yaml = "mcpServers:\n"
                + "  context7:\n"
                + "    command: D:\\nodejs\\node\\node.exe\n"
                + "    args: [\"npx-cli.js\", \"-y\", \"@upstash/context7-mcp@latest\"]\n"
                + "    env:\n"
                + "      DEFAULT_MINIMUM_TOKENS: \"10000\"\n"
                + "    initTimeout: 3m\n"
                + "    requestTimeout: 2m\n"
                + "    prefixToolNames: true\n"
                + "  remote:\n"
                + "    transport: streamable-http\n"
                + "    endpoint: https://mcp.example.com/sse\n"
                + "    headers:\n"
                + "      Authorization: \"Bearer token\"\n";
        List<McpServerConfig> configs = McpConfigLoader.fromYaml(yaml);
        assertThat(configs).hasSize(2);

        McpServerConfig context7 = configs.get(0);
        assertThat(context7.getName()).isEqualTo("context7");
        assertThat(context7.getTransport()).isEqualTo(McpTransportType.STDIO);
        assertThat(context7.getCommand()).isEqualTo("D:\\nodejs\\node\\node.exe");
        assertThat(context7.getArgs()).containsExactly("npx-cli.js", "-y", "@upstash/context7-mcp@latest");
        assertThat(context7.getEnv()).containsEntry("DEFAULT_MINIMUM_TOKENS", "10000");
        assertThat(context7.getInitTimeout()).isEqualTo(Duration.ofMinutes(3));
        assertThat(context7.getRequestTimeout()).isEqualTo(Duration.ofMinutes(2));
        assertThat(context7.isPrefixToolNames()).isTrue();

        McpServerConfig remote = configs.get(1);
        assertThat(remote.getName()).isEqualTo("remote");
        assertThat(remote.getTransport()).isEqualTo(McpTransportType.STREAMABLE_HTTP);
        assertThat(remote.getEndpoint()).isEqualTo("https://mcp.example.com/sse");
        assertThat(remote.getHeaders()).containsEntry("Authorization", "Bearer token");
    }

    /**
     * 验证单服务根节点形态解析
     */
    @Test
    void loadSingleServer() {
        String yaml = "name: demo\n"
                + "transport: sse\n"
                + "endpoint: https://demo.example.com/sse\n"
                + "prefixToolNames: false\n";
        List<McpServerConfig> configs = McpConfigLoader.fromYaml(yaml);
        assertThat(configs).hasSize(1);
        McpServerConfig config = configs.get(0);
        assertThat(config.getName()).isEqualTo("demo");
        assertThat(config.getTransport()).isEqualTo(McpTransportType.SSE);
        assertThat(config.getEndpoint()).isEqualTo("https://demo.example.com/sse");
        assertThat(config.isPrefixToolNames()).isFalse();
    }

    /**
     * 验证transport缺省为stdio
     */
    @Test
    void transportDefaultsToStdio() {
        String yaml = "mcpServers:\n  tool:\n    command: node\n";
        McpServerConfig config = McpConfigLoader.fromYaml(yaml).get(0);
        assertThat(config.getTransport()).isEqualTo(McpTransportType.STDIO);
    }

    /**
     * 验证空或无效内容返回空列表
     */
    @Test
    void emptyContentYieldsEmptyList() {
        assertThat(McpConfigLoader.fromYaml("")).isEmpty();
        assertThat(McpConfigLoader.fromYaml("nested: {a: 1}")).isEmpty();
    }

    /**
     * 验证context7风格的JSON多服务配置解析
     */
    @Test
    void loadFromJson() {
        String json = "{"
                + "  \"mcpServers\": {"
                + "    \"context7\": {"
                + "      \"command\": \"npx\","
                + "      \"args\": [\"-y\", \"@upstash/context7-mcp@latest\"],"
                + "      \"env\": { \"DEFAULT_MINIMUM_TOKENS\": \"10000\" }"
                + "    }"
                + "  }"
                + "}";
        List<McpServerConfig> configs = McpConfigLoader.fromJson(json);
        assertThat(configs).hasSize(1);
        McpServerConfig context7 = configs.get(0);
        assertThat(context7.getName()).isEqualTo("context7");
        assertThat(context7.getTransport()).isEqualTo(McpTransportType.STDIO);
        // 本机装有node时裸npx会被自动重写为node.exe+npx-cli.js，否则保持原样
        String command = context7.getCommand();
        List<String> args = context7.getArgs();
        boolean isBare = command.equals("npx") && args.contains("-y");
        boolean isRewritten = command.endsWith("node.exe") && !args.isEmpty()
                && args.get(0).endsWith("npx-cli.js") && args.contains("-y");
        assertThat(isBare || isRewritten).as("裸npx应兼容原样或自动重写").isTrue();
        assertThat(context7.getEnv()).containsEntry("DEFAULT_MINIMUM_TOKENS", "10000");
    }

    /**
     * 验证平台相关node可执行文件名：Windows为node.exe，其余为node
     */
    @Test
    void nodeExecutableNameMatchesPlatform() {
        String name = McpConfigLoader.nodeExecutableName();
        String os = System.getProperty("os.name", "").toLowerCase();
        if (os.contains("win")) {
            assertThat(name).isEqualTo("node.exe");
        } else {
            assertThat(name).isEqualTo("node");
        }
    }

    /**
     * 验证PATH中查找npx可执行文件：优先命中npx.cmd，按目录顺序
     */
    @Test
    void locateOnPathFindsNpxInPathOrder() throws Exception {
        // 沙箱限制系统临时目录，改在模块target目录下创建
        java.nio.file.Path dir = Files.createTempDirectory(Path.of("target"), "npx-path-test");
        try {
            Files.createFile(dir.resolve("npx.cmd"));
            java.nio.file.Path found = McpConfigLoader.locateOnPath(
                    new String[]{"npx.cmd", "npx.bat", "npx"}, dir.toString());
            assertThat(found).isNotNull();
            assertThat(found.getFileName().toString()).isEqualTo("npx.cmd");
        } finally {
            try (var stream = Files.list(dir)) {
                stream.forEach(p -> {
                    try {
                        Files.deleteIfExists(p);
                    } catch (java.io.IOException ignored) {
                        // 清理忽略
                    }
                });
            }
            Files.deleteIfExists(dir);
        }
    }

    /**
     * 验证PATH为空或目录中无目标时返回null
     */
    @Test
    void locateOnPathReturnsNullWhenMissing() {
        assertThat(McpConfigLoader.locateOnPath(new String[]{"npx.cmd"}, (String) null)).isNull();
        assertThat(McpConfigLoader.locateOnPath(new String[]{"npx.cmd"}, "")).isNull();
    }

    /**
     * 验证禁用npx解析时裸npx命令保持原样
     */
    @Test
    void bareNpxKeptWhenResolveDisabled() {
        String json = "{\"mcpServers\":{\"context7\":{\"command\":\"npx\","
                + "\"args\":[\"-y\",\"@upstash/context7-mcp@latest\"]}}}";
        System.setProperty("mcp.disableNpxResolve", "true");
        try {
            McpServerConfig context7 = McpConfigLoader.fromJson(json).get(0);
            assertThat(context7.getCommand()).isEqualTo("npx");
            assertThat(context7.getArgs()).containsExactly("-y", "@upstash/context7-mcp@latest");
        } finally {
            System.clearProperty("mcp.disableNpxResolve");
        }
    }
}