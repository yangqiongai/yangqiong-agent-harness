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
package com.yangqiongai.agent.harness.integration;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

import com.yangqiongai.agent.harness.HarnessRuntimeBuilder;
import com.yangqiongai.agent.harness.config.AgentCompactionConfig;
import com.yangqiongai.agent.harness.core.AgentRuntime;
import com.yangqiongai.agent.harness.core.AgentRuntimeFactory;
import com.yangqiongai.agent.harness.core.event.AgentEvent;
import com.yangqiongai.agent.harness.core.event.AgentEventType;
import com.yangqiongai.agent.harness.core.event.AgentResultEvent;
import com.yangqiongai.agent.harness.core.message.AgentContentBlock;
import com.yangqiongai.agent.harness.core.message.AgentMessage;
import com.yangqiongai.agent.harness.core.message.AgentMessageRole;
import com.yangqiongai.agent.harness.core.message.AgentTextBlock;
import com.yangqiongai.agent.harness.core.message.AgentToolResultBlock;
import com.yangqiongai.agent.harness.core.message.AgentToolUseBlock;
import com.yangqiongai.agent.harness.core.model.AgentChatResponse;
import com.yangqiongai.agent.harness.core.model.AgentGenerateOptions;
import com.yangqiongai.agent.harness.core.model.AgentModel;
import com.yangqiongai.agent.harness.core.model.AgentModelFactory;
import com.yangqiongai.agent.harness.subagent.orchestration.OrchestrationStrategy;
import com.yangqiongai.agent.harness.subagent.orchestration.SubagentDeclaration;
import com.yangqiongai.agent.harness.durable.AgentRunRecord;
import com.yangqiongai.agent.harness.durable.AgentRunState;
import com.yangqiongai.agent.harness.durable.InMemoryAgentRunStore;
import com.yangqiongai.agent.harness.durable.InMemoryCheckpointStore;
import com.yangqiongai.agent.harness.engine.AgentRuntimeContext;
import com.yangqiongai.agent.harness.engine.DurableExecutionTracker;
import com.yangqiongai.agent.harness.core.message.MessageFactory;
import reactor.core.publisher.Flux;

/**
 * 多智能体多轮交互集成测试
 * <p>
 * 使用脚本化桩模型驱动完整Harness运行时，无需真实大模型API即可验证：
 * 主协调Agent通过handoff移交控制权给多个专业子Agent（产品/库存/订单），
 * 跨3轮以上多轮对话累积上下文并保持协作，验证长时间运行的稳定性（上下文压缩与持久化）。
 * </p>
 * @author yangqiong
 */
class MultiAgentMultiTurnIntegrationTest {

    /**
     * 子代理调用超时
     */
    private static final Duration TURN_TIMEOUT = Duration.ofSeconds(30);

