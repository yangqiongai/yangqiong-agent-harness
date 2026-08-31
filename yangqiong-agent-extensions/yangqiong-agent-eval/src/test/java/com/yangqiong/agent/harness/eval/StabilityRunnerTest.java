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
package com.yangqiong.agent.harness.eval;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import com.yangqiong.agent.harness.core.AgentRuntime;
import com.yangqiong.agent.harness.core.event.AgentEvent;
import com.yangqiong.agent.harness.core.event.AgentEventType;
import com.yangqiong.agent.harness.core.event.AgentResultEvent;
import com.yangqiong.agent.harness.core.message.AgentMessage;
import com.yangqiong.agent.harness.core.message.AgentToolUseBlock;
import com.yangqiong.agent.harness.core.message.MessageFactory;
import com.yangqiong.agent.harness.engine.AgentRuntimeContext;
import com.yangqiong.agent.harness.eval.EvalCase;
import com.yangqiong.agent.harness.eval.EvalDataset;
import com.yangqiong.agent.harness.eval.EvalRunner;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * 稳定性评测执行测试
 * @author yangqiong
 */
class StabilityRunnerTest {

    @Test
    void shouldAggregateStablePassingCase() {
        StabilityRunner stabilityRunner = new StabilityRunner(new EvalRunner(new StableRuntime()));
        EvalDataset dataset = EvalDataset.of("stable-check",
                EvalCase.builder()
                        .id("case-a")
                        .query("北京天气怎么样")
                        .expectedToolNames(List.of("search"))
                        .expectedKeywords(List.of("天气"))
                        .build());

        StabilityReport report = stabilityRunner.run(dataset, 3);

        assertThat(report.getRuns()).isEqualTo(3);
        assertThat(report.getTotalObservations()).isEqualTo(3);
        assertThat(report.getOverallPassRate()).isEqualTo(1.0);
        assertThat(report.getCases()).hasSize(1);
        CaseStability stat = report.getCases().get(0);
        assertThat(stat.getCaseId()).isEqualTo("case-a");
        assertThat(stat.getPassedRuns()).isEqualTo(3);
        assertThat(stat.getPassRate()).isEqualTo(1.0);
        assertThat(stat.isFlaky()).isFalse();
        assertThat(stat.getLastFailureReason()).isNull();
        assertThat(stat.getMinScore()).isEqualTo(stat.getMaxScore());
        assertThat(report.summary()).contains("稳定通过1条").contains("波动(flaky)0条");
    }

    @Test
    void shouldDetectFlakyCaseAcrossRuns() {
        StabilityRunner stabilityRunner = new StabilityRunner(new EvalRunner(new AlternatingRuntime()));
        EvalDataset dataset = EvalDataset.of("flaky-check",
                EvalCase.builder()
                        .id("case-flaky")
                        .query("北京天气怎么样")
                        .expectedToolNames(List.of("search"))
                        .build());

        StabilityReport report = stabilityRunner.run(dataset, 4);

        CaseStability stat = report.getCases().get(0);
        assertThat(stat.getTotalRuns()).isEqualTo(4);
        assertThat(stat.getPassedRuns()).isEqualTo(2);
        assertThat(stat.getPassRate()).isEqualTo(0.5);
        assertThat(stat.isFlaky()).isTrue();
        assertThat(report.summary()).contains("波动(flaky)1条");
    }

    @Test
    void shouldAggregateMultipleCasesConcurrently() {
        StabilityRunner stabilityRunner = new StabilityRunner(new EvalRunner(new StableRuntime()));
        EvalDataset dataset = EvalDataset.of("multi-case-check",
                EvalCase.builder().id("case-1").query("问题一").build(),
                EvalCase.builder().id("case-2").query("问题二").build());

        StabilityReport report = stabilityRunner.run(dataset, 2, 2, null);

        assertThat(report.getCases()).hasSize(2);
        assertThat(report.getTotalObservations()).isEqualTo(4);
        assertThat(report.getOverallPassRate()).isEqualTo(1.0);
    }

    @Test
    void shouldExportStabilityMarkdown() {
        StabilityRunner stabilityRunner = new StabilityRunner(new EvalRunner(new StableRuntime()));
        EvalDataset dataset = EvalDataset.of("md-check",
                EvalCase.builder().id("case-md").query("问题").build());

        StabilityReport report = stabilityRunner.run(dataset, 2);

        assertThat(report.toMarkdown())
                .contains("# 稳定性评测报告：md-check")
                .contains("| case-md | 2/2 | 100.0% |")
                .contains("整体通过率：100.0%");
    }

