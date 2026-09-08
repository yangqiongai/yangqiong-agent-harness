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

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.yangqiongai.agent.harness.model.embedding.EmbeddingModel;

/**
 * Ollama文本嵌入
 * <p>
 * 调用Ollama的/api/embed端点将文本映射为稠密向量，
 * 向量维度由服务端模型决定，首次嵌入后缓存。
 * </p>
 * @author yangqiong
 */
public class OllamaEmbeddingModel implements EmbeddingModel {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /**
     * 嵌入请求超时
     */
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(30);

    /**
     * 可用性探测超时
     */
    private static final Duration PROBE_TIMEOUT = Duration.ofSeconds(1);

    /**
     * 服务基础地址，不含尾部斜杠
     */
    private final String baseUrl;

    /**
     * 嵌入模型名称
     */
    private final String model;

    /**
     * HTTP客户端（复用连接）
     */
    private final HttpClient httpClient;

    /**
     * 缓存的向量维度，首次嵌入后确定
     */
    private volatile int cachedDimension = -1;

    /**
     * 构造Ollama文本嵌入
     * @param baseUrl
     * @param model
     */
    public OllamaEmbeddingModel(String baseUrl, String model) {
        if (baseUrl == null || baseUrl.isBlank()) {
            throw new IllegalArgumentException("baseUrl 不能为空");
        }
        if (model == null || model.isBlank()) {
            throw new IllegalArgumentException("model 不能为空");
        }
        this.baseUrl = normalizeBaseUrl(baseUrl);
        this.model = model;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(1))
                .build();
    }

    /**
     * 计算文本嵌入向量，输入为null或空文本时返回全零向量
     * @param text
     * @return
     */
    @Override
    public float[] embed(String text) {
        if (text == null || text.isBlank()) {
            return new float[dimension()];
        }
        ObjectNode body = MAPPER.createObjectNode();
        body.put("model", model);
        body.put("input", text);
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/api/embed"))
                .header("Content-Type", "application/json")
                .timeout(REQUEST_TIMEOUT)
                .POST(HttpRequest.BodyPublishers.ofString(body.toString()))
                .build();
        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 400) {
                throw new RuntimeException("Ollama嵌入接口返回错误码: " + response.statusCode()
                        + ", 响应: " + response.body());
            }
            return parseEmbedding(response.body());
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("Ollama嵌入调用失败", e);
        }
    }

    /**
     * 获取嵌入向量维度，首次调用会触发一次真实嵌入请求探测
     * @return
     */
    @Override
    public int dimension() {
        int dimension = cachedDimension;
        if (dimension < 0) {
            dimension = embed("dimension").length;
            cachedDimension = dimension;
        }
        return dimension;
    }

    /**
     * 探测Ollama服务是否可用
     * @return
     */
    public boolean isAvailable() {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/api/tags"))
                    .timeout(PROBE_TIMEOUT)
                    .GET()
                    .build();
            HttpResponse<Void> response = httpClient.send(request, HttpResponse.BodyHandlers.discarding());
            return response.statusCode() < 400;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 解析嵌入响应，取embeddings数组的首个向量
     * @param responseBody
     * @return
     */
    private float[] parseEmbedding(String responseBody) throws Exception {
        JsonNode root = MAPPER.readTree(responseBody);
        JsonNode embeddings = root.get("embeddings");
        if (embeddings == null || !embeddings.isArray() || embeddings.size() == 0) {
            throw new RuntimeException("Ollama嵌入响应缺少embeddings数组: " + responseBody);
        }
        JsonNode first = embeddings.get(0);
        float[] vector = new float[first.size()];
        for (int i = 0; i < first.size(); i++) {
            vector[i] = (float) first.get(i).asDouble();
        }
        cachedDimension = vector.length;
        return vector;
    }

    /**
     * 规范化基础地址，去除尾部斜杠
     * @param url
     * @return
     */
    private static String normalizeBaseUrl(String url) {
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }
}
