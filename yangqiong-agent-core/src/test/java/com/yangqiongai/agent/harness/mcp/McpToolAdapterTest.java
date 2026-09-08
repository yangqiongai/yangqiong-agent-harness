/*
 * Copyright 2026 yangqiongai.com
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
package com.yangqiongai.agent.harness.mcp;

import static org.assertj.core.api.Assertions.assertThat;

import com.yangqiongai.agent.harness.core.message.AgentToolResultBlock;
import com.yangqiongai.agent.harness.core.tool.AgentToolCallParam;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

/**
 * MCP工具适配器测试
 * @author yangqiong
 */
class McpToolAdapterTest {

    @Test
    void exposesPrefixedNameAndMcpCategory() {
        McpToolAdapter adapter = McpToolAdapter.prefixed("demo", new FakeClient(), descriptor("search"));

        assertThat(adapter.getName()).isEqualTo("demo__search");
        assertThat(adapter.getToolCategory()).isEqualTo("mcp");
        assertThat(adapter.getDescription()).isEqualTo("检索");
        assertThat(adapter.getParameters()).containsEntry("type", "object");
    }

    @Test
    void callAsyncMapsTextContentToResultBlock() {
        McpToolCallResult result = new McpToolCallResult(
                List.of(Map.of("type", "text", "text", "命中1"), Map.of("type", "text", "text", "命中2")),
                false);
        McpToolAdapter adapter = new McpToolAdapter("demo__search",
                new FakeClient(result), descriptor("search"));

        AgentToolResultBlock block = adapter.callAsync(new AgentToolCallParam(Map.of("q", "kw")))
                .block(Duration.ofSeconds(5));

        assertThat(block.isError()).isFalse();
        assertThat(block.getTextContent()).isEqualTo("命中1命中2");
    }

    @Test
    void callAsyncMapsToolErrorToErrorBlock() {
        McpToolCallResult result = new McpToolCallResult(
                List.of(Map.of("type", "text", "text", "上游超时")), true);
        McpToolAdapter adapter = new McpToolAdapter("demo__search",
                new FakeClient(result), descriptor("search"));

        AgentToolResultBlock block = adapter.callAsync(new AgentToolCallParam(Map.of()))
                .block(Duration.ofSeconds(5));

        assertThat(block.isError()).isTrue();
        assertThat(block.getTextContent()).contains("上游超时");
    }

    @Test
    void callAsyncMapsTransportFailureToErrorBlock() {
        McpClient failing = new McpClient() {
            @Override
            public Mono<Void> initialize() {
                return Mono.empty();
            }

            @Override
            public Mono<List<McpToolDescriptor>> listTools() {
                return Mono.just(List.of());
            }

            @Override
            public Mono<McpToolCallResult> callTool(String name, Map<String, Object> arguments) {
                return Mono.error(new McpRpcException(-32000, "tools/call", "连接重置"));
            }

            @Override
            public Mono<Void> close() {
                return Mono.empty();
            }
        };
        McpToolAdapter adapter = new McpToolAdapter("demo__search", failing, descriptor("search"));

        AgentToolResultBlock block = adapter.callAsync(new AgentToolCallParam(Map.of()))
                .block(Duration.ofSeconds(5));

        assertThat(block.isError()).isTrue();
        assertThat(block.getTextContent()).contains("连接重置");
    }

    private McpToolDescriptor descriptor(String name) {
        return new McpToolDescriptor(name, "检索", Map.of("type", "object"));
    }

    private static final class FakeClient implements McpClient {

        private final McpToolCallResult result;

        FakeClient() {
            this(null);
        }

        FakeClient(McpToolCallResult result) {
            this.result = result;
        }

        @Override
        public Mono<Void> initialize() {
            return Mono.empty();
        }

        @Override
        public Mono<List<McpToolDescriptor>> listTools() {
            return Mono.just(List.of());
        }

        @Override
        public Mono<McpToolCallResult> callTool(String name, Map<String, Object> arguments) {
            return result != null ? Mono.just(result) : Mono.empty();
        }

        @Override
        public Mono<Void> close() {
            return Mono.empty();
        }
    }
}
