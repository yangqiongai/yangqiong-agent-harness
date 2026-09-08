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
import static org.assertj.core.api.Assertions.within;

import java.util.List;
import java.util.Map;

import com.yangqiongai.agent.harness.core.message.AgentToolUseBlock;
import org.junit.jupiter.api.Test;

/**
 * 规则评分测试
 * @author yangqiong
 */
class RuleScorerTest {

    @Test
    void shouldScoreToolSelectionAccuracy() {
        assertThat(RuleScorer.toolSelectionAccuracy(List.of("a", "b"), List.of("a", "b"))).isEqualTo(1.0);
        assertThat(RuleScorer.toolSelectionAccuracy(List.of("a", "b"), List.of("a"))).isEqualTo(0.5,
                within(0.0001));
        assertThat(RuleScorer.toolSelectionAccuracy(List.of("a"), List.of())).isEqualTo(0.0);
        assertThat(RuleScorer.toolSelectionAccuracy(List.of(), List.of("a"))).isEqualTo(1.0);
    }

    @Test
    void shouldZeroScoreWhenForbiddenToolCalled() {
        assertThat(RuleScorer.toolSelectionAccuracy(List.of(), List.of("a", "b"), List.of("a")))
                .isEqualTo(0.0);
        assertThat(RuleScorer.toolSelectionAccuracy(List.of("x"), List.of("a"), List.of("x")))
                .isEqualTo(1.0);
        assertThat(RuleScorer.toolSelectionAccuracy(List.of(), List.of("a"), List.of()))
                .isEqualTo(1.0);
    }

    @Test
    void shouldScoreArgMatchRateWithContainsMatching() {
        Map<String, Map<String, Object>> expectedArgs = Map.of(
                "get_weather", Map.of("city", "北京"));
        List<AgentToolUseBlock> calls = List.of(
                new AgentToolUseBlock("get_weather", "t1", Map.of("city", "北京市")),
                new AgentToolUseBlock("other_tool", "t2", Map.of("city", "北京")));

        assertThat(RuleScorer.argMatchRate(expectedArgs, calls)).isEqualTo(1.0);
    }

    @Test
    void shouldScoreArgMatchRateWithLastCallAndNumericEquality() {
        Map<String, Map<String, Object>> expectedArgs = Map.of(
                "calculator", Map.<String, Object>of("a", 100));
        List<AgentToolUseBlock> calls = List.of(
                new AgentToolUseBlock("calculator", "t1", Map.of("a", 200)),
                new AgentToolUseBlock("calculator", "t2", Map.of("a", 100)));

        assertThat(RuleScorer.argMatchRate(expectedArgs, calls)).isEqualTo(1.0);
    }

    @Test
    void shouldPenalizeMissingOrMismatchedArgs() {
        Map<String, Map<String, Object>> expectedArgs = Map.of(
                "get_weather", Map.of("city", "上海"));
        List<AgentToolUseBlock> wrongCity = List.of(
                new AgentToolUseBlock("get_weather", "t1", Map.of("city", "北京")));
        List<AgentToolUseBlock> missingArgs = List.of(
                new AgentToolUseBlock("get_weather", "t1", Map.of()));

        assertThat(RuleScorer.argMatchRate(expectedArgs, wrongCity)).isEqualTo(0.0);
        assertThat(RuleScorer.argMatchRate(expectedArgs, missingArgs)).isEqualTo(0.0);
        assertThat(RuleScorer.argMatchRate(expectedArgs, List.of())).isEqualTo(0.0);
        assertThat(RuleScorer.argMatchRate(Map.of(), List.of())).isEqualTo(1.0);
    }

    @Test
    void shouldKeepLegacyCompositeWeights() {
        double score = RuleScorer.compositeScore(List.of("a"), 1.0, 1.0);
        assertThat(score).isEqualTo(1.0, within(0.0001));
        double partial = RuleScorer.compositeScore(List.of("a"), 0.5, 1.0);
        assertThat(partial).isEqualTo(0.7, within(0.0001));
    }

    @Test
    void shouldUseThreeDimensionWeightsWhenArgsAndKeywordExpected() {
        EvalCase evalCase = EvalCase.builder()
                .id("3d")
                .query("查询")
                .expectedToolNames(List.of("get_weather"))
                .expectedToolArgs(Map.of("get_weather", Map.of("city", "北京")))
                .expectedKeywords(List.of("晴"))
                .build();

        double score = RuleScorer.compositeScore(evalCase, 1.0, 1.0, 1.0);
        assertThat(score).isEqualTo(1.0, within(0.0001));
        double partial = RuleScorer.compositeScore(evalCase, 1.0, 1.0, 0.0);
        assertThat(partial).isEqualTo(0.7, within(0.0001));
    }

    @Test
    void shouldUseTwoDimensionWeightsWhenArgsOnly() {
        EvalCase evalCase = EvalCase.builder()
                .id("tool-args")
                .query("查询")
                .expectedToolNames(List.of("get_weather"))
                .expectedToolArgs(Map.of("get_weather", Map.<String, Object>of("city", "北京")))
                .build();

        double score = RuleScorer.compositeScore(evalCase, 1.0, 1.0, 0.0);
        assertThat(score).isEqualTo(1.0, within(0.0001));
        double partial = RuleScorer.compositeScore(evalCase, 1.0, 0.5, 0.0);
        assertThat(partial).isEqualTo(0.8, within(0.0001));
    }

    @Test
    void shouldDetectFalseCallViaForbiddenOnlyCase() {
        EvalCase evalCase = EvalCase.builder()
                .id("no-tool")
                .query("1+1等于几")
                .forbiddenToolNames(List.of("get_weather", "book_meeting_room"))
                .build();

        double cleanScore = RuleScorer.compositeScore(evalCase, 1.0, 1.0, 1.0);
        assertThat(cleanScore).isEqualTo(1.0, within(0.0001));
        double falseCallScore = RuleScorer.compositeScore(evalCase, 0.0, 1.0, 1.0);
        assertThat(falseCallScore).isEqualTo(0.4, within(0.0001));
    }
}
