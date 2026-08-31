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
package com.yangqiong.agent.harness.model;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yangqiong.agent.harness.core.message.AgentMessage;
import com.yangqiong.agent.harness.core.model.AgentChatResponse;
import com.yangqiong.agent.harness.core.model.AgentGenerateOptions;
import com.yangqiong.agent.harness.core.model.AgentModel;
import com.yangqiong.agent.harness.core.model.protocol.AgentModelProtocolAdapter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;

/**
 * HTTP模型基类
 * <p>
 * 基于JDK 17内置HttpClient的通用HTTP模型基类，通过注入{@link AgentModelProtocolAdapter}处理协议差异。
 * 各provider的模型实现继承此类并传入对应的协议适配器。
 * </p>
 * @author yangqiong
 */
public abstract class HttpChatModel implements AgentModel {

    private static final Logger log = LoggerFactory.getLogger(HttpChatModel.class);

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /**
     * 错误日志最大输出长度，防止长对话请求体刷爆日志
     */
    private static final int MAX_ERROR_LOG_LENGTH = 2000;

    /**
     * HTTP客户端（复用连接池）
     */
    private final HttpClient httpClient;

    /**
     * 协议适配器
     */
    private final AgentModelProtocolAdapter protocolAdapter;

    /**
     * API基础地址
     */
    private final String baseUrl;

    /**
     * API密钥
     */
    private final String apiKey;

    /**
     * 模型名称
     */
    private final String modelName;

    /**
     * 默认生成选项
     */
    private final AgentGenerateOptions defaultOptions;

    /**
     * 请求超时
     */
    private final Duration timeout;

