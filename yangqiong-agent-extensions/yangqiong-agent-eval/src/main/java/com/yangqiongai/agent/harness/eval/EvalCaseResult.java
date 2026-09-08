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

import java.util.List;

/**
 * 评测用例结果
 * @author yangqiong
 */
public final class EvalCaseResult {

    /**
     * 用例ID
     */
    private final String caseId;

    /**
     * 是否通过
     */
    private final boolean passed;

    /**
     * 综合得分（0-1）
     */
    private final double score;

    /**
     * 期望工具命中率（0-1）
     */
    private final double toolSelectionAccuracy;

    /**
     * 期望参数匹配率（0-1，无参数期望时为1.0）
     */
    private final double argMatchRate;

    /**
     * 期望关键词命中率（0-1）
     */
    private final double keywordHitRate;

    /**
     * 实际调用的工具名称列表
     */
    private final List<String> actualToolNames;

    /**
     * 最终答案文本
     */
    private final String finalAnswer;

    /**
     * 执行耗时（毫秒）
     */
    private final long latencyMs;

    /**
     * 失败原因
     */
    private final String failureReason;

    /**
     * 判分器得分（0-1，未判分时为空）
     */
    private final Double judgeScore;

    /**
     * 判分器理由（未判分时为空）
     */
    private final String judgeReason;

    /**
     * ReAct迭代轮次（模型调用次数）
     */
    private final int reactIterations;

    /**
     * 提示Token数
     */
    private final long promptTokens;

    /**
     * 完成Token数
     */
    private final long completionTokens;

    /**
     * 总Token数
     */
    private final long totalTokens;

    /**
     * 累计成本（美元），可空
     */
    private final Double costUsd;

    /**
     * 构建评测用例结果（不含判分器结果）
     * @param caseId
     * @param passed
     * @param score
     * @param toolSelectionAccuracy
     * @param keywordHitRate
     * @param actualToolNames
     * @param finalAnswer
     * @param latencyMs
     * @param failureReason
     */
    public EvalCaseResult(String caseId, boolean passed, double score, double toolSelectionAccuracy,
                          double keywordHitRate, List<String> actualToolNames, String finalAnswer,
                          long latencyMs, String failureReason) {
        this(caseId, passed, score, toolSelectionAccuracy, 1.0, keywordHitRate, actualToolNames,
                finalAnswer, latencyMs, failureReason, null, null);
    }

    /**
     * 构建评测用例结果（含判分器结果，参数匹配率默认1.0）
     * @param caseId
     * @param passed
     * @param score
     * @param toolSelectionAccuracy
     * @param keywordHitRate
     * @param actualToolNames
     * @param finalAnswer
     * @param latencyMs
     * @param failureReason
     * @param judgeScore
     * @param judgeReason
     */
    public EvalCaseResult(String caseId, boolean passed, double score, double toolSelectionAccuracy,
                          double keywordHitRate, List<String> actualToolNames, String finalAnswer,
                          long latencyMs, String failureReason, Double judgeScore, String judgeReason) {
        this(caseId, passed, score, toolSelectionAccuracy, 1.0, keywordHitRate, actualToolNames,
                finalAnswer, latencyMs, failureReason, judgeScore, judgeReason);
    }

