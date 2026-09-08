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

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import com.yangqiongai.agent.harness.core.event.AgentEvent;
import com.yangqiongai.agent.harness.core.event.AgentEventType;
import com.yangqiongai.agent.harness.core.event.AgentResultEvent;
import com.yangqiongai.agent.harness.core.message.MessageFactory;
import com.yangqiongai.agent.harness.core.message.AgentToolUseBlock;
import com.yangqiongai.agent.harness.core.AgentRuntime;
import com.yangqiongai.agent.harness.engine.AgentRuntimeContext;
import com.yangqiongai.agent.harness.eval.EvalCase;
import com.yangqiongai.agent.harness.eval.EvalDataset;
import com.yangqiongai.agent.harness.eval.EvalRunner;
import com.yangqiongai.agent.harness.eval.EvalReport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * 评测报告导出测试
 * @author yangqiong
 */
class EvalReportExporterTest {

    private final EvalReportExporter exporter = new EvalReportExporter();

    @TempDir
    Path tempDir;

    @Test
    void shouldExportJsonWithReportFields() {
        EvalReport report = buildReport();

        String json = exporter.toJson(report);

        assertThat(json).contains("\"datasetName\"")
                .contains("\"totalCases\"")
                .contains("\"avgScore\"")
                .contains("smoke-suite");
    }

    @Test
    void shouldExportMarkdownWithSummaryAndCaseTable() {
        EvalReport report = buildReport();

        String markdown = exporter.toMarkdown(report);

        assertThat(markdown)
                .contains("# 评测报告：smoke-suite")
                .contains("通过率 50.0%")
                .contains("- 效率指标：任务平均0.0轮迭代、0.0k tokens（总 0 tokens）")
                .contains("| weather-tool | 是 | 1.00 | 100.0% | 100.0% | 100.0% | search | 0 | 0 |")
                .contains("| weather-miss | 否 | 0.60 | 100.0% | 100.0% | 0.0% | search | 0 | 0 |");
    }

    @Test
    void shouldEscapePipeAndNewlineInMarkdownCell() {
        EvalReport report = buildBrokenAnswerReport();

        String markdown = exporter.toMarkdown(report);

        assertThat(markdown).contains("答案含竖线\\|与换行 文本");
    }

    @Test
    void shouldWriteJsonAndMarkdownFiles() throws Exception {
        EvalReport report = buildReport();
        Path jsonFile = tempDir.resolve("out").resolve("report.json");
        Path markdownFile = tempDir.resolve("out").resolve("report.md");

        exporter.writeJson(report, jsonFile);
        exporter.writeMarkdown(report, markdownFile);

        assertThat(jsonFile).exists();
        assertThat(Files.readString(jsonFile)).contains("\"datasetName\"");
        assertThat(markdownFile).exists();
        assertThat(Files.readString(markdownFile)).contains("# 评测报告：smoke-suite");
    }

    /**
     * 构建标准评测报告
     * @return
     */
    private EvalReport buildReport() {
        EvalRunner runner = new EvalRunner(new StubRuntime(false));
        EvalDataset dataset = EvalDataset.of("smoke-suite",
                EvalCase.builder()
                        .id("weather-tool")
                        .query("北京天气怎么样")
                        .expectedToolNames(List.of("search"))
                        .expectedKeywords(List.of("天气"))
                        .build(),
                EvalCase.builder()
                        .id("weather-miss")
                        .query("北京天气怎么样")
                        .expectedToolNames(List.of("search"))
                        .expectedKeywords(List.of("下雨"))
                        .build());
        return runner.run(dataset);
    }

    /**
     * 构建含特殊字符答案的报告
     * @return
     */
    private EvalReport buildBrokenAnswerReport() {
        EvalRunner runner = new EvalRunner(new StubRuntime(true));
        EvalDataset dataset = EvalDataset.of("smoke-suite",
                EvalCase.builder()
                        .id("broken-answer")
                        .query("打个招呼")
                        .build());
        return runner.run(dataset);
    }

    /**
     * 桩运行时
     * @author yangqiong
     */
    static class StubRuntime implements AgentRuntime {

        /**
         * 是否输出含竖线与换行的答案
         */
        private final boolean brokenAnswer;

        StubRuntime(boolean brokenAnswer) {
            this.brokenAnswer = brokenAnswer;
        }

        @Override
        public Mono<com.yangqiongai.agent.harness.core.message.AgentMessage> call(
                List<com.yangqiongai.agent.harness.core.message.AgentMessage> inputs,
                AgentRuntimeContext context) {
            return Mono.just(MessageFactory.createUserMessage("桩运行时同步结果"));
        }

        @Override
        public Flux<AgentEvent> stream(
                List<com.yangqiongai.agent.harness.core.message.AgentMessage> inputs,
                AgentRuntimeContext context) {
            String answer = brokenAnswer ? "答案含竖线|与换行\n文本" : "北京今日天气晴朗";
            return Flux.just(
                    AgentEvent.of(AgentEventType.TOOL_CALL_START,
                            List.of(new AgentToolUseBlock("search", "t1", Map.of()))),
                    new AgentResultEvent(MessageFactory.createUserMessage(answer), null),
                    AgentEvent.completed());
        }

        @Override
        public String getName() {
            return "stub";
        }
    }
}
