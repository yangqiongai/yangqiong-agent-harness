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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import com.yangqiongai.agent.harness.core.message.AgentContentBlock;
import com.yangqiongai.agent.harness.core.message.AgentMessage;
import com.yangqiongai.agent.harness.core.message.AgentTextBlock;
import com.yangqiongai.agent.harness.core.model.AgentChatResponse;
import com.yangqiongai.agent.harness.core.model.AgentGenerateOptions;
import com.yangqiongai.agent.harness.core.model.AgentModel;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

/**
 * 可重试模型调用器测试
 * @author yangqiong
 */
class RetryableModelCallerTest {

    private static final List<AgentMessage> MESSAGES = Collections.emptyList();

    /**
     * 构建快速退避的重试配置，避免测试等待
     * @return
     */
    private ModelRetryConfig fastConfig() {
        return ModelRetryConfig.builder()
                .maxRetries(3)
                .initialBackoff(Duration.ofMillis(1))
                .maxBackoff(Duration.ofMillis(5))
                .build();
    }

    @Test
    void shouldRetryOnRateLimitThenSucceed() {
        AtomicInteger calls = new AtomicInteger(0);
        AgentModel delegate = new StubModel(calls, 2, "429 Too Many Requests", "ok");
        RetryableModelCaller caller = new RetryableModelCaller(delegate, fastConfig());

        AgentChatResponse response = caller.generate(MESSAGES, null, null);

        assertThat(getText(response)).isEqualTo("ok");
        assertThat(calls.get()).isEqualTo(3);
    }

    @Test
    void shouldNotRetryOnBadRequest() {
        AtomicInteger calls = new AtomicInteger(0);
        AgentModel delegate = new StubModel(calls, 10, "400 Bad Request", "ok");
        RetryableModelCaller caller = new RetryableModelCaller(delegate, fastConfig());

        assertThatThrownBy(() -> caller.generate(MESSAGES, null, null))
                .hasMessageContaining("模型调用重试耗尽");
        assertThat(calls.get()).isEqualTo(1);
    }

    @Test
    void shouldRetryStreamBeforeFirstChunkThenSucceed() {
        AtomicInteger calls = new AtomicInteger(0);
        AgentModel delegate = new StubModel(calls, 2, "503 Service Unavailable", "ok");
        RetryableModelCaller caller = new RetryableModelCaller(delegate, fastConfig());

        StepVerifier.create(caller.stream(MESSAGES, null, null))
                .expectNextMatches(resp -> "ok".equals(getText(resp)))
                .verifyComplete();
        assertThat(calls.get()).isEqualTo(3);
    }

    @Test
    void shouldNotRetryStreamAfterFirstChunk() {
        AtomicInteger calls = new AtomicInteger(0);
        // 首片到达后失败：emit一个分片后再报错，不应重试
        AgentModel delegate = new AgentModel() {
            @Override
            public AgentChatResponse generate(List<AgentMessage> messages, List<Map<String, Object>> tools,
                                                AgentGenerateOptions options) {
                throw new UnsupportedOperationException();
            }

            @Override
            public Flux<AgentChatResponse> stream(List<AgentMessage> messages, List<Map<String, Object>> tools,
                                                    AgentGenerateOptions options) {
                calls.incrementAndGet();
                return Flux.concat(Flux.just(buildResponse("first")), Flux.error(new RuntimeException("503")));
            }
        };
        RetryableModelCaller caller = new RetryableModelCaller(delegate, fastConfig());

        StepVerifier.create(caller.stream(MESSAGES, null, null))
                .expectNextMatches(resp -> "first".equals(getText(resp)))
                .expectError()
                .verify();
        assertThat(calls.get()).isEqualTo(1);
    }

    @Test
    void shouldNotRetryWhenDisabled() {
        AtomicInteger calls = new AtomicInteger(0);
        AgentModel delegate = new StubModel(calls, 5, "429 Too Many Requests", "ok");
        RetryableModelCaller caller = new RetryableModelCaller(delegate, ModelRetryConfig.disabled());

        assertThatThrownBy(() -> caller.generate(MESSAGES, null, null))
                .isNotNull();
        assertThat(calls.get()).isEqualTo(1);
    }

    private String getText(AgentChatResponse response) {
        if (response == null || response.getContent() == null || response.getContent().isEmpty()) {
            return "";
        }
        AgentContentBlock block = response.getContent().get(0);
        return block instanceof AgentTextBlock textBlock ? textBlock.getText() : "";
    }

    private AgentChatResponse buildResponse(String text) {
        return new AgentChatResponse(Collections.singletonList(
                AgentTextBlock.builder().text(text).build()), null);
    }

    /**
     * 桩模型，前failCount次抛指定错误，之后返回成功响应
     */
    private static class StubModel implements AgentModel {

        private final AtomicInteger calls;
        private final int failCount;
        private final String errorMessage;
        private final String successText;

        StubModel(AtomicInteger calls, int failCount, String errorMessage, String successText) {
            this.calls = calls;
            this.failCount = failCount;
            this.errorMessage = errorMessage;
            this.successText = successText;
        }

        @Override
        public AgentChatResponse generate(List<AgentMessage> messages, List<Map<String, Object>> tools,
                                            AgentGenerateOptions options) {
            int n = calls.incrementAndGet();
            if (n <= failCount) {
                throw new RuntimeException(errorMessage);
            }
            return new AgentChatResponse(Collections.singletonList(
                    AgentTextBlock.builder().text(successText).build()), null);
        }

        @Override
        public Flux<AgentChatResponse> stream(List<AgentMessage> messages, List<Map<String, Object>> tools,
                                                AgentGenerateOptions options) {
            int n = calls.incrementAndGet();
            if (n <= failCount) {
                return Flux.error(new RuntimeException(errorMessage));
            }
            return Flux.just(new AgentChatResponse(Collections.singletonList(
                    AgentTextBlock.builder().text(successText).build()), null));
        }
    }
}