    /**
     * 多智能体多轮协作，主协调Agent跨4轮会话依次委派产品、库存、订单子Agent
     * <p>
     * 验证主Agent通过handoff移交控制权给不同专业子Agent，子Agent结果回流主Agent，
     * 且多轮之间会话历史持续累积（>3轮）。
     * </p>
     */
    @Test
    void multiAgent_cooperativeHandoff_acrossFourTurns() {
        ScriptedModel productAgent = new ScriptedModel(List.of(textResponse("智能音箱，售价299元")));
        ScriptedModel inventoryAgent = new ScriptedModel(List.of(textResponse("智能音箱余500件")));
        ScriptedModel orderAgent = new ScriptedModel(List.of(textResponse("订单ORD-1001已创建")));

        AgentRuntime productRuntime = buildRuntime("product-specialist", "你是产品专家", productAgent);
        AgentRuntime inventoryRuntime = buildRuntime("inventory-specialist", "你是库存专家", inventoryAgent);
        AgentRuntime orderRuntime = buildRuntime("order-specialist", "你是订单专家", orderAgent);

        List<AgentChatResponse> mainScript = List.of(
                handoffResponse("c1", "product", "查询智能音箱产品信息"),
                textResponse("产品信息：智能音箱，售价299元。"),
                handoffResponse("c2", "inventory", "查询智能音箱库存"),
                textResponse("库存查询：智能音箱余500件。"),
                handoffResponse("c3", "order", "创建智能音箱订单"),
                textResponse("订单已创建：ORD-1001，金额299元。"),
                handoffResponse("c4", "order", "确认订单ORD-1001"),
                textResponse("订单ORD-1001已确认，全部完成。")
        );
        ScriptedModel coordinator = new ScriptedModel(mainScript);

        AgentRuntime mainRuntime = new HarnessRuntimeBuilder()
                .name("multi-agent-coordinator")
                .model(coordinator)
                .systemPrompt("你是电商客服协调者，需要产品、库存、订单信息时通过handoff移交给对应子Agent。")
                .handoffTarget("product", productRuntime)
                .handoffTarget("inventory", inventoryRuntime)
                .handoffTarget("order", orderRuntime)
                .maxIters(8)
                .build();

        List<String> queries = List.of(
                "请查询智能音箱的产品信息",
                "请查询智能音箱的库存",
                "请帮我下一单智能音箱",
                "请确认刚才的订单ORD-1001"
        );

        List<AgentMessage> history = new ArrayList<>();
        String lastAnswer = "";
        for (String query : queries) {
            history.add(MessageFactory.createUserMessage(query));
            AgentMessage assistant = mainRuntime.call(history, AgentRuntimeContext.empty())
                    .block(TURN_TIMEOUT);
            assertThat(assistant).as("第%d轮应返回助手回复", history.size() / 2).isNotNull();
            assertThat(assistant.getRole()).isEqualTo(AgentMessageRole.ASSISTANT);
            lastAnswer = extractText(assistant);
            assertThat(lastAnswer).as("第%d轮回复不应为空", history.size() / 2).isNotBlank();
            history.add(assistant);
        }

        assertThat(queries).as("至少进行4轮多轮交互").hasSizeGreaterThanOrEqualTo(4);
        assertThat(productAgent.calls()).as("产品子Agent应被委派1次").isEqualTo(1);
        assertThat(inventoryAgent.calls()).as("库存子Agent应被委派1次").isEqualTo(1);
        assertThat(orderAgent.calls()).as("订单子Agent应被委派2次").isEqualTo(2);
        assertThat(lastAnswer).as("最终回答应反映订单子Agent回流结果").contains("ORD-1001");
    }

    /**
     * 长时间运行稳定性，8轮长对话开启上下文压缩与持久化存储
     * <p>
     * 多轮累积历史触发压缩后仍能稳定推进，且运行落持久化存储并写入检查点，
     * 全程不出现错误事件，验证引擎长时间运行不崩溃。
     * </p>
     */
    @Test
    void multiAgent_longRunning_compactionAndDurableStable() {
        AgentCompactionConfig compactionConfig = AgentCompactionConfig.builder()
                .triggerMessages(4)
                .keepMessages(2)
                .build();

        InMemoryAgentRunStore runStore = new InMemoryAgentRunStore();
        InMemoryCheckpointStore checkpointStore = new InMemoryCheckpointStore();

        AgentRuntimeContext context = AgentRuntimeContext.builder()
                .scopeId("scope-longrun")
                .sessionId("session-longrun")
                .build();

        ScriptedModel orderAgent = new ScriptedModel(List.of(textResponse("已确认")));
        AgentRuntime orderRuntime = buildRuntime("order-specialist-long", "你是订单专家", orderAgent);

        List<AgentChatResponse> mainScript = new ArrayList<>();
        for (int turn = 1; turn <= 8; turn++) {
            if (turn % 2 == 0) {
                mainScript.add(handoffResponse("tc" + turn, "order", "确认进度"));
                mainScript.add(textResponse("第" + turn + "轮已与子Agent协作完成。"));
            } else {
                mainScript.add(textResponse("第" + turn + "轮记录到关键足迹KEY-TRACE-" + turn + "。"));
                mainScript.add(textResponse("第" + turn + "轮完成。"));
            }
        }
        ScriptedModel coordinator = new ScriptedModel(mainScript);

        AgentRuntime mainRuntime = new HarnessRuntimeBuilder()
                .name("long-running-coordinator")
                .model(coordinator)
                .systemPrompt("你是长时间稳定运行的客服协调者，请持续完成多轮任务。")
                .handoffTarget("order", orderRuntime)
                .compactionConfig(compactionConfig)
                .agentRunStore(runStore)
                .checkpointStore(checkpointStore)
                .maxIters(8)
                .build();

        List<AgentMessage> history = new ArrayList<>();
        int totalErrorEvents = 0;
        for (int turn = 1; turn <= 8; turn++) {
            history.add(MessageFactory.createUserMessage("第" + turn + "轮任务，请继续推进。"));
            List<AgentEvent> events = mainRuntime.stream(history, context)
                    .collectList().block(TURN_TIMEOUT);
            assertThat(events).as("第%d轮应产生事件流", turn).isNotNull();
            totalErrorEvents += countErrorEvents(events);
            AgentMessage assistant = extractResult(events);
            assertThat(assistant).as("第%d轮应产生最终结果", turn).isNotNull();
            assertThat(extractText(assistant)).as("第%d轮结果不应为空", turn).isNotBlank();
            history.add(assistant);
        }

        assertThat(totalErrorEvents).as("长时间运行全程不应出现任何错误事件").isEqualTo(0);

        String lastRunId = (String) context.get(DurableExecutionTracker.ATTR_RUN_ID);
        assertThat(lastRunId).as("持久化运行应记录runId").isNotNull();
        AgentRunRecord lastRecord = runStore.findByRunId(lastRunId).orElse(null);
        assertThat(lastRecord).as("末轮运行记录应落库").isNotNull();
        assertThat(lastRecord.getState()).as("末轮运行应成功完成")
                .isEqualTo(AgentRunState.SUCCEEDED);
        assertThat(checkpointStore.latest("scope-longrun", "session-longrun"))
                .as("检查点应写入").isPresent();
    }

