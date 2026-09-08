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
package com.yangqiongai.agent.harness.orchestration;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import com.yangqiongai.agent.harness.subagent.orchestration.HandoffRegistry;
import com.yangqiongai.agent.harness.subagent.orchestration.HandoffTarget;
import org.junit.jupiter.api.Test;

import com.yangqiongai.agent.harness.core.AgentRuntime;
import com.yangqiongai.agent.harness.core.event.AgentEvent;
import com.yangqiongai.agent.harness.core.message.AgentMessage;
import com.yangqiongai.agent.harness.engine.AgentRuntimeContext;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Handoff目标注册表测试
 * @author yangqiong
 */
class HandoffRegistryTest {

    @Test
    void shouldRegisterTarget() {
        HandoffRegistry registry = new HandoffRegistry();
        assertThat(registry.register(target("agent-1", "角色一"))).isTrue();
        assertThat(registry.names()).contains("agent-1");
        assertThat(registry.get("agent-1")).isPresent();
    }

    @Test
    void shouldOverwriteSameNameTarget() {
        HandoffRegistry registry = new HandoffRegistry();
        assertThat(registry.register(target("agent-1", "旧角色"))).isTrue();
        assertThat(registry.register(target("agent-1", "新角色"))).isTrue();
        assertThat(registry.names()).containsExactly("agent-1");
        assertThat(registry.get("agent-1").orElseThrow().roleDescription()).isEqualTo("新角色");
    }

    @Test
    void shouldRejectWhenOverLimit() {
        HandoffRegistry registry = new HandoffRegistry();
        for (int i = 0; i < HandoffRegistry.MAX_TARGETS; i++) {
            assertThat(registry.register(target("agent-" + i, "角色"))).isTrue();
        }
        assertThat(registry.register(target("agent-overflow", "角色"))).isFalse();
        assertThat(registry.names()).hasSize(HandoffRegistry.MAX_TARGETS);
    }

    @Test
    void shouldReturnEmptyForUnknownTarget() {
        HandoffRegistry registry = new HandoffRegistry();
        assertThat(registry.get("unknown")).isEmpty();
    }

    @Test
    void shouldRemoveTarget() {
        HandoffRegistry registry = new HandoffRegistry();
        registry.register(target("agent-1", "角色"));
        assertThat(registry.remove("agent-1")).isNotNull();
        assertThat(registry.names()).doesNotContain("agent-1");
    }

    @Test
    void shouldRejectNullTarget() {
        HandoffRegistry registry = new HandoffRegistry();
        assertThat(registry.register(null)).isFalse();
    }

    private static HandoffTarget target(String name, String roleDescription) {
        return new HandoffTarget(new StubRuntime(name), roleDescription, null);
    }

    /**
     * 桩运行时
     */
    private static class StubRuntime implements AgentRuntime {
        private final String name;

        StubRuntime(String name) {
            this.name = name;
        }

        @Override
        public Mono<AgentMessage> call(List<AgentMessage> inputs, AgentRuntimeContext context) {
            return Mono.empty();
        }

        @Override
        public Flux<AgentEvent> stream(List<AgentMessage> inputs, AgentRuntimeContext context) {
            return Flux.empty();
        }

        @Override
        public String getName() {
            return name;
        }
    }
}