    @Test
    void shouldSynthesizeFailureReasonOnScoreFailure() {
        StabilityRunner stabilityRunner = new StabilityRunner(new EvalRunner(new KeywordMissRuntime()));
        EvalDataset dataset = EvalDataset.of("null-reason-check",
                EvalCase.builder()
                        .id("case-null-reason")
                        .query("北京天气怎么样")
                        .expectedKeywords(List.of("不存在的关键词"))
                        .build());

        StabilityReport report = stabilityRunner.run(dataset, 2);

        CaseStability stat = report.getCases().get(0);
        assertThat(stat.getPassedRuns()).isZero();
        assertThat(stat.getLastFailureReason()).isEqualTo("最终答案未命中期望关键词");
        assertThat(stat.isFlaky()).isFalse();
    }

    @Test
    void shouldRejectInvalidRuns() {
        StabilityRunner stabilityRunner = new StabilityRunner(new EvalRunner(new StableRuntime()));
        EvalDataset dataset = EvalDataset.of("invalid-check",
                EvalCase.builder().id("case-1").query("问题").build());

        assertThatThrownBy(() -> stabilityRunner.run(dataset, 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("轮数");
        assertThatThrownBy(() -> stabilityRunner.run(dataset, 1, 0, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("并发度");
    }

    /**
     * 稳定通过的桩运行时
     * @author yangqiong
     */
    static class StableRuntime implements AgentRuntime {

        @Override
        public Mono<AgentMessage> call(List<AgentMessage> inputs, AgentRuntimeContext context) {
            return Mono.just(MessageFactory.createUserMessage("桩运行时同步结果"));
        }

        @Override
        public Flux<AgentEvent> stream(List<AgentMessage> inputs, AgentRuntimeContext context) {
            return Flux.just(
                    AgentEvent.of(AgentEventType.TOOL_CALL_START,
                            List.of(new AgentToolUseBlock("search", "t1", Map.of()))),
                    new AgentResultEvent(MessageFactory.createUserMessage("北京今日天气晴朗"), null),
                    AgentEvent.completed());
        }

        @Override
        public String getName() {
            return "stub-stable";
        }
    }

    /**
     * 关键词不命中的桩运行时，模拟评分失败但无异常的场景（校验合成失败原因）
     * @author yangqiong
     */
    static class KeywordMissRuntime implements AgentRuntime {

        @Override
        public Mono<AgentMessage> call(List<AgentMessage> inputs, AgentRuntimeContext context) {
            return Mono.just(MessageFactory.createUserMessage("桩运行时同步结果"));
        }

        @Override
        public Flux<AgentEvent> stream(List<AgentMessage> inputs, AgentRuntimeContext context) {
            return Flux.just(
                    new AgentResultEvent(MessageFactory.createUserMessage("北京今日天气晴朗"), null),
                    AgentEvent.completed());
        }

        @Override
        public String getName() {
            return "stub-keyword-miss";
        }
    }

    /**
     * 交替通过失败的桩运行时，模拟波动用例
     * @author yangqiong
     */
    static class AlternatingRuntime implements AgentRuntime {

        /**
         * 调用计数器
         */
        private final AtomicInteger counter = new AtomicInteger();

        @Override
        public Mono<AgentMessage> call(List<AgentMessage> inputs, AgentRuntimeContext context) {
            return Mono.just(MessageFactory.createUserMessage("桩运行时同步结果"));
        }

        @Override
        public Flux<AgentEvent> stream(List<AgentMessage> inputs, AgentRuntimeContext context) {
            boolean pass = counter.getAndIncrement() % 2 == 0;
            if (pass) {
                return Flux.just(
                        AgentEvent.of(AgentEventType.TOOL_CALL_START,
                                List.of(new AgentToolUseBlock("search", "t1", Map.of()))),
                        new AgentResultEvent(MessageFactory.createUserMessage("北京今日天气晴朗"), null),
                        AgentEvent.completed());
            }
            return Flux.error(new IllegalStateException("模拟波动失败"));
        }

        @Override
        public String getName() {
            return "stub-alternating";
        }
    }
}
