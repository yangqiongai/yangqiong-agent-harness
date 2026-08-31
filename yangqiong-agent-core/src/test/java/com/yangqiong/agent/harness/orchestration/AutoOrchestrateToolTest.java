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
package com.yangqiong.agent.harness.orchestration;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import com.yangqiong.agent.harness.HarnessRuntimeBuilder;
import com.yangqiong.agent.harness.core.message.AgentToolResultBlock;
import com.yangqiong.agent.harness.core.tool.AgentToolCallParam;
import com.yangqiong.agent.harness.engine.AgentRuntimeContext;
import com.yangqiong.agent.harness.subagent.SubagentSpawner;
import com.yangqiong.agent.harness.subagent.orchestration.*;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

/**
 * 自动编排工具参数配置测试
 * <p>
 * 验证strategy/rounds以参数方式指定后的内部还原与兜底行为：
 * 默认策略、默认轮次、轮次上限约束。
 * </p>
 * @author yangqiong
 */
class AutoOrchestrateToolTest {

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
     * 构建带默认策略与轮次配置的编排工具
     * @param spawner
     * @param declarations
     * @param strategy
     * @param rounds
     * @param maxRounds
     * @return
     */
    private AutoOrchestrateTool tool(SubagentSpawner spawner, List<SubagentDeclaration> declarations,
                                     OrchestrationStrategy strategy, Integer rounds, int maxRounds) {
        return new AutoOrchestrateTool(spawner, declarations, 3, true,
                null, 5, null, strategy, rounds, maxRounds);
    }

    /**
     * 默认策略兜底：LLM未指定strategy时使用配置策略
     */
    @Test
    void 默认策略兜底_LLM未指定strategy时使用配置策略() {
        CountingSpawner spawner = new CountingSpawner();
        List<SubagentDeclaration> declarations = List.of(
                declaration("debater-a", "正方代理"),
                declaration("debater-b", "反方代理"));
        AutoOrchestrateTool tool = tool(spawner, declarations, OrchestrationStrategy.DEBATE, 1, 3);

        AgentToolCallParam param = new AgentToolCallParam(Map.of("task", "should we promote remote work"));
        AgentToolResultBlock result = tool.callAsync(param).block();

        assertThat(result).isNotNull();
        assertThat(result.isError()).isFalse();
        assertThat(result.getTextContent()).as("未指定strategy时应走配置的DEBATE策略").contains("最优立场");
    }

    /**
     * 默认轮次兜底：LLM未指定rounds时使用配置轮次
     */
    @Test
    void 默认轮次兜底_LLM未指定rounds时使用配置轮次() {
        CountingSpawner spawner = new CountingSpawner();
        List<SubagentDeclaration> declarations = List.of(
                declaration("debater-a", "正方代理"),
                declaration("debater-b", "反方代理"));
        AutoOrchestrateTool tool = tool(spawner, declarations, OrchestrationStrategy.DEBATE, 2, 10);

        AgentToolCallParam param = new AgentToolCallParam(Map.of("task", "should we promote remote work"));
        tool.callAsync(param).block();

        assertThat(spawner.spawnCount()).as("未指定rounds时应使用配置的2轮").isEqualTo(4);
    }

    /**
     * 轮次上限约束：LLM指定rounds超过上限时被截断
     */
    @Test
    void 轮次上限约束_LLM指定rounds超上限时截断() {
        CountingSpawner spawner = new CountingSpawner();
        List<SubagentDeclaration> declarations = List.of(
                declaration("debater-a", "正方代理"),
                declaration("debater-b", "反方代理"));
        AutoOrchestrateTool tool = tool(spawner, declarations, OrchestrationStrategy.DEBATE, 1, 2);

        AgentToolCallParam param = new AgentToolCallParam(
                Map.of("task", "should we promote remote work", "rounds", 10));
        tool.callAsync(param).block();

        assertThat(spawner.spawnCount()).as("LLM指定rounds=10应被截断为2").isEqualTo(4);
    }

    /**
     * 未配置默认策略时保持ADAPTIVE行为
     */
    @Test
    void 未配置默认策略时保持ADAPTIVE行为() {
        CountingSpawner spawner = new CountingSpawner();
        List<SubagentDeclaration> declarations = List.of(
                declaration("agent-a", "代理A"),
                declaration("agent-b", "代理B"));
        AutoOrchestrateTool tool = tool(spawner, declarations, null, null, 5);

        AgentToolCallParam param = new AgentToolCallParam(Map.of("task", "some task"));
        AgentToolResultBlock result = tool.callAsync(param).block();

        assertThat(result).isNotNull();
        assertThat(result.getTextContent()).as("未配置默认策略时应保持ADAPTIVE（顺序执行）").doesNotContain("最优立场");
    }

    /**
     * 已传静态声明时不交给LLM重新生成，直接使用预配置声明
     */
    @Test
    void 已传静态声明_不触发LLM动态生成() {
        CountingSpawner spawner = new CountingSpawner();
        List<SubagentDeclaration> declarations = List.of(
                declaration("agent-a", "代理A"),
                declaration("agent-b", "代理B"));
        GenerationCountingGenerator generator = new GenerationCountingGenerator();
        AutoOrchestrateTool tool = new AutoOrchestrateTool(spawner, declarations, 3, true,
                generator, 5, null);

        AgentToolCallParam param = new AgentToolCallParam(Map.of("task", "some task"));
        tool.callAsync(param).block();

        assertThat(generator.generationCount())
                .as("已传静态声明时应直接使用声明，不交给LLM生成").isEqualTo(0);
        assertThat(spawner.spawnCount()).as("应执行2个静态声明的子代理").isEqualTo(2);
    }