    /**
     * 构建评测用例结果（含判分器与效率指标全字段）
     * @param caseId
     * @param passed
     * @param score
     * @param toolSelectionAccuracy
     * @param argMatchRate
     * @param keywordHitRate
     * @param actualToolNames
     * @param finalAnswer
     * @param latencyMs
     * @param failureReason
     * @param judgeScore
     * @param judgeReason
     * @param reactIterations
     * @param promptTokens
     * @param completionTokens
     * @param totalTokens
     * @param costUsd
     */
    public EvalCaseResult(String caseId, boolean passed, double score, double toolSelectionAccuracy,
                          double argMatchRate, double keywordHitRate, List<String> actualToolNames,
                          String finalAnswer, long latencyMs, String failureReason,
                          Double judgeScore, String judgeReason, int reactIterations,
                          long promptTokens, long completionTokens, long totalTokens, Double costUsd) {
        this.caseId = caseId;
        this.passed = passed;
        this.score = score;
        this.toolSelectionAccuracy = toolSelectionAccuracy;
        this.argMatchRate = argMatchRate;
        this.keywordHitRate = keywordHitRate;
        this.actualToolNames = actualToolNames == null ? List.of() : List.copyOf(actualToolNames);
        this.finalAnswer = finalAnswer;
        this.latencyMs = latencyMs;
        this.failureReason = failureReason;
        this.judgeScore = judgeScore;
        this.judgeReason = judgeReason;
        this.reactIterations = reactIterations;
        this.promptTokens = promptTokens;
        this.completionTokens = completionTokens;
        this.totalTokens = totalTokens;
        this.costUsd = costUsd;
    }

    /**
     * 构建评测用例结果（不含效率指标，历史兼容）
     * @param caseId
     * @param passed
     * @param score
     * @param toolSelectionAccuracy
     * @param argMatchRate
     * @param keywordHitRate
     * @param actualToolNames
     * @param finalAnswer
     * @param latencyMs
     * @param failureReason
     * @param judgeScore
     * @param judgeReason
     */
    public EvalCaseResult(String caseId, boolean passed, double score, double toolSelectionAccuracy,
                          double argMatchRate, double keywordHitRate, List<String> actualToolNames,
                          String finalAnswer, long latencyMs, String failureReason,
                          Double judgeScore, String judgeReason) {
        this(caseId, passed, score, toolSelectionAccuracy, argMatchRate, keywordHitRate, actualToolNames,
                finalAnswer, latencyMs, failureReason, judgeScore, judgeReason, 0, 0, 0, 0, null);
    }

    /**
     * 获取用例ID
     * @return
     */
    public String getCaseId() {
        return caseId;
    }

    /**
     * 判断用例是否通过
     * @return
     */
    public boolean isPassed() {
        return passed;
    }

    /**
     * 获取综合得分
     * @return
     */
    public double getScore() {
        return score;
    }

    /**
     * 获取期望工具命中率
     * @return
     */
    public double getToolSelectionAccuracy() {
        return toolSelectionAccuracy;
    }

    /**
     * 获取期望参数匹配率，无参数期望时返回1.0
     * @return
     */
    public double getArgMatchRate() {
        return argMatchRate;
    }

    /**
     * 获取期望关键词命中率
     * @return
     */
    public double getKeywordHitRate() {
        return keywordHitRate;
    }

    /**
     * 获取实际调用的工具名称列表
     * @return
     */
    public List<String> getActualToolNames() {
        return actualToolNames;
    }

    /**
     * 获取最终答案文本
     * @return
     */
    public String getFinalAnswer() {
        return finalAnswer;
    }

    /**
     * 获取执行耗时（毫秒）
     * @return
     */
    public long getLatencyMs() {
        return latencyMs;
    }

    /**
     * 获取失败原因
     * @return
     */
    public String getFailureReason() {
        return failureReason;
    }

    /**
     * 获取判分器得分，未判分时返回null
     * @return
     */
    public Double getJudgeScore() {
        return judgeScore;
    }

    /**
     * 获取判分器理由，未判分时返回null
     * @return
     */
    public String getJudgeReason() {
        return judgeReason;
    }

    /**
     * 获取ReAct迭代轮次（模型调用次数）
     * @return
     */
    public int getReactIterations() {
        return reactIterations;
    }

    /**
     * 获取提示Token数
     * @return
     */
    public long getPromptTokens() {
        return promptTokens;
    }

    /**
     * 获取完成Token数
     * @return
     */
    public long getCompletionTokens() {
        return completionTokens;
    }

    /**
     * 获取总Token数
     * @return
     */
    public long getTotalTokens() {
        return totalTokens;
    }

    /**
     * 获取累计成本（美元），无数据时返回null
     * @return
     */
    public Double getCostUsd() {
        return costUsd;
    }
}
