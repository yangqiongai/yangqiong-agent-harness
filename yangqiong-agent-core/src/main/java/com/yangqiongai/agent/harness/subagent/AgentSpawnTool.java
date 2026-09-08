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
package com.yangqiongai.agent.harness.subagent;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.yangqiongai.agent.harness.subagent.orchestration.SubagentDeclaration;
import com.yangqiongai.agent.harness.engine.AgentRuntimeContext;
import com.yangqiongai.agent.harness.core.message.AgentTextBlock;
import com.yangqiongai.agent.harness.core.message.AgentToolResultBlock;
import com.yangqiongai.agent.harness.core.tool.AgentTool;
import com.yangqiongai.agent.harness.core.tool.AgentToolCallParam;
import reactor.core.publisher.Mono;

/**
 * 子代理生成工具
 * @author yangqiong
 */
public class AgentSpawnTool implements AgentTool {

    /**
     * 子代理声明
     */
    private final SubagentDeclaration declaration;

    /**
     * 子代理执行器
     */
    private final SubagentSpawner spawner;

    /**
     * 父级运行时上下文（通过构造函数闭包传入）
     */
    private final AgentRuntimeContext parentCtx;

    public AgentSpawnTool(SubagentDeclaration declaration, SubagentSpawner spawner,
                           AgentRuntimeContext parentCtx) {
        this.declaration = declaration;
        this.spawner = spawner;
        this.parentCtx = parentCtx;
    }

    /**
     * 获取工具名称
     * @return
     */
    @Override
    public String getName() {
        return "agent_spawn_" + declaration.getName();
    }

    /**
     * 获取工具描述
     * @return
     */
    @Override
    public String getDescription() {
        return "委派子代理 [" + declaration.getName() + "] 执行子任务: "
                + declaration.getDescription();
    }

    /**
     * 获取工具参数定义
     * @return
     */
    @Override
    public Map<String, Object> getParameters() {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("type", "object");
        Map<String, Object> properties = new LinkedHashMap<>();
        Map<String, Object> inputProp = new LinkedHashMap<>();
        inputProp.put("type", "string");
        inputProp.put("description", "子代理的输入内容");
        properties.put("input", inputProp);
        params.put("properties", properties);
        params.put("required", List.of("input"));
        return params;
    }

    /**
     * 异步调用工具
     * @param param
     * @return
     */
    @Override
    public Mono<AgentToolResultBlock> callAsync(AgentToolCallParam param) {
        String input = "";
        if (param != null && param.getInput() != null) {
            Object value = param.getInput().get("input");
            if (value != null) {
                input = String.valueOf(value);
            }
        }
        return spawner.spawn(declaration, input, parentCtx)
                .map(result -> {
                    String text = result.isSuccess()
                            ? result.output()
                            : "子代理执行失败: " + result.error();
                    return AgentToolResultBlock.of(List.of(
                            AgentTextBlock.builder().text(text).build()));
                })
                .onErrorResume(e -> Mono.just(AgentToolResultBlock.error(
                        e.getMessage() != null ? e.getMessage() : "子代理执行异常")));
    }
}