    /**
     * 同一复杂任务分别用并行与顺序编排执行，对比两者运行时差异
     * <p>
     * 订单处理三个子代理（下单→支付→发货）执行同一任务：
     * 并行模式各子代理同时收到同一原始任务、并发执行并合并结果；
     * 顺序模式各阶段串行执行，后一阶段输入携带前一阶段输出形成数据链。
     * 通过共享追踪器观测并发度与数据流，并对比两者整体耗时。
     * </p>
     */
    @Test
    void multiAgent_sameTask_parallelAndSequentialShowRuntimeDifference() {
        String task = "处理订单：下单、支付、发货";
        long delayMs = 150;

        OrchestrationOutcome parallel = runOrchestration(task, OrchestrationStrategy.PARALLEL, delayMs);
        OrchestrationOutcome sequential = runOrchestration(task, OrchestrationStrategy.SEQUENTIAL, delayMs);

        System.out.println("[PARALLEL] 耗时=" + parallel.elapsedMs + "ms, 并发度=" + parallel.maxConcurrent
                + ", 阶段A入参=" + parallel.inputs.get("stage-a"));
        System.out.println("[SEQUENTIAL] 耗时=" + sequential.elapsedMs + "ms, 并发度=" + sequential.maxConcurrent
                + ", 阶段A入参=" + sequential.inputs.get("stage-a") + ", 阶段B入参=" + sequential.inputs.get("stage-b"));

        // 并发差异：并行模式多子代理并发执行，顺序模式严格串行
        assertThat(parallel.maxConcurrent).as("并行模式应观察到并发执行").isGreaterThan(1);
        assertThat(sequential.maxConcurrent).as("顺序模式应严格串行执行").isEqualTo(1);

        // 数据流差异：并行模式各子代理收到同一个原始任务（互不依赖）
        assertThat(parallel.inputs.get("stage-a")).isEqualTo(task);
        assertThat(parallel.inputs.get("stage-b")).isEqualTo(task);
        assertThat(parallel.inputs.get("stage-c")).isEqualTo(task);

        // 数据流差异：顺序模式后续阶段输入携带前一阶段输出
        assertThat(sequential.inputs.get("stage-a")).isEqualTo(task);
        assertThat(sequential.inputs.get("stage-b")).as("阶段B入参应携带阶段A输出")
                .contains("订单编号ORD-9527已创建");
        assertThat(sequential.inputs.get("stage-c")).as("阶段C入参应携带阶段B输出")
                .contains("已支付");

        // 两种模式汇总结果都包含全部子代理产物
        assertThat(parallel.aggregated).contains("订单编号ORD-9527已创建")
                .contains("已支付").contains("已发货");
        assertThat(sequential.aggregated).contains("订单编号ORD-9527已创建")
                .contains("已支付").contains("已发货");

        // 耗时差异：并行整体耗时显著小于顺序（顺序需累加各阶段耗时）
        assertThat(parallel.elapsedMs).as("并行模式整体耗时应小于顺序模式").isLessThan(sequential.elapsedMs);
    }

