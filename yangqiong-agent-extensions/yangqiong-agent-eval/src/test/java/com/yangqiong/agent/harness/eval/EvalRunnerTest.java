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
import static org.assertj.core.api.Assertions.within;

import java.util.List;
import java.util.Map;

import com.yangqiong.agent.harness.core.AgentRuntime;
import com.yangqiong.agent.harness.core.event.AgentEvent;
import com.yangqiong.agent.harness.core.event.AgentEventType;
import com.yangqiong.agent.harness.core.event.AgentResultEvent;
import com.yangqiong.agent.harness.core.message.AgentMessage;
import com.yangqiong.agent.harness.core.message.AgentToolUseBlock;
import com.yangqiong.agent.harness.engine.AgentRuntimeContext;
import com.yangqiong.agent.harness.core.message.MessageFactory;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * 评测执行测试
 * @author yangqiong
 */
class EvalRunnerTest {

    private static final String FAIL_MARKER = "触发失败";

    private final EvalRunner runner = new EvalRunner(new StubRuntime());

    @Test
    void shouldScoreToolAndKeywordHits() {
        EvalCase fullHitCase = EvalCase.builder()
                .id("case-full")
                .query("北京天气怎么样")
                .expectedToolNames(List.of("search"))
                .expectedKeywords(List.of("天气"))
                .build();
        EvalCase partialHitCase = EvalCase.builder()
                .id("case-partial")
                .query("北京天气怎么样")
                .expectedToolNames(List.of("search", "calendar"))
                .expectedKeywords(List.of("天气", "下雨"))
                .build();

        EvalReport report = runner.run(EvalDataset.of("score-check", fullHitCase, partialHitCase));

        EvalCaseResult fullHit = findResult(report, "case-full");
        assertThat(fullHit.isPassed()).isTrue();
        assertThat(fullHit.getToolSelectionAccuracy()).isEqualTo(1.0);
        assertThat(fullHit.getKeywordHitRate()).isEqualTo(1.0);
        assertThat(fullHit.getScore()).isEqualTo(1.0);
        assertThat(fullHit.getActualToolNames()).containsExactly("search");
        assertThat(fullHit.getFinalAnswer()).contains("天气");

        EvalCaseResult partialHit = findResult(report, "case-partial");
        assertThat(partialHit.isPassed()).isFalse();
        assertThat(partialHit.getToolSelectionAccuracy()).isEqualTo(0.5);
        assertThat(partialHit.getKeywordHitRate()).isEqualTo(0.5);
        assertThat(partialHit.getScore()).isCloseTo(0.5, within(1e-9));
    }

    @Test
    void shouldFallBackToKeywordRateWhenNoToolExpected() {
        EvalCase keywordHitCase = EvalCase.builder()
                .id("case-keyword-hit")
                .query("打个招呼")
                .expectedKeywords(List.of("天气"))
                .build();
        EvalCase keywordMissCase = EvalCase.builder()
                .id("case-keyword-miss")
                .query("打个招呼")
                .expectedKeywords(List.of("股票"))
                .build();
        EvalCase noExpectationCase = EvalCase.builder()
                .id("case-no-expectation")
                .query("打个招呼")
                .build();

        EvalReport report = runner.run(EvalDataset.of("keyword-check",
                keywordHitCase, keywordMissCase, noExpectationCase));

        assertThat(findResult(report, "case-keyword-hit").getScore()).isEqualTo(1.0);
        assertThat(findResult(report, "case-keyword-miss").getScore()).isEqualTo(0.0);
        assertThat(findResult(report, "case-no-expectation").getScore()).isEqualTo(1.0);
        assertThat(findResult(report, "case-no-expectation").isPassed()).isTrue();
    }

