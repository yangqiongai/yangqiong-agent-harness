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
package com.yangqiongai.agent.harness.core.tool;

import com.yangqiongai.agent.harness.core.message.AgentToolResultBlock;
import reactor.core.publisher.Mono;

import java.util.Map;

/**
 * Agent工具调用契约
 * @author yangqiong
 */
public interface AgentTool {

    /**
     * 获取工具名称
     * @return
     */
    String getName();

    /**
     * 获取工具描述
     * @return
     */
    String getDescription();

    /**
     * 获取工具参数定义
     * @return
     */
    Map<String, Object> getParameters();

    /**
     * 调用工具
     * @param param
     * @return
     */
    Mono<AgentToolResultBlock> callAsync(AgentToolCallParam param);

    /**
     * 是否为只读工具（无副作用，可安全重试与并发执行）
     * @return
     */
    default boolean isReadOnly() {
        return false;
    }

    /**
     * 获取风险等级（高风险工具建议强制审批）
     * @return
     */
    default ToolRiskLevel getRiskLevel() {
        return isReadOnly() ? ToolRiskLevel.LOW : ToolRiskLevel.MEDIUM;
    }

    /**
     * 获取幂等键，非空时同键重复调用直接复用首次结果（副作用去重）
     * @param param
     * @return
     */
    default String getIdempotencyKey(AgentToolCallParam param) {
        return null;
    }

    /**
     * 获取补偿描述（副作用工具失败后的补偿指引）
     * @return
     */
    default String getCompensationHint() {
        return null;
    }

    /**
     * 工具类别标识，用于权限策略判定
     * <p>
     * 默认 builtin，MCP 工具适配器覆写为 mcp。
     * </p>
     * @return
     */
    default String getToolCategory() {
        return "builtin";
    }

    /**
     * 获取工具规格定义，基于 name/description/parameters 构建
     * @return
     */
    default AgentToolSpec getSpec() {
        return AgentToolSpec.builder()
                .name(getName())
                .description(getDescription())
                .parameters(getParameters())
                .build();
    }
}
