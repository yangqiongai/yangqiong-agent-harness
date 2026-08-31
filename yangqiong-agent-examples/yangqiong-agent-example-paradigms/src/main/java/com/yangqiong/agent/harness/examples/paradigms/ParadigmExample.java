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
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.yangqiong.agent.harness.examples.paradigms;

import java.time.Duration;
import java.util.List;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import com.yangqiong.agent.harness.HarnessRuntimeBuilder;
import com.yangqiong.agent.harness.core.AgentRuntime;
import com.yangqiong.agent.harness.core.event.AgentEvent;
import com.yangqiong.agent.harness.core.event.AgentEventType;
import com.yangqiong.agent.harness.core.event.AgentResultEvent;
import com.yangqiong.agent.harness.core.event.AgentTextBlockDeltaEvent;
import com.yangqiong.agent.harness.core.event.ModelCallInfo;
import com.yangqiong.agent.harness.core.message.AgentMessage;
import com.yangqiong.agent.harness.core.message.AgentTextBlock;
import com.yangqiong.agent.harness.core.message.AgentToolUseBlock;
import com.yangqiong.agent.harness.core.message.MessageFactory;
import com.yangqiong.agent.harness.core.model.AgentGenerateOptions;
import com.yangqiong.agent.harness.core.model.AgentModel;
import com.yangqiong.agent.harness.core.model.registry.AgentModelRegistry;
import com.yangqiong.agent.harness.engine.AgentLoop;
import com.yangqiong.agent.harness.engine.AgentRuntimeContext;
import com.yangqiong.agent.harness.model.HarnessModelFactory;
import com.yangqiong.agent.harness.model.HarnessModelProperties;
import com.yangqiong.agent.harness.model.provider.AnthropicModelProvider;
import com.yangqiong.agent.harness.model.provider.DashScopeModelProvider;
import com.yangqiong.agent.harness.model.provider.OllamaModelProvider;
import com.yangqiong.agent.harness.model.provider.OpenAIModelProvider;
import com.yangqiong.agent.harness.paradigms.PlanExecuteEngine;
import com.yangqiong.agent.harness.paradigms.ReWooEngine;
import com.yangqiong.agent.harness.paradigms.ReflexionEngine;
import com.yangqiong.agent.harness.paradigms.RouterEngine;
import com.yangqiong.agent.harness.paradigms.SelfAskEngine;
import com.yangqiong.agent.harness.paradigms.SelfRefineEngine;
import com.yangqiong.agent.harness.tool.HarnessToolkit;

/**
 * 执行范式真实大模型示例
 * <p>
 * 统一经HarnessRuntimeBuilder构建运行时，通过agentLoop挂载ReAct基座范式、
 * PlanExecute/ReWoo/Reflexion/SelfAsk/SelfRefine五种范式引擎与RouterEngine元层路由，
 * 逐一运行十个真实模型场景并打印可观测证据（路由决策、规划内容、子问题分解、草稿与审稿意见等），
 * 全部场景通过时以0退出。未配置API密钥时打印配置说明后退出。
 * </p>
 * <p>
 * 环境变量：
 * HARNESS_TEST_MODEL_CODE（可选，默认openai:deepseek-v4-flash）
 * HARNESS_TEST_API_KEY（必填，ollama本地部署除外）
 * HARNESS_TEST_BASE_URL（可选，默认https://api.deepseek.com）
 * HARNESS_TEST_TIMEOUT_SECONDS（可选，默认120）
 * </p>
 * @author yangqiong
 */
public class ParadigmExample {

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
     * 模型提供者前缀
     */
    private static final String PROVIDER = MODEL_CODE.contains(":")
            ? MODEL_CODE.substring(0, MODEL_CODE.indexOf(':')) : "openai";

    /**
     * 模型实例
     */
    private static AgentModel model;

    /**
     * 生成选项
     */
    private static AgentGenerateOptions generateOptions;

    /**
     * 工具集
     */
    private static HarnessToolkit toolkit;

    private ParadigmExample() {
    }

