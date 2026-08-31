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
import java.util.Objects;

/**
 * 单条用例稳定性统计
 * @author yangqiong
 */
public final class CaseStability {

    /**
     * 用例ID
     */
    private final String caseId;

    /**
     * 运行轮数
     */
    private final int totalRuns;

    /**
     * 通过轮数
     */
    private final int passedRuns;

    /**
     * 通过率（0-1）
     */
    private final double passRate;

    /**
     * 平均综合得分
     */
    private final double avgScore;

    /**
     * 最低综合得分
     */
    private final double minScore;

    /**
     * 最高综合得分
     */
    private final double maxScore;

    /**
     * 平均执行耗时（毫秒）
     */
    private final long avgLatencyMs;

    /**
     * 最大执行耗时（毫秒）
     */
    private final long maxLatencyMs;

    /**
     * 最近一次失败原因（全部通过时为空）
     */
    private final String lastFailureReason;

    /**
     * 平均ReAct迭代轮次
     */
    private final double avgReactIterations;

    /**
     * 平均Token消耗
     */
    private final long avgTokens;

    /**
     * 构建单条用例稳定性统计并汇总指标
     * @param caseId
     * @param results
     */
    public CaseStability(String caseId, List<EvalCaseResult> results) {
        this.caseId = caseId;
        this.totalRuns = results.size();
        this.passedRuns = (int) results.stream().filter(EvalCaseResult::isPassed).count();
        this.passRate = totalRuns == 0 ? 0.0 : (double) passedRuns / totalRuns;
        this.avgScore = results.stream().mapToDouble(EvalCaseResult::getScore).average().orElse(0.0);
        this.minScore = results.stream().mapToDouble(EvalCaseResult::getScore).min().orElse(0.0);
        this.maxScore = results.stream().mapToDouble(EvalCaseResult::getScore).max().orElse(0.0);
        this.avgLatencyMs = (long) results.stream().mapToLong(EvalCaseResult::getLatencyMs).average().orElse(0.0);
        this.maxLatencyMs = results.stream().mapToLong(EvalCaseResult::getLatencyMs).max().orElse(0L);
        this.lastFailureReason = results.stream()
                .filter(result -> !result.isPassed())
                .map(EvalCaseResult::getFailureReason)
                .filter(Objects::nonNull)
                .reduce((first, second) -> second)
                .orElse(null);
        this.avgReactIterations = results.isEmpty() ? 0.0
                : results.stream().mapToInt(EvalCaseResult::getReactIterations).average().orElse(0.0);
        this.avgTokens = (long) results.stream().mapToLong(EvalCaseResult::getTotalTokens).average().orElse(0.0);
    }

    /**
     * 判断用例是否波动（既非全部通过也非全部失败）
     * @return
     */
    public boolean isFlaky() {
        return passedRuns > 0 && passedRuns < totalRuns;
    }

    /**
     * 获取用例ID
     * @return
     */
    public String getCaseId() {
        return caseId;
    }

    /**
     * 获取运行轮数
     * @return
     */
    public int getTotalRuns() {
        return totalRuns;
    }

    /**
     * 获取通过轮数
     * @return
     */
    public int getPassedRuns() {
        return passedRuns;
    }

    /**
     * 获取通过率
     * @return
     */
    public double getPassRate() {
        return passRate;
    }

    /**
     * 获取平均综合得分
     * @return
     */
    public double getAvgScore() {
        return avgScore;
    }

    /**
     * 获取最低综合得分
     * @return
     */
    public double getMinScore() {
        return minScore;
    }

    /**
     * 获取最高综合得分
     * @return
     */
    public double getMaxScore() {
        return maxScore;
    }

    /**
     * 获取平均执行耗时（毫秒）
     * @return
     */
    public long getAvgLatencyMs() {
        return avgLatencyMs;
    }

    /**
     * 获取最大执行耗时（毫秒）
     * @return
     */
    public long getMaxLatencyMs() {
        return maxLatencyMs;
    }

    /**
     * 获取最近一次失败原因，全部通过时返回null
     * @return
     */
    public String getLastFailureReason() {
        return lastFailureReason;
    }

    /**
     * 获取平均ReAct迭代轮次
     * @return
     */
    public double getAvgReactIterations() {
        return avgReactIterations;
    }

    /**
     * 获取平均Token消耗
     * @return
     */
    public long getAvgTokens() {
        return avgTokens;
    }
}
