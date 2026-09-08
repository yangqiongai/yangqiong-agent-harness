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
package com.yangqiongai.agent.harness.orchestration;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Collection;
import java.util.List;

import com.yangqiongai.agent.harness.engine.AgentRuntimeContext;
import com.yangqiongai.agent.harness.subagent.SubagentSpawner;
import com.yangqiongai.agent.harness.subagent.orchestration.*;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

/**
 * 自动编排引擎多智能体对话模式测试
 * @author yangqiong
 */
class AutoOrchestrationEngineTest {

    /**
     * 构建子代理声明
     * @param name
     * @param description
     * @return
     */
    private SubagentDeclaration declaration(String name, String description) {
        return SubagentDeclaration.builder()
                .name(name)
                .description(description)
                .build();
    }

    /**
     * 辩论模式：2代理2轮+裁判汇总，输出包含双方观点
     */
    @Test
    void debate模式_多轮反馈后汇总双方观点() {
        List<SubagentDeclaration> declarations = List.of(
                declaration("debater-a", "正方代理"),
                declaration("debater-b", "反方代理"));
        AutoOrchestrationEngine engine = new AutoOrchestrationEngine(new SubagentResultAggregator());
        String result = engine.orchestrateDebate("should we promote remote work",
                declarations, new EchoSpawner(), AgentRuntimeContext.empty(), 2, null).block();
        assertThat(result).isNotNull();
        assertThat(result).contains("观点[debater-a]", "观点[debater-b]");
        assertThat(result).contains("最优立场");
    }

    /**
     * 辩论模式：每个参与者输入包含角色发言约束，防止越权替他人发言或输出裁判结论
     */
    @Test
    void debate模式_参与者输入包含角色发言约束() {
        List<SubagentDeclaration> declarations = List.of(
                declaration("proponent", "正方代理"),
                declaration("opponent", "反方代理"));
        AutoOrchestrationEngine engine = new AutoOrchestrationEngine(new SubagentResultAggregator());
        String result = engine.orchestrateDebate("debate topic",
                declarations, new EchoSpawner(), AgentRuntimeContext.empty(), 1, null).block();
        assertThat(result).isNotNull();
        assertThat(result).contains("【发言约束】你只以【proponent】的身份作答");
        assertThat(result).contains("【发言约束】你只以【opponent】的身份作答");
        assertThat(result).contains("不要替其他参与者发言");
        assertThat(result).contains("不要输出裁判汇总或最终结论");
    }

    /**
     * 反思模式：执行者作答、评审者批判、执行者据批判修订，最终回答含修订标记
     */
    @Test
    void reflection模式_最终回答含批判后修订标记() {
        List<SubagentDeclaration> declarations = List.of(
                declaration("executor", "执行者代理"),
                declaration("critic", "评审者代理"));
        AutoOrchestrationEngine engine = new AutoOrchestrationEngine(new SubagentResultAggregator());
        String result = engine.orchestrateReflection("write a marketing copy",
                declarations, new EchoSpawner(), AgentRuntimeContext.empty(), 2).block();
        assertThat(result).isNotNull();
        assertThat(result).contains("（已根据评审意见修订完成）");
        assertThat(result).contains("观点[executor]", "观点[critic]");
    }

    /**
     * 群聊模式：3代理轮转发言，各代理输出被后续代理引用
     */
    @Test
    void groupChat模式_各代理输出被后续代理引用() {
        List<SubagentDeclaration> declarations = List.of(
                declaration("group-1", "讨论代理1"),
                declaration("group-2", "讨论代理2"),
                declaration("group-3", "讨论代理3"));
        AutoOrchestrationEngine engine = new AutoOrchestrationEngine(new SubagentResultAggregator());
        String result = engine.orchestrateGroupChat("how to improve product quality",
                declarations, new EchoSpawner(), AgentRuntimeContext.empty(), 1).block();
        assertThat(result).isNotNull();
        assertThat(result).contains("观点[group-1]", "观点[group-2]", "观点[group-3]");
        // 后续代理引用前序发言，观点标记出现次数大于参与者数量
        assertThat(countOccurrences(result, "观点[")).isGreaterThan(3);
    }

    /**
     * 自适应模式：LLM决策为DEBATE时走辩论编排
     */
    @Test
    void adaptive_注入specGenerator决策为DEBATE时走辩论编排() {
        List<SubagentDeclaration> declarations = List.of(
                declaration("debater-a", "正方代理"),
                declaration("debater-b", "反方代理"));
        AutoOrchestrationEngine engine = new AutoOrchestrationEngine(
                new SubagentResultAggregator(),
                new FixedStrategySpecGenerator(OrchestrationStrategy.DEBATE), 5);
        String result = engine.orchestrate("should we promote remote work",
                declarations, OrchestrationStrategy.ADAPTIVE,
                new EchoSpawner(), AgentRuntimeContext.empty(),
                OrchestrationFailurePolicy.CONTINUE_ON_FAILURE).block();
        assertThat(result).isNotNull();
        assertThat(result).as("ADAPTIVE决策为DEBATE应走辩论编排").contains("最优立场");
    }

    /**
     * 自适应模式：LLM决策为REFLECTION时走反思编排
     */
    @Test
    void adaptive_注入specGenerator决策为REFLECTION时走反思编排() {
        List<SubagentDeclaration> declarations = List.of(
                declaration("executor", "执行者代理"),
                declaration("critic", "评审者代理"));
        AutoOrchestrationEngine engine = new AutoOrchestrationEngine(
                new SubagentResultAggregator(),
                new FixedStrategySpecGenerator(OrchestrationStrategy.REFLECTION), 5);
        String result = engine.orchestrate("write a marketing copy",
                declarations, OrchestrationStrategy.ADAPTIVE,
                new EchoSpawner(), AgentRuntimeContext.empty(),
                OrchestrationFailurePolicy.CONTINUE_ON_FAILURE, 2, null).block();
        assertThat(result).isNotNull();
        assertThat(result).as("ADAPTIVE决策为REFLECTION应走反思编排")
                .contains("（已根据评审意见修订完成）");
    }

