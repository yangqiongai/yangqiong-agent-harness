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
package com.yangqiongai.agent.harness.local.model;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * Ollama文本嵌入测试
 * @author yangqiong
 */
class OllamaEmbeddingModelTest {

    private HttpServer server;

    private String baseUrl;

    /**
     * 捕获到的嵌入请求体
     */
    private final AtomicReference<String> embedRequestBody = new AtomicReference<>();

    @BeforeEach
    void setUp() throws IOException {
        server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/api/tags", exchange ->
                respondJson(exchange, "{\"models\":[{\"name\":\"llama3\"}]}"));
        server.createContext("/api/embed", exchange -> {
            embedRequestBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            respondJson(exchange, "{\"embeddings\":[[0.1,0.2,0.3]]}");
        });
        server.start();
        baseUrl = "http://localhost:" + server.getAddress().getPort();
    }

    @AfterEach
    void tearDown() {
        server.stop(0);
    }

    @Test
    void shouldEmbedTextIntoVector() {
        OllamaEmbeddingModel embeddingModel = new OllamaEmbeddingModel(baseUrl, "nomic-embed-text");
        float[] vector = embeddingModel.embed("hello");
        assertThat(vector).containsExactly(new float[]{0.1f, 0.2f, 0.3f}, within(1e-6f));
        assertThat(embedRequestBody.get())
                .contains("\"model\":\"nomic-embed-text\"")
                .contains("\"input\":\"hello\"");
    }

    @Test
    void shouldReturnDimensionFromEmbedding() {
        OllamaEmbeddingModel embeddingModel = new OllamaEmbeddingModel(baseUrl, "nomic-embed-text");
        assertThat(embeddingModel.dimension()).isEqualTo(3);
    }

    @Test
    void shouldReturnZeroVectorForBlankText() {
        OllamaEmbeddingModel embeddingModel = new OllamaEmbeddingModel(baseUrl, "nomic-embed-text");
        float[] vector = embeddingModel.embed("");
        assertThat(vector).hasSize(3);
        assertThat(vector).containsOnly(0.0f);
    }

    @Test
    void shouldDetectAvailableService() {
        OllamaEmbeddingModel embeddingModel = new OllamaEmbeddingModel(baseUrl, "nomic-embed-text");
        assertThat(embeddingModel.isAvailable()).isTrue();
    }

    @Test
    void shouldDetectUnavailableService() throws IOException {
        // 持有端口占用但不提供HTTP响应，模拟服务不可用
        try (ServerSocket socket = new ServerSocket(0)) {
            OllamaEmbeddingModel embeddingModel = new OllamaEmbeddingModel(
                    "http://localhost:" + socket.getLocalPort(), "nomic-embed-text");
            assertThat(embeddingModel.isAvailable()).isFalse();
        }
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
