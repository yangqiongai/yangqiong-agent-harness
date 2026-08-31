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

import java.util.List;

/**
 * 规则评测判分器
 * @author yangqiong
 */
public final class RuleEvalJudge implements EvalJudge {

    /**
     * 默认判分器实例
     */
    private static final RuleEvalJudge INSTANCE = new RuleEvalJudge();

    private RuleEvalJudge() {
    }

    /**
     * 获取默认判分器实例
     * @return
     */
    public static RuleEvalJudge instance() {
        return INSTANCE;
    }

    /**
     * 基于期望工具命中率与关键词命中率计算综合得分并给出理由
     * @param evalCase
     * @param finalAnswer
     * @param actualToolNames
     * @return
     */
    @Override
    public JudgeVerdict judge(EvalCase evalCase, String finalAnswer, List<String> actualToolNames) {
        double toolSelectionAccuracy = RuleScorer.toolSelectionAccuracy(evalCase.getExpectedToolNames(), actualToolNames);
        double keywordHitRate = RuleScorer.keywordHitRate(evalCase.getExpectedKeywords(), finalAnswer);
        double score = RuleScorer.compositeScore(evalCase.getExpectedToolNames(), toolSelectionAccuracy, keywordHitRate);
        String reasoning = String.format("规则评分：工具命中率%.2f，关键词命中率%.2f，综合得分%.2f",
                toolSelectionAccuracy, keywordHitRate, score);
        return new JudgeVerdict(score, reasoning);
    }
}
