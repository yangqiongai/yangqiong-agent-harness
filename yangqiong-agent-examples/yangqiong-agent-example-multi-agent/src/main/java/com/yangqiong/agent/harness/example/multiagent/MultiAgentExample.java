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

import java.util.Collection;
import java.util.List;
import java.util.Map;

import com.yangqiong.agent.harness.HarnessRuntimeBuilder;
import com.yangqiong.agent.harness.core.AgentRuntime;
import com.yangqiong.agent.harness.core.message.AgentMessage;
import com.yangqiong.agent.harness.core.message.AgentMessageRole;
import com.yangqiong.agent.harness.core.message.AgentTextBlock;
import com.yangqiong.agent.harness.core.message.AgentToolUseBlock;
import com.yangqiong.agent.harness.core.model.AgentChatResponse;
import com.yangqiong.agent.harness.core.model.AgentGenerateOptions;
import com.yangqiong.agent.harness.core.model.AgentModel;
import com.yangqiong.agent.harness.engine.AgentRuntimeContext;
import com.yangqiong.agent.harness.subagent.orchestration.AutoOrchestrationEngine;
import com.yangqiong.agent.harness.subagent.orchestration.MsgHub;
import com.yangqiong.agent.harness.subagent.orchestration.OrchestrationStrategy;
import com.yangqiong.agent.harness.subagent.orchestration.SubagentDeclaration;
import com.yangqiong.agent.harness.subagent.orchestration.SubagentResult;
import com.yangqiong.agent.harness.subagent.orchestration.SubagentResultAggregator;
import com.yangqiong.agent.harness.subagent.orchestration.SubagentSpecGenerator;
import com.yangqiong.agent.harness.subagent.SubagentSpawner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * 多Agent编排示例
 * <p>
 * 无需API Key，使用桩模型演示三种编排能力：群聊（MsgHub轮转讨论）、
 * 交接（handoff协调委派）、自动编排（辩论/群聊模式）。用于快速体验框架多Agent能力。
 * </p>
 * @author yangqiong
 */
public class MultiAgentExample {

    private static final Logger log = LoggerFactory.getLogger(MultiAgentExample.class);

    private MultiAgentExample() {
    }

    /**
     * 示例入口
     * @param args
     */
    public static void main(String[] args) {
        demoGroupChat();
        demoHandoff();
        demoSequential();
        demoParallel();
        demoAutoOrchestration();
        demoDynamicGeneration();
    }

    /**
     * 群聊演示：三个专业Agent经MsgHub轮转讨论同一主题
     */
    private static void demoGroupChat() {
        log.info("========== 演示1：群聊模式（MsgHub 轮转讨论） ==========");
        AgentRuntime productAgent = buildRuntime("产品专家", "你是产品专家", new StubModel("智能音箱，售价299元。"));
        AgentRuntime inventoryAgent = buildRuntime("库存专家", "你是库存专家", new StubModel("智能音箱余500件。"));
        AgentRuntime orderAgent = buildRuntime("订单专家", "你是订单专家", new StubModel("订单ORD-1001已创建。"));
        MsgHub hub = new MsgHub();
        hub.register(productAgent);
        hub.register(inventoryAgent);
        hub.register(orderAgent);

        List<AgentMessage> discussion = hub.roundRobin("如何完成智能音箱的销售服务", AgentRuntimeContext.empty(), 2).block();
        log.info("参与讨论Agent: {}", hub.getParticipantNames());
        if (discussion != null) {
            discussion.forEach(msg -> log.info("发言: {}", msg.getTextContent()));
        }
        closeAll(productAgent, inventoryAgent, orderAgent);
    }

