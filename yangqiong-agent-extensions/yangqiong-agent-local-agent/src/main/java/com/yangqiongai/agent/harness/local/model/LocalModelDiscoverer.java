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
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 本地模型发现
 * <p>
 * 单线程顺序探测本机常见推理服务：Ollama（http://localhost:11434）、
 * LM Studio（http://localhost:1234/v1）与llama.cpp（http://localhost:8080/v1），
 * 也可通过构造器追加额外的OpenAI兼容端点。任何端点探测失败均直接跳过，不抛出异常。
 * </p>
 * @author yangqiong
 */
public class LocalModelDiscoverer {

    private static final Logger log = LoggerFactory.getLogger(LocalModelDiscoverer.class);

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /**
     * Ollama默认基础地址
     */
    private static final String DEFAULT_OLLAMA_BASE_URL = "http://localhost:11434";

    /**
     * OpenAI兼容协议默认候选地址（LM Studio与llama.cpp）
     */
    private static final List<String> DEFAULT_OPENAI_COMPATIBLE_BASE_URLS =
            List.of("http://localhost:1234/v1", "http://localhost:8080/v1");

    /**
     * 探测连接超时
     */
    private static final Duration CONNECT_TIMEOUT = Duration.ofMillis(500);

    /**
     * 探测请求超时
     */
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(1);

    /**
     * Ollama候选基础地址
     */
    private final String ollamaBaseUrl;

    /**
     * OpenAI兼容协议候选地址
     */
    private final List<String> openAiCompatibleBaseUrls;

    /**
     * HTTP客户端（复用连接）
     */
    private final HttpClient httpClient;

    /**
     * 构造本地模型发现器，仅探测默认候选端点
     */
    public LocalModelDiscoverer() {
        this(DEFAULT_OLLAMA_BASE_URL, DEFAULT_OPENAI_COMPATIBLE_BASE_URLS);
    }

    /**
     * 构造本地模型发现器，在默认候选之外追加额外的OpenAI兼容端点
     * @param extraOpenAiCompatibleBaseUrls
     */
    public LocalModelDiscoverer(List<String> extraOpenAiCompatibleBaseUrls) {
        this(DEFAULT_OLLAMA_BASE_URL, mergeBaseUrls(extraOpenAiCompatibleBaseUrls));
    }

    /**
     * 构造本地模型发现器，完全指定候选端点
     * @param ollamaBaseUrl
     * @param openAiCompatibleBaseUrls
     */
    LocalModelDiscoverer(String ollamaBaseUrl, List<String> openAiCompatibleBaseUrls) {
        this.ollamaBaseUrl = normalizeBaseUrl(ollamaBaseUrl);
        this.openAiCompatibleBaseUrls = openAiCompatibleBaseUrls == null
                ? List.of() : List.copyOf(openAiCompatibleBaseUrls);
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(CONNECT_TIMEOUT)
                .build();
    }

    /**
     * 探测本地可用的模型端点及其模型列表
     * @return
     */
    public List<LocalModelEndpoint> discover() {
        List<LocalModelEndpoint> endpoints = new ArrayList<>();
        LocalModelEndpoint ollama = probeOllama(ollamaBaseUrl);
        if (ollama != null) {
            endpoints.add(ollama);
        }
        for (String baseUrl : openAiCompatibleBaseUrls) {
            LocalModelEndpoint endpoint = probeOpenAiCompatible(baseUrl);
            if (endpoint != null) {
                endpoints.add(endpoint);
            }
        }
        return endpoints;
    }

    /**
     * 探测Ollama端点，解析/api/tags响应中的模型名
     * @param baseUrl
     * @return 端点不可达时返回null
     */
    private LocalModelEndpoint probeOllama(String baseUrl) {
        String normalized = normalizeBaseUrl(baseUrl);
        try {
            JsonNode root = httpGetJson(normalized + "/api/tags");
            if (root == null) {
                return null;
            }
            List<String> models = new ArrayList<>();
            JsonNode modelsNode = root.get("models");
            if (modelsNode != null && modelsNode.isArray()) {
                for (JsonNode modelNode : modelsNode) {
                    JsonNode nameNode = modelNode.get("name");
                    if (nameNode != null && !nameNode.isNull()) {
                        models.add(nameNode.asText());
                    }
                }
            }
            return new LocalModelEndpoint(LocalModelEndpointType.OLLAMA, normalized, models);
        } catch (Exception e) {
            log.debug("Ollama端点探测失败: {}", baseUrl);
            return null;
        }
    }

    /**
     * 探测OpenAI兼容端点，解析/v1/models响应中的模型id
     * @param baseUrl
     * @return 端点不可达时返回null
     */
    private LocalModelEndpoint probeOpenAiCompatible(String baseUrl) {
        String normalized = normalizeBaseUrl(baseUrl);
        try {
            JsonNode root = httpGetJson(normalized + "/models");
            if (root == null) {
                return null;
            }
            List<String> models = new ArrayList<>();
            JsonNode dataNode = root.get("data");
            if (dataNode != null && dataNode.isArray()) {
                for (JsonNode modelNode : dataNode) {
                    JsonNode idNode = modelNode.get("id");
                    if (idNode != null && !idNode.isNull()) {
                        models.add(idNode.asText());
                    }
                }
            }
            return new LocalModelEndpoint(LocalModelEndpointType.OPENAI_COMPATIBLE, normalized, models);
        } catch (Exception e) {
            log.debug("OpenAI兼容端点探测失败: {}", baseUrl);
            return null;
        }
    }

    /**
     * 发送短超时GET请求并解析JSON响应
     * @param url
     * @return 状态码异常时返回null
     */
    private JsonNode httpGetJson(String url) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(REQUEST_TIMEOUT)
                .GET()
                .build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() >= 400) {
            return null;
        }
        return MAPPER.readTree(response.body());
    }

    /**
     * 合并默认候选与额外候选，保持顺序并去重
     * @param extraOpenAiCompatibleBaseUrls
     * @return
     */
    private static List<String> mergeBaseUrls(List<String> extraOpenAiCompatibleBaseUrls) {
        Set<String> merged = new LinkedHashSet<>(DEFAULT_OPENAI_COMPATIBLE_BASE_URLS);
        if (extraOpenAiCompatibleBaseUrls != null) {
            for (String baseUrl : extraOpenAiCompatibleBaseUrls) {
                if (baseUrl != null && !baseUrl.isBlank()) {
                    merged.add(normalizeBaseUrl(baseUrl));
                }
            }
        }
        return List.copyOf(merged);
    }

    /**
     * 规范化基础地址，去除尾部斜杠
     * @param url
     * @return
     */
    private static String normalizeBaseUrl(String url) {
        if (url == null || url.isBlank()) {
            return url;
        }
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }
}
