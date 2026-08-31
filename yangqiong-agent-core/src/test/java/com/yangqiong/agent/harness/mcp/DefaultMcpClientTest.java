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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

/**
 * MCP默认客户端协议流程测试
 * @author yangqiong
 */
class DefaultMcpClientTest {

    @Test
    void initializeSendsHandshakeAndInitializedNotification() throws Exception {
        FakeTransport transport = new FakeTransport();
        transport.enqueue(McpJsonRpc.mapper().readTree(
                "{\"protocolVersion\":\"2025-06-18\",\"capabilities\":{},\"serverInfo\":{\"name\":\"demo\"}}"));
        DefaultMcpClient client = new DefaultMcpClient(config("demo"), transport);

        client.initialize().block(Duration.ofSeconds(5));

        assertThat(transport.methods).containsExactly("initialize");
        ObjectNode initParams = transport.params.get(0);
        assertThat(initParams.get("protocolVersion").asText()).isEqualTo("2025-06-18");
        assertThat(initParams.path("clientInfo").path("name").asText()).isEqualTo("yangqiong-agent-harness");
        assertThat(transport.notifications.get(0)).isEqualTo("notifications/initialized");
        assertThat(client.getNegotiatedProtocolVersion()).isEqualTo("2025-06-18");
    }

    @Test
    void initializeDowngradesToServerVersion() throws Exception {
        FakeTransport transport = new FakeTransport();
        transport.enqueue(McpJsonRpc.mapper().readTree(
                "{\"protocolVersion\":\"2024-11-05\",\"capabilities\":{}}"));
        DefaultMcpClient client = new DefaultMcpClient(config("demo"), transport);

        client.initialize().block(Duration.ofSeconds(5));

        assertThat(client.getNegotiatedProtocolVersion()).isEqualTo("2024-11-05");
    }

    @Test
    void initializeErrorMappedToRpcException() {
        FakeTransport transport = new FakeTransport();
        transport.failNext(new McpRpcException(-32000, "initialize", "服务不可用"));
        DefaultMcpClient client = new DefaultMcpClient(config("demo"), transport);

        assertThatThrownBy(() -> client.initialize().block(Duration.ofSeconds(5)))
                .hasMessageContaining("服务不可用");
    }

    @Test
    void listToolsParsesDescriptors() throws Exception {
        FakeTransport transport = new FakeTransport();
        transport.enqueue(McpJsonRpc.mapper().readTree(
                "{\"tools\":[{\"name\":\"search\",\"description\":\"检索\",\"inputSchema\":"
                        + "{\"type\":\"object\",\"properties\":{\"q\":{\"type\":\"string\"}}}}]}"));
        DefaultMcpClient client = new DefaultMcpClient(config("demo"), transport);

        List<McpToolDescriptor> tools = client.listTools().block(Duration.ofSeconds(5));

        assertThat(tools).hasSize(1);
        assertThat(tools.get(0).getName()).isEqualTo("search");
        assertThat(tools.get(0).getDescription()).isEqualTo("检索");
        assertThat(tools.get(0).getInputSchema()).containsEntry("type", "object");
    }

    @Test
    void listToolsFollowsCursorPagination() throws Exception {
        FakeTransport transport = new FakeTransport();
        transport.enqueue(McpJsonRpc.mapper().readTree(
                "{\"tools\":[{\"name\":\"a\"}],\"nextCursor\":\"c2\"}"));
        transport.enqueue(McpJsonRpc.mapper().readTree(
                "{\"tools\":[{\"name\":\"b\"}]}"));
        DefaultMcpClient client = new DefaultMcpClient(config("demo"), transport);

        List<McpToolDescriptor> tools = client.listTools().block(Duration.ofSeconds(5));

        assertThat(tools).extracting(McpToolDescriptor::getName).containsExactly("a", "b");
        ObjectNode secondParams = transport.params.get(1);
        assertThat(secondParams.get("cursor").asText()).isEqualTo("c2");
    }

    @Test
    void callToolMapsContentAndErrorFlag() throws Exception {
        FakeTransport transport = new FakeTransport();
        transport.enqueue(McpJsonRpc.mapper().readTree(
                "{\"content\":[{\"type\":\"text\",\"text\":\"hello\"},{\"type\":\"other\"}],\"isError\":true}"));
        DefaultMcpClient client = new DefaultMcpClient(config("demo"), transport);

        McpToolCallResult result = client.callTool("echo", Map.of("q", "x"))
                .block(Duration.ofSeconds(5));

        assertThat(result.isError()).isTrue();
        assertThat(result.extractText()).isEqualTo("hello");
        ObjectNode callParams = transport.params.get(0);
        assertThat(callParams.get("name").asText()).isEqualTo("echo");
        assertThat(callParams.path("arguments").path("q").asText()).isEqualTo("x");
    }

    @Test
    void callToolWithNullArgumentsSendsEmptyMap() throws Exception {
        FakeTransport transport = new FakeTransport();
        transport.enqueue(McpJsonRpc.mapper().readTree("{\"content\":[]}"));
        DefaultMcpClient client = new DefaultMcpClient(config("demo"), transport);

        McpToolCallResult result = client.callTool("echo", null).block(Duration.ofSeconds(5));

        assertThat(result.getContent()).isEmpty();
        ObjectNode callParams = transport.params.get(0);
        assertThat(callParams.path("arguments").isObject()).isTrue();
    }

    private McpServerConfig config(String name) {
        return McpServerConfig.builder(name, McpTransportType.STREAMABLE_HTTP)
                .endpoint("http://localhost/mcp")
                .build();
    }

    private static final class FakeTransport implements McpTransport {

        final List<String> methods = new ArrayList<>();

        final List<ObjectNode> params = new ArrayList<>();

        final List<String> notifications = new ArrayList<>();

        private final Deque<JsonNode> results = new ArrayDeque<>();

        private RuntimeException nextFailure;

        void enqueue(JsonNode result) {
            results.add(result);
        }

        void failNext(RuntimeException failure) {
            this.nextFailure = failure;
        }

        @Override
        public Mono<JsonNode> call(String method, ObjectNode callParams) {
            methods.add(method);
            params.add(callParams);
            if (nextFailure != null) {
                RuntimeException failure = nextFailure;
                nextFailure = null;
                return Mono.error(failure);
            }
            if (results.isEmpty()) {
                return Mono.error(new IllegalStateException("无脚本化应答"));
            }
            return Mono.just(results.poll());
        }

        @Override
        public Mono<Void> notify(String method) {
            notifications.add(method);
            return Mono.empty();
        }

        @Override
        public Mono<Void> close() {
            return Mono.empty();
        }
    }
}
