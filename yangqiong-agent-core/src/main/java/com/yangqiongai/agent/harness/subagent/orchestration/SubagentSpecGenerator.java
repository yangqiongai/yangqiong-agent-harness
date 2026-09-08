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
package com.yangqiongai.agent.harness.subagent.orchestration;

import reactor.core.publisher.Mono;

import java.util.Collection;
import java.util.List;

/**
 * 子代理规格生成器
 * <p>
 * 引擎层契约：通过 LLM 根据任务描述自动生成子代理声明。
 * 实现由业务层提供
 * 通过 Spring 依赖注入接入，引擎层不绑定具体 LLM 服务。
 * </p>
 * @author yangqiong
 */
public interface SubagentSpecGenerator {

    /**
     * 根据任务描述自动生成子代理声明列表
     * @param task
     * @param maxSubagents
     * @param existingAgentNames
     * @param requestModelCode
     * @return
     */
    Mono<List<SubagentDeclaration>> generateDeclarations(
            String task, int maxSubagents, Collection<String> existingAgentNames,
            String requestModelCode);

    /**
     * 根据任务特征选择编排策略
     * <p>
     * 供ADAPTIVE策略使用：LLM实现可根据任务语义从SEQUENTIAL、PARALLEL、
     * DEBATE、REFLECTION、GROUP_CHAT中决策；默认按子代理数量启发式选择。
     * </p>
     * @param task
     * @param declarations
     * @param requestModelCode
     * @return
     */
    default Mono<OrchestrationStrategy> selectStrategy(
            String task, List<SubagentDeclaration> declarations, String requestModelCode) {
        boolean sequential = declarations == null || declarations.size() <= 2;
        return Mono.just(sequential ? OrchestrationStrategy.SEQUENTIAL : OrchestrationStrategy.PARALLEL);
    }

    /**
     * 是否启用声明动态生成
     * @return
     */
    boolean isDeclarationGenerationEnabled();
}