    /**
     * 交接演示：协调Agent按需将任务移交给专业子Agent
     */
    private static void demoHandoff() {
        log.info("========== 演示2：交接模式（handoff 协调委派） ==========");
        AgentRuntime productRuntime = buildRuntime("product-specialist", "你是产品专家", new StubModel("智能音箱，售价299元。"));
        // 协调Agent：首轮触发handoff移交给产品子Agent，随后返回最终答复
        HandoffModel coordinatorModel = new HandoffModel(
                List.of(new AgentChatResponse(List.of(
                                new AgentToolUseBlock("handoff", "c1", Map.of("target", "product", "reason", "查询智能音箱产品信息"))),
                        null),
                        textResponse("产品信息：智能音箱，售价299元。")));
        AgentRuntime coordinator = new HarnessRuntimeBuilder()
                .name("coordinator")
                .model(coordinatorModel)
                .systemPrompt("你是客服协调者，需要产品信息时通过handoff移交给对应子Agent。")
                .handoffTarget("product", productRuntime)
                .maxIters(5)
                .build();

        AgentMessage reply = coordinator.call(
                List.of(userMessage("请查询智能音箱的产品信息")), AgentRuntimeContext.empty()).block();
        log.info("协调Agent答复: {}", reply != null ? reply.getTextContent() : "空");
        closeAll(productRuntime, coordinator);
    }

    /**
     * 顺序编排演示：子代理按序依次执行，前一个的输出作为后一个的输入
     */
    private static void demoSequential() {
        log.info("========== 演示3：顺序模式（SEQUENTIAL 链式执行） ==========");
        List<SubagentDeclaration> declarations = List.of(
                SubagentDeclaration.builder().name("researcher").description("资料调研代理").build(),
                SubagentDeclaration.builder().name("analyst").description("分析代理").build(),
                SubagentDeclaration.builder().name("writer").description("撰写代理").build());
        AutoOrchestrationEngine engine = new AutoOrchestrationEngine(new SubagentResultAggregator());
        String result = engine.orchestrate("评估远程办公的可行性并输出结论",
                declarations, OrchestrationStrategy.SEQUENTIAL,
                new EchoSpawner(), AgentRuntimeContext.empty(),
                null).block();
        log.info("顺序编排汇总: {}", result);
    }

    /**
     * 并行编排演示：子代理同时执行，结果统一汇总
     */
    private static void demoParallel() {
        log.info("========== 演示4：并行模式（PARALLEL 并发执行） ==========");
        List<SubagentDeclaration> declarations = List.of(
                SubagentDeclaration.builder().name("cost-analyst").description("成本分析代理").build(),
                SubagentDeclaration.builder().name("risk-analyst").description("风险分析代理").build());
        AutoOrchestrationEngine engine = new AutoOrchestrationEngine(new SubagentResultAggregator());
        String result = engine.orchestrate("评估远程办公的成本与风险",
                declarations, OrchestrationStrategy.PARALLEL,
                new EchoSpawner(), AgentRuntimeContext.empty(),
                null).block();
        log.info("并行编排汇总: {}", result);
    }

    /**
     * 自动编排演示：AutoOrchestrationEngine 辩论模式，输出汇总双方观点
     */
    private static void demoAutoOrchestration() {
        log.info("========== 演示5：自动编排（辩论模式） ==========");
        List<SubagentDeclaration> declarations = List.of(
                SubagentDeclaration.builder().name("proponent").description("正方代理").build(),
                SubagentDeclaration.builder().name("opponent").description("反方代理").build());
        AutoOrchestrationEngine engine = new AutoOrchestrationEngine(new SubagentResultAggregator());
        String result = engine.orchestrate("是否应该推动远程办公",
                declarations, OrchestrationStrategy.DEBATE,
                new EchoSpawner(), AgentRuntimeContext.empty(),
                null).block();
        log.info("辩论汇总: {}", result);
    }

    /**
     * 动态生成演示：不预先指定子代理，由生成器依据任务动态分解产生
     */
    private static void demoDynamicGeneration() {
        log.info("========== 演示6：动态生成子代理（任务自动分解） ==========");
        // 不传入预配置声明，由生成器按任务动态生成子代理并编排执行
        AutoOrchestrationEngine engine = new AutoOrchestrationEngine(
                new SubagentResultAggregator(), new TaskSpecGenerator(), 8);
        String result = engine.orchestrateWithGeneration("制定远程办公实施方案",
                null, OrchestrationStrategy.PARALLEL,
                new EchoSpawner(), AgentRuntimeContext.empty(),
                null, null, 1).block();
        log.info("动态生成编排汇总: {}", result);
    }

    /**
     * 构建指定角色的Agent运行时
     * @param name
     * @param systemPrompt
     * @param model
     * @return
     */
    private static AgentRuntime buildRuntime(String name, String systemPrompt, AgentModel model) {
        return new HarnessRuntimeBuilder()
                .name(name)
                .model(model)
                .systemPrompt(systemPrompt)
                .maxIters(5)
                .build();
    }

    /**
     * 依次关闭所有运行时，释放底层线程资源
     * @param runtimes
     */
    private static void closeAll(AgentRuntime... runtimes) {
        for (AgentRuntime runtime : runtimes) {
            runtime.close().block();
        }
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
     * 创建文本响应
     * @param text
     * @return
     */
    private static AgentChatResponse textResponse(String text) {
        return new AgentChatResponse(List.of(AgentTextBlock.builder().text(text).build()), null);
    }

    /**
     * 固定文本桩模型
     */
    static class StubModel implements AgentModel {

        /**
         * 固定回复文本
         */
        private final String text;

        StubModel(String text) {
            this.text = text;
        }

        @Override
        public AgentChatResponse generate(List<AgentMessage> messages, List<Map<String, Object>> tools,
                                          AgentGenerateOptions options) {
            return textResponse(text);
        }

        @Override
        public Flux<AgentChatResponse> stream(List<AgentMessage> messages, List<Map<String, Object>> tools,
                                              AgentGenerateOptions options) {
            return Flux.just(textResponse(text));
        }
    }

    /**
     * 交接桩模型：依次消费脚本响应，触发handoff后返回最终答复
     */
    static class HandoffModel implements AgentModel {

        /**
         * 脚本响应列表
         */
        private final List<AgentChatResponse> script;

        /**
         * 当前响应索引
         */
        private int index;

        HandoffModel(List<AgentChatResponse> script) {
            this.script = script;
        }

        @Override
        public AgentChatResponse generate(List<AgentMessage> messages, List<Map<String, Object>> tools,
                                          AgentGenerateOptions options) {
            AgentChatResponse response = script.get(Math.min(index, script.size() - 1));
            index++;
            return response;
        }

        @Override
        public Flux<AgentChatResponse> stream(List<AgentMessage> messages, List<Map<String, Object>> tools,
                                              AgentGenerateOptions options) {
            return Flux.just(generate(messages, tools, options));
        }
    }

    /**
     * 回显桩执行器：将输入原样回显为子代理输出
     */
    static class EchoSpawner extends SubagentSpawner {

        EchoSpawner() {
            super(null, null, null, null);
        }

        @Override
        public Mono<SubagentResult> spawn(SubagentDeclaration declaration, String input,
                                          AgentRuntimeContext parentCtx) {
            return Mono.just(SubagentResult.success(declaration.getName(),
                    "观点[" + declaration.getName() + "]: " + (input != null ? input : ""), 1));
        }
    }

    /**
     * 任务规格生成器桩：根据任务动态返回固定子代理声明，演示不预配置子代理
     */
    static class TaskSpecGenerator implements SubagentSpecGenerator {

        @Override
        public Mono<List<SubagentDeclaration>> generateDeclarations(
                String task, int maxSubagents, Collection<String> existingAgentNames,
                String requestModelCode) {
            return Mono.just(List.of(
                    SubagentDeclaration.builder().name("solution-designer").description("方案设计代理").build(),
                    SubagentDeclaration.builder().name("schedule-planner").description("排期规划代理").build(),
                    SubagentDeclaration.builder().name("risk-guard").description("风险管控代理").build()));
        }

        @Override
        public boolean isDeclarationGenerationEnabled() {
            return true;
        }
    }
}
