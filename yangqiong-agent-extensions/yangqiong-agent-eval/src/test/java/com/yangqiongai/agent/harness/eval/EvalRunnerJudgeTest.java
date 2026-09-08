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

import com.yangqiongai.agent.harness.core.AgentRuntime;
import com.yangqiongai.agent.harness.core.event.AgentEvent;
import com.yangqiongai.agent.harness.core.event.AgentEventType;
import com.yangqiongai.agent.harness.core.event.AgentResultEvent;
import com.yangqiongai.agent.harness.core.message.AgentMessage;
import com.yangqiongai.agent.harness.core.message.AgentToolUseBlock;
import com.yangqiongai.agent.harness.engine.AgentRuntimeContext;
import com.yangqiongai.agent.harness.core.message.MessageFactory;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * 带判分器的评测执行测试
 * @author yangqiong
 */
class EvalRunnerJudgeTest {

    private final EvalRunner runner = new EvalRunner(new StubRuntime());

    @Test
    void shouldIncludeJudgeScoreWhenJudgeProvided() {
        EvalCase evalCase = EvalCase.builder()
                .id("case-judged")
                .query("北京天气怎么样")
                .expectedToolNames(List.of("search"))
                .expectedKeywords(List.of("天气"))
                .build();
        EvalJudge judge = (ec, answer, tools) -> new JudgeVerdict(0.9, "LLM判分：回答质量较高");

        EvalReport report = runner.run(EvalDataset.of("judge-test", evalCase), 1, judge);
        EvalCaseResult result = findResult(report, "case-judged");

        assertThat(result.getJudgeScore()).isEqualTo(0.9);
        assertThat(result.getJudgeReason()).isEqualTo("LLM判分：回答质量较高");
        assertThat(result.getScore()).isEqualTo(1.0); // 规则评分满分
        assertThat(report.getAvgJudgeScore()).isEqualTo(0.9);
    }

    @Test
    void shouldNotIncludeJudgeScoreWhenJudgeNotProvided() {
        EvalCase evalCase = EvalCase.builder()
                .id("case-no-judge")
                .query("北京天气怎么样")
                .expectedToolNames(List.of("search"))
                .expectedKeywords(List.of("天气"))
                .build();

        EvalReport report = runner.run(EvalDataset.of("no-judge-test", evalCase), 1);
        EvalCaseResult result = findResult(report, "case-no-judge");

        assertThat(result.getJudgeScore()).isNull();
        assertThat(result.getJudgeReason()).isNull();
        assertThat(report.getAvgJudgeScore()).isEqualTo(0.0);
    }

    @Test
    void shouldHandleFailureCaseWithJudge() {
        EvalCase failureCase = EvalCase.builder()
                .id("case-failure")
                .query("触发失败")
                .expectedToolNames(List.of("search"))
                .build();
        EvalJudge judge = (ec, answer, tools) -> new JudgeVerdict(0.5, "异常");

        EvalReport report = runner.run(EvalDataset.of("failure-judge", failureCase), 1, judge);
        EvalCaseResult result = findResult(report, "case-failure");

        assertThat(result.getScore()).isEqualTo(0.0);
        assertThat(result.getJudgeScore()).isNull(); // 异常时无判分
        assertThat(result.getFailureReason()).isNotNull();
    }

    @Test
    void shouldReportSummaryWithJudge() {
        EvalCase evalCase = EvalCase.builder()
                .id("case-summary")
                .query("北京天气")
                .expectedToolNames(List.of("search"))
                .build();
        EvalJudge judge = (ec, answer, tools) -> new JudgeVerdict(0.8, "好");

        EvalReport report = runner.run(EvalDataset.of("summary-judge", evalCase), 1, judge);
        String summary = report.summary();

        assertThat(summary).contains("平均LLM判分0.80");
    }

    @Test
    void shouldReportSummaryWithoutJudge() {
        EvalCase evalCase = EvalCase.builder()
                .id("case-no-judge-summary")
                .query("北京天气")
                .expectedToolNames(List.of("search"))
                .build();

        EvalReport report = runner.run(EvalDataset.of("no-judge-summary", evalCase), 1);
        String summary = report.summary();

        assertThat(summary).doesNotContain("平均LLM判分");
    }

    private EvalCaseResult findResult(EvalReport report, String caseId) {
        return report.getResults().stream()
                .filter(result -> caseId.equals(result.getCaseId()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("未找到用例结果: " + caseId));
    }

    static class StubRuntime implements AgentRuntime {

        @Override
        public Mono<AgentMessage> call(List<AgentMessage> inputs, AgentRuntimeContext context) {
            return Mono.just(MessageFactory.createUserMessage("桩运行时同步结果"));
        }

        @Override
        public Flux<AgentEvent> stream(List<AgentMessage> inputs, AgentRuntimeContext context) {
            String query = inputs.get(0).getTextContent();
            if (query.contains("失败")) {
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