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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.MonoSink;
import reactor.core.publisher.Sinks;

/**
 * MCP旧版SSE传输，GET建立事件流接收响应，POST消息端点发送请求，按id关联
 * @author yangqiong
 */
public class McpHttpSseTransport implements McpTransport {

    private static final Logger log = LoggerFactory.getLogger(McpHttpSseTransport.class);

    /**
     * SSE基础地址
     */
    private final String baseUrl;

    /**
     * 附加请求头
     */
    private final Map<String, String> headers;

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
     * 待应答请求挂起表
     */
    private final Map<Long, MonoSink<JsonNode>> pending = new ConcurrentHashMap<>();

    /**
     * 服务端公告的消息端点
     */
    private volatile CompletableFuture<String> messageEndpointFuture;

    /**
     * 事件流订阅句柄
     */
    private volatile Disposable streamSubscription;

    /**
     * 传输是否已关闭，关闭后不再重连
     */
    private volatile boolean closed;

    /**
     * 服务端主动推送通知转发
     */
    private final Sinks.Many<JsonNode> notificationsSink = Sinks.many().multicast().onBackpressureBuffer();

    public McpHttpSseTransport(String baseUrl, Map<String, String> headers, Duration requestTimeout) {
        this.baseUrl = baseUrl;
        this.headers = headers != null ? Map.copyOf(headers) : Map.of();
        this.requestTimeout = requestTimeout != null ? requestTimeout : Duration.ofSeconds(60);
        this.httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15)).build();
    }

    /**
     * 发送JSON-RPC请求，应答经事件流按id回送
     * @param method
     * @param params
     * @return
     */
    @Override
    public Mono<JsonNode> call(String method, ObjectNode params) {
        return ensureConnected().timeout(requestTimeout).then(Mono.create(sink -> {
            long id = idSequence.getAndIncrement();
            pending.put(id, sink);
            sink.onDispose(() -> pending.remove(id));
            postMessage(McpJsonRpc.request(id, method, params), method, id, sink);
        }));
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
        return ensureConnected().timeout(requestTimeout).then(Mono.fromRunnable(() -> {
            try {
                postMessage(McpJsonRpc.notification(method, params), method, null, null);
            } catch (Exception e) {
                throw new McpRpcException(-32603, method, method + "通知发送失败：" + e.getMessage());
            }
        }));
    }

    /**
     * 订阅服务端主动推送的JSON-RPC通知
     * @return
     */
    @Override
    public Flux<JsonNode> notifications() {
        return notificationsSink.asFlux();
    }

    /**
     * 关闭事件流并失败所有挂起请求
     * @return
     */
    @Override
    public Mono<Void> close() {
        return Mono.fromRunnable(() -> {
            closed = true;
            Disposable subscription = streamSubscription;
            if (subscription != null && !subscription.isDisposed()) {
                subscription.dispose();
            }
            pending.forEach((id, sink) -> sink.error(new McpRpcException(-32603, "close", "传输已关闭")));
            pending.clear();
            notificationsSink.tryEmitComplete();
        });
    }

    private Mono<Void> ensureConnected() {
        if (closed) {
            return Mono.error(new McpRpcException(-32603, "call", "传输已关闭"));
        }
        CompletableFuture<String> existing = messageEndpointFuture;
        if (existing != null) {
            return Mono.fromFuture(existing).then();
        }
        synchronized (this) {
            if (messageEndpointFuture == null && !closed) {
                messageEndpointFuture = new CompletableFuture<>();
                openEventStream();
            }
            CompletableFuture<String> future = messageEndpointFuture;
            if (future == null) {
                return Mono.error(new McpRpcException(-32603, "call", "传输已关闭"));
            }
            return Mono.fromFuture(future).then();
        }
    }

    private void openEventStream() {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl))
                .timeout(requestTimeout)
                .header("Accept", "text/event-stream")
                .GET();
        headers.forEach(builder::header);
        McpJsonRpc.SseEventAccumulator accumulator = new McpJsonRpc.SseEventAccumulator();
        httpClient.sendAsync(builder.build(), HttpResponse.BodyHandlers.ofLines())
                .whenComplete((response, error) -> {
                    if (error != null || response.statusCode() < 200 || response.statusCode() >= 300) {
                        // 建立失败时复位连接状态，允许下次调用重试而非永久缓存异常future
                        resetConnection(new IllegalStateException(
                                "SSE事件流建立失败：" + (error != null ? error.getMessage() : "HTTP " + response.statusCode())));
                        return;
                    }
                    streamSubscription = Flux.fromStream(response.body())
                            .subscribe(line -> handleStreamLine(accumulator, line),
                                    e -> {
                                        log.warn("SSE事件流异常终止", e);
                                        onStreamTerminated();
                                    },
                                    () -> {
                                        log.debug("SSE事件流关闭");
                                        onStreamTerminated();
                                    });
                });
    }

    /**
     * 事件流断开后复位连接状态并失败挂起请求，保证下次调用触发重连
     */
    private void onStreamTerminated() {
        if (closed) {
            return;
        }
        resetConnection(new IllegalStateException("SSE事件流已断开"));
        pending.forEach((id, sink) -> sink.error(new McpRpcException(-32603, "call", "SSE事件流已断开，请求中断")));
        pending.clear();
    }

    /**
     * 复位连接状态：完成或失败端点future并清空引用，下次调用时重新建立事件流
     * @param error
     */
    private void resetConnection(IllegalStateException error) {
        CompletableFuture<String> future = messageEndpointFuture;
        synchronized (this) {
            messageEndpointFuture = null;
            streamSubscription = null;
        }
        if (future != null && !future.isDone()) {
            future.completeExceptionally(error);
        }
    }

    private void handleStreamLine(McpJsonRpc.SseEventAccumulator accumulator, String line) {
        Optional<McpJsonRpc.SseEvent> event = accumulator.feed(line);
        if (event.isEmpty()) {
            return;
        }
        McpJsonRpc.SseEvent sse = event.get();
        if ("endpoint".equals(sse.event())) {
            resolveMessageEndpoint(sse.data());
            return;
        }
        Optional<JsonNode> json = sse.parseJson();
        if (json.isPresent()) {
            if (McpJsonRpc.isResponse(json.get())) {
                dispatchResponse(json.get());
                return;
            }
            notificationsSink.tryEmitNext(json.get());
        }
    }

    private void resolveMessageEndpoint(String data) {
        try {
            URI base = URI.create(baseUrl);
            URI resolved = base.resolve(data);
            messageEndpointFuture.complete(resolved.toString());
        } catch (Exception e) {
            messageEndpointFuture.completeExceptionally(
                    new IllegalStateException("消息端点地址解析失败：" + data));
        }
    }

    private void dispatchResponse(JsonNode response) {
        JsonNode idNode = response.get("id");
        if (idNode == null || !idNode.isIntegralNumber()) {
            return;
        }
        MonoSink<JsonNode> sink = pending.remove(idNode.asLong());
        if (sink == null) {
            return;
        }
        JsonNode error = response.get("error");
        if (error != null && !error.isNull()) {
            int code = error.path("code").asInt(-32603);
            String message = error.path("message").asText("MCP调用失败");
            sink.error(new McpRpcException(code, "", "SSE应答失败：" + message + "（code=" + code + "）"));
            return;
        }
        JsonNode result = response.get("result");
        sink.success(result != null ? result : McpJsonRpc.mapper().createObjectNode());
    }

    private void postMessage(ObjectNode payload, String method, Long id, MonoSink<JsonNode> sink) {
        try {
            String endpoint = messageEndpointFuture.getNow(null);
            if (endpoint == null || !messageEndpointFuture.isDone() || messageEndpointFuture.isCompletedExceptionally()) {
                throw new IllegalStateException("消息端点未就绪");
            }
            HttpRequest.Builder builder = HttpRequest.newBuilder()
                    .uri(URI.create(endpoint))
                    .timeout(requestTimeout)
                    .header("Content-Type", "application/json")
                    .header("Accept", "text/event-stream")
                    .POST(HttpRequest.BodyPublishers.ofString(payload.toString(), java.nio.charset.StandardCharsets.UTF_8));
            headers.forEach(builder::header);
            httpClient.sendAsync(builder.build(), HttpResponse.BodyHandlers.ofString())
                    .whenComplete((response, error) -> {
                        if (sink == null) {
                            return;
                        }
                        if (error != null) {
                            pending.remove(id);
                            sink.error(new McpRpcException(-32603, method, method + "请求发送失败：" + error.getMessage()));
                            return;
                        }
                        if (response.statusCode() < 200 || response.statusCode() >= 300) {
                            pending.remove(id);
                            sink.error(new McpRpcException(response.statusCode(), method,
                                    method + "消息端点HTTP状态异常：" + response.statusCode()));
                        }
                    });
        } catch (Exception e) {
            if (sink != null) {
                pending.remove(id);
                sink.error(new McpRpcException(-32603, method, method + "请求发送失败：" + e.getMessage()));
            }
        }
    }
}
