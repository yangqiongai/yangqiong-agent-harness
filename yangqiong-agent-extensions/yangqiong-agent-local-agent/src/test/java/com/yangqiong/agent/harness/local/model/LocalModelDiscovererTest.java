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
package com.yangqiong.agent.harness.local.model;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.util.List;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 本地模型发现测试
 * @author yangqiong
 */
class LocalModelDiscovererTest {

    private HttpServer server;

    private int port;

    @BeforeEach
    void setUp() throws IOException {
        server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        registerContext("/api/tags", "{\"models\":[{\"name\":\"llama3\"}]}");
        registerContext("/v1/models", "{\"data\":[{\"id\":\"qwen2.5\"}]}");
        server.start();
        port = server.getAddress().getPort();
    }

    @AfterEach
    void tearDown() {
        server.stop(0);
    }

    @Test
    void shouldDiscoverOllamaEndpoint() {
        LocalModelDiscoverer discoverer = new LocalModelDiscoverer(
                "http://localhost:" + port, List.of());
        List<LocalModelEndpoint> endpoints = discoverer.discover();
        assertThat(endpoints).hasSize(1);
        LocalModelEndpoint endpoint = endpoints.get(0);
        assertThat(endpoint.type()).isEqualTo(LocalModelEndpointType.OLLAMA);
        assertThat(endpoint.baseUrl()).isEqualTo("http://localhost:" + port);
        assertThat(endpoint.models()).containsExactly("llama3");
    }

    @Test
    void shouldDiscoverOpenAiCompatibleEndpoint() throws IOException {
        // 持有端口占用让Ollama候选不可达，仅模拟OpenAI兼容端点
        try (ServerSocket socket = new ServerSocket(0)) {
            int deadPort = socket.getLocalPort();
            LocalModelDiscoverer discoverer = new LocalModelDiscoverer(
                    "http://localhost:" + deadPort, List.of("http://localhost:" + port + "/v1"));
            List<LocalModelEndpoint> endpoints = discoverer.discover();
            assertThat(endpoints).hasSize(1);
            LocalModelEndpoint endpoint = endpoints.get(0);
            assertThat(endpoint.type()).isEqualTo(LocalModelEndpointType.OPENAI_COMPATIBLE);
            assertThat(endpoint.baseUrl()).isEqualTo("http://localhost:" + port + "/v1");
            assertThat(endpoint.models()).containsExactly("qwen2.5");
        }
    }

    @Test
    void shouldReturnEmptyWhenAllEndpointsUnreachable() throws IOException {
        try (ServerSocket socket = new ServerSocket(0)) {
            int deadPort = socket.getLocalPort();
            LocalModelDiscoverer discoverer = new LocalModelDiscoverer(
                    "http://localhost:" + deadPort,
                    List.of("http://localhost:" + deadPort + "/v1"));
            assertThat(discoverer.discover()).isEmpty();
        }
    }

    @Test
    void shouldIncludeExtraBaseUrls() {
        LocalModelDiscoverer discoverer = new LocalModelDiscoverer(
                List.of("http://localhost:" + port + "/v1"));
        List<LocalModelEndpoint> endpoints = discoverer.discover();
        assertThat(endpoints).anySatisfy(endpoint -> {
            assertThat(endpoint.type()).isEqualTo(LocalModelEndpointType.OPENAI_COMPATIBLE);
            assertThat(endpoint.baseUrl()).isEqualTo("http://localhost:" + port + "/v1");
            assertThat(endpoint.models()).containsExactly("qwen2.5");
        });
    }

    /**
     * 注册返回固定JSON响应的上下文
     * @param path
     * @param responseBody
     */
    private void registerContext(String path, String responseBody) {
        server.createContext(path, exchange -> respondJson(exchange, responseBody));
    }

    /**
     * 写出JSON响应并关闭交换
     * @param exchange
     * @param responseBody
     */
    private static void respondJson(HttpExchange exchange, String responseBody) throws IOException {
        byte[] body = responseBody.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(200, body.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(body);
        }
    }
}
