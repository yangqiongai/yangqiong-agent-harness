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
package com.yangqiongai.agent.harness.core;

import com.yangqiongai.agent.harness.config.AgentCompactionConfig;
import com.yangqiongai.agent.harness.config.AgentMemoryConfig;
import com.yangqiongai.agent.harness.config.AgentPermissionContextState;
import com.yangqiongai.agent.harness.config.AgentPermissionMode;
import com.yangqiongai.agent.harness.config.AgentPermissionRule;
import com.yangqiongai.agent.harness.config.AgentResponseFormat;
import com.yangqiongai.agent.harness.config.AgentToolChoice;
import com.yangqiongai.agent.harness.config.AgentToolResultEvictionConfig;
import com.yangqiongai.agent.harness.core.skill.AgentSkillBox;

/**
 * Agent运行时构建器（高级）
 * <p>
 * 扩展基础构建器，提供技能箱、响应格式、权限、记忆、压缩等高级配置。
 * 大多数运行时实现应支持这些配置项。
 * </p>
 * @author yangqiong
 */
public interface AdvancedAgentRuntimeBuilder extends AgentRuntimeBuilder {

    /**
     * 设置技能箱
     * @param skillBox
     * @return
     */
    AdvancedAgentRuntimeBuilder skillBox(AgentSkillBox skillBox);

    /**
     * 设置响应格式
     * @param format
     * @return
     */
    AdvancedAgentRuntimeBuilder responseFormat(AgentResponseFormat format);

    /**
     * 设置结构化输出类型
     * @param type
     * @return
     */
    AdvancedAgentRuntimeBuilder structuredOutputType(Class<?> type);

    /**
     * 设置权限模式与规则
     * @param mode
     * @param rule
     * @return
     */
    AdvancedAgentRuntimeBuilder permission(AgentPermissionMode mode, AgentPermissionRule rule);

    /**
     * 设置权限上下文状态
     * @param state
     * @return
     */
    AdvancedAgentRuntimeBuilder permissionContextState(AgentPermissionContextState state);

    /**
     * 设置记忆配置
     * @param config
     * @return
     */
    AdvancedAgentRuntimeBuilder memoryConfig(AgentMemoryConfig config);

    /**
     * 设置压缩配置
     * @param config
     * @return
     */
    AdvancedAgentRuntimeBuilder compactionConfig(AgentCompactionConfig config);

    /**
     * 设置工具结果驱逐配置
     * @param config
     * @return
     */
    AdvancedAgentRuntimeBuilder toolResultEvictionConfig(AgentToolResultEvictionConfig config);

    /**
     * 设置工具选择策略
     * @param choice
     * @return
     */
    AdvancedAgentRuntimeBuilder toolChoice(AgentToolChoice choice);
}