    /**
     * 未传静态声明且注入生成器时走LLM动态生成
     */
    @Test
    void 未传静态声明_触发LLM动态生成() {
        CountingSpawner spawner = new CountingSpawner();
        GenerationCountingGenerator generator = new GenerationCountingGenerator() {
            @Override
            public Mono<List<SubagentDeclaration>> generateDeclarations(String task, int maxSubagents,
                                                                        Collection<String> existingAgentNames,
                                                                        String requestModelCode) {
                super.generateDeclarations(task, maxSubagents, existingAgentNames, requestModelCode);
                return Mono.just(List.of(
                        SubagentDeclaration.builder().name("gen-agent").description("动态代理").build()));
            }
        };
        AutoOrchestrateTool tool = new AutoOrchestrateTool(spawner, Collections.emptyList(), 3, true,
                generator, 5, null);

        AgentToolCallParam param = new AgentToolCallParam(Map.of("task", "some task"));
        tool.callAsync(param).block();

        assertThat(generator.generationCount()).as("未传静态声明时应走LLM动态生成").isEqualTo(1);
        assertThat(spawner.spawnCount()).as("应执行LLM生成的1个动态子代理").isEqualTo(1);
    }

    /**
     * 编排参数还原为提示词指令
     */
    @Test
    void 编排参数还原为提示词指令() throws Exception {
        HarnessRuntimeBuilder builder = new HarnessRuntimeBuilder()
                .orchestrationStrategy(OrchestrationStrategy.DEBATE)
                .orchestrationRounds(1)
                .maxOrchestrationRounds(3);
        String fragment = invokePromptFragment(builder);
        assertThat(fragment)
                .contains("strategy必须使用DEBATE")
                .contains("rounds使用1")
                .contains("不得超过3");
    }

    /**
     * rounds未指定时提示LLM按任务自行决定
     */
    @Test
    void rounds未指定时提示LLM按任务自行决定() throws Exception {
        HarnessRuntimeBuilder builder = new HarnessRuntimeBuilder()
                .orchestrationStrategy(OrchestrationStrategy.GROUP_CHAT)
                .maxOrchestrationRounds(4);
        String fragment = invokePromptFragment(builder);
        assertThat(fragment)
                .contains("strategy必须使用GROUP_CHAT")
                .contains("rounds可由你根据任务复杂度自行决定")
                .contains("不得超过4");
    }

    /**
     * ADAPTIVE策略提示LLM根据任务特征自行选择具体策略
     */
    @Test
    void adaptive策略提示LLM自行选择策略() throws Exception {
        HarnessRuntimeBuilder builder = new HarnessRuntimeBuilder()
                .orchestrationStrategy(OrchestrationStrategy.ADAPTIVE)
                .orchestrationRounds(1)
                .maxOrchestrationRounds(3);
        String fragment = invokePromptFragment(builder);
        assertThat(fragment)
                .as("ADAPTIVE不应强制使用ADAPTIVE")
                .doesNotContain("strategy必须使用ADAPTIVE")
                .as("ADAPTIVE应提示LLM按任务特征自行选择")
                .contains("strategy可由你根据任务特征自行选择")
                .contains("SEQUENTIAL/PARALLEL/DEBATE/REFLECTION/GROUP_CHAT")
                .contains("rounds使用1")
                .contains("不得超过3");
    }

    /**
     * 未配置编排参数时不生成提示词指令
     */
    @Test
    void 未配置编排参数时不生成提示词指令() throws Exception {
        HarnessRuntimeBuilder builder = new HarnessRuntimeBuilder();
        assertThat(invokePromptFragment(builder)).isEmpty();
    }

    /**
     * 反射调用构建器的提示词指令生成方法
     * @param builder
     * @return
     */
    private String invokePromptFragment(HarnessRuntimeBuilder builder) throws Exception {
        Method method = HarnessRuntimeBuilder.class.getDeclaredMethod("buildOrchestrationPromptFragment");
        method.setAccessible(true);
        return (String) method.invoke(builder);
    }

    /**
     * 计数声明生成器
     * <p>
     * 统计generateDeclarations调用次数，用于验证静态声明存在时跳过LLM动态生成。
     * </p>
     */
    private static class GenerationCountingGenerator implements SubagentSpecGenerator {

        /**
         * 生成调用次数
         */
        private final AtomicInteger counter = new AtomicInteger();

        @Override
        public Mono<List<SubagentDeclaration>> generateDeclarations(String task, int maxSubagents,
                                                                    Collection<String> existingAgentNames,
                                                                    String requestModelCode) {
            counter.incrementAndGet();
            return Mono.just(Collections.emptyList());
        }

        @Override
        public boolean isDeclarationGenerationEnabled() {
            return true;
        }

        int generationCount() {
            return counter.get();
        }
    }

    /**
     * 计数执行器
     * <p>
     * 统计子代理执行次数，用于验证默认轮次与轮次上限。
     * </p>
     */
    private static class CountingSpawner extends SubagentSpawner {

        /**
         * 子代理执行次数
         */
        private final AtomicInteger counter = new AtomicInteger();

        CountingSpawner() {
            super(null, null, null, null);
        }

        @Override
        public Mono<SubagentResult> spawn(SubagentDeclaration declaration, String input,
                                          AgentRuntimeContext parentCtx) {
            counter.incrementAndGet();
            return Mono.just(SubagentResult.success(declaration.getName(),
                    "观点[" + declaration.getName() + "]: " + (input != null ? input : ""), 1));
        }

        int spawnCount() {
            return counter.get();
        }
    }
}