    /**
     * 以指定编排策略运行同一订单处理任务并收集运行时观测数据
     * @param task 原始任务
     * @param strategy 编排策略
     * @param delayMs 单个子代理模拟耗时
     * @return
     */
    private OrchestrationOutcome runOrchestration(String task, OrchestrationStrategy strategy, long delayMs) {
        SubagentRuntimeTracker tracker = new SubagentRuntimeTracker();
        Map<String, AgentModel> subModels = new HashMap<>();
        subModels.put("stage-a", new SubagentModel("stage-a", "订单编号ORD-9527已创建", delayMs, tracker));
        subModels.put("stage-b", new SubagentModel("stage-b", "订单ORD-9527已支付", delayMs, tracker));
        subModels.put("stage-c", new SubagentModel("stage-c", "订单ORD-9527已发货", delayMs, tracker));
        AgentModelFactory subFactory = new SubagentModelFactory(subModels);

        List<SubagentDeclaration> declarations = List.of(
                declaration("stage-a", "负责创建订单", "stage-a"),
                declaration("stage-b", "负责完成支付", "stage-b"),
                declaration("stage-c", "负责安排发货", "stage-c"));

        AgentRuntimeFactory runtimeFactory = () -> {
            HarnessRuntimeBuilder childBuilder = new HarnessRuntimeBuilder();
            childBuilder.modelFactory(subFactory);
            return childBuilder;
        };

        ScriptedModel main = new ScriptedModel(List.of(
                orchestrateResponse("o1", task, strategy),
                textResponse("已按" + strategy + "模式汇总各子代理结果。")));

        AgentRuntime runtime = new HarnessRuntimeBuilder()
                .name("orchestration-" + strategy)
                .model(main)
                .systemPrompt("你是订单处理协调者，收到任务后用auto_orchestrate工具按指定策略分发子代理并汇总结果。")
                .subagentDeclarations(declarations)
                .runtimeFactory(runtimeFactory)
                .modelFactory(subFactory)
                .maxIters(4)
                .build();

        long start = System.nanoTime();
        List<AgentEvent> events = runtime.stream(
                List.of(MessageFactory.createUserMessage(task)), AgentRuntimeContext.empty())
                .collectList().block(TURN_TIMEOUT);
        long elapsedMs = (System.nanoTime() - start) / 1_000_000;
        assertThat(events).isNotNull();
        return new OrchestrationOutcome(elapsedMs, tracker.maxActive.get(),
                tracker.inputs, extractToolResultTexts(events));
    }

    /**
     * 构建子代理声明
     * @param name
     * @param description
     * @param modelCode
     * @return
     */
    private SubagentDeclaration declaration(String name, String description, String modelCode) {
        return SubagentDeclaration.builder()
                .name(name)
                .description(description)
                .systemPrompt("你是" + description + "。请根据输入完成对应环节，仅输出本环节结果。")
                .modelCode(modelCode)
                .maxIterations(5)
                .build();
    }

    /**
     * 构建独立运行时
     * @param name
     * @param systemPrompt
     * @param model
     * @return
     */
    private AgentRuntime buildRuntime(String name, String systemPrompt, AgentModel model) {
        return new HarnessRuntimeBuilder()
                .name(name)
                .model(model)
                .systemPrompt(systemPrompt)
                .maxIters(6)
                .build();
    }

    /**
     * 构建返回文本的模型响应
     * @param text
     * @return
     */
    private AgentChatResponse textResponse(String text) {
        return new AgentChatResponse(List.of(AgentTextBlock.builder().text(text).build()), null);
    }

    /**
     * 构建触发handoff移交的工具调用响应，任务内容以reason字符串传给子Agent处理
     * @param callId
     * @param target
     * @param contextText
     * @return
     */
    private AgentChatResponse handoffResponse(String callId, String target, String contextText) {
        AgentToolUseBlock toolUse = new AgentToolUseBlock(
                "handoff", callId, Map.of("target", target, "reason", contextText));
        return new AgentChatResponse(List.of(toolUse), null);
    }

    /**
     * 统计事件流中的错误事件数量
     * @param events
     * @return
     */
    private int countErrorEvents(List<AgentEvent> events) {
        int count = 0;
        for (AgentEvent event : events) {
            if (event.getType() == AgentEventType.ERROR
                    || event.getType() == AgentEventType.TOKEN_BUDGET_EXCEEDED) {
                count++;
            }
        }
        return count;
    }

    /**
     * 从事件流中提取最终结果消息
     * @param events
     * @return
     */
    private AgentMessage extractResult(List<AgentEvent> events) {
        return events.stream()
                .filter(e -> e.getType() == AgentEventType.AGENT_RESULT)
                .map(e -> ((AgentResultEvent) e).getResult())
                .findFirst()
                .orElse(null);
    }

