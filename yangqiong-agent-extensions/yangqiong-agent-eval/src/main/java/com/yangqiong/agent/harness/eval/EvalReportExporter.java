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

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

/**
 * 评测报告导出
 * @author yangqiong
 */
public final class EvalReportExporter {

    /**
     * JSON 导出器
     */
    private final ObjectMapper jsonMapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);

    /**
     * 导出评测报告为 JSON 文本
     * @param report
     * @return
     */
    public String toJson(EvalReport report) {
        try {
            return jsonMapper.writeValueAsString(report);
        } catch (JsonProcessingException e) {
            throw new UncheckedIOException("评测报告序列化为JSON失败", e);
        }
    }

    /**
     * 导出评测报告为 Markdown 文本
     * @param report
     * @return
     */
    public String toMarkdown(EvalReport report) {
        StringBuilder md = new StringBuilder();
        double passRate = report.getTotalCases() == 0
                ? 0.0
                : (double) report.getPassedCases() / report.getTotalCases();
        md.append("# 评测报告：").append(report.getDatasetName()).append("\n\n");
        md.append("- 总用例数：").append(report.getTotalCases()).append("\n");
        md.append("- 通过：").append(report.getPassedCases())
                .append(" / 失败：").append(report.getFailedCases())
                .append(String.format("（通过率 %.1f%%）", passRate * 100)).append("\n");
        md.append(String.format("- 平均得分：%.2f\n", report.getAvgScore()));
        md.append(String.format("- 平均工具选择准确率：%.1f%%\n", report.getAvgToolSelectionAccuracy() * 100));
        md.append("- 平均耗时：").append(report.getAvgLatencyMs()).append("ms\n");
        md.append(String.format("- 效率指标：任务平均%.1f轮迭代、%.1fk tokens（总 %d tokens）\n",
                report.getAvgReactIterations(), report.getAvgTokens() / 1000.0, report.getTotalTokens()));
        if (report.getTotalCostUsd() != null) {
            md.append(String.format("- 总成本：$%.4f\n", report.getTotalCostUsd()));
        }
        if (report.getAvgJudgeScore() > 0) {
            md.append(String.format("- 平均LLM判分：%.2f\n", report.getAvgJudgeScore()));
        }
        md.append("\n## 用例明细\n\n");
        md.append("| 用例ID | 通过 | 综合得分 | 工具命中 | 参数命中 | 关键词命中 | 实际工具 | 轮次 | tokens | 耗时(ms) | 最终答案 | 失败原因 |\n");
        md.append("| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |\n");
        for (EvalCaseResult result : report.getResults()) {
            md.append("| ").append(escapeCell(result.getCaseId()))
                    .append(" | ").append(result.isPassed() ? "是" : "否")
                    .append(" | ").append(String.format("%.2f", result.getScore()))
                    .append(" | ").append(String.format("%.1f%%", result.getToolSelectionAccuracy() * 100))
                    .append(" | ").append(String.format("%.1f%%", result.getArgMatchRate() * 100))
                    .append(" | ").append(String.format("%.1f%%", result.getKeywordHitRate() * 100))
                    .append(" | ").append(escapeCell(join(result.getActualToolNames())))
                    .append(" | ").append(result.getReactIterations())
                    .append(" | ").append(result.getTotalTokens())
                    .append(" | ").append(result.getLatencyMs())
                    .append(" | ").append(escapeCell(truncate(result.getFinalAnswer(), 80)))
                    .append(" | ").append(escapeCell(nullToEmpty(result.getFailureReason())))
                    .append(" |\n");
        }
        return md.toString();
    }

    /**
     * 将评测报告写入 JSON 文件
     * @param report
     * @param file
     */
    public void writeJson(EvalReport report, Path file) {
        writeText(toJson(report), file);
    }

    /**
     * 将评测报告写入 Markdown 文件
     * @param report
     * @param file
     */
    public void writeMarkdown(EvalReport report, Path file) {
        writeText(toMarkdown(report), file);
    }

    /**
     * 写入文本文件，自动创建父目录
     * @param content
     * @param file
     */
    private void writeText(String content, Path file) {
        try {
            Path parent = file.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Files.writeString(file, content, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        } catch (IOException e) {
            throw new UncheckedIOException("写入评测报告文件失败: " + file, e);
        }
    }

    /**
     * 拼接工具名称列表
     * @param toolNames
     * @return
     */
    private String join(List<String> toolNames) {
        return toolNames == null || toolNames.isEmpty() ? "-" : String.join(", ", toolNames);
    }

    /**
     * 空值转空字符串
     * @param value
     * @return
     */
    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    /**
     * 截断超长文本，超出部分以省略号结尾
     * @param value
     * @param maxLength
     * @return
     */
    private String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength) + "…";
    }

    /**
     * 转义 Markdown 表格单元格中的竖线与换行
     * @param value
     * @return
     */
    private String escapeCell(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("|", "\\|").replace("\r", " ").replace("\n", " ");
    }
}
