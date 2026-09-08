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

import org.junit.jupiter.api.Test;

import com.yangqiongai.agent.harness.core.AgentRuntime;
import com.yangqiongai.agent.harness.core.event.AgentEvent;
import com.yangqiongai.agent.harness.core.event.AgentEventType;
import com.yangqiongai.agent.harness.core.event.AgentResultEvent;
import com.yangqiongai.agent.harness.core.event.ModelCallResult;
import com.yangqiongai.agent.harness.core.message.AgentChatUsage;
import com.yangqiongai.agent.harness.core.message.MessageFactory;
import com.yangqiongai.agent.harness.engine.AgentRuntimeContext;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * 效率指标统计测试：ReAct轮次、Token消耗与成本
 * @author yangqiong
 */
class EfficiencyMetricsTest {

    @Test
    void shouldCountIterationsAndTokensFromModelCallEnd() {
        EvalRunner runner = new EvalRunner(new MultiRoundRuntime(null));
        EvalDataset dataset = EvalDataset.of("efficiency-check",
                EvalCase.builder().id("case-iter").query("北京天气怎么样").build());

        EvalReport report = runner.run(dataset);
        EvalCaseResult result = report.getResults().get(0);

        assertThat(result.getReactIterations()).isEqualTo(2);
        assertThat(result.getPromptTokens()).isEqualTo(1500);
        assertThat(result.getCompletionTokens()).isEqualTo(200);
        assertThat(result.getTotalTokens()).isEqualTo(1700);
        assertThat(report.getAvgReactIterations()).isEqualTo(2.0);
        assertThat(report.getTotalTokens()).isEqualTo(1700);
    }

    @Test
    void shouldPreferCumulativeUsageFromResultEvent() {
        EvalRunner runner = new EvalRunner(new MultiRoundRuntime(new AgentChatUsage(3000, 500, 3500)));
        EvalDataset dataset = EvalDataset.of("efficiency-check",
                EvalCase.builder().id("case-cumulative").query("北京天气怎么样").build());

        EvalReport report = runner.run(dataset);
        EvalCaseResult result = report.getResults().get(0);

        assertThat(result.getReactIterations()).isEqualTo(2);
        assertThat(result.getTotalTokens()).isEqualTo(3500);
        assertThat(report.summary()).contains("任务平均2.0轮迭代、3.5k tokens");
    }

    @Test
    void shouldExportCostWhenPresent() {
        EvalRunner runner = new EvalRunner(new CostedRuntime());
        EvalDataset dataset = EvalDataset.of("cost-check",
                EvalCase.builder().id("case-cost").query("北京天气怎么样").build());

        EvalReport report = runner.run(dataset);

        assertThat(report.getTotalCostUsd()).isEqualTo(0.021);
        assertThat(report.summary()).contains("总成本$0.0210");
    }

    @Test
    void shouldAggregateIterationsAndTokensInStabilityReport() {
        StabilityRunner stabilityRunner = new StabilityRunner(new EvalRunner(new MultiRoundRuntime(null)));
        EvalDataset dataset = EvalDataset.of("stability-efficiency",
                EvalCase.builder().id("case-iter").query("北京天气怎么样").build());

        StabilityReport report = stabilityRunner.run(dataset, 2);

        assertThat(report.getAvgReactIterations()).isEqualTo(2.0);
        assertThat(report.getAvgTokens()).isEqualTo(1700);
        assertThat(report.summary()).contains("任务平均2.0轮迭代、1.7k tokens");
        assertThat(report.toMarkdown()).contains("| case-iter | 2/2 | 100.0% | 1.00 | 1.00 | 1.00 | 2.0 | 1700 |");
    }

    /**
     * 两轮模型调用的桩运行时，逐次上报Token用量
     * @author yangqiong
     */
    static class MultiRoundRuntime implements AgentRuntime {

        /**
         * 结果事件携带的累计用量，为null时模拟不携带
         */
        private final AgentChatUsage cumulativeUsage;

        MultiRoundRuntime(AgentChatUsage cumulativeUsage) {
            this.cumulativeUsage = cumulativeUsage;
        }

        @Override
        public Mono<com.yangqiongai.agent.harness.core.message.AgentMessage> call(
                List<com.yangqiongai.agent.harness.core.message.AgentMessage> inputs,
                AgentRuntimeContext context) {
            return Mono.just(MessageFactory.createUserMessage("桩运行时同步结果"));
        }

        @Override
        public Flux<AgentEvent> stream(List<com.yangqiongai.agent.harness.core.message.AgentMessage> inputs,
                                       AgentRuntimeContext context) {
            return Flux.just(
                    AgentEvent.of(AgentEventType.MODEL_CALL_END,
                            new ModelCallResult("eval-agent", "stub-model", 1,
                                    new AgentChatUsage(800, 100, 900))),
                    AgentEvent.of(AgentEventType.MODEL_CALL_END,
                            new ModelCallResult("eval-agent", "stub-model", 2,
                                    new AgentChatUsage(700, 100, 800))),
                    new AgentResultEvent(MessageFactory.createUserMessage("北京今日天气晴朗"), cumulativeUsage),
                    AgentEvent.completed());
        }

        @Override
        public String getName() {
            return "stub-multi-round";
        }
    }

    /**
     * 携带成本数据的桩运行时
     * @author yangqiong
     */
    static class CostedRuntime implements AgentRuntime {

        @Override
        public Mono<com.yangqiongai.agent.harness.core.message.AgentMessage> call(
                List<com.yangqiongai.agent.harness.core.message.AgentMessage> inputs,
                AgentRuntimeContext context) {
            return Mono.just(MessageFactory.createUserMessage("桩运行时同步结果"));
        }

        @Override
        public Flux<AgentEvent> stream(List<com.yangqiongai.agent.harness.core.message.AgentMessage> inputs,
                                       AgentRuntimeContext context) {
            return Flux.just(
                    AgentEvent.of(AgentEventType.MODEL_CALL_END,
                            new ModelCallResult("eval-agent", "stub-model", 1,
                                    new AgentChatUsage(500, 50, 550))),
                    new AgentResultEvent(MessageFactory.createUserMessage("北京今日天气晴朗"),
                            new AgentChatUsage(500, 50, 550), 0.021),
                    AgentEvent.completed());
        }

        @Override
        public String getName() {
            return "stub-costed";
        }
    }
}
