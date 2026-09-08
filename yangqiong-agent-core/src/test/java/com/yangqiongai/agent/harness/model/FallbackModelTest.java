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

import java.util.Collections;
import java.util.List;
import java.util.Map;

import com.yangqiongai.agent.harness.core.model.AgentGenerateOptions;
import org.junit.jupiter.api.Test;

import com.yangqiongai.agent.harness.core.message.AgentContentBlock;
import com.yangqiongai.agent.harness.core.message.AgentMessage;
import com.yangqiongai.agent.harness.core.message.AgentMessageRole;
import com.yangqiongai.agent.harness.core.message.AgentTextBlock;
import com.yangqiongai.agent.harness.core.model.AgentChatResponse;
import com.yangqiongai.agent.harness.core.model.AgentModel;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

/**
 * 回退模型测试
 * @author yangqiong
 */
class FallbackModelTest {

    @Test
    void shouldThrowWhenModelsEmpty() {
        assertThatThrownBy(() -> new FallbackModel(List.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldUsePrimaryWhenSuccessful() {
        AgentModel primary = new StubModel("primary", false);
        FallbackModel model = FallbackModel.builder().primary(primary).build();
        AgentChatResponse response = model.generate(List.of(createMessage("test")), null, null);
        assertThat(getText(response)).isEqualTo("primary");
    }

    @Test
    void shouldFallbackWhenPrimaryFails() {
        AgentModel primary = new StubModel("primary", true);
        AgentModel fallback = new StubModel("fallback", false);
        FallbackModel model = FallbackModel.builder().primary(primary).fallback(fallback).build();
        AgentChatResponse response = model.generate(List.of(createMessage("test")), null, null);
        assertThat(getText(response)).isEqualTo("fallback");
    }

    @Test
    void shouldThrowWhenAllModelsFail() {
        AgentModel primary = new StubModel("primary", true);
        AgentModel fallback = new StubModel("fallback", true);
        FallbackModel model = FallbackModel.builder().primary(primary).fallback(fallback).build();
        assertThatThrownBy(() -> model.generate(List.of(createMessage("test")), null, null))
                .hasMessageContaining("所有模型均调用失败");
    }

    @Test
    void shouldStreamWithFallback() {
        AgentModel primary = new StubModel("primary", true);
        AgentModel fallback = new StubModel("fallback", false);
        FallbackModel model = FallbackModel.builder().primary(primary).fallback(fallback).build();
        StepVerifier.create(model.stream(List.of(createMessage("test")), null, null))
                .expectNextMatches(resp -> "fallback".equals(getText(resp)))
                .verifyComplete();
    }

    private AgentMessage createMessage(String text) {
        return AgentMessage.builder()
                .role(AgentMessageRole.USER)
                .content(List.of(AgentTextBlock.builder().text(text).build()))
                .build();
    }

    private String getText(AgentChatResponse response) {
        if (response == null || response.getContent() == null || response.getContent().isEmpty()) {
            return "";
        }
        AgentContentBlock block = response.getContent().get(0);
        return block instanceof AgentTextBlock textBlock ? textBlock.getText() : "";
    }

    /**
     * 桩模型
     */
    private static class StubModel implements AgentModel {

        private final String text;
        private final boolean fail;

        StubModel(String text, boolean fail) {
            this.text = text;
            this.fail = fail;
        }

        @Override
        public AgentChatResponse generate(List<AgentMessage> messages, List<Map<String, Object>> tools,
                                            AgentGenerateOptions options) {
            if (fail) {
                throw new RuntimeException(text + " 模型调用失败");
            }
            return createResponse();
        }

        @Override
        public Flux<AgentChatResponse> stream(List<AgentMessage> messages, List<Map<String, Object>> tools,
                                                AgentGenerateOptions options) {
            if (fail) {
                return Flux.error(new RuntimeException(text + " 模型流式调用失败"));
            }
            return Flux.just(createResponse());
        }

        private AgentChatResponse createResponse() {
            List<AgentContentBlock> blocks = Collections.singletonList(
                    AgentTextBlock.builder().text(text).build());
            return new AgentChatResponse(blocks, null);
        }
    }
}
