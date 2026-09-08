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
package com.yangqiongai.agent.harness.eval;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.junit.jupiter.api.Test;

import com.yangqiongai.agent.harness.core.AgentRuntime;
import com.yangqiongai.agent.harness.core.event.AgentEvent;
import com.yangqiongai.agent.harness.core.event.AgentResultEvent;
import com.yangqiongai.agent.harness.engine.AgentRuntimeContext;
import com.yangqiongai.agent.harness.core.message.MessageFactory;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * 用例隔离测试：每条用例使用独立的scopeId/sessionId，防止记忆与会话状态串扰
 * @author yangqiong
 */
class CaseIsolationTest {

    @Test
    void shouldUseDistinctScopePerCase() {
        ContextCapturingRuntime runtime = new ContextCapturingRuntime();
        EvalRunner runner = new EvalRunner(runtime);
        EvalDataset dataset = EvalDataset.of("isolation-check",
                EvalCase.builder().id("case-a").query("查询A").build(),
                EvalCase.builder().id("case-b").query("查询B").build());

        runner.run(dataset);

        assertThat(runtime.contexts).hasSize(2);
        AgentRuntimeContext contextA = runtime.contexts.get("case-a");
        AgentRuntimeContext contextB = runtime.contexts.get("case-b");
        assertThat(contextA.getScopeId()).isEqualTo("eval-case-case-a");
        assertThat(contextA.getSessionId()).isEqualTo("case-a");
        assertThat(contextB.getScopeId()).isEqualTo("eval-case-case-b");
        assertThat(contextB.getSessionId()).isEqualTo("case-b");
        assertThat(contextA.getScopeId()).isNotEqualTo(contextB.getScopeId());
    }

    /**
     * 捕获每条用例执行上下文的桩运行时
     * @author yangqiong
     */
    static class ContextCapturingRuntime implements AgentRuntime {

        /**
         * 按用例ID记录执行上下文
         */
        final Map<String, AgentRuntimeContext> contexts = new ConcurrentHashMap<>();

        @Override
        public Mono<com.yangqiongai.agent.harness.core.message.AgentMessage> call(
                List<com.yangqiongai.agent.harness.core.message.AgentMessage> inputs,
                AgentRuntimeContext context) {
            return Mono.just(MessageFactory.createUserMessage("桩运行时同步结果"));
        }

        @Override
        public Flux<AgentEvent> stream(List<com.yangqiongai.agent.harness.core.message.AgentMessage> inputs,
                                       AgentRuntimeContext context) {
            contexts.put(context.getSessionId(), context);
            return Flux.just(new AgentResultEvent(MessageFactory.createUserMessage("桩运行时结果"), null));
        }

        @Override
        public String getName() {
            return "stub-context-capturing";
        }
    }
}