    /**
     * 构造HTTP聊天模型
     * @param baseUrl
     * @param apiKey
     * @param modelName
     * @param defaultOptions
     * @param timeoutSeconds
     * @param protocolAdapter
     */
    protected HttpChatModel(String baseUrl, String apiKey, String modelName,
                              AgentGenerateOptions defaultOptions, int timeoutSeconds,
                              AgentModelProtocolAdapter protocolAdapter) {
        this.baseUrl = normalizeBaseUrl(baseUrl);
        this.apiKey = apiKey;
        this.modelName = modelName;
        this.defaultOptions = defaultOptions;
        this.timeout = Duration.ofSeconds(timeoutSeconds);
        this.protocolAdapter = protocolAdapter;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    /**
     * 获取模型名称
     * @return
     */
    @Override
    public String modelName() {
        return modelName;
    }

    /**
     * 同步生成模型响应
     * <p>
     * 使用非流式请求（stream=false）直接获取完整JSON响应。
     * </p>
     * @param messages
     * @param tools
     * @param options
     * @return
     */
    @Override
    public AgentChatResponse generate(List<AgentMessage> messages, List<Map<String, Object>> tools,
                                        AgentGenerateOptions options) {
        AgentGenerateOptions effectiveOptions = AgentGenerateOptions.mergeOptions(options, defaultOptions);
        String requestBody = protocolAdapter.buildRequestBody(modelName, messages, tools, effectiveOptions, defaultOptions, false);
        HttpRequest request = buildHttpRequest(requestBody, false);
        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 400) {
                throw new RuntimeException("模型API返回错误码: " + response.statusCode() + ", 响应: " + response.body());
            }
            JsonNode root = MAPPER.readTree(response.body());
            return protocolAdapter.parseNonStreamResponse(root);
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("模型同步调用失败", e);
        }
    }

    /**
     * 流式生成模型响应
     * <p>
     * 下游取消时取消在途请求并关闭响应体，避免HttpClient线程继续拉流与连接泄漏；
     * 响应正文读取阶段以{@code timeout}作为空闲超时（请求级timeout仅覆盖响应头到达），
     * 服务端发送响应头后长时间不再发数据时中断，避免整个回合无限期挂起。
     * </p>
     * @param messages
     * @param tools
     * @param options
     * @return
     */
    @Override
    public Flux<AgentChatResponse> stream(List<AgentMessage> messages, List<Map<String, Object>> tools,
                                            AgentGenerateOptions options) {
        AgentGenerateOptions effectiveOptions = AgentGenerateOptions.mergeOptions(options, defaultOptions);
        String requestBody = protocolAdapter.buildRequestBody(modelName, messages, tools, effectiveOptions, defaultOptions, true);
        HttpRequest request = buildHttpRequest(requestBody, true);
        return Flux.<AgentChatResponse>create(sink -> {
            CompletableFuture<HttpResponse<Stream<String>>> future =
                    httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofLines());
            AtomicReference<HttpResponse<Stream<String>>> responseRef = new AtomicReference<>();
            // 订阅取消或终止时取消在途请求并关闭响应体，释放HTTP连接
            sink.onDispose(() -> {
                future.cancel(true);
                HttpResponse<Stream<String>> pending = responseRef.get();
                if (pending != null && pending.body() != null) {
                    pending.body().close();
                }
            });
            future.whenComplete((response, error) -> {
                if (error != null) {
                    sink.error(new RuntimeException("模型调用失败", error));
                    return;
                }
                if (response.statusCode() >= 400) {
                    // 一次获取所有响应行，避免流式分片合并导致的丢内容问题
                    List<String> lines = response.body().collect(Collectors.toList());
                    log.error("模型API返回错误码: {}, 响应: {}", response.statusCode(), truncate(lines.toString(), MAX_ERROR_LOG_LENGTH));
                    // 调试辅助：打印触发错误的请求体，便于排查工具调用协议问题
                    log.error("触发错误的请求体: {}", truncate(requestBody, MAX_ERROR_LOG_LENGTH));

                    // 关闭未消费的响应体，避免HTTP连接泄漏
                    response.body().close();
                    sink.error(new RuntimeException("模型API返回错误码: " + response.statusCode()));
                    return;
                }
                responseRef.set(response);
                try (Stream<String> body = response.body()) {
                    body.forEach(line -> {
                        if (sink.isCancelled()) {
                            throw new IllegalStateException("订阅已取消，中断流式读取");
                        }
                        try {
                            AgentChatResponse chunk = protocolAdapter.parseStreamLine(line);
                            if (chunk != null) {
                                sink.next(chunk);
                            }
                        } catch (Exception e) {
                            log.warn("解析流式行失败: {}", line, e);
                        }
                    });
                    sink.complete();
                } catch (Exception e) {
                    sink.error(new RuntimeException("模型流式读取失败", e));
                }
            });
        })
        // 正文空闲超时：相邻分片间隔超过timeout视为流挂起，触发取消并关闭响应体
        .timeout(timeout, Flux.error(new RuntimeException("模型流式响应读取超时")));
    }

    /**
     * 按最大长度截断文本，超出部分追加省略标记
     * @param text
     * @param maxLength
     * @return
     */
    private static String truncate(String text, int maxLength) {
        if (text == null || text.length() <= maxLength) {
            return text;
        }
        return text.substring(0, maxLength) + "...(truncated, total=" + text.length() + " chars)";
    }

    /**
     * 构建HTTP请求
     * @param requestBody
     * @param stream 是否流式请求，决定Accept头
     * @return
     */
    private HttpRequest buildHttpRequest(String requestBody, boolean stream) {
        String endpointPath = protocolAdapter.getEndpointPath();
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + endpointPath))
                .header("Content-Type", "application/json")
                .header("Accept", stream ? "text/event-stream" : "application/json")
                .timeout(timeout)
                .POST(HttpRequest.BodyPublishers.ofString(requestBody));
        // 按provider适配器决定认证方式
        if (protocolAdapter.useBearerAuth() && apiKey != null && !apiKey.isBlank()) {
            builder.header("Authorization", "Bearer " + apiKey);
        }
        // 追加provider特有请求头
        Map<String, String> extraHeaders = protocolAdapter.getHeaders(apiKey);
        if (extraHeaders != null) {
            for (Map.Entry<String, String> entry : extraHeaders.entrySet()) {
                builder.header(entry.getKey(), entry.getValue());
            }
        }
        return builder.build();
    }

    /**
     * 规范化基础地址，去除尾部斜杠
     * @param url
     * @return
     */
    private static String normalizeBaseUrl(String url) {
        if (url == null || url.isBlank()) {
            throw new IllegalArgumentException("baseUrl 不能为空，请通过 ProviderConfig.baseUrl 注入");
        }
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }

    /**
     * 获取模型名称
     * @return
     */
    public String getModelName() {
        return modelName;
    }

    /**
     * 获取基础地址
     * @return
     */
    public String getBaseUrl() {
        return baseUrl;
    }

    /**
     * 获取协议适配器
     * @return
     */
    protected AgentModelProtocolAdapter getProtocolAdapter() {
        return protocolAdapter;
    }
}
