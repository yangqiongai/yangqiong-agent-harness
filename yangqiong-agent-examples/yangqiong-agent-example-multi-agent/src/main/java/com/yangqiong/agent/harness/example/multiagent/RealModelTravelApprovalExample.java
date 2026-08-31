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
package com.yangqiong.agent.harness.example.multiagent;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import com.yangqiong.agent.harness.HarnessRuntimeBuilder;
import com.yangqiong.agent.harness.core.AgentRuntime;
import com.yangqiong.agent.harness.core.AgentRuntimeFactory;
import com.yangqiong.agent.harness.core.event.AgentEvent;
import com.yangqiong.agent.harness.core.event.AgentEventType;
import com.yangqiong.agent.harness.core.message.AgentMessage;
import com.yangqiong.agent.harness.core.message.AgentMessageRole;
import com.yangqiong.agent.harness.core.message.AgentTextBlock;
import com.yangqiong.agent.harness.core.message.AgentToolResultBlock;
import com.yangqiong.agent.harness.core.model.AgentGenerateOptions;
import com.yangqiong.agent.harness.core.model.AgentModel;
import com.yangqiong.agent.harness.core.model.registry.AgentModelRegistry;
import com.yangqiong.agent.harness.core.skill.AgentSkill;
import com.yangqiong.agent.harness.core.skill.AgentSkillBox;
import com.yangqiong.agent.harness.core.tool.AgentTool;
import com.yangqiong.agent.harness.core.tool.AgentToolCallParam;
import com.yangqiong.agent.harness.engine.AgentRuntimeContext;
import com.yangqiong.agent.harness.model.HarnessModelFactory;
import com.yangqiong.agent.harness.model.HarnessModelProperties;
import com.yangqiong.agent.harness.model.provider.AnthropicModelProvider;
import com.yangqiong.agent.harness.model.provider.DashScopeModelProvider;
import com.yangqiong.agent.harness.model.provider.OllamaModelProvider;
import com.yangqiong.agent.harness.model.provider.OpenAIModelProvider;
import com.yangqiong.agent.harness.subagent.orchestration.SubagentDeclaration;
import com.yangqiong.agent.harness.tool.HarnessToolkit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

/**
 * 企业差旅审批助手真实模型示例
 * <p>
 * 对接真实大模型，覆盖两种企业级编排场景：单机多步ReAct（工具+技能+多轮）与
 * 多子代理协调（费用核算子代理、合规校验子代理并行委派）。
 * </p>
 * <p>
 * 运行前需配置环境变量：HARNESS_TEST_API_KEY（必填）、HARNESS_TEST_MODEL_CODE、
 * HARNESS_TEST_BASE_URL、HARNESS_TEST_TIMEOUT_SECONDS（可选）。
 * </p>
 * @author yangqiong
 */
public class RealModelTravelApprovalExample {

    private static final Logger log = LoggerFactory.getLogger(RealModelTravelApprovalExample.class);

    /**
     * 模型编码
     */
    private static final String MODEL_CODE = env("HARNESS_TEST_MODEL_CODE", "openai:deepseek-v4-flash");

    /**
     * API密钥
     */
    private static final String API_KEY = env("HARNESS_TEST_API_KEY", "");

    /**
     * API基础地址
     */
    private static final String BASE_URL = env("HARNESS_TEST_BASE_URL", "https://api.deepseek.com");

    /**
     * 请求超时秒数
     */
    private static final int TIMEOUT_SECONDS = Integer.parseInt(env("HARNESS_TEST_TIMEOUT_SECONDS", "120"));

    /**
     * 真实模型实例
     */
    private static AgentModel model;

    /**
     * 模型工厂
     */
    private static HarnessModelFactory modelFactory;

    /**
     * 生成选项
     */
    private static AgentGenerateOptions generateOptions;

    private RealModelTravelApprovalExample() {
    }

    /**
     * 示例入口
     * @param args
     */
    public static void main(String[] args) {
        if (!isConfigured()) {
            log.error("未配置环境变量 HARNESS_TEST_API_KEY，无法运行真实大模型示例，请先设置后再执行。");
            return;
        }
        initModel();
        demoSingleTurn();
      //  demoMultiSubagent();
    }

    /**
     * 演示单机多步ReAct：工具（汇率/天气）+ 技能（差旅政策）+ 多轮循环
     */
    private static void demoSingleTurn() {
        log.info("========== 差旅审批场景1：单机多步ReAct（工具+技能+多轮） ==========");

        HarnessToolkit toolkit = new HarnessToolkit(null);
        toolkit.addTool(new FxConvertTool());
        toolkit.addTool(new WeatherTool());

        AgentRuntime runtime = new HarnessRuntimeBuilder()
                .name("travel-approval")
                .model(model)
                .systemPrompt("你是企业差旅审批助手。审批需求时，先用get_weather查询目的城市天气，"
                        + "用fx_convert将境外美元费用换算为人民币，再依据travel-policy技能判断是否符合差旅合规标准，"
                        + "最后给出明确的通过或驳回建议及核算明细。")
                .toolkit(toolkit)
                .skillBox(travelPolicySkill())
                .generateOptions(generateOptions)
                .maxIters(8)
                .build();

        AgentMessage result = runtime.call(
                List.of(userMessage("员工提交一笔旧金山差旅申请：去程经济舱1200美元，酒店2晚每晚220美元"
                        + "（共440美元），往返高铁商务座折算1000元人民币。请判断是否合规并给出建议。")),
                AgentRuntimeContext.empty()
        ).block(Duration.ofSeconds(TIMEOUT_SECONDS * 2));

        log.info("审批结果: {}", result != null ? result.getTextContent() : "空");
        closeAll(runtime);
    }

    /**
     * 演示多子代理：主代理委派费用核算与合规校验两个子代理并行执行并汇总
     */
    private static void demoMultiSubagent() {
        log.info("========== 差旅审批场景2：多子代理协调（费用核算 + 合规校验） ==========");

        SubagentDeclaration costAgent = SubagentDeclaration.builder()
                .name("cost-calculator")
                .description("负责核算差旅各项费用，汇总总成本，请使用计算方式说明费用构成")
                .modelCode(MODEL_CODE)
                .maxIterations(5)
                .build();

        SubagentDeclaration complianceAgent = SubagentDeclaration.builder()
                .name("compliance-checker")
                .description("负责依据差旅合规政策逐项校验住宿、交通、餐补是否符合标准")
                .modelCode(MODEL_CODE)
                .maxIterations(5)
                .build();

        AgentRuntimeFactory runtimeFactory = () -> new HarnessRuntimeBuilder()
                .modelFactory(modelFactory);

        HarnessToolkit parentToolkit = new HarnessToolkit(null);
        parentToolkit.addTool(new FxConvertTool());

        AgentRuntime subagentRuntime = new HarnessRuntimeBuilder()
                .name("travel-approval-multi")
                .model(model)
                .systemPrompt("你是差旅审批总协调员。收到复杂审批请求时，将任务分解为费用核算与合规校验，"
                        + "分别委派子代理cost-calculator与compliance-checker执行，再用fx_convert完成美元换算，"
                        + "最后汇总两方结论给出综合审批意见。")
                .toolkit(parentToolkit)
                .subagentDeclarations(List.of(costAgent, complianceAgent))
                .runtimeFactory(runtimeFactory)
                .modelFactory(modelFactory)
                .generateOptions(generateOptions)
                .maxIters(8)
                .build();

        List<AgentEvent> events = subagentRuntime.stream(
                List.of(userMessage("请审批这笔差旅：北京出发旧金山，往返经济舱共3000美元，酒店3晚每晚150美元。"
                        + "请委派子代理完成费用核算与合规校验后给出综合意见。")),
                AgentRuntimeContext.empty()
        ).doOnNext(RealModelTravelApprovalExample::echoEvent)
                .collectList().block(Duration.ofSeconds(TIMEOUT_SECONDS * 2));

       //  System.out.println();
     //   log.info("综合审批意见: {}", extractFinalText(events));
        closeAll(subagentRuntime);
    }

    /**
     * 生成选项初始化与模型/工厂装配
     */
    private static void initModel() {
        generateOptions = AgentGenerateOptions.builder()
                .temperature(0.1)
                .maxTokens(4096)
                .build();

        HarnessModelProperties properties = new HarnessModelProperties();
        properties.setApiKey(API_KEY);
        properties.setBaseUrl(BASE_URL);
        properties.setModelCode(MODEL_CODE);
        properties.setTimeoutSeconds(TIMEOUT_SECONDS);

        AgentModelRegistry registry = new AgentModelRegistry();
        registry.registerProviders(List.of(
                new OpenAIModelProvider(),
                new AnthropicModelProvider(),
                new DashScopeModelProvider(),
                new OllamaModelProvider()
        ));

        modelFactory = new HarnessModelFactory(properties, registry);
        model = modelFactory.getModel(MODEL_CODE, generateOptions);
    }