    /**
     * 构建触发自动编排的工具调用响应
     * @param callId
     * @param task
     * @param strategy
     * @return
     */
    private AgentChatResponse orchestrateResponse(String callId, String task, OrchestrationStrategy strategy) {
        AgentToolUseBlock toolUse = new AgentToolUseBlock(
                "auto_orchestrate", callId, Map.of("task", task, "strategy", strategy.name()));
        return new AgentChatResponse(List.of(toolUse), null);
    }

    /**
     * 提取所有工具调用结果文本（含自动编排汇总结果）
     * @param events
     * @return
     */
    private String extractToolResultTexts(List<AgentEvent> events) {
        StringBuilder sb = new StringBuilder();
        for (AgentEvent event : events) {
            if (event.getType() == AgentEventType.TOOL_CALL_END
                    && event.getPayload() instanceof List<?> results) {
                for (Object item : results) {
                    if (item instanceof AgentMessage message && message.getContent() != null) {
                        for (AgentContentBlock block : message.getContent()) {
                            if (block instanceof AgentToolResultBlock toolBlock
                                    && toolBlock.getTextContent() != null) {
                                sb.append(toolBlock.getTextContent()).append('\n');
                            } else if (block instanceof AgentTextBlock textBlock && textBlock.getText() != null) {
                                sb.append(textBlock.getText()).append('\n');
                            }
                        }
                    }
                }
            }
        }
        return sb.toString();
    }

