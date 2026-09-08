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
package com.yangqiongai.agent.harness.model;

import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import com.yangqiongai.agent.harness.core.message.AgentMessage;
import com.yangqiongai.agent.harness.core.model.AgentChatResponse;
import com.yangqiongai.agent.harness.core.model.AgentGenerateOptions;
import com.yangqiongai.agent.harness.core.model.AgentModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * 可重试模型调用器
 * <p>
 * 装饰{@link AgentModel}，对模型调用增加带指数退避的重试能力。
 * 仅对可重试的瞬时故障（网络异常、429限流、5xx服务端错误）重试，
 * 不对4xx客户端错误重试。流式场景下仅首片到达前失败才重试，
 * 首片到达后失败直接传播，避免重复消费已发出的分片。
 * 可与{@link FallbackModel}组合使用（重试→降级），不互斥。
 * </p>
 * @author yangqiong
 */
public class RetryableModelCaller implements AgentModel {

    private static final Logger log = LoggerFactory.getLogger(RetryableModelCaller.class);

    /**
     * 被装饰的实际模型
     */
    private final AgentModel delegate;

    /**
     * 重试配置
     */
    private final ModelRetryConfig config;

    public RetryableModelCaller(AgentModel delegate, ModelRetryConfig config) {
        if (delegate == null) {
            throw new IllegalArgumentException("被装饰的模型不能为空");
        }
        this.delegate = delegate;
        this.config = config != null ? config : ModelRetryConfig.defaultEnabled();
    }

    /**
     * 获取底层模型名称
     * @return
     */
    @Override
    public String modelName() {
        return delegate.modelName();
    }

    @Override
    public AgentChatResponse generate(List<AgentMessage> messages, List<Map<String, Object>> tools,
                                        AgentGenerateOptions options) {
        if (!config.isEnabled() || config.getMaxRetries() <= 0) {
            return delegate.generate(messages, tools, options);
        }
        Exception lastError = null;
        for (int attempt = 0; attempt <= config.getMaxRetries(); attempt++) {
            try {
                return delegate.generate(messages, tools, options);
            } catch (Exception e) {
                lastError = e;
                if (!isRetryable(e) || attempt >= config.getMaxRetries()) {
                    break;
                }
                sleepBackoff(attempt);
            }
        }
        throw new RuntimeException("模型调用重试耗尽", lastError);
    }

    @Override
    public Flux<AgentChatResponse> stream(List<AgentMessage> messages, List<Map<String, Object>> tools,
                                            AgentGenerateOptions options) {
        if (!config.isEnabled() || config.getMaxRetries() <= 0) {
            return delegate.stream(messages, tools, options);
        }
        // 首片到达标记，跨重试共享：首片到达后失败不再重试，避免重复消费
        AtomicBoolean firstReceived = new AtomicBoolean(false);
        return doStreamWithRetry(messages, tools, options, 0, firstReceived);
    }

    /**
     * 递归重试流式调用，仅首片到达前失败才重试
     * @param messages
     * @param tools
     * @param options
     * @param attempt
     * @param firstReceived
     * @return
     */
    private Flux<AgentChatResponse> doStreamWithRetry(List<AgentMessage> messages,
                                                         List<Map<String, Object>> tools,
                                                         AgentGenerateOptions options,
                                                         int attempt, AtomicBoolean firstReceived) {
        return delegate.stream(messages, tools, options)
                .doOnNext(resp -> firstReceived.set(true))
                .onErrorResume(error -> {
                    // 首片已到达则不重试，避免重复消费已发出的分片
                    if (firstReceived.get()) {
                        return Flux.error(error);
                    }
                    if (!isRetryable(error) || attempt >= config.getMaxRetries()) {
                        return Flux.error(error);
                    }
                    long delayMs = computeBackoffMillis(attempt);
                    log.warn("[Retryable] 模型流式调用失败，{}ms后重试({}/{}): {}",
                            delayMs, attempt + 1, config.getMaxRetries(), error.getMessage());
                    return Mono.delay(Duration.ofMillis(delayMs))
                            .thenMany(doStreamWithRetry(messages, tools, options, attempt + 1, firstReceived));
                });
    }

    /**
     * 指数退避睡眠
     * @param attempt
     */
    private void sleepBackoff(int attempt) {
        long delayMs = computeBackoffMillis(attempt);
        log.warn("[Retryable] 模型同步调用失败，{}ms后重试({}/{})", delayMs, attempt + 1, config.getMaxRetries());
        try {
            Thread.sleep(delayMs);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("模型重试等待被中断", e);
        }
    }

    /**
     * 计算指数退避时长（毫秒），受maxBackoff封顶
     * @param attempt
     * @return
     */
    private long computeBackoffMillis(int attempt) {
        double multiplier = Math.pow(config.getBackoffMultiplier(), attempt);
        long initialMs = config.getInitialBackoff() != null ? config.getInitialBackoff().toMillis() : 1000L;
        long delayMs = (long) (initialMs * multiplier);
        long maxMs = config.getMaxBackoff() != null ? config.getMaxBackoff().toMillis() : 10000L;
        return Math.min(delayMs, maxMs);
    }

    /**
     * 判断异常是否可重试，遍历异常链检查瞬时故障特征
     * @param throwable
     * @return
     */
    private boolean isRetryable(Throwable throwable) {
        List<Throwable> chain = new ArrayList<>();
        Throwable current = throwable;
        while (current != null && !chain.contains(current)) {
            chain.add(current);
            current = current.getCause();
        }
        for (Throwable t : chain) {
            if (t instanceof IOException) {
                return true;
            }
            String name = t.getClass().getName().toLowerCase();
            if (name.contains("timeout") || name.contains("connect")
                    || name.contains("socket") || name.contains("network")) {
                return true;
            }
            String message = t.getMessage() != null ? t.getMessage().toLowerCase() : "";
            // 4xx客户端错误不重试
            if (message.contains("400") || message.contains("401")
                    || message.contains("403") || message.contains("404")
                    || message.contains("bad request") || message.contains("unauthorized")
                    || message.contains("forbidden") || message.contains("not found")) {
                return false;
            }
            // 可重试的瞬时故障特征
            if (message.contains("429") || message.contains("rate limit")
                    || message.contains("too many requests")
                    || message.contains("500") || message.contains("502")
                    || message.contains("503") || message.contains("504")
                    || message.contains("service unavailable") || message.contains("bad gateway")
                    || message.contains("internal server error")
                    || message.contains("timeout") || message.contains("timed out")
                    || message.contains("connection reset") || message.contains("temporarily")) {
                return true;
            }
        }
        return false;
    }
}