    /**
     * 自适应模式：LLM决策为SEQUENTIAL时顺序执行，后者输入包含前者输出
     */
    @Test
    void adaptive_注入specGenerator决策为SEQUENTIAL时顺序执行() {
        List<SubagentDeclaration> declarations = List.of(
                declaration("agent-a", "代理A"),
                declaration("agent-b", "代理B"));
        AutoOrchestrationEngine engine = new AutoOrchestrationEngine(
                new SubagentResultAggregator(),
                new FixedStrategySpecGenerator(OrchestrationStrategy.SEQUENTIAL), 5);
        String result = engine.orchestrate("some task",
                declarations, OrchestrationStrategy.ADAPTIVE,
                new EchoSpawner(), AgentRuntimeContext.empty(),
                OrchestrationFailurePolicy.CONTINUE_ON_FAILURE).block();
        assertThat(result).isNotNull();
        assertThat(result).as("ADAPTIVE决策为SEQUENTIAL时后者输入应含前者输出")
                .contains("观点[agent-b]: 观点[agent-a]");
    }

    /**
     * 自适应模式：未注入specGenerator时按子代理数量启发式（≤2顺序，否则并行）
     */
    @Test
    void adaptive_未注入specGenerator按数量启发式() {
        AutoOrchestrationEngine engine = new AutoOrchestrationEngine(new SubagentResultAggregator());
        String sequentialResult = engine.orchestrate("some task",
                List.of(declaration("a", "代理A"), declaration("b", "代理B")),
                OrchestrationStrategy.ADAPTIVE, new EchoSpawner(),
                AgentRuntimeContext.empty(), OrchestrationFailurePolicy.CONTINUE_ON_FAILURE).block();
        assertThat(sequentialResult).contains("观点[b]: 观点[a]");
        String parallelResult = engine.orchestrate("some task",
                List.of(declaration("a", "代理A"), declaration("b", "代理B"), declaration("c", "代理C")),
                OrchestrationStrategy.ADAPTIVE, new EchoSpawner(),
                AgentRuntimeContext.empty(), OrchestrationFailurePolicy.CONTINUE_ON_FAILURE).block();
        assertThat(parallelResult).doesNotContain("观点[b]: 观点[a]");
    }

    /**
     * 自适应模式：LLM返回ADAPTIVE或空值时回退数量启发式
     */
    @Test
    void adaptive_specGenerator返回ADAPTIVE或空值时回退启发式() {
        AutoOrchestrationEngine adaptiveFallback = new AutoOrchestrationEngine(
                new SubagentResultAggregator(),
                new FixedStrategySpecGenerator(OrchestrationStrategy.ADAPTIVE), 5);
        String result = adaptiveFallback.orchestrate("some task",
                List.of(declaration("a", "代理A"), declaration("b", "代理B")),
                OrchestrationStrategy.ADAPTIVE, new EchoSpawner(),
                AgentRuntimeContext.empty(), OrchestrationFailurePolicy.CONTINUE_ON_FAILURE).block();
        assertThat(result).contains("观点[b]: 观点[a]");
        AutoOrchestrationEngine nullFallback = new AutoOrchestrationEngine(
                new SubagentResultAggregator(),
                new FixedStrategySpecGenerator(null), 5);
        String nullResult = nullFallback.orchestrate("some task",
                List.of(declaration("a", "代理A"), declaration("b", "代理B")),
                OrchestrationStrategy.ADAPTIVE, new EchoSpawner(),
                AgentRuntimeContext.empty(), OrchestrationFailurePolicy.CONTINUE_ON_FAILURE).block();
        assertThat(nullResult).contains("观点[b]: 观点[a]");
    }

    /**
     * 固定策略生成器
     * <p>
     * 返回预设策略，用于验证ADAPTIVE的LLM决策分派路径。
     * </p>
     */
    private static class FixedStrategySpecGenerator implements SubagentSpecGenerator {

        /**
         * 预设策略
         */
        private final OrchestrationStrategy strategy;

        FixedStrategySpecGenerator(OrchestrationStrategy strategy) {
            this.strategy = strategy;
        }

        @Override
        public Mono<List<SubagentDeclaration>> generateDeclarations(String task, int maxSubagents,
                                                                    Collection<String> existingAgentNames,
                                                                    String requestModelCode) {
            return Mono.just(List.of());
        }

        @Override
        public Mono<OrchestrationStrategy> selectStrategy(String task,
                                                          List<SubagentDeclaration> declarations,
                                                          String requestModelCode) {
            return Mono.justOrEmpty(strategy);
        }

        @Override
        public boolean isDeclarationGenerationEnabled() {
            return false;
        }
    }

    /**
     * 统计文本中指定字符串出现次数
     * @param text
     * @param token
     * @return
     */
    private int countOccurrences(String text, String token) {
        int count = 0;
        int idx = 0;
        while ((idx = text.indexOf(token, idx)) != -1) {
            count++;
            idx += token.length();
        }
        return count;
    }

    /**
     * 回声执行器
     * <p>
     * 将输入原样回显为子代理输出，便于断言反馈注入与群聊前序发言引用。
     * </p>
     */
    private static class EchoSpawner extends SubagentSpawner {

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
}
