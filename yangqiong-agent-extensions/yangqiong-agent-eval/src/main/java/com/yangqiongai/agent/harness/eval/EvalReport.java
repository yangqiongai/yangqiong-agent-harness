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
import java.util.Objects;

/**
 * 评测报告
 * @author yangqiong
 */
public final class EvalReport {

    /**
     * 数据集名称
     */
    private final String datasetName;

    /**
     * 用例结果列表
     */
    private final List<EvalCaseResult> results;

    /**
     * 总用例数
     */
    private final int totalCases;

    /**
     * 通过用例数
     */
    private final int passedCases;

    /**
     * 失败用例数
     */
    private final int failedCases;

    /**
     * 平均综合得分
     */
    private final double avgScore;

    /**
     * 平均期望工具命中率
     */
    private final double avgToolSelectionAccuracy;

    /**
     * 平均执行耗时（毫秒）
     */
    private final long avgLatencyMs;

    /**
     * 平均判分器得分（未判分时为0）
     */
    private final double avgJudgeScore;

    /**
     * 平均ReAct迭代轮次
     */
    private final double avgReactIterations;

    /**
     * 总Token消耗
     */
    private final long totalTokens;

    /**
     * 平均Token消耗
     */
    private final long avgTokens;

    /**
     * 总成本（美元），全部用例均无成本数据时为null
     */
    private final Double totalCostUsd;

    /**
     * 构建评测报告并汇总统计指标
     * @param datasetName
     * @param results
     */
    public EvalReport(String datasetName, List<EvalCaseResult> results) {
        Objects.requireNonNull(datasetName, "datasetName不能为空");
        this.datasetName = datasetName;
        this.results = results == null ? List.of() : List.copyOf(results);
        this.totalCases = this.results.size();
        this.passedCases = (int) this.results.stream().filter(EvalCaseResult::isPassed).count();
        this.failedCases = totalCases - passedCases;
        this.avgScore = totalCases == 0 ? 0.0
                : this.results.stream().mapToDouble(EvalCaseResult::getScore).average().orElse(0.0);
        this.avgToolSelectionAccuracy = totalCases == 0 ? 0.0
                : this.results.stream().mapToDouble(EvalCaseResult::getToolSelectionAccuracy).average().orElse(0.0);
        this.avgLatencyMs = totalCases == 0 ? 0L
                : (long) this.results.stream().mapToLong(EvalCaseResult::getLatencyMs).average().orElse(0.0);
        this.avgJudgeScore = this.results.stream()
                .map(EvalCaseResult::getJudgeScore)
                .filter(Objects::nonNull)
                .mapToDouble(Double::doubleValue)
                .average().orElse(0.0);
        this.avgReactIterations = totalCases == 0 ? 0.0
                : this.results.stream().mapToInt(EvalCaseResult::getReactIterations).average().orElse(0.0);
        this.totalTokens = this.results.stream().mapToLong(EvalCaseResult::getTotalTokens).sum();
        this.avgTokens = totalCases == 0 ? 0L
                : (long) this.results.stream().mapToLong(EvalCaseResult::getTotalTokens).average().orElse(0.0);
        boolean hasCost = this.results.stream().anyMatch(result -> result.getCostUsd() != null);
        this.totalCostUsd = hasCost
                ? this.results.stream().map(EvalCaseResult::getCostUsd).filter(Objects::nonNull)
                        .mapToDouble(Double::doubleValue).sum()
                : null;
    }

    /**
     * 获取数据集名称
     * @return
     */
    public String getDatasetName() {
        return datasetName;
    }

    /**
     * 获取用例结果列表
     * @return
     */
    public List<EvalCaseResult> getResults() {
        return results;
    }

    /**
     * 获取总用例数
     * @return
     */
    public int getTotalCases() {
        return totalCases;
    }

    /**
     * 获取通过用例数
     * @return
     */
    public int getPassedCases() {
        return passedCases;
    }

    /**
     * 获取失败用例数
     * @return
     */
    public int getFailedCases() {
        return failedCases;
    }

    /**
     * 获取平均综合得分
     * @return
     */
    public double getAvgScore() {
        return avgScore;
    }

    /**
     * 获取平均期望工具命中率
     * @return
     */
    public double getAvgToolSelectionAccuracy() {
        return avgToolSelectionAccuracy;
    }

    /**
     * 获取平均执行耗时（毫秒）
     * @return
     */
    public long getAvgLatencyMs() {
        return avgLatencyMs;
    }

    /**
     * 获取平均判分器得分，未判分时返回0
     * @return
     */
    public double getAvgJudgeScore() {
        return avgJudgeScore;
    }

    /**
     * 获取平均ReAct迭代轮次
     * @return
     */
    public double getAvgReactIterations() {
        return avgReactIterations;
    }

    /**
     * 获取总Token消耗
     * @return
     */
    public long getTotalTokens() {
        return totalTokens;
    }

    /**
     * 获取平均Token消耗
     * @return
     */
    public long getAvgTokens() {
        return avgTokens;
    }

    /**
     * 获取总成本（美元），无成本数据时返回null
     * @return
     */
    public Double getTotalCostUsd() {
        return totalCostUsd;
    }

    /**
     * 生成中文摘要文本
     * @return
     */
    public String summary() {
        double passRate = totalCases == 0 ? 0.0 : (double) passedCases / totalCases;
        String summary = String.format("评测数据集[%s]共%d条用例，通过%d条，失败%d条，通过率%.1f%%；"
                        + "平均得分%.2f，平均工具选择准确率%.1f%%，平均耗时%dms，"
                        + "任务平均%.1f轮迭代、%.1fk tokens",
                datasetName, totalCases, passedCases, failedCases, passRate * 100,
                avgScore, avgToolSelectionAccuracy * 100, avgLatencyMs,
                avgReactIterations, avgTokens / 1000.0);
        if (totalCostUsd != null) {
            summary += String.format("、总成本$%.4f", totalCostUsd);
        }
        boolean judged = this.results.stream().anyMatch(result -> result.getJudgeScore() != null);
        if (judged) {
            summary += String.format("，平均LLM判分%.2f", avgJudgeScore);
        }
        return summary;
    }
}
