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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * MCP流式HTTP传输测试，基于JDK内置HttpServer模拟MCP服务端
 * @author yangqiong
 */
class McpHttpStreamableTransportTest {

    private HttpServer server;

    private RecordingHandler handler;

    private McpHttpStreamableTransport transport;

    @BeforeEach
    void startServer() throws IOException {
        handler = new RecordingHandler();
        try {
            server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        } catch (IOException e) {
            // 受限网络环境无法创建本地监听端口时跳过本用例
            Assumptions.abort("当前环境无法创建本地HttpServer：" + e.getMessage());
        }
        server.createContext("/mcp", handler);
        server.start();
        String endpoint = "http://localhost:" + server.getAddress().getPort() + "/mcp";
        transport = new McpHttpStreamableTransport(endpoint,
                Map.of("Authorization", "Bearer test-token"), Duration.ofSeconds(5));
    }

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void callParsesJsonResponseAndSendsAuthHeader() {
        handler.nextBody = "{\"jsonrpc\":\"2.0\",\"id\":{ID},\"result\":{\"tools\":[{\"name\":\"a\"}]}}";
        handler.nextContentType = "application/json";

        ObjectNode params = McpJsonRpc.mapper().createObjectNode();
        JsonNode result = transport.call("tools/list", params).block(Duration.ofSeconds(5));

        assertThat(result.path("tools").get(0).path("name").asText()).isEqualTo("a");
        assertThat(handler.receivedAuthHeaders).containsExactly("Bearer test-token");
        assertThat(handler.receivedBodies.get(0)).contains("\"method\":\"tools/list\"");
        assertThat(handler.receivedAccept.get(0)).contains("text/event-stream");
    }

    @Test
    void callParsesSseFramedResponse() {
        handler.nextBody = "event: message\r\ndata: {\"jsonrpc\":\"2.0\",\"id\":{ID},\"result\":{\"ok\":true}}\r\n\r\n";
        handler.nextContentType = "text/event-stream";

        JsonNode result = transport.call("tools/call", McpJsonRpc.mapper().createObjectNode())
                .block(Duration.ofSeconds(5));

        assertThat(result.path("ok").asBoolean()).isTrue();
    }

    @Test
    void sessionHeaderCapturedAndResent() {
        handler.nextBody = "{\"jsonrpc\":\"2.0\",\"id\":{ID},\"result\":{}}";
        handler.nextContentType = "application/json";
        handler.nextSessionId = "session-42";

        transport.call("initialize", McpJsonRpc.mapper().createObjectNode())
                .block(Duration.ofSeconds(5));
        transport.call("tools/list", McpJsonRpc.mapper().createObjectNode())
                .block(Duration.ofSeconds(5));

        assertThat(handler.receivedSessionIds).containsExactly(null, "session-42");
    }

    @Test
    void errorResponseMappedToRpcException() {
        handler.nextBody = "{\"jsonrpc\":\"2.0\",\"id\":{ID},\"error\":{\"code\":-32601,\"message\":\"方法不存在\"}}";
        handler.nextContentType = "application/json";

        assertThatThrownBy(() -> transport.call("tools/list", McpJsonRpc.mapper().createObjectNode())
                .block(Duration.ofSeconds(5)))
                .hasMessageContaining("方法不存在");
    }

    @Test
    void mismatchedResponseIdRejected() {
        handler.nextBody = "{\"jsonrpc\":\"2.0\",\"id\":999,\"result\":{}}";
        handler.nextContentType = "application/json";

        assertThatThrownBy(() -> transport.call("tools/list", McpJsonRpc.mapper().createObjectNode())
                .block(Duration.ofSeconds(5)))
                .hasMessageContaining("响应id不匹配");
    }

    private static final class RecordingHandler implements HttpHandler {

        volatile String nextBody;

        volatile String nextContentType = "application/json";

        volatile String nextSessionId;

        final List<String> receivedBodies = new CopyOnWriteArrayList<>();

        final List<String> receivedAuthHeaders = new CopyOnWriteArrayList<>();

        final List<String> receivedAccept = new CopyOnWriteArrayList<>();

        final List<String> receivedSessionIds = new CopyOnWriteArrayList<>();

        @Override
        public void handle(HttpExchange exchange) throws IOException {
            receivedAuthHeaders.add(exchange.getRequestHeaders().getFirst("Authorization"));
            receivedAccept.add(exchange.getRequestHeaders().getFirst("Accept"));
            receivedSessionIds.add(exchange.getRequestHeaders().getFirst("Mcp-Session-Id"));
            String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            receivedBodies.add(body);
            String id = extractId(body);
            String payload = nextBody.replace("{ID}", id);
            byte[] bytes = payload.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", nextContentType);
            if (nextSessionId != null) {
                exchange.getResponseHeaders().set("Mcp-Session-Id", nextSessionId);
            }
            exchange.sendResponseHeaders(200, bytes.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(bytes);
            }
        }

        private String extractId(String body) {
            com.fasterxml.jackson.databind.JsonNode node;
            try {
                node = McpJsonRpc.mapper().readTree(body);
            } catch (Exception e) {
                return "0";
            }
            return node.has("id") ? node.get("id").asText() : "0";
        }
    }
}