    /**
     * 提取消息文本内容
     * @param message
     * @return
     */
    private String extractText(AgentMessage message) {
        if (message == null || message.getContent() == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (AgentContentBlock block : message.getContent()) {
            if (block instanceof AgentTextBlock textBlock && textBlock.getText() != null) {
                sb.append(textBlock.getText());
            }
        }
        return sb.toString();
    }

    /**
     * 脚本化模型桩
     * <p>
     * 按调用顺序依次返回预设响应，覆盖generate与stream两条调用路径，
     * 用于在无真实大模型API时确定性驱动完整运行时多轮协作。
     * </p>
     * @author yangqiong
     */
    private static class ScriptedModel implements AgentModel {

        /**
         * 预设响应序列
         */
        private final List<AgentChatResponse> script;

        /**
         * 当前调用计数
         */
        private final AtomicInteger index = new AtomicInteger();

        /**
         * 构造脚本化模型
         * @param script
         */
        ScriptedModel(List<AgentChatResponse> script) {
            this.script = script;
        }

        /**
         * 获取已调用次数
         * @return
         */
        int calls() {
            return index.get();
        }

        @Override
        public AgentChatResponse generate(List<AgentMessage> messages, List<Map<String, Object>> tools,
                                          AgentGenerateOptions options) {
            return nextResponse();
        }

        @Override
        public Flux<AgentChatResponse> stream(List<AgentMessage> messages, List<Map<String, Object>> tools,
                                              AgentGenerateOptions options) {
            return Flux.just(nextResponse());
        }

        /**
         * 取下一个预设响应，超出末尾时复用最后一条
         * @return
         */
        private AgentChatResponse nextResponse() {
            int callIndex = index.getAndIncrement();
            if (script.isEmpty()) {
                return textFallback();
            }
            return script.get(Math.min(callIndex, script.size() - 1));
        }

        /**
         * 生成兜底文本响应
         * @return
         */
        private AgentChatResponse textFallback() {
            return new AgentChatResponse(List.of(AgentTextBlock.builder().text("已完成").build()), null);
        }
    }

    /**
     * 编排运行时观测结果
     * @author yangqiong
     */
    private static class OrchestrationOutcome {

        /**
         * 整体耗时（毫秒）
         */
        private final long elapsedMs;

        /**
         * 观测到的最大并发数
         */
        private final int maxConcurrent;

        /**
         * 各子代理收到的输入
         */
        private final Map<String, String> inputs;

        /**
         * 编排汇总结果文本
         */
        private final String aggregated;

        /**
         * 构造观测结果
         * @param elapsedMs
         * @param maxConcurrent
         * @param inputs
         * @param aggregated
         */
        OrchestrationOutcome(long elapsedMs, int maxConcurrent, Map<String, String> inputs, String aggregated) {
            this.elapsedMs = elapsedMs;
            this.maxConcurrent = maxConcurrent;
            this.inputs = inputs;
            this.aggregated = aggregated;
        }
    }

    /**
     * 编排运行时并发追踪器
     * <p>
     * 记录子代理并发度与各子代理实际收到的输入，用于观测并行/顺序模式的运行时差异。
     * </p>
     * @author yangqiong
     */
    private static class SubagentRuntimeTracker {

        /**
         * 当前活跃子代理数
         */
        private final AtomicInteger active = new AtomicInteger();

        /**
         * 观测到的最大并发数
         */
        private final AtomicInteger maxActive = new AtomicInteger();

        /**
         * 各子代理收到的输入
         */
        private final Map<String, String> inputs = new ConcurrentHashMap<>();

        /**
         * 标记子代理开始，并更新最大并发观测值
         * @param name
         */
        void enter(String name) {
            int current = active.incrementAndGet();
            maxActive.accumulateAndGet(current, Math::max);
        }

        /**
         * 标记子代理结束
         */
        void leave() {
            active.decrementAndGet();
        }

        /**
         * 记录子代理收到的输入
         * @param name
         * @param input
         */
        void recordInput(String name, String input) {
            inputs.put(name, input);
        }
    }

    /**
     * 编排子代理脚本化模型
     * <p>
     * 返回固定输出并模拟耗时时长，stream路径采用异步延时以真实体现并行/顺序执行的耗时差异，
     * 同时把收到的输入记录到追踪器，便于断言并行同入参与顺序数据链。
     * </p>
     * @author yangqiong
     */
    private static class SubagentModel implements AgentModel {

        /**
         * 子代理名称
         */
        private final String name;

        /**
         * 固定输出文本
         */
        private final String fixedOutput;

        /**
         * 模拟的模型响应时长（毫秒）
         */
        private final long delayMs;

        /**
         * 并发追踪器
         */
        private final SubagentRuntimeTracker tracker;

        /**
         * 构造子代理模型
         * @param name
         * @param fixedOutput
         * @param delayMs
         * @param tracker
         */
        SubagentModel(String name, String fixedOutput, long delayMs, SubagentRuntimeTracker tracker) {
            this.name = name;
            this.fixedOutput = fixedOutput;
            this.delayMs = delayMs;
            this.tracker = tracker;
        }

        @Override
        public AgentChatResponse generate(List<AgentMessage> messages, List<Map<String, Object>> tools,
                                          AgentGenerateOptions options) {
            tracker.enter(name);
            tracker.recordInput(name, firstUserText(messages));
            try {
                Thread.sleep(delayMs);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            tracker.leave();
            return new AgentChatResponse(List.of(AgentTextBlock.builder().text(fixedOutput).build()), null);
        }

        @Override
        public Flux<AgentChatResponse> stream(List<AgentMessage> messages, List<Map<String, Object>> tools,
                                              AgentGenerateOptions options) {
            tracker.enter(name);
            tracker.recordInput(name, firstUserText(messages));
            return Flux.just(new AgentChatResponse(
                            List.of(AgentTextBlock.builder().text(fixedOutput).build()), null))
                    .delayElements(Duration.ofMillis(delayMs))
                    .doOnNext(response -> tracker.leave());
        }

        /**
         * 提取入参文本
         * @param messages
         * @return
         */
        private String firstUserText(List<AgentMessage> messages) {
            if (messages == null) {
                return "";
            }
            for (AgentMessage message : messages) {
                if (message.getRole() == AgentMessageRole.USER && message.getContent() != null) {
                    for (AgentContentBlock block : message.getContent()) {
                        if (block instanceof AgentTextBlock textBlock && textBlock.getText() != null) {
                            return textBlock.getText();
                        }
                    }
                }
            }
            return "";
        }
    }

    /**
     * 子代理模型工厂，按模型编码返回对应的脚本化子代理模型
     * @author yangqiong
     */
    private static class SubagentModelFactory implements AgentModelFactory {

        /**
         * 模型编码到子代理模型的映射
         */
        private final Map<String, AgentModel> models;

        /**
         * 构造模型工厂
         * @param models
         */
        SubagentModelFactory(Map<String, AgentModel> models) {
            this.models = models;
        }

        @Override
        public AgentModel getModel(String modelCode, AgentGenerateOptions options) {
            if (modelCode != null && models.containsKey(modelCode)) {
                return models.get(modelCode);
            }
            return models.values().iterator().next();
        }

        @Override
        public AgentModel getModelByConfig(String provider, String apiKey, String modelCode,
                                           AgentGenerateOptions options) {
            return getModel(modelCode, options);
        }
    }
}