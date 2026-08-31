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

import org.junit.jupiter.api.Test;

/**
 * 规则评测判分器测试
 * @author yangqiong
 */
class RuleEvalJudgeTest {

    private final RuleEvalJudge judge = RuleEvalJudge.instance();

    @Test
    void shouldScoreFullHit() {
        EvalCase evalCase = EvalCase.builder()
                .id("full-hit")
                .query("北京天气怎么样")
                .expectedToolNames(List.of("search"))
                .expectedKeywords(List.of("天气"))
                .build();
        JudgeVerdict verdict = judge.judge(evalCase, "北京今日天气晴朗", List.of("search"));
        assertThat(verdict.score()).isEqualTo(1.0);
        assertThat(verdict.reasoning()).contains("工具命中率").contains("关键词命中率").contains("综合得分");
    }

    @Test
    void shouldScorePartialHit() {
        EvalCase evalCase = EvalCase.builder()
                .id("partial-hit")
                .query("北京天气怎么样")
                .expectedToolNames(List.of("search", "calendar"))
                .expectedKeywords(List.of("天气", "下雨"))
                .build();
        JudgeVerdict verdict = judge.judge(evalCase, "北京今日天气晴朗", List.of("search"));
        assertThat(verdict.score()).isCloseTo(0.5, within(1e-9));
    }

    @Test
    void shouldScoreNoExpectation() {
        EvalCase evalCase = EvalCase.builder()
                .id("no-expectation")
                .query("打个招呼")
                .build();
        JudgeVerdict verdict = judge.judge(evalCase, "你好", List.of());
        assertThat(verdict.score()).isEqualTo(1.0);
    }

    @Test
    void shouldHandleNullAnswer() {
        EvalCase evalCase = EvalCase.builder()
                .id("null-answer")
                .query("测试")
                .expectedKeywords(List.of("关键字"))
                .build();
        JudgeVerdict verdict = judge.judge(evalCase, null, List.of());
        assertThat(verdict.score()).isEqualTo(0.0);
    }
}