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

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import com.yangqiong.agent.harness.core.message.AgentToolResultBlock;
import com.yangqiong.agent.harness.core.tool.AgentToolCallParam;
import com.yangqiong.agent.harness.engine.AgentRuntimeContext;
import com.yangqiong.agent.harness.subagent.SubagentSpawner;
import com.yangqiong.agent.harness.subagent.orchestration.*;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

/**
 * 自定义编排策略注册接口测试
 * <p>
 * 验证内置策略之外的自定义策略可通过{@link OrchestrationStrategyHandler}注册，
 * auto_orchestrate按策略名命中后触发处理器执行，实现第七类策略的开箱扩展。
 * </p>
 * @author yangqiong
 */
class OrchestrationStrategyHandlerTest {

    /**
     * 自定义策略命中处理器并承接任务
     */
    @Test
    void customStrategyTriggersRegisteredHandler() {
        AtomicReference<String> receivedTask = new AtomicReference<>();
        AtomicReference<Integer> receivedRounds = new AtomicReference<>();
        OrchestrationStrategyHandler handler = (task, declarations, spawner, parentCtx, failurePolicy, rounds) -> {
            receivedTask.set(task);
            receivedRounds.set(rounds);
            return Mono.just("团队主管汇总结论");
        };

        AutoOrchestrateTool tool = new AutoOrchestrateTool(spawner(), Collections.emptyList(), 3, true);
        tool.registerOrchestrationHandler("MASTER", handler);

        AgentToolResultBlock result = tool.callAsync(new AgentToolCallParam(
                Map.of("task", "整合多方意见", "strategy", "MASTER", "rounds", 2)))
                .block();

        assertThat(result).isNotNull();
        assertThat(result.isError()).isFalse();
        assertThat(receivedTask.get()).isEqualTo("整合多方意见");
        assertThat(receivedRounds.get()).isEqualTo(2);
        assertThat(result.getTextContent()).contains("团队主管汇总结论");
    }

    /**
     * 处理器可用工具级别直接注册，引擎按名触发
     */
    @Test
    void engineDispatchesByRegisteredHandlerName() {
        AutoOrchestrationEngine engine = new AutoOrchestrationEngine(new SubagentResultAggregator(), null, 5);
        AtomicReference<String> received = new AtomicReference<>();
        engine.registerHandler("custom-heuristic", (task, declarations, spawner, parentCtx, fp, rounds) -> {
            received.set(task);
            return Mono.just("启发式策略结论");
        });

        Mono<String> mono = engine.orchestrate("平衡维度", Collections.emptyList(), "custom-heuristic",
                spawner(), AgentRuntimeContext.builder().build(),
                OrchestrationFailurePolicy.FAIL_FAST, 1, null);

        assertThat(mono.block()).isEqualTo("启发式策略结论");
        assertThat(received.get()).isEqualTo("平衡维度");
    }

    /**
     * 未注册的策略名回退并行编排，不阻塞
     */
    @Test
    void unknownStrategyFallsBack() {
        AutoOrchestrationEngine engine = new AutoOrchestrationEngine(new SubagentResultAggregator(), null, 5);
        List<SubagentDeclaration> declarations = List.of(
                SubagentDeclaration.builder().name("agent-a").description("代理A").build());
        String result = engine.orchestrate("未知策略任务", declarations, "NOT_REGISTERED",
                spawner(), AgentRuntimeContext.builder().build(),
                OrchestrationFailurePolicy.CONTINUE_ON_FAILURE, 1, null).block();

        assertThat(result).isNotBlank();
    }

    /**
     * 空执行器桩（自定义处理器路径不触发子代理执行）
     */
    private SubagentSpawner spawner() {
        return new SubagentSpawner(null, null, null, null) {
            @Override
            public Mono<SubagentResult> spawn(SubagentDeclaration declaration, String input,
                                              AgentRuntimeContext parentCtx) {
                return Mono.just(SubagentResult.success(declaration.getName(), "结果", 1));
            }
        };
    }
}