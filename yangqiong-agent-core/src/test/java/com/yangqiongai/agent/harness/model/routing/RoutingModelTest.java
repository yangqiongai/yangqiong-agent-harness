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
package com.yangqiongai.agent.harness.model.routing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import com.yangqiongai.agent.harness.core.message.AgentContentBlock;
import com.yangqiongai.agent.harness.core.message.AgentMessage;
import com.yangqiongai.agent.harness.core.message.AgentMessageRole;
import com.yangqiongai.agent.harness.core.message.AgentTextBlock;
import com.yangqiongai.agent.harness.core.model.AgentChatResponse;
import com.yangqiongai.agent.harness.core.model.AgentGenerateOptions;
import com.yangqiongai.agent.harness.core.model.AgentModel;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

/**
 * 路由模型测试
 * @author yangqiong
 */
class RoutingModelTest {

    @Test
    void shouldDelegateGenerateToSelectedModel() {
        StubModel cheap = new StubModel("cheap", false);
        StubModel expensive = new StubModel("expensive", false);
        ModelHealthRegistry registry = new ModelHealthRegistry();
        RoutingModel model = RoutingModel.builder()
                .candidate(candidate("cheap", cheap, 1.0d, Set.of()))
                .candidate(candidate("expensive", expensive, 5.0d, Set.of()))
                .router(new HealthAwareModelRouter(registry, RoutingWeights.DEFAULT))
                .registry(registry)
                .build();
        AgentChatResponse response = model.generate(List.of(message("hello")), null, null);
        assertThat(textOf(response)).isEqualTo("cheap");
        assertThat(cheap.calls.get()).isEqualTo(1);
        assertThat(expensive.calls.get()).isZero();
        assertThat(registry.healthScore("cheap")).isEqualTo(1.0d);
    }

    @Test
    void shouldRecordFailureWhenDelegateThrows() {
        StubModel failing = new StubModel("failing", true);
        ModelHealthRegistry registry = new ModelHealthRegistry();
        RoutingModel model = RoutingModel.builder()
                .candidate(candidate("failing", failing, 1.0d, Set.of()))
                .router(new HealthAwareModelRouter(registry, RoutingWeights.DEFAULT))
                .registry(registry)
                .build();
        assertThatThrownBy(() -> model.generate(List.of(message("hello")), null, null))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("failing 模型调用失败");
        assertThat(registry.healthScore("failing")).isZero();
        assertThat(registry.isHealthy("failing")).isTrue();
    }

    @Test
    void shouldMarkUnhealthyAfterRepeatedFailures() {
        StubModel failing = new StubModel("failing", true);
        ModelHealthRegistry registry = new ModelHealthRegistry();
        RoutingModel model = RoutingModel.builder()
                .candidate(candidate("failing", failing, 1.0d, Set.of()))
                .router(new HealthAwareModelRouter(registry, RoutingWeights.DEFAULT))
                .registry(registry)
                .build();
        for (int i = 0; i < 5; i++) {
            assertThatThrownBy(() -> model.generate(List.of(message("hello")), null, null))
                    .isInstanceOf(RuntimeException.class);
        }
        assertThat(registry.isHealthy("failing")).isFalse();
    }

    @Test
    void shouldRecordHealthOnStreamCompletion() {
        StubModel stub = new StubModel("stub", false);
        ModelHealthRegistry registry = new ModelHealthRegistry();
        RoutingModel model = RoutingModel.builder()
                .candidate(candidate("stub", stub, 1.0d, Set.of()))
                .router(new HealthAwareModelRouter(registry, RoutingWeights.DEFAULT))
                .registry(registry)
                .build();
        StepVerifier.create(model.stream(List.of(message("hello")), null, null))
                .expectNextMatches(response -> "stub".equals(textOf(response)))
                .verifyComplete();
        assertThat(stub.calls.get()).isEqualTo(1);
        assertThat(registry.healthScore("stub")).isEqualTo(1.0d);
    }

    @Test
    void shouldUseTaskTypeSupplierWhenRouting() {
        StubModel cheap = new StubModel("cheap", false);
        StubModel matched = new StubModel("matched", false);
        ModelHealthRegistry registry = new ModelHealthRegistry();
        RoutingModel model = RoutingModel.builder()
                .candidate(candidate("cheap", cheap, 1.0d, Set.of()))
                .candidate(candidate("matched", matched, 2.0d, Set.of("code")))
                .router(new HealthAwareModelRouter(registry, RoutingWeights.DEFAULT))
                .registry(registry)
                .taskTypeSupplier(() -> "code")
                .build();
        AgentChatResponse response = model.generate(List.of(message("hello")), null, null);
        assertThat(textOf(response)).isEqualTo("matched");
        assertThat(cheap.calls.get()).isZero();
        assertThat(matched.calls.get()).isEqualTo(1);
    }

    @Test
    void shouldThrowWhenNoCandidateSelected() {
        ModelHealthRegistry registry = new ModelHealthRegistry();
        RoutingModel model = RoutingModel.builder()
                .candidate(candidate("only", new StubModel("only", false), 1.0d, Set.of()))
                .router((taskType, inputMessages, candidates) -> RouteDecision.empty(Map.of()))
                .registry(registry)
                .build();
        assertThatThrownBy(() -> model.generate(List.of(message("hello")), null, null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("模型路由未选中任何可用候选");
    }

    private RouteCandidate candidate(String modelCode, AgentModel model, double cost, Set<String> taskTypes) {
        return RouteCandidate.builder()
                .modelCode(modelCode)
                .model(model)
                .costPerMillionTokens(cost)
                .avgLatencyMs(1000.0d)
                .contextWindowTokens(100_000)
                .taskTypes(taskTypes)
                .build();
    }

    private AgentMessage message(String text) {
        return AgentMessage.builder()
                .role(AgentMessageRole.USER)
                .content(List.of(AgentTextBlock.builder().text(text).build()))
                .build();
    }

    private String textOf(AgentChatResponse response) {
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

        private final String code;

        private final boolean fail;

        private final AtomicInteger calls = new AtomicInteger();

        StubModel(String code, boolean fail) {
            this.code = code;
            this.fail = fail;
        }

        @Override
        public AgentChatResponse generate(List<AgentMessage> messages, List<Map<String, Object>> tools,
                                            AgentGenerateOptions options) {
            calls.incrementAndGet();
            if (fail) {
                throw new RuntimeException(code + " 模型调用失败");
            }
            return new AgentChatResponse(List.of(AgentTextBlock.builder().text(code).build()), null);
        }

        @Override
        public Flux<AgentChatResponse> stream(List<AgentMessage> messages, List<Map<String, Object>> tools,
                                                AgentGenerateOptions options) {
            calls.incrementAndGet();
            if (fail) {
                return Flux.error(new RuntimeException(code + " 模型流式调用失败"));
            }
            return Flux.just(new AgentChatResponse(List.of(AgentTextBlock.builder().text(code).build()), null));
        }
    }
}
