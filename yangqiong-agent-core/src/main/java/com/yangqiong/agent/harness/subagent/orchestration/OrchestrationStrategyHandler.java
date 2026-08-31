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
package com.yangqiong.agent.harness.subagent.orchestration;

import java.util.List;

import com.yangqiong.agent.harness.engine.AgentRuntimeContext;
import com.yangqiong.agent.harness.subagent.SubagentSpawner;
import reactor.core.publisher.Mono;

/**
 * 自定义编排策略处理器
 * <p>
 * 通过{@code HarnessRuntimeBuilder.orchestrationStrategyHandler(name, handler)}
 * 注册到 auto_orchestrate 引擎，工具入参 strategy 匹配策略名即触发该处理器，
 * 实现对内置编排策略之外的第七类自定义策略的开放扩展。
 * </p>
 * @author yangqiong
 */
public interface OrchestrationStrategyHandler {

    /**
     * 执行自定义编排
     * @param task 编排任务
     * @param declarations 子代理声明（为空时由处理器自行生成/分发）
     * @param spawner 子代理执行器
     * @param parentCtx 父级运行时上下文
     * @param failurePolicy 容错策略
     * @param rounds 编排轮次
     * @return 编排汇总结果
     */
    Mono<String> orchestrate(String task, List<SubagentDeclaration> declarations,
                             SubagentSpawner spawner, AgentRuntimeContext parentCtx,
                             OrchestrationFailurePolicy failurePolicy, int rounds);
}