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
package com.yangqiongai.agent.harness.example.eval;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import com.yangqiongai.agent.harness.HarnessRuntimeBuilder;
import com.yangqiongai.agent.harness.config.AgentApprovalMode;
import com.yangqiongai.agent.harness.core.AgentRuntime;
import com.yangqiongai.agent.harness.core.message.AgentTextBlock;
import com.yangqiongai.agent.harness.core.message.AgentToolResultBlock;
import com.yangqiongai.agent.harness.core.model.AgentModel;
import com.yangqiongai.agent.harness.core.model.registry.AgentModelRegistry;
import com.yangqiongai.agent.harness.core.tool.AgentTool;
import com.yangqiongai.agent.harness.core.tool.AgentToolCallParam;
import com.yangqiongai.agent.harness.durable.MemoryDistributedStores;
import com.yangqiongai.agent.harness.eval.EvalDataset;
import com.yangqiongai.agent.harness.eval.EvalReport;
import com.yangqiongai.agent.harness.eval.EvalRunner;
import com.yangqiongai.agent.harness.eval.EvalReportExporter;
import com.yangqiongai.agent.harness.eval.StabilityReport;
import com.yangqiongai.agent.harness.eval.StabilityRunner;
import com.yangqiongai.agent.harness.eval.YamlEvalDatasetLoader;
import com.yangqiongai.agent.harness.model.HarnessModelFactory;
import com.yangqiongai.agent.harness.model.HarnessModelProperties;
import com.yangqiongai.agent.harness.model.provider.AnthropicModelProvider;
import com.yangqiongai.agent.harness.model.provider.OpenAIModelProvider;
import com.yangqiongai.agent.harness.tool.HarnessToolkit;
import reactor.core.publisher.Mono;

/**
 * 评测扩展示例
 * <p>
 * 演示完整评测链路：内置演示工具 + YAML用例集 + 单轮评测 + 稳定性跑批 + JSON/Markdown报告导出。
 * 报告输出到 target/eval/ 目录，稳定轮数可通过环境变量 EVAL_ROUNDS 覆盖。
 * </p>
 * @author yangqiong
 */
public class EvalExample {

    /**
     * 默认API密钥（请替换为你自己的密钥）
     */
    private static final String DEFAULT_API_KEY = "";

    /**
     * 默认模型名称
     */
    private static final String DEFAULT_MODEL = "deepseek-v4-flash";

    /**
     * 默认API基础地址
     */
    private static final String DEFAULT_BASE_URL = "https://api.deepseek.com";

    /**
     * 默认稳定性跑批轮数
     */
    private static final int DEFAULT_ROUNDS = 5;

    private EvalExample() {
    }

    /**
     * 示例入口
     * @param args
     */
    public static void main(String[] args) {

        String apiKey = System.getenv().getOrDefault("AI_API_KEY", DEFAULT_API_KEY);
        String modelName = System.getenv().getOrDefault("AI_MODEL", DEFAULT_MODEL);
        String baseUrl = System.getenv().getOrDefault("AI_BASE_URL", DEFAULT_BASE_URL);
        int rounds = Integer.parseInt(System.getenv().getOrDefault("EVAL_ROUNDS", String.valueOf(DEFAULT_ROUNDS)));

        AgentModel model = buildModel(apiKey, modelName, baseUrl);
        AgentRuntime runtime = new HarnessRuntimeBuilder()
                .name("eval-agent")
                .model(model)
                .systemPrompt("你是一个乐于助人的智能助手，查询天气用get_weather工具，预订会议室用book_meeting_room工具，"
                        + "查询物流订单用query_order工具，一般知识问题直接回答不要调用工具，请用中文回答。")
                .maxIters(8)
                .toolkit(buildToolkit())
                // 无人值守评测需跳过人工审批，FULL_ACCESS完全放行工具调用
                .approvalMode(AgentApprovalMode.FULL_ACCESS)
                .stores(new MemoryDistributedStores())
                .build();

        try {
            runEvaluation(runtime, rounds);
        } finally {
            runtime.close().block();
        }
    }

    /**
     * 执行单轮评测与稳定性跑批并导出报告
     * @param runtime
     * @param rounds
     */
    private static void runEvaluation(AgentRuntime runtime, int rounds) {
        EvalDataset dataset = new YamlEvalDatasetLoader().loadFromClasspath("eval/agent-smoke-suite.yaml");
        EvalRunner runner = new EvalRunner(runtime);

        // 单轮评测：快速校验当前版本能力
        EvalReport report = runner.run(dataset, 3);
        System.out.println("\n===== 单轮评测结果 =====");
        System.out.println(report.summary());

        // 稳定性跑批：重复N轮统计通过率与波动用例
        StabilityReport stability = new StabilityRunner(runner).run(dataset, rounds, 2, null);
        System.out.println("\n===== 稳定性评测结果 =====");
        System.out.println(stability.summary());

        // 导出可发布报告
        EvalReportExporter exporter = new EvalReportExporter();
        Path outDir = Path.of("target", "eval");
        exporter.writeJson(report, outDir.resolve("report.json"));
        exporter.writeMarkdown(report, outDir.resolve("report.md"));
        writeStabilityReport(stability, outDir.resolve("stability-report.md"));
        System.out.println("\n评测报告已输出到: " + outDir.toAbsolutePath().normalize());
    }

    /**
     * 写入稳定性报告文件
     * @param stability
     * @param file
     */
    private static void writeStabilityReport(StabilityReport stability, Path file) {
        try {
            Files.createDirectories(file.getParent());
            Files.writeString(file, stability.toMarkdown());
        } catch (Exception e) {
            throw new IllegalStateException("写入稳定性报告失败: " + file, e);
        }
    }

    /**
     * 装配模型，可通过环境变量 AI_API_KEY/AI_MODEL/AI_BASE_URL 覆盖默认值
     * @param apiKey
     * @param modelName
     * @param baseUrl
     * @return
     */
    private static AgentModel buildModel(String apiKey, String modelName, String baseUrl) {
        HarnessModelProperties properties = new HarnessModelProperties();
        properties.setApiKey(apiKey);
        properties.setBaseUrl(baseUrl);
        properties.setModelName(modelName);
        AgentModelRegistry registry = new AgentModelRegistry(properties.getDefaultProvider());
        registry.registerProvider(new OpenAIModelProvider());
        registry.registerProvider(new AnthropicModelProvider());
        return new HarnessModelFactory(properties, registry).getModel(properties.getModelName(), null);
    }

    /**
     * 装配演示工具集
     * @return
     */
    private static HarnessToolkit buildToolkit() {
        HarnessToolkit toolkit = new HarnessToolkit(null);
        toolkit.addTool(weatherTool());
        toolkit.addTool(bookMeetingRoomTool());
        toolkit.addTool(orderQueryTool());
        return toolkit;
    }

    /**
     * 天气查询演示工具，按传入城市回显天气信息
     * @return
     */
    private static AgentTool weatherTool() {
        return new AgentTool() {

            @Override
            public String getName() {
                return "get_weather";
            }

            @Override
            public String getDescription() {
                return "查询指定城市的实时天气";
            }

            @Override
            public Map<String, Object> getParameters() {
                return Map.of(
                        "type", "object",
                        "properties", Map.of(
                                "city", Map.of("type", "string", "description", "城市名称")),
                        "required", List.of("city"));
            }

            @Override
            public boolean isReadOnly() {
                return true;
            }

            @Override
            public Mono<AgentToolResultBlock> callAsync(AgentToolCallParam param) {
                Object city = param.getInput().getOrDefault("city", "未知城市");
                return Mono.just(AgentToolResultBlock.of(List.of(
                        AgentTextBlock.builder()
                                .text(city + "今日晴，气温22℃，微风，适合出行")
                                .build())));
            }
        };
    }

    /**
     * 会议室预订演示工具
     * @return
     */
    private static AgentTool bookMeetingRoomTool() {
        return new AgentTool() {

            @Override
            public String getName() {
                return "book_meeting_room";
            }

            @Override
            public String getDescription() {
                return "预订指定时间的会议室";
            }

            @Override
            public Map<String, Object> getParameters() {
                return Map.of(
                        "type", "object",
                        "properties", Map.of(
                                "room", Map.of("type", "string", "description", "会议室名称"),
                                "time", Map.of("type", "string", "description", "预订时间")),
                        "required", List.of("room", "time"));
            }

            @Override
            public boolean isReadOnly() {
                return true;
            }

            @Override
            public Mono<AgentToolResultBlock> callAsync(AgentToolCallParam param) {
                return Mono.just(AgentToolResultBlock.of(List.of(
                        AgentTextBlock.builder().text("会议室预订成功：明天 15:00，参会请准时").build())));
            }
        };
    }

    /**
     * 订单物流查询演示工具，内置故障注入：每个订单前两次调用模拟超时失败，第三次起成功，用于评测模型的失败重试恢复能力
     * @return
     */
    private static AgentTool orderQueryTool() {
        Map<String, Integer> callCounters = new java.util.concurrent.ConcurrentHashMap<>();
        return new AgentTool() {

            @Override
            public String getName() {
                return "query_order";
            }

            @Override
            public String getDescription() {
                return "查询订单的物流状态，服务偶尔超时需要重试";
            }

            @Override
            public Map<String, Object> getParameters() {
                return Map.of(
                        "type", "object",
                        "properties", Map.of(
                                "order_id", Map.of("type", "string", "description", "订单编号")),
                        "required", List.of("order_id"));
            }

            @Override
            public boolean isReadOnly() {
                return true;
            }

            @Override
            public Mono<AgentToolResultBlock> callAsync(AgentToolCallParam param) {
                String orderId = String.valueOf(param.getInput().getOrDefault("order_id", "unknown"));
                int callCount = callCounters.merge(orderId, 1, Integer::sum);
                if (callCount % 3 != 0) {
                    return Mono.error(new RuntimeException("物流服务超时，请稍后重试"));
                }
                return Mono.just(AgentToolResultBlock.of(List.of(
                        AgentTextBlock.builder()
                                .text("订单" + orderId + "已发货，物流单号SF123，预计明天送达")
                                .build())));
            }
        };
    }
}
