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
package com.yangqiongai.agent.harness.example.observability;

import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import com.yangqiongai.agent.harness.HarnessRuntimeBuilder;
import com.yangqiongai.agent.harness.config.AgentApprovalMode;
import com.yangqiongai.agent.harness.core.AgentRuntime;
import com.yangqiongai.agent.harness.core.message.AgentTextBlock;
import com.yangqiongai.agent.harness.core.message.AgentToolResultBlock;
import com.yangqiongai.agent.harness.core.model.AgentModel;
import com.yangqiongai.agent.harness.core.model.registry.AgentModelRegistry;
import com.yangqiongai.agent.harness.core.message.MessageFactory;
import com.yangqiongai.agent.harness.core.tool.AgentTool;
import com.yangqiongai.agent.harness.core.tool.AgentToolCallParam;
import com.yangqiongai.agent.harness.durable.MemoryDistributedStores;
import com.yangqiongai.agent.harness.engine.AgentRuntimeContext;
import com.yangqiongai.agent.harness.model.HarnessModelFactory;
import com.yangqiongai.agent.harness.model.HarnessModelProperties;
import com.yangqiongai.agent.harness.model.ModelPricing;
import com.yangqiongai.agent.harness.model.ModelPricingRegistry;
import com.yangqiongai.agent.harness.model.provider.AnthropicModelProvider;
import com.yangqiongai.agent.harness.model.provider.OpenAIModelProvider;
import com.yangqiongai.agent.harness.observability.config.ObservabilityConfig;
import com.yangqiongai.agent.harness.observability.wiring.ObservabilityHandle;
import com.yangqiongai.agent.harness.observability.wiring.ObservabilityInstaller;
import com.yangqiongai.agent.harness.tool.HarnessToolkit;

import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import reactor.core.publisher.Mono;

/**
 * 可观测性扩展示例
 * <p>
 * 演示完整可观测链路：一行装配 Trace 导出与指标采集，执行多类任务（工具调用/故障重试/纯对话）后
 * 打印指标快照。设置环境变量 OTLP_ENDPOINT（如 http://localhost:4318）可同步导出调用链到 Jaeger/Tempo。
 * </p>
 * @author yangqiong
 */
public class ObservabilityExample {

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

    private ObservabilityExample() {
    }

    /**
     * 示例入口
     * @param args
     */
    public static void main(String[] args) {
        String apiKey = System.getenv().getOrDefault("AI_API_KEY", DEFAULT_API_KEY);
        String modelName = System.getenv().getOrDefault("AI_MODEL", DEFAULT_MODEL);
        String baseUrl = System.getenv().getOrDefault("AI_BASE_URL", DEFAULT_BASE_URL);
        String otlpEndpoint = System.getenv("OTLP_ENDPOINT");

        AgentModel model = buildModel(apiKey, modelName, baseUrl);
        HarnessRuntimeBuilder builder = new HarnessRuntimeBuilder()
                .name("observability-agent")
                .model(model)
                .systemPrompt("你是一个乐于助人的智能助手，查询天气用get_weather工具，查询物流订单用query_order工具，"
                        + "一般知识问题直接回答不要调用工具，请用中文回答。")
                .maxIters(8)
                .toolkit(buildToolkit())
                .approvalMode(AgentApprovalMode.FULL_ACCESS)
                .stores(new MemoryDistributedStores());

        // 一行装配：OTLP端点非空时同时导出Trace；指标走内部SimpleMeterRegistry，演示结束后打印快照
        ObservabilityConfig config = ObservabilityConfig.builder()
                .otlpEndpoint(otlpEndpoint)
                .serviceName("observability-example")
                .metricsEnabled(true)
                .commonTag("env", "demo")
                .pricingRegistry(buildPricingRegistry(modelName))
                .build();
        ObservabilityHandle handle = ObservabilityInstaller.install(builder, config);

        AgentRuntime runtime = builder.build();
        try {
            runTasks(runtime);
        } finally {
            runtime.close().block();
            // 关闭句柄：触发Trace缓冲区最后一批导出（幂等）
            handle.close();
        }

        printMetricSnapshot(handle.meterRegistry(), otlpEndpoint);
    }