    /**
     * 示例入口：逐一运行全部范式场景并汇总通过情况
     * @param args
     */
    public static void main(String[] args) {
        if (!isConfigured()) {
            System.out.println("未配置HARNESS_TEST_API_KEY，无法运行真实模型范式示例。");
            System.out.println("请设置环境变量后重试，例如：");
            System.out.println("  HARNESS_TEST_API_KEY=sk-xxx");
            System.out.println("  HARNESS_TEST_MODEL_CODE=openai:deepseek-v4-flash（可选）");
            System.out.println("  HARNESS_TEST_BASE_URL=https://api.deepseek.com（可选）");
            return;
        }
        initModelAndToolkit();

        int failed = 0;
        failed += run("路由范式-简单工具任务自动默认选型", ParadigmExample::routerSelectsParadigm);
        failed += run("路由范式-探索性问题路由ReAct", ParadigmExample::routerRoutesExplorationToReAct);
        failed += run("路由范式-强制指定ReWoo跳过分类", ParadigmExample::routerForcedParadigm);
        failed += run("路由范式-工具密集并行任务路由ReWoo", ParadigmExample::routerRoutesToReWoo);
        failed += run("路由范式-多实体问题路由SelfAsk", ParadigmExample::routerRoutesToSelfAsk);
        failed += run("ReAct-思考行动观察循环", ParadigmExample::reActLoop);
        failed += run("PlanExecute-先规划后执行", ParadigmExample::planExecute);
        failed += run("ReWoo-规划并行执行统一推理", ParadigmExample::reWoo);
        failed += run("Reflexion-执行后反思重试", ParadigmExample::reflexion);
        failed += run("SelfAsk-子问题拆解作答", ParadigmExample::selfAsk);
        failed += run("SelfRefine-草稿批评修订", ParadigmExample::selfRefine);

        System.out.println();
        System.out.println("======== 范式示例汇总 ========");
        if (failed == 0) {
            System.out.println("全部11个场景通过");
        } else {
            System.out.println(failed + "个场景未通过，请检查上方证据输出");
            System.exit(1);
        }
    }

    /**
     * 路由范式：简单工具计算任务经分类轮自动选定范式并转发执行
     * @return
     */
    private static boolean routerSelectsParadigm() {
        AgentRuntime runtime = buildRuntime("router-real", "你是一个严谨的计算助手。", 10, new RouterEngine());

        List<AgentEvent> events = collectEvents(runtime,
                "请使用calculator工具计算 (123+456)*2 的值，并给出最终答案。");
        if (!completedWithResult(events)) {
            return false;
        }

        RouterEngine.RoutingDecision decision = routingDecision(events);
        if (decision == null) {
            return fail("未产出ENGINE_ROUTED路由决策事件");
        }
        System.out.println("路由决策：" + decision.getParadigm() + "，理由：" + decision.getReason());
        boolean routedToToolParadigm = decision.getParadigm() == RouterEngine.ParadigmType.REACT
                || decision.getParadigm() == RouterEngine.ParadigmType.PLAN_EXECUTE
                || decision.getParadigm() == RouterEngine.ParadigmType.REWOO
                || decision.getParadigm() == RouterEngine.ParadigmType.REFLEXION;
        if (!routedToToolParadigm) {
            return fail("计算任务应路由到工具系范式，实际：" + decision.getParadigm());
        }
        if (extractModelCallIterations(events).size() < 2) {
            return fail("分类轮与被转发引擎执行轮应至少2轮模型调用");
        }
        if (!hasToolCall(events, "calculator")) {
            return fail("被转发引擎应调用calculator工具");
        }
        return checkResultContains(events, "1158");
    }

