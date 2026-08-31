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
package com.yangqiong.agent.harness.model.routing;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.Set;

import com.yangqiong.agent.harness.core.message.AgentMessage;
import com.yangqiong.agent.harness.core.message.AgentMessageRole;
import com.yangqiong.agent.harness.core.message.AgentTextBlock;
import com.yangqiong.agent.harness.core.model.AgentChatResponse;
import com.yangqiong.agent.harness.core.model.AgentModel;
import com.yangqiong.agent.harness.core.model.AgentGenerateOptions;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;

/**
 * 健康感知模型路由测试
 * @author yangqiong
 */
class HealthAwareModelRouterTest {

    private final ModelHealthRegistry registry = new ModelHealthRegistry();

    private final HealthAwareModelRouter router = new HealthAwareModelRouter(registry, RoutingWeights.DEFAULT);

    @Test
    void shouldPreferCheaperModelWhenEquallyHealthy() {
        RouteCandidate cheap = candidate("cheap", 1.0d, 1000.0d, 100_000);
        RouteCandidate expensive = candidate("expensive", 5.0d, 1000.0d, 100_000);
        RouteDecision decision = router.select("chat", List.of(message("hello")), List.of(expensive, cheap));
        assertThat(decision.getSelected().getModelCode()).isEqualTo("cheap");
        assertThat(decision.getScore()).isPositive();
        assertThat(decision.getExcluded()).isEmpty();
    }

    @Test
    void shouldExcludeCandidateWithInsufficientContextWindow() {
        RouteCandidate small = candidate("small", 1.0d, 1000.0d, 10);
        RouteCandidate large = candidate("large", 1.0d, 1000.0d, 100_000);
        RouteDecision decision = router.select("chat", List.of(message("a".repeat(400))), List.of(small, large));
        assertThat(decision.getSelected().getModelCode()).isEqualTo("large");
        assertThat(decision.getExcluded()).containsEntry("small", "上下文窗口不足");
    }

    @Test
    void shouldExcludeUnhealthyCandidate() {
        RouteCandidate bad = candidate("bad", 1.0d, 1000.0d, 100_000);
        RouteCandidate good = candidate("good", 5.0d, 1000.0d, 100_000);
        for (int i = 0; i < 5; i++) {
            registry.recordFailure("bad");
        }
        RouteDecision decision = router.select("chat", List.of(message("hello")), List.of(bad, good));
        assertThat(decision.getSelected().getModelCode()).isEqualTo("good");
        assertThat(decision.getExcluded()).containsEntry("bad", "模型不健康处于冷却期");
    }

    @Test
    void shouldApplyTaskAffinityBonus() {
        RouteCandidate cheap = candidate("cheap", 1.0d, 1000.0d, 100_000);
        RouteCandidate matched = new RouteCandidate.Builder()
                .modelCode("matched")
                .model(new StubModel("matched"))
                .costPerMillionTokens(2.0d)
                .avgLatencyMs(1000.0d)
                .contextWindowTokens(100_000)
                .taskTypes(Set.of("code"))
                .build();
        RouteDecision decision = router.select("code", List.of(message("hello")), List.of(cheap, matched));
        assertThat(decision.getSelected().getModelCode()).isEqualTo("matched");
    }

    @Test
    void shouldFallbackToFirstCandidateWhenAllExcluded() {
        RouteCandidate first = candidate("first", 1.0d, 1000.0d, 100_000);
        RouteCandidate second = candidate("second", 2.0d, 1000.0d, 100_000);
        for (String code : List.of("first", "second")) {
            for (int i = 0; i < 5; i++) {
                registry.recordFailure(code);
            }
        }
        RouteDecision decision = router.select("chat", List.of(message("hello")), List.of(first, second));
        assertThat(decision.getSelected()).isSameAs(first);
        assertThat(decision.getExcluded()).hasSize(2);
        assertThat(decision.getScore()).isZero();
    }

    @Test
    void shouldReturnEmptyDecisionWhenNoCandidates() {
        RouteDecision decision = router.select("chat", List.of(message("hello")), List.of());
        assertThat(decision.getSelected()).isNull();
        assertThat(decision.getExcluded()).isEmpty();
    }

    @Test
    void shouldEstimateTokensByCharacterScript() {
        long tokens = HealthAwareModelRouter.estimateTokens(List.of(message("你好吗abcdefgh")));
        assertThat(tokens).isEqualTo(4L);
    }

    private RouteCandidate candidate(String modelCode, double cost, double latency, int windowTokens) {
        return RouteCandidate.builder()
                .modelCode(modelCode)
                .model(new StubModel(modelCode))
                .costPerMillionTokens(cost)
                .avgLatencyMs(latency)
                .contextWindowTokens(windowTokens)
                .build();
    }

    private AgentMessage message(String text) {
        return AgentMessage.builder()
                .role(AgentMessageRole.USER)
                .content(List.of(AgentTextBlock.builder().text(text).build()))
                .build();
    }

    /**
     * 桩模型
     */
    private static class StubModel implements AgentModel {

        private final String code;

        StubModel(String code) {
            this.code = code;
        }

        @Override
        public AgentChatResponse generate(List<AgentMessage> messages, List<Map<String, Object>> tools,
                                            AgentGenerateOptions options) {
            return new AgentChatResponse(List.of(AgentTextBlock.builder().text(code).build()), null);
        }

        @Override
        public Flux<AgentChatResponse> stream(List<AgentMessage> messages, List<Map<String, Object>> tools,
                                                AgentGenerateOptions options) {
            return Flux.just(generate(messages, tools, options));
        }
    }
}