    @Test
    void shouldIsolateFailureCase() {
        EvalCase normalCase = EvalCase.builder()
                .id("case-normal")
                .query("正常提问")
                .expectedToolNames(List.of("search"))
                .build();
        EvalCase failureCase = EvalCase.builder()
                .id("case-failure")
                .query("这个问题会触发失败")
                .expectedToolNames(List.of("search"))
                .build();

        EvalReport report = runner.run(EvalDataset.of("failure-check", normalCase, failureCase));

        EvalCaseResult normal = findResult(report, "case-normal");
        assertThat(normal.isPassed()).isTrue();
        assertThat(normal.getFailureReason()).isNull();

        EvalCaseResult failure = findResult(report, "case-failure");
        assertThat(failure.isPassed()).isFalse();
        assertThat(failure.getScore()).isEqualTo(0.0);
        assertThat(failure.getFailureReason()).contains("模拟运行时异常");
        assertThat(report.getTotalCases()).isEqualTo(2);
    }

    @Test
    void shouldComputeReportStatistics() {
        EvalCase caseOne = EvalCase.builder()
                .id("case-one")
                .query("问题一")
                .expectedToolNames(List.of("search"))
                .expectedKeywords(List.of("天气"))
                .build();
        EvalCase caseTwo = EvalCase.builder()
                .id("case-two")
                .query("问题二")
                .expectedToolNames(List.of("search", "calendar"))
                .expectedKeywords(List.of("天气"))
                .build();
        EvalCase caseThree = EvalCase.builder()
                .id("case-three")
                .query("问题三会触发失败")
                .build();

        EvalReport report = runner.run(EvalDataset.of("stats-check", caseOne, caseTwo, caseThree));

        assertThat(report.getDatasetName()).isEqualTo("stats-check");
        assertThat(report.getTotalCases()).isEqualTo(3);
        assertThat(report.getPassedCases()).isEqualTo(1);
        assertThat(report.getFailedCases()).isEqualTo(2);
        assertThat(report.getAvgScore()).isCloseTo(1.7 / 3.0, within(1e-9));
        assertThat(report.getAvgToolSelectionAccuracy()).isCloseTo(0.5, within(1e-9));
        assertThat(report.getAvgLatencyMs()).isGreaterThanOrEqualTo(0L);
        assertThat(report.getResults()).hasSize(3);
        assertThat(report.summary())
                .contains("评测数据集[stats-check]")
                .contains("通过1条")
                .contains("失败2条");
    }

    @Test
    void shouldRunWithConcurrencyOverload() {
        EvalCase caseOne = EvalCase.builder()
                .id("case-concurrent-one")
                .query("并发问题一")
                .build();
        EvalCase caseTwo = EvalCase.builder()
                .id("case-concurrent-two")
                .query("并发问题二")
                .build();

        EvalReport report = runner.run(EvalDataset.of("concurrency-check", caseOne, caseTwo), 2);

        assertThat(report.getTotalCases()).isEqualTo(2);
        assertThat(report.getPassedCases()).isEqualTo(2);
    }

    /**
     * 按用例ID查找结果
     * @param report
     * @param caseId
     * @return
     */
    private EvalCaseResult findResult(EvalReport report, String caseId) {
        return report.getResults().stream()
                .filter(result -> caseId.equals(result.getCaseId()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("未找到用例结果: " + caseId));
    }

    /**
     * 桩运行时
     * @author yangqiong
     */
    static class StubRuntime implements AgentRuntime {

        @Override
        public Mono<AgentMessage> call(List<AgentMessage> inputs, AgentRuntimeContext context) {
            return Mono.just(MessageFactory.createUserMessage("桩运行时同步结果"));
        }

        @Override
        public Flux<AgentEvent> stream(List<AgentMessage> inputs, AgentRuntimeContext context) {
            String query = inputs.get(0).getTextContent();
            if (query.contains(FAIL_MARKER)) {
                return Flux.error(new IllegalStateException("模拟运行时异常"));
            }
            return Flux.just(
                    AgentEvent.of(AgentEventType.TOOL_CALL_START,
                            List.of(new AgentToolUseBlock("search", "t1", Map.of()))),
                    new AgentResultEvent(MessageFactory.createUserMessage("北京今日天气晴朗，适合出行"), null),
                    AgentEvent.completed());
        }

        @Override
        public String getName() {
            return "stub";
        }
    }
}