    /**
     * 企业差旅政策技能
     * @return
     */
    private static AgentSkillBox travelPolicySkill() {
        AgentSkill policy = new AgentSkill(
                "travel-policy",
                "BUILTIN",
                "企业差旅合规政策：住宿标准不超过每晚800元；"
                        + "交通标准经济舱；餐补每天300元；"
                        + "出境项目需评估汇率并按美元计价；"
                        + "单程出行超过3小时应申请替换成商务座并经审批。",
                Map.of()
        );
        return new AgentSkillBox() {
            private final List<AgentSkill> skills = List.of(policy);

            @Override
            public List<AgentSkill> getSkills() {
                return skills;
            }

            @Override
            public boolean isEmpty() {
                return false;
            }

            @Override
            public void addSkill(AgentSkill skill) {
                // 静态技能箱不支持运行时添加
            }
        };
    }

    /**
     * 逐字实时打印文本增量事件
     * @param event
     */
    private static void echoEvent(AgentEvent event) {
        if (event.getType() == AgentEventType.TEXT_BLOCK_DELTA && event.getPayload() instanceof String s) {
            System.out.print(s);
            System.out.flush();
        }
    }

    /**
     * 从异步事件流中聚合最终答复文本
     * @param events
     * @return
     */
    private static String extractFinalText(List<AgentEvent> events) {
        if (events == null) {
            return "空";
        }
        StringBuilder sb = new StringBuilder();
        for (AgentEvent event : events) {
            if (event.getType() == AgentEventType.TEXT_BLOCK_DELTA
                    && event.getPayload() instanceof String s) {
                sb.append(s);
            }
        }
        String text = sb.toString();
        return text.isBlank() ? "空" : text;
    }

    /**
     * 创建用户消息
     * @param text
     * @return
     */
    private static AgentMessage userMessage(String text) {
        return AgentMessage.builder()
                .role(AgentMessageRole.USER)
                .content(List.of(AgentTextBlock.builder().text(text).build()))
                .build();
    }

    /**
     * 依次关闭运行时
     * @param runtimes
     */
    private static void closeAll(AgentRuntime... runtimes) {
        for (AgentRuntime runtime : runtimes) {
            runtime.close().block();
        }
    }

    /**
     * 读取环境变量
     * @param key
     * @param defaultValue
     * @return
     */
    private static String env(String key, String defaultValue) {
        String value = System.getenv(key);
        return value != null && !value.isBlank() ? value : defaultValue;
    }

    /**
     * 是否已配置API密钥
     * @return
     */
    private static boolean isConfigured() {
        return API_KEY != null && !API_KEY.isBlank();
    }

    /**
     * 汇率换算工具实现
     */
    static class FxConvertTool implements AgentTool {

        @Override
        public String getName() {
            return "fx_convert";
        }

        @Override
        public String getDescription() {
            return "将美元金额按当前汇率换算为人民币，输入USD金额，返回CNY金额";
        }

        @Override
        public Map<String, Object> getParameters() {
            return Map.of("usd", Map.of("type", "number", "description", "美元金额"));
        }

        @Override
        public Mono<AgentToolResultBlock> callAsync(AgentToolCallParam param) {
            Object usdObj = param.getInput().get("usd");
            double usd = usdObj != null ? Double.parseDouble(usdObj.toString()) : 0;
            double cny = Math.round(usd * 7.2 * 100) / 100.0;
            return Mono.just(AgentToolResultBlock.of(List.of(
                    AgentTextBlock.builder().text("USD " + usd + " = CNY " + cny).build())));
        }
    }

    /**
     * 天气查询工具实现
     */
    static class WeatherTool implements AgentTool {

        @Override
        public String getName() {
            return "get_weather";
        }

        @Override
        public String getDescription() {
            return "查询指定城市的天气情况，输入城市名称，返回天气描述";
        }

        @Override
        public Map<String, Object> getParameters() {
            return Map.of("city", Map.of("type", "string", "description", "城市名称，如北京、上海"));
        }

        @Override
        public Mono<AgentToolResultBlock> callAsync(AgentToolCallParam param) {
            Object cityObj = param.getInput().get("city");
            String city = cityObj != null ? cityObj.toString() : "未知";
            return Mono.just(AgentToolResultBlock.of(List.of(
                    AgentTextBlock.builder().text("当前查询到" + city + "未来3天多云转晴，平均气温18°C，适合出行")
                            .build())));
        }
    }
}