    /**
     * 路由范式：探索性问题无预先规划诉求，经分类轮路由到ReAct逐步尝试
     * @return
     */
    private static boolean routerRoutesExplorationToReAct() {
        AgentRuntime runtime = buildRuntime("router-exploration-real", "你是一个严谨的计算助手。", 10, new RouterEngine());

        List<AgentEvent> events = collectEvents(runtime,
                "我想探索一下calculator工具的能力：先试着算一个简单的 5+3，"
                        + "根据结果再决定下一步尝试什么（比如更大的数或者乘除法），"
                        + "一步步边算边观察，不需要提前做完整计划，最后告诉我你的探索结论。");
        if (!completedWithResult(events)) {
            return false;
        }

        RouterEngine.RoutingDecision decision = routingDecision(events);
        if (decision == null) {
            return fail("未产出ENGINE_ROUTED路由决策事件");
        }
        System.out.println("路由决策：" + decision.getParadigm() + "，理由：" + decision.getReason());
        if (decision.getParadigm() != RouterEngine.ParadigmType.REACT) {
            return fail("无预先规划诉求的探索性问题应路由到ReAct，实际：" + decision.getParadigm());
        }
        if (!hasToolCall(events)) {
            return fail("被转发的ReAct应执行探索性工具调用");
        }
        return check(!extractResultText(events).isBlank(), "探索结论应为非空文本");
    }

    /**
     * 路由范式强制模式：指定forcedParadigm后跳过分类轮直接转发ReWoo执行
     * @return
     */
    private static boolean routerForcedParadigm() {
        AgentRuntime runtime = buildRuntime("router-forced-real", "你是一个严谨的计算助手。", 10,
                new RouterEngine(null, RouterEngine.ParadigmType.REWOO));

        List<AgentEvent> events = collectEvents(runtime,
                "请完成两步计算：第一步使用calculator计算 12*12，"
                        + "第二步使用calculator将第一步的结果加上8，最后给出最终数值。");
        if (!completedWithResult(events)) {
            return false;
        }
        if (events.stream().anyMatch(e -> e.getType() == AgentEventType.ENGINE_ROUTED)) {
            return fail("强制范式不应产生路由决策事件");
        }
        if (extractModelCallIterations(events).size() < 2) {
            return fail("被转发的ReWoo应完整执行规划轮与统一推理轮");
        }
        if (!hasToolCall(events, "calculator")) {
            return fail("被转发的ReWoo应调用calculator工具");
        }
        return checkResultContains(events, "152");
    }

    /**
     * 路由范式：多个互不依赖的计算步骤应路由到ReWoo并行规划执行
     * @return
     */
    private static boolean routerRoutesToReWoo() {
        AgentRuntime runtime = buildRuntime("router-rewoo-real", "你是一个严谨的计算助手。", 10, new RouterEngine());

        List<AgentEvent> events = collectEvents(runtime,
                "请分别使用calculator计算以下三个互不依赖的算式：12*12、34*56、56*78，"
                        + "这些计算相互独立可并行处理，最后汇总给出三个结果。");
        if (!completedWithResult(events)) {
            return false;
        }

        RouterEngine.RoutingDecision decision = routingDecision(events);
        if (decision == null) {
            return fail("未产出ENGINE_ROUTED路由决策事件");
        }
        System.out.println("路由决策：" + decision.getParadigm() + "，理由：" + decision.getReason());
        if (decision.getParadigm() != RouterEngine.ParadigmType.REWOO) {
            return fail("工具密集且步骤可并行的任务应路由到ReWoo，实际：" + decision.getParadigm());
        }
        int planModelIdx = indexOfFirst(events, e -> e.getType() == AgentEventType.MODEL_CALL_START);
        int firstToolIdx = indexOfFirst(events, e -> e.getType() == AgentEventType.TOOL_CALL_START);
        if (planModelIdx >= firstToolIdx) {
            return fail("被转发的ReWoo规划调用应先于任何工具调用");
        }
        String planText = extractModelCallText(events, 2);
        System.out.println("ReWoo规划内容：\n" + planText);
        if (!planText.contains("calculator")) {
            return fail("ReWoo规划轮应产出含calculator工具步的计划");
        }
        if (!hasToolCall(events)) {
            return fail("被转发的ReWoo应执行工具步");
        }
        String resultText = extractResultText(events);
        boolean allResults = resultText.contains("144") && resultText.contains("1904") && resultText.contains("4368");
        return check(allResults, "最终答案应包含144/1904/4368三个独立计算结果");
    }

    /**
     * 路由范式：多实体多跳知识问题应路由到SelfAsk拆解作答
     * @return
     */
    private static boolean routerRoutesToSelfAsk() {
        AgentRuntime runtime = buildRuntime("router-self-ask-real", "你是一个知识渊博的智能助手。", 10, new RouterEngine());

        List<AgentEvent> events = collectEvents(runtime, "长江和黄河都发源于哪个省？");
        if (!completedWithResult(events)) {
            return false;
        }

        RouterEngine.RoutingDecision decision = routingDecision(events);
        if (decision == null) {
            return fail("未产出ENGINE_ROUTED路由决策事件");
        }
        System.out.println("路由决策：" + decision.getParadigm() + "，理由：" + decision.getReason());
        if (decision.getParadigm() != RouterEngine.ParadigmType.SELF_ASK) {
            return fail("多实体多跳问题应路由到SelfAsk，实际：" + decision.getParadigm());
        }
        if (extractModelCallIterations(events).size() < 3) {
            return fail("分类轮+拆解轮+子问题作答轮+汇总轮应至少3轮模型调用");
        }
        String subQuestionsText = extractModelCallText(events, 2);
        System.out.println("路由SelfAsk子问题分解：\n" + subQuestionsText);
        boolean decomposed = subQuestionsText.contains("长江") && subQuestionsText.contains("黄河");
        if (!decomposed) {
            return fail("子问题应按对象拆分：长江与黄河各一项");
        }
        return checkResultContains(events, "青海");
    }

    /**
     * ReAct基座范式：思考-行动-观察循环驱动工具调用直至产出最终答案
     * @return
     */
    private static boolean reActLoop() {
        AgentRuntime runtime = buildRuntime("react-real", "你是一个严谨的计算助手。", 8, null);

        List<AgentEvent> events = collectEvents(runtime,
                "请使用calculator工具计算 (123+456)*2 的值，并给出最终答案。");
        if (!completedWithResult(events)) {
            return false;
        }
        if (!hasToolCall(events, "calculator")) {
            return fail("ReAct行动阶段应调用calculator工具");
        }
        return checkResultContains(events, "1158");
    }

    /**
     * Plan-and-Execute范式：先产出计划再按序执行工具步与推理步
     * @return
     */
    private static boolean planExecute() {
        AgentRuntime runtime = buildRuntime("plan-execute-real", "你是一个严谨的计算助手。", 8, new PlanExecuteEngine());

        List<AgentEvent> events = collectEvents(runtime,
                "请使用calculator工具计算 (123+456)*2 的值，并给出最终答案。");
        if (!completedWithResult(events)) {
            return false;
        }

        List<Integer> modelCallIterations = extractModelCallIterations(events);
        if (modelCallIterations.size() < 2 || modelCallIterations.get(0) != 1) {
            return fail("规划与执行应为多轮分离的模型调用且首轮为规划轮");
        }
        int planModelIdx = indexOfFirst(events, e -> e.getType() == AgentEventType.MODEL_CALL_START);
        int firstToolIdx = indexOfFirst(events, e -> e.getType() == AgentEventType.TOOL_CALL_START);
        if (planModelIdx >= firstToolIdx) {
            return fail("规划模型调用应先于任何工具调用");
        }
        String planText = extractModelCallText(events, 1);
        System.out.println("PlanExecute规划内容：\n" + planText);
        if (!planText.contains("calculator")) {
            return fail("计划中应包含calculator工具步");
        }
        if (!hasToolCall(events, "calculator")) {
            return fail("PlanExecute计划应包含并执行calculator工具步");
        }
        return checkResultContains(events, "1158");
    }

    /**
     * ReWoo范式：一次性规划全部工具步，依赖步引用前序结果#E，统一推理产出答案
     * @return
     */
    private static boolean reWoo() {
        AgentRuntime runtime = buildRuntime("rewoo-real", "你是一个严谨的计算助手。", 8, new ReWooEngine());

        List<AgentEvent> events = collectEvents(runtime,
                "请完成两步计算：第一步使用calculator计算 12*12，"
                        + "第二步使用calculator将第一步的结果加上8，最后给出最终数值。");
        if (!completedWithResult(events)) {
            return false;
        }

        List<Integer> modelCallIterations = extractModelCallIterations(events);
        if (modelCallIterations.size() < 2 || modelCallIterations.get(0) != 1) {
            return fail("规划与统一推理应为多轮分离的模型调用且首轮为规划轮");
        }
        int planModelIdx = indexOfFirst(events, e -> e.getType() == AgentEventType.MODEL_CALL_START);
        int firstToolIdx = indexOfFirst(events, e -> e.getType() == AgentEventType.TOOL_CALL_START);
        if (planModelIdx >= firstToolIdx) {
            return fail("规划模型调用应先于任何工具调用");
        }
        String planText = extractModelCallText(events, 1);
        System.out.println("ReWoo规划内容：\n" + planText);
        if (!planText.contains("calculator")) {
            return fail("计划中应包含calculator工具步");
        }
        if (!hasToolCall(events)) {
            return fail("ReWoo规划应产出并执行工具步");
        }
        return checkResultContains(events, "152");
    }

    /**
     * Reflexion范式：执行后自我反思，反思循环收敛后给出答案
     * @return
     */
    private static boolean reflexion() {
        AgentRuntime runtime = buildRuntime("reflexion-real",
                "你是一个严谨的计算助手，回答前先检查自己的计算是否正确。", 8, new ReflexionEngine());

        List<AgentEvent> events = collectEvents(runtime, "请使用calculator工具计算 37*89 的值");
        if (!completedWithResult(events)) {
            return false;
        }
        if (!hasToolCall(events, "calculator")) {
            return fail("Reflexion行动阶段应调用calculator工具");
        }
        return checkResultContains(events, "3293");
    }

    /**
     * Self-Ask范式：将复合问题分解为子问题逐个自答后汇总
     * @return
     */
    private static boolean selfAsk() {
        AgentRuntime runtime = buildRuntime("self-ask-real", "你是一个知识渊博的智能助手。", 8, new SelfAskEngine());

        List<AgentEvent> events = collectEvents(runtime, "长江和黄河都发源于哪个省？");
        if (!completedWithResult(events)) {
            return false;
        }
        if (extractModelCallIterations(events).size() < 3) {
            return fail("拆解轮+子问题作答轮+汇总轮应至少3轮模型调用，降级直答只有2轮");
        }
        String subQuestionsText = extractModelCallText(events, 1);
        System.out.println("SelfAsk子问题分解：\n" + subQuestionsText);
        boolean decomposed = subQuestionsText.contains("长江") && subQuestionsText.contains("黄河");
        if (!decomposed) {
            return fail("子问题应按对象拆分：长江与黄河各一项");
        }
        return checkResultContains(events, "青海");
    }

    /**
     * Self-Refine范式：产出草稿后自我评估并修订，收敛后给出最终版本
     * @return
     */
    private static boolean selfRefine() {
        AgentRuntime runtime = buildRuntime("self-refine-real", "你是一位专业的文字创作者。", 6, new SelfRefineEngine());

        List<AgentEvent> events = collectEvents(runtime, "请为\"智能体框架\"写一段不超过50字的介绍。");
        if (!completedWithResult(events)) {
            return false;
        }
        if (extractModelCallIterations(events).size() < 2) {
            return fail("草稿轮+评估轮应至少2轮模型调用，直接回答只有1轮");
        }
        String draftText = extractModelCallText(events, 1);
        String critiqueText = extractModelCallText(events, 2);
        System.out.println("SelfRefine草稿：\n" + draftText);
        System.out.println("SelfRefine评估意见：\n" + critiqueText);
        if (draftText.isBlank() || critiqueText.isBlank()) {
            return fail("草稿轮与评估轮均应产出文本");
        }
        return check(!extractResultText(events).isBlank(), "SelfRefine最终版本应为非空文本");
    }

    /**
     * 初始化真实模型与计算器工具集
     */
    private static void initModelAndToolkit() {
        generateOptions = AgentGenerateOptions.builder()
                .temperature(0.1)
                .maxTokens(4096)
                .build();

        HarnessModelProperties properties = new HarnessModelProperties();
        properties.setApiKey(API_KEY);
        properties.setBaseUrl(resolveBaseUrl());
        properties.setTimeoutSeconds(TIMEOUT_SECONDS);

        AgentModelRegistry registry = new AgentModelRegistry(PROVIDER);
        registry.registerProviders(List.of(
                new OpenAIModelProvider(),
                new AnthropicModelProvider(),
                new DashScopeModelProvider(),
                new OllamaModelProvider()
        ));

        HarnessModelFactory modelFactory = new HarnessModelFactory(properties, registry);
        model = modelFactory.getModel(MODEL_CODE, generateOptions);

        toolkit = new HarnessToolkit(null);
        toolkit.addTool(ParadigmTools.calculator());
    }

    /**
     * 经HarnessRuntimeBuilder构建携带指定执行范式的运行时，engine为空时使用默认ReActEngine
     * @param agentName
     * @param systemPrompt
     * @param maxIters
     * @param engine
     * @return
     */
    private static AgentRuntime buildRuntime(String agentName, String systemPrompt, int maxIters, AgentLoop engine) {
        HarnessRuntimeBuilder builder = new HarnessRuntimeBuilder()
                .name(agentName)
                .model(model)
                .systemPrompt(systemPrompt)
                .toolkit(toolkit)
                .generateOptions(generateOptions)
                .maxIters(maxIters);
        if (engine != null) {
            builder.agentLoop(engine);
        }
        return builder.build();
    }

    /**
     * 流式运行单个用户消息并收集全部事件
     * @param runtime
     * @param userMessage
     * @return
     */
    private static List<AgentEvent> collectEvents(AgentRuntime runtime, String userMessage) {
        return runtime.stream(
                List.of(MessageFactory.createUserMessage(userMessage)),
                AgentRuntimeContext.empty()
        ).collectList().block(Duration.ofSeconds(TIMEOUT_SECONDS * 2L));
    }

    /**
     * 运行单个场景并打印通过/失败结果
     * @param title
     * @param scenario
     * @return 失败返回1，通过返回0
     */
    private static int run(String title, Scenario scenario) {
        System.out.println();
        System.out.println("======== " + title + " ========");
        try {
            boolean passed = scenario.run();
            System.out.println(passed ? "[通过] " + title : "[失败] " + title);
            return passed ? 0 : 1;
        } catch (Exception e) {
            System.out.println("[异常] " + title + "：" + e.getMessage());
            return 1;
        }
    }

    /**
     * 断言事件流以AGENT_END收尾且存在非空最终答案
     * @param events
     * @return
     */
    private static boolean completedWithResult(List<AgentEvent> events) {
        if (events == null || events.isEmpty()) {
            return fail("应产出事件流");
        }
        if (events.get(events.size() - 1).getType() != AgentEventType.AGENT_END) {
            return fail("应以AGENT_END收尾，实际末事件：" + events.get(events.size() - 1).getType());
        }
        if (extractResultText(events).isBlank()) {
            return fail("应产出非空最终答案");
        }
        return true;
    }

    /**
     * 提取事件流中的路由决策，未产出时返回null
     * @param events
     * @return
     */
    private static RouterEngine.RoutingDecision routingDecision(List<AgentEvent> events) {
        return events.stream()
                .filter(e -> e.getType() == AgentEventType.ENGINE_ROUTED)
                .map(e -> (RouterEngine.RoutingDecision) e.getPayload())
                .findFirst()
                .orElse(null);
    }

    /**
     * 提取所有模型调用轮次（按发生顺序）
     * @param events
     * @return
     */
    private static List<Integer> extractModelCallIterations(List<AgentEvent> events) {
        return events.stream()
                .filter(e -> e.getType() == AgentEventType.MODEL_CALL_START)
                .map(e -> (ModelCallInfo) e.getPayload())
                .map(ModelCallInfo::getIteration)
                .collect(Collectors.toList());
    }

    /**
     * 提取指定序次模型调用轮产生的流式文本，如第1轮（规划/拆解/草稿）与第2轮（评估）
     * @param events
     * @param ordinal
     * @return
     */
    private static String extractModelCallText(List<AgentEvent> events, int ordinal) {
        StringBuilder text = new StringBuilder();
        int seen = 0;
        boolean collecting = false;
        for (AgentEvent e : events) {
            if (e.getType() == AgentEventType.MODEL_CALL_START) {
                seen++;
                if (seen == ordinal) {
                    collecting = true;
                    continue;
                }
                if (collecting) {
                    break;
                }
            }
            if (e.getType() == AgentEventType.MODEL_CALL_END && collecting) {
                break;
            }
            if (collecting && e instanceof AgentTextBlockDeltaEvent) {
                text.append(((AgentTextBlockDeltaEvent) e).getDelta());
            }
        }
        return text.toString();
    }

    /**
     * 返回首个满足条件的事件下标，不存在时返回-1
     * @param events
     * @param predicate
     * @return
     */
    private static int indexOfFirst(List<AgentEvent> events, Predicate<AgentEvent> predicate) {
        for (int i = 0; i < events.size(); i++) {
            if (predicate.test(events.get(i))) {
                return i;
            }
        }
        return -1;
    }

    /**
     * 提取消息中的文本内容
     * @param message
     * @return
     */
    private static String extractText(AgentMessage message) {
        if (message == null || message.getContent() == null) {
            return "";
        }
        return message.getContent().stream()
                .filter(AgentTextBlock.class::isInstance)
                .map(AgentTextBlock.class::cast)
                .map(AgentTextBlock::getText)
                .collect(Collectors.joining());
    }

    /**
     * 提取事件流中最终结果文本
     * @param events
     * @return
     */
    private static String extractResultText(List<AgentEvent> events) {
        return events.stream()
                .filter(e -> e instanceof AgentResultEvent)
                .map(e -> extractText(((AgentResultEvent) e).getResult()))
                .findFirst()
                .orElse("");
    }

    /**
     * 判断事件流中是否出现指定名称的工具调用
     * @param events
     * @param toolName
     * @return
     */
    private static boolean hasToolCall(List<AgentEvent> events, String toolName) {
        return events.stream()
                .filter(e -> e.getType() == AgentEventType.TOOL_CALL_START)
                .map(AgentEvent::getPayload)
                .filter(List.class::isInstance)
                .flatMap(payload -> ((List<?>) payload).stream())
                .filter(AgentToolUseBlock.class::isInstance)
                .map(AgentToolUseBlock.class::cast)
                .anyMatch(tool -> toolName.equals(tool.getToolName()));
    }

    /**
     * 判断事件流中是否出现任意工具调用
     * @param events
     * @return
     */
    private static boolean hasToolCall(List<AgentEvent> events) {
        return events.stream().anyMatch(e -> e.getType() == AgentEventType.TOOL_CALL_START);
    }

    /**
     * 校验最终答案包含指定内容
     * @param events
     * @param expected
     * @return
     */
    private static boolean checkResultContains(List<AgentEvent> events, String expected) {
        return check(extractResultText(events).contains(expected), "最终答案应包含：" + expected);
    }

    /**
     * 按条件打印失败原因
     * @param condition
     * @param failMessage
     * @return
     */
    private static boolean check(boolean condition, String failMessage) {
        return condition || fail(failMessage);
    }

    /**
     * 打印失败原因并返回false
     * @param message
     * @return
     */
    private static boolean fail(String message) {
        System.out.println("[校验失败] " + message);
        return false;
    }

    /**
     * 是否已配置真实模型连接
     * @return
     */
    private static boolean isConfigured() {
        if ("ollama".equals(PROVIDER)) {
            return true;
        }
        return API_KEY != null && !API_KEY.isBlank();
    }

    /**
     * 解析API基础地址，未配置时使用provider默认地址
     * @return
     */
    private static String resolveBaseUrl() {
        if (BASE_URL != null && !BASE_URL.isBlank()) {
            return BASE_URL;
        }
        return switch (PROVIDER) {
            case "dashscope" -> "https://dashscope.aliyuncs.com/compatible-mode/v1";
            case "anthropic" -> "https://api.anthropic.com";
            case "ollama" -> "http://localhost:11434/v1";
            default -> "https://api.openai.com/v1";
        };
    }

    /**
     * 读取环境变量，为空时返回默认值
     * @param key
     * @param defaultValue
     * @return
     */
    private static String env(String key, String defaultValue) {
        String value = System.getenv(key);
        return value != null && !value.isBlank() ? value : defaultValue;
    }

    /**
     * 单个范式场景
     */
    private interface Scenario {

        /**
         * 运行场景
         * @return 通过返回true
         */
        boolean run();
    }
}