    /**
     * 执行三类任务制造可观测数据：工具调用、故障重试、纯对话
     * @param runtime
     */
    private static void runTasks(AgentRuntime runtime) {
        System.out.println("\n===== 任务1：天气查询（正常工具调用） =====");
        System.out.println(ask(runtime, "北京今天天气怎么样？"));

        System.out.println("\n===== 任务2：物流查询（前两次工具超时，验证故障重试与工具错误指标） =====");
        System.out.println(ask(runtime, "帮我查一下订单 1001 的物流状态"));

        System.out.println("\n===== 任务3：纯对话（不调用工具） =====");
        System.out.println(ask(runtime, "用一句话介绍你自己"));
    }

    /**
     * 同步执行一次提问并返回最终答案文本
     * @param runtime
     * @param query
     * @return
     */
    private static String ask(AgentRuntime runtime, String query) {
        return runtime.call(List.of(MessageFactory.createUserMessage(query)), AgentRuntimeContext.empty())
                .block()
                .getTextContent();
    }

    /**
     * 遍历注册表打印指标快照，演示如何以编程方式读取采集结果
     * @param registry
     * @param otlpEndpoint
     */
    private static void printMetricSnapshot(MeterRegistry registry, String otlpEndpoint) {
        System.out.println("\n===== 指标快照（Micrometer） =====");
        for (Meter meter : registry.getMeters()) {
            Meter.Id id = meter.getId();
            if (meter instanceof io.micrometer.core.instrument.Counter counter) {
                System.out.printf("%-45s %s = %.4f%n", id.getName(), id.getTags(), counter.count());
            } else if (meter instanceof Timer timer) {
                System.out.printf("%-45s %s = count=%d, mean=%.1fms, max=%.1fms%n",
                        id.getName(), id.getTags(), timer.count(),
                        timer.mean(TimeUnit.MILLISECONDS), timer.max(TimeUnit.MILLISECONDS));
            } else if (meter instanceof io.micrometer.core.instrument.Gauge gauge) {
                System.out.printf("%-45s %s = %.2f%n", id.getName(), id.getTags(), gauge.value());
            } else if (meter instanceof io.micrometer.core.instrument.DistributionSummary summary) {
                System.out.printf("%-45s %s = count=%d, mean=%.4f%n",
                        id.getName(), id.getTags(), summary.count(), summary.mean());
            }
        }
        if (otlpEndpoint == null || otlpEndpoint.isBlank()) {
            System.out.println("\n本次未设置 OTLP_ENDPOINT，仅输出指标。要查看调用链可先启动 Jaeger：");
            System.out.println("  docker run -p 16686:16686 -p 4318:4318 jaegertracing/all-in-one:latest");
            System.out.println("再以 OTLP_ENDPOINT=http://localhost:4318 重新运行本示例，然后打开 http://localhost:16686 查询。");
        } else {
            System.out.println("\n调用链已导出到 " + otlpEndpoint + "，可在对应后端（如 http://localhost:16686）按服务名 observability-example 查询。");
        }
    }

    /**
     * 注册示例模型单价，启用 agent_cost_usd_total 成本指标
     * @param modelName
     * @return
     */
    private static ModelPricingRegistry buildPricingRegistry(String modelName) {
        ModelPricingRegistry registry = new ModelPricingRegistry();
        registry.register(new ModelPricing(modelName, 0.002, 0.008));
        return registry;
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
     * 订单物流查询演示工具，内置故障注入：每个订单前两次调用模拟超时失败，第三次起成功，用于制造工具错误与重试指标
     * @return
     */
    private static AgentTool orderQueryTool() {
        java.util.concurrent.ConcurrentHashMap<String, Integer> callCounters = new java.util.concurrent.ConcurrentHashMap<>();
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
