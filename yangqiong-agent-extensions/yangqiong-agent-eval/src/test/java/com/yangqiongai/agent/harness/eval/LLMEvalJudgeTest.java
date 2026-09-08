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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;

import com.yangqiongai.agent.harness.core.message.AgentTextBlock;
import com.yangqiongai.agent.harness.core.model.AgentChatResponse;
import com.yangqiongai.agent.harness.core.model.AgentModel;
import org.junit.jupiter.api.Test;

/**
 * LLM 评测判分器测试
 * @author yangqiong
 */
class LLMEvalJudgeTest {

    private AgentModel mockModel(String responseText) {
        AgentModel model = mock(AgentModel.class);
        AgentTextBlock text = AgentTextBlock.builder().text(responseText).build();
        AgentChatResponse response = new AgentChatResponse(List.of(text), null);
        when(model.generate(any(), any(), any())).thenReturn(response);
        return model;
    }

    @Test
    void shouldParseScoreAndReasoning() {
        AgentModel model = mockModel("Score: 0.85\nReasoning: 回答完整，工具使用正确");
        LLMEvalJudge judge = new LLMEvalJudge(model);
        EvalCase evalCase = EvalCase.builder()
                .id("test")
                .query("北京天气怎么样")
                .expectedToolNames(List.of("search"))
                .expectedKeywords(List.of("天气"))
                .build();
        JudgeVerdict verdict = judge.judge(evalCase, "北京今日天气晴朗", List.of("search"));
        assertThat(verdict.score()).isEqualTo(0.85);
        assertThat(verdict.reasoning()).contains("回答完整");
    }

    @Test
    void shouldFallbackOnIllegalResponse() {
        AgentModel model = mockModel("这只是一个普通回复，没有评分格式");
        LLMEvalJudge judge = new LLMEvalJudge(model);
        EvalCase evalCase = EvalCase.builder()
                .id("test-fallback")
                .query("打个招呼")
                .expectedKeywords(List.of("关键字"))
                .build();
        // 无法解析score时回退规则评分，关键字未命中得0分
        JudgeVerdict verdict = judge.judge(evalCase, "你好", List.of());
        assertThat(verdict.score()).isEqualTo(0.0);
        assertThat(verdict.reasoning()).contains("规则评分");
    }

    @Test
    void shouldFallbackOnModelException() {
        AgentModel model = mock(AgentModel.class);
        when(model.generate(any(), any(), any())).thenThrow(new RuntimeException("模型调用失败"));
        LLMEvalJudge judge = new LLMEvalJudge(model);
        EvalCase evalCase = EvalCase.builder()
                .id("test-exception")
                .query("打个招呼")
                .build();
        JudgeVerdict verdict = judge.judge(evalCase, "你好", List.of());
        // 无期望时规则评分满分
        assertThat(verdict.score()).isEqualTo(1.0);
        assertThat(verdict.reasoning()).contains("规则评分");
    }

    @Test
    void shouldParseScoreWithDifferentFormat() {
        AgentModel model = mockModel("score: 0.5 reasoning: 部分正确");
        LLMEvalJudge judge = new LLMEvalJudge(model);
        EvalCase evalCase = EvalCase.builder()
                .id("test-format")
                .query("测试")
                .build();
        JudgeVerdict verdict = judge.judge(evalCase, "回答", List.of());
        assertThat(verdict.score()).isEqualTo(0.5);
        assertThat(verdict.reasoning()).contains("部分正确");
    }

    @Test
    void shouldUseCustomFallback() {
        AgentModel model = mock(AgentModel.class);
        when(model.generate(any(), any(), any())).thenThrow(new RuntimeException("失败"));
        EvalJudge customFallback = (evalCase, finalAnswer, actualToolNames) -> new JudgeVerdict(0.5, "自定义回退");
        LLMEvalJudge judge = new LLMEvalJudge(model, customFallback);
        EvalCase evalCase = EvalCase.builder().id("test").query("测试").build();
        JudgeVerdict verdict = judge.judge(evalCase, "回答", List.of());
        assertThat(verdict.score()).isEqualTo(0.5);
        assertThat(verdict.reasoning()).isEqualTo("自定义回退");
    }
}