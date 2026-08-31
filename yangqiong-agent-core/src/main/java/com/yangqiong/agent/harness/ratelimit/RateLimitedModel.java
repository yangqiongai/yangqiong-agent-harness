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
package com.yangqiong.agent.harness.ratelimit;

import com.yangqiong.agent.harness.core.message.AgentMessage;
import com.yangqiong.agent.harness.core.model.AgentChatResponse;
import com.yangqiong.agent.harness.core.model.AgentGenerateOptions;
import com.yangqiong.agent.harness.core.model.AgentModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * 速率限制模型装饰器
 * <p>
 * 装饰{@link AgentModel}，在调用前按令牌桶调度等待；等待时长超过上限时抛
 * {@link RateLimitExceededException}，防止长时间阻塞拖垮整个执行。
 * 可与RetryableModelCaller、FallbackModel自由组合（外层重试→限流→内层降级）。
 * </p>
 * @author yangqiong
 */
public class RateLimitedModel implements AgentModel {

    private static final Logger log = LoggerFactory.getLogger(RateLimitedModel.class);

    /**
     * 默认最大等待时长
     */
    public static final Duration DEFAULT_MAX_WAIT = Duration.ofSeconds(30);

    /**
     * 被装饰的实际模型
     */
    private final AgentModel delegate;

    /**
     * 速率限制器
     */
    private final RateLimiter limiter;

    /**
     * 最大等待时长
     */
    private final Duration maxWait;

    public RateLimitedModel(AgentModel delegate, RateLimiter limiter) {
        this(delegate, limiter, DEFAULT_MAX_WAIT);
    }

    /**
     * 按指定最大等待时长构建速率限制模型
     * @param delegate
     * @param limiter
     * @param maxWait
     */
    public RateLimitedModel(AgentModel delegate, RateLimiter limiter, Duration maxWait) {
        if (delegate == null) {
            throw new IllegalArgumentException("被装饰的模型不能为空");
        }
        if (limiter == null) {
            throw new IllegalArgumentException("速率限制器不能为空");
        }
        this.delegate = delegate;
        this.limiter = limiter;
        this.maxWait = maxWait != null ? maxWait : DEFAULT_MAX_WAIT;
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
        waitForToken();
        return delegate.generate(messages, tools, options);
    }

    @Override
    public Flux<AgentChatResponse> stream(List<AgentMessage> messages, List<Map<String, Object>> tools,
                                          AgentGenerateOptions options) {
        long waitNanos;
        try {
            waitNanos = limiter.waitNanos();
        } catch (Exception e) {
            return Flux.error(e);
        }
        if (waitNanos > maxWait.toNanos()) {
            return Flux.error(new RateLimitExceededException(
                    "模型速率限制等待超上限: " + Duration.ofNanos(waitNanos) + " > " + maxWait));
        }
        if (waitNanos > 0) {
            log.debug("模型速率限制等待: {}ms", Duration.ofNanos(waitNanos).toMillis());
        }
        return Mono.delay(Duration.ofNanos(waitNanos))
                .thenMany(delegate.stream(messages, tools, options));
    }

    /**
     * 同步等待令牌，等待时长超过上限时抛异常
     */
    private void waitForToken() {
        long waitNanos = limiter.waitNanos();
        if (waitNanos > maxWait.toNanos()) {
            throw new RateLimitExceededException(
                    "模型速率限制等待超上限: " + Duration.ofNanos(waitNanos) + " > " + maxWait);
        }
        try {
            Thread.sleep(waitNanos / 1_000_000, (int) (waitNanos % 1_000_000));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("模型速率限制等待被中断", e);
        }
    }
}