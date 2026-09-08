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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import reactor.core.publisher.Mono;

/**
 * MCP流式HTTP传输，每次请求POST报文，响应为单个JSON或SSE封装
 * @author yangqiong
 */
public class McpHttpStreamableTransport implements McpTransport {

    /**
     * 服务端地址
     */
    private final String endpoint;

    /**
     * 附加请求头
     */
    private final java.util.Map<String, String> headers;

    /**
     * 单次请求超时
     */
    private final Duration requestTimeout;

    /**
     * JDK共享HTTP客户端
     */
    private final HttpClient httpClient;

    /**
     * 请求id自增序列
     */
    private final AtomicLong idSequence = new AtomicLong(1);

    /**
     * 服务端分配的会话标识，initialize响应后携带
     */
    private volatile String sessionId;

    public McpHttpStreamableTransport(String endpoint, java.util.Map<String, String> headers,
            Duration requestTimeout) {
        this.endpoint = endpoint;
        this.headers = headers != null ? java.util.Map.copyOf(headers) : java.util.Map.of();
        this.requestTimeout = requestTimeout != null ? requestTimeout : Duration.ofSeconds(60);
        this.httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15)).build();
    }

    /**
     * 发送JSON-RPC请求并解析响应
     * @param method
     * @param params
     * @return
     */
    @Override
    public Mono<JsonNode> call(String method, ObjectNode params) {
        return Mono.fromCallable(() -> doCall(method, params)).timeout(requestTimeout);
    }

    /**
     * 发送JSON-RPC通知
     * @param method
     * @return
     */
    @Override
    public Mono<Void> notify(String method) {
        return notify(method, null);
    }

    /**
     * 发送带参数的JSON-RPC通知
     * @param method
     * @param params
     * @return
     */
    @Override
    public Mono<Void> notify(String method, ObjectNode params) {
        return Mono.fromCallable(() -> {
            doNotify(method, params);
            return null;
        }).timeout(requestTimeout).then();
    }

    /**
     * 释放连接资源（JDK HttpClient无显式关闭，置空会话）
     * @return
     */
    @Override
    public Mono<Void> close() {
        return Mono.fromRunnable(() -> sessionId = null);
    }

    private JsonNode doCall(String method, ObjectNode params) throws IOException, InterruptedException {
        long id = idSequence.getAndIncrement();
        ObjectNode request = McpJsonRpc.request(id, method, params);
        HttpResponse<String> response = exchange(request);
        captureSession(response);
        JsonNode result = resolveResponse(method, id, response);
        return result;
    }

    private void doNotify(String method, ObjectNode params) throws IOException, InterruptedException {
        ObjectNode notification = McpJsonRpc.notification(method, params);
        exchange(notification);
    }

    private HttpResponse<String> exchange(ObjectNode payload) throws IOException, InterruptedException {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(endpoint))
                .timeout(requestTimeout)
                .header("Content-Type", "application/json")
                .header("Accept", "application/json, text/event-stream")
                .POST(HttpRequest.BodyPublishers.ofString(payload.toString(), java.nio.charset.StandardCharsets.UTF_8));
        headers.forEach(builder::header);
        String currentSession = sessionId;
        if (currentSession != null) {
            builder.header("Mcp-Session-Id", currentSession);
        }
        return httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }

    private void captureSession(HttpResponse<String> response) {
        List<String> sessionValues = response.headers().allValues("Mcp-Session-Id");
        if (!sessionValues.isEmpty() && sessionId == null) {
            sessionId = sessionValues.get(0);
        }
    }

    private JsonNode resolveResponse(String method, long id, HttpResponse<String> response) {
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new McpRpcException(response.statusCode(), method,
                    method + "HTTP状态异常：" + response.statusCode());
        }
        String contentType = response.headers().firstValue("Content-Type").orElse("");
        if (contentType.contains("text/event-stream")) {
            return resolveSseResponseBody(method, id, response.body());
        }
        return resolveJsonResponseBody(method, id, response.body());
    }

    private JsonNode resolveSseResponseBody(String method, long id, String body) {
        McpJsonRpc.SseEventAccumulator accumulator = new McpJsonRpc.SseEventAccumulator();
        for (String line : body.split("\n", -1)) {
            String normalized = line.endsWith("\r") ? line.substring(0, line.length() - 1) : line;
            java.util.Optional<McpJsonRpc.SseEvent> event = accumulator.feed(normalized);
            if (event.isPresent()) {
                java.util.Optional<JsonNode> json = event.get().parseJson();
                if (json.isPresent() && McpJsonRpc.isResponse(json.get())) {
                    return matchResponse(method, id, json.get());
                }
            }
        }
        throw new McpRpcException(-32603, method, method + "SSE响应中未找到请求id=" + id + "的应答");
    }

    private JsonNode resolveJsonResponseBody(String method, long id, String body) {
        JsonNode json;
        try {
            json = McpJsonRpc.mapper().readTree(body);
        } catch (Exception e) {
            throw new McpRpcException(-32700, method, method + "响应体非JSON：" + e.getMessage());
        }
        if (!McpJsonRpc.isResponse(json)) {
            throw new McpRpcException(-32603, method, method + "响应不是JSON-RPC应答");
        }
        return matchResponse(method, id, json);
    }

    private JsonNode matchResponse(String method, long id, JsonNode response) {
        JsonNode responseId = response.get("id");
        if (responseId != null && responseId.isIntegralNumber() && responseId.asLong() != id) {
            throw new McpRpcException(-32603, method, method + "响应id不匹配：期望" + id + "实际" + responseId.asLong());
        }
        return McpJsonRpc.extractResult(response, method);
    }
}
