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

import static org.assertj.core.api.Assertions.assertThat;

import com.yangqiong.agent.harness.core.tool.AgentToolkit;
import com.yangqiong.agent.harness.core.tool.AgentTool;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

/**
 * MCP工具箱测试，验证多服务端接入、失败跳过与工具注册
 * @author yangqiong
 */
class McpToolkitTest {

    @Test
    void initializeAllConnectsServersAndDiscoversTools() {
        FakeClient alpha = FakeClient.withTools("a1", "a2");
        FakeClient beta = FakeClient.withTools("b1");
        McpToolkit mcp = new McpToolkit(List.of(config("alpha"), config("beta")),
                cfg -> "alpha".equals(cfg.getName()) ? alpha : beta);

        mcp.initializeAll().block(Duration.ofSeconds(5));

        assertThat(mcp.getServerNames()).containsExactlyInAnyOrder("alpha", "beta");
        assertThat(mcp.getTools("alpha")).hasSize(2);
        assertThat(mcp.getClient("beta")).isSameAs(beta);
    }

    @Test
    void registerAddsPrefixedToolsWithMcpCategory() {
        FakeClient alpha = FakeClient.withTools("search", "fetch");
        FakeClient beta = FakeClient.withTools("echo");
        McpToolkit mcp = new McpToolkit(List.of(config("alpha"), config("beta")),
                cfg -> "alpha".equals(cfg.getName()) ? alpha : beta);
        RecordingToolkit engineToolkit = new RecordingToolkit();

        mcp.register(engineToolkit).block(Duration.ofSeconds(5));

        assertThat(engineToolkit.getTools())
                .extracting(AgentTool::getName)
                .containsExactlyInAnyOrder("alpha__search", "alpha__fetch", "beta__echo");
        assertThat(engineToolkit.getTools())
                .allMatch(tool -> McpToolAdapter.TOOL_CATEGORY.equals(tool.getToolCategory()));
    }

    @Test
    void registerWithoutPrefixUsesRawNames() {
        FakeClient alpha = FakeClient.withTools("search");
        McpServerConfig rawNameConfig = McpServerConfig
                .builder("alpha", McpTransportType.STREAMABLE_HTTP)
                .endpoint("http://localhost/mcp")
                .prefixToolNames(false)
                .build();
        McpToolkit mcp = new McpToolkit(List.of(rawNameConfig), cfg -> alpha);
        RecordingToolkit engineToolkit = new RecordingToolkit();

        mcp.register(engineToolkit).block(Duration.ofSeconds(5));

        assertThat(engineToolkit.getTools())
                .extracting(AgentTool::getName)
                .containsExactly("search");
    }

    @Test
    void failedServerIsSkippedAndOthersStillRegistered() {
        FakeClient bad = FakeClient.failing();
        FakeClient good = FakeClient.withTools("ok");
        McpToolkit mcp = new McpToolkit(List.of(config("bad"), config("good")),
                cfg -> "bad".equals(cfg.getName()) ? bad : good);
        RecordingToolkit engineToolkit = new RecordingToolkit();

        mcp.register(engineToolkit).block(Duration.ofSeconds(5));

        assertThat(mcp.getServerNames()).containsExactly("good");
        assertThat(engineToolkit.getTools())
                .extracting(AgentTool::getName)
                .containsExactly("good__ok");
    }

    @Test
    void closeClosesAllConnectedClients() {
        FakeClient alpha = FakeClient.withTools("a1");
        FakeClient beta = FakeClient.withTools("b1");
        McpToolkit mcp = new McpToolkit(List.of(config("alpha"), config("beta")),
                cfg -> "alpha".equals(cfg.getName()) ? alpha : beta);
        mcp.initializeAll().block(Duration.ofSeconds(5));

        mcp.close().block(Duration.ofSeconds(5));

        assertThat(alpha.closed).isTrue();
        assertThat(beta.closed).isTrue();
    }

    @Test
    void initializeAllIsIdempotentAfterWarmup() {
        CountingClient counter = new CountingClient("search");
        McpToolkit mcp = new McpToolkit(List.of(config("alpha")), cfg -> counter);

        mcp.initializeAll().block(Duration.ofSeconds(5));
        mcp.initializeAll().block(Duration.ofSeconds(5));
        mcp.register(new RecordingToolkit()).block(Duration.ofSeconds(5));

        assertThat(counter.initCount).isEqualTo(1);
        assertThat(mcp.isInitialized()).isTrue();
    }

    private McpServerConfig config(String name) {
        return McpServerConfig.builder(name, McpTransportType.STREAMABLE_HTTP)
                .endpoint("http://localhost/mcp")
                .build();
    }

    private static final class FakeClient implements McpClient {

        private final List<McpToolDescriptor> tools;

        private final RuntimeException initFailure;

        private boolean closed;

        static FakeClient withTools(String... names) {
            List<McpToolDescriptor> tools = Arrays.stream(names)
                    .map(name -> new McpToolDescriptor(name, name + "描述", Map.of()))
                    .collect(Collectors.toList());
            return new FakeClient(tools, null);
        }

        static FakeClient failing() {
            return new FakeClient(List.of(), new McpRpcException(-32000, "initialize", "连接失败"));
        }

        private FakeClient(List<McpToolDescriptor> tools, RuntimeException initFailure) {
            this.tools = tools;
            this.initFailure = initFailure;
        }

        @Override
        public Mono<Void> initialize() {
            if (initFailure != null) {
                return Mono.error(initFailure);
            }
            return Mono.empty();
        }

        @Override
        public Mono<List<McpToolDescriptor>> listTools() {
            return Mono.just(tools);
        }

        @Override
        public Mono<McpToolCallResult> callTool(String name, Map<String, Object> arguments) {
            return Mono.just(new McpToolCallResult(
                    List.of(Map.of("type", "text", "text", "ok")), false));
        }

        @Override
        public Mono<Void> close() {
            closed = true;
            return Mono.empty();
        }
    }

    private static final class RecordingToolkit implements AgentToolkit {

        private final List<AgentTool> tools = new ArrayList<>();

        @Override
        public List<AgentTool> getTools() {
            return tools;
        }

        @Override
        public boolean isEmpty() {
            return tools.isEmpty();
        }

        @Override
        public void addTool(AgentTool tool) {
            tools.add(tool);
        }
    }

    private static final class CountingClient implements McpClient {

        private int initCount;

        private final List<McpToolDescriptor> tools;

        CountingClient(String toolName) {
            this.tools = List.of(new McpToolDescriptor(toolName, toolName + "描述", Map.of()));
        }

        @Override
        public Mono<Void> initialize() {
            initCount++;
            return Mono.empty();
        }

        @Override
        public Mono<List<McpToolDescriptor>> listTools() {
            return Mono.just(tools);
        }

        @Override
        public Mono<McpToolCallResult> callTool(String name, Map<String, Object> arguments) {
            return Mono.just(new McpToolCallResult(
                    List.of(Map.of("type", "text", "text", "ok")), false));
        }

        @Override
        public Mono<Void> close() {
            return Mono.empty();
        }
    }
}
