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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.List;

import com.yangqiong.agent.harness.core.message.AgentTextBlock;
import com.yangqiong.agent.harness.core.model.AgentChatResponse;
import com.yangqiong.agent.harness.core.model.AgentModel;
import org.junit.jupiter.api.Test;

import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

/**
 * 速率限制模型装饰器测试
 * @author yangqiong
 */
class RateLimitedModelTest {

    /**
     * 构建返回固定文本的mock模型
     * @return
     */
    private AgentModel mockDelegate() {
        AgentModel delegate = mock(AgentModel.class);
        AgentTextBlock text = AgentTextBlock.builder().text("ok").build();
        AgentChatResponse response = new AgentChatResponse(List.of(text), null);
        when(delegate.stream(any(), any(), any())).thenReturn(Flux.just(response));
        when(delegate.generate(any(), any(), any())).thenReturn(response);
        return delegate;
    }

    @Test
    void overSpeedStreamCallIsDelayed() {
        // 容量1、补充2/s：第二次调用需等待约500ms
        RateLimiter limiter = new TokenBucketRateLimiter(1.0, 2.0);
        RateLimitedModel model = new RateLimitedModel(mockDelegate(), limiter);

        StepVerifier.create(model.stream(List.of(), List.of(), null))
                .expectNextCount(1)
                .verifyComplete();

        long start = System.nanoTime();
        StepVerifier.create(model.stream(List.of(), List.of(), null))
                .expectNextCount(1)
                .verifyComplete();
        long elapsedMs = (System.nanoTime() - start) / 1_000_000;
        assertThat(elapsedMs).isGreaterThanOrEqualTo(400);
    }

    @Test
    void streamWaitExceedingLimitThrows() {
        // 容量1、补充0.001/s：第二次等待约1000秒远超上限
        RateLimiter limiter = new TokenBucketRateLimiter(1.0, 0.001);
        RateLimitedModel model = new RateLimitedModel(mockDelegate(), limiter, Duration.ofMillis(500));

        StepVerifier.create(model.stream(List.of(), List.of(), null))
                .expectNextCount(1)
                .verifyComplete();

        StepVerifier.create(model.stream(List.of(), List.of(), null))
                .expectError(RateLimitExceededException.class)
                .verify();
    }

    @Test
    void overSpeedGenerateIsDelayed() {
        RateLimiter limiter = new TokenBucketRateLimiter(1.0, 2.0);
        RateLimitedModel model = new RateLimitedModel(mockDelegate(), limiter);

        assertThat(model.generate(List.of(), List.of(), null)).isNotNull();

        long start = System.nanoTime();
        model.generate(List.of(), List.of(), null);
        long elapsedMs = (System.nanoTime() - start) / 1_000_000;
        assertThat(elapsedMs).isGreaterThanOrEqualTo(400);
    }

    @Test
    void generateWaitExceedingLimitThrows() {
        RateLimiter limiter = new TokenBucketRateLimiter(1.0, 0.001);
        RateLimitedModel model = new RateLimitedModel(mockDelegate(), limiter, Duration.ofMillis(500));

        model.generate(List.of(), List.of(), null);
        org.assertj.core.api.Assertions.assertThatThrownBy(
                        () -> model.generate(List.of(), List.of(), null))
                .isInstanceOf(RateLimitExceededException.class);
    }

    @Test
    void modelNameDelegatesToInner() {
        AgentModel delegate = mockDelegate();
        when(delegate.modelName()).thenReturn("inner");
        RateLimitedModel model = new RateLimitedModel(delegate,
                new TokenBucketRateLimiter(1.0, 1.0));
        assertThat(model.modelName()).isEqualTo("inner");
    }
}