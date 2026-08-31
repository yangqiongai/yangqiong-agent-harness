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
 * 稳定性评测报告
 * @author yangqiong
 */
public final class StabilityReport {

    /**
     * 数据集名称
     */
    private final String datasetName;

    /**
     * 运行轮数
     */
    private final int runs;

    /**
     * 用例稳定性统计列表
     */
    private final List<CaseStability> cases;

    /**
     * 观测总次数（全部用例运行次数之和）
     */
    private final int totalObservations;

    /**
     * 观测通过次数
     */
    private final int passedObservations;

    /**
     * 整体通过率（0-1）
     */
    private final double overallPassRate;

    /**
     * 平均ReAct迭代轮次
     */
    private final double avgReactIterations;

    /**
     * 平均Token消耗
     */
    private final long avgTokens;

    /**
     * 构建稳定性评测报告并汇总统计指标
     * @param datasetName
     * @param runs
     * @param cases
     */
    public StabilityReport(String datasetName, int runs, List<CaseStability> cases) {
        Objects.requireNonNull(datasetName, "datasetName不能为空");
        this.datasetName = datasetName;
        this.runs = runs;
        this.cases = cases == null ? List.of() : List.copyOf(cases);
        this.totalObservations = this.cases.stream().mapToInt(CaseStability::getTotalRuns).sum();
        this.passedObservations = this.cases.stream().mapToInt(CaseStability::getPassedRuns).sum();
        this.overallPassRate = totalObservations == 0
                ? 0.0
                : (double) passedObservations / totalObservations;
        this.avgReactIterations = this.cases.isEmpty() ? 0.0
                : this.cases.stream().mapToDouble(CaseStability::getAvgReactIterations).average().orElse(0.0);
        this.avgTokens = (long) (this.cases.isEmpty() ? 0.0
                : this.cases.stream().mapToLong(CaseStability::getAvgTokens).average().orElse(0.0));
    }

    /**
     * 生成中文摘要文本
     * @return
     */
    public String summary() {
        long flakyCount = cases.stream().filter(CaseStability::isFlaky).count();
        return String.format("稳定性评测数据集[%s]共运行%d轮，整体通过率%.1f%%；共%d条用例，"
                        + "稳定通过%d条，稳定失败%d条，波动(flaky)%d条，"
                        + "任务平均%.1f轮迭代、%.1fk tokens",
                datasetName, runs, overallPassRate * 100, cases.size(),
                cases.stream().filter(c -> c.getPassedRuns() == c.getTotalRuns()).count(),
                cases.stream().filter(c -> c.getPassedRuns() == 0).count(),
                flakyCount, avgReactIterations, avgTokens / 1000.0);
    }

    /**
     * 导出 Markdown 文本
     * @return
     */
    public String toMarkdown() {
        StringBuilder md = new StringBuilder();
        md.append("# 稳定性评测报告：").append(datasetName).append("\n\n");
        md.append("- 运行轮数：").append(runs).append("\n");
        md.append(String.format("- 整体通过率：%.1f%%", overallPassRate * 100))
                .append("（").append(passedObservations).append("/").append(totalObservations).append("）\n");
        md.append(String.format("- 效率指标：任务平均%.1f轮迭代、%.1fk tokens\n", avgReactIterations, avgTokens / 1000.0));
        md.append("\n## 用例稳定性明细\n\n");
        md.append("| 用例ID | 通过轮数 | 通过率 | 平均得分 | 最低得分 | 最高得分 | 平均轮次 | 平均tokens | 平均耗时(ms) | 最大耗时(ms) | 波动 |\n");
        md.append("| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |\n");
        for (CaseStability stat : cases) {
            md.append("| ").append(stat.getCaseId())
                    .append(" | ").append(stat.getPassedRuns()).append("/").append(stat.getTotalRuns())
                    .append(" | ").append(String.format("%.1f%%", stat.getPassRate() * 100))
                    .append(" | ").append(String.format("%.2f", stat.getAvgScore()))
                    .append(" | ").append(String.format("%.2f", stat.getMinScore()))
                    .append(" | ").append(String.format("%.2f", stat.getMaxScore()))
                    .append(" | ").append(String.format("%.1f", stat.getAvgReactIterations()))
                    .append(" | ").append(stat.getAvgTokens())
                    .append(" | ").append(stat.getAvgLatencyMs())
                    .append(" | ").append(stat.getMaxLatencyMs())
                    .append(" | ").append(stat.isFlaky() ? "是" : "否")
                    .append(" |\n");
        }
        return md.toString();
    }

    /**
     * 获取数据集名称
     * @return
     */
    public String getDatasetName() {
        return datasetName;
    }

    /**
     * 获取运行轮数
     * @return
     */
    public int getRuns() {
        return runs;
    }

    /**
     * 获取用例稳定性统计列表
     * @return
     */
    public List<CaseStability> getCases() {
        return cases;
    }

    /**
     * 获取观测总次数
     * @return
     */
    public int getTotalObservations() {
        return totalObservations;
    }

    /**
     * 获取观测通过次数
     * @return
     */
    public int getPassedObservations() {
        return passedObservations;
    }

    /**
     * 获取整体通过率
     * @return
     */
    public double getOverallPassRate() {
        return overallPassRate;
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
