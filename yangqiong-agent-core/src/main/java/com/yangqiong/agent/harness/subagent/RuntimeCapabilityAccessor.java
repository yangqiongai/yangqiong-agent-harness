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
package com.yangqiong.agent.harness.subagent;

import com.yangqiong.agent.harness.core.HarnessAgentRuntimeBuilder;
import com.yangqiong.agent.harness.config.AgentCompactionConfig;
import com.yangqiong.agent.harness.config.AgentMemoryConfig;
import com.yangqiong.agent.harness.config.AgentPermissionContextState;
import com.yangqiong.agent.harness.config.AgentToolChoice;
import com.yangqiong.agent.harness.config.AgentToolResultEvictionConfig;
import com.yangqiong.agent.harness.core.middleware.AgentMiddleware;
import com.yangqiong.agent.harness.core.model.AgentGenerateOptions;
import com.yangqiong.agent.harness.core.skill.AgentSkillBox;
import com.yangqiong.agent.harness.core.tool.AgentToolkit;
import com.yangqiong.agent.harness.engine.AgentLoop;

import java.time.Duration;
import java.util.List;
import java.util.Set;

/**
 * 运行时能力快照访问器
 * <p>
 * 从构建器捕获能力配置,供子代理继承。
 * HarnessRuntimeBuilder需实现此接口以支持子代理能力继承。
 * </p>
 * @author yangqiong
 */
public interface RuntimeCapabilityAccessor extends HarnessAgentRuntimeBuilder {

    /**
     * 获取工具箱
     * @return
     */
    AgentToolkit getToolkit();

    /**
     * 获取技能箱
     * @return
     */
    AgentSkillBox getSkillBox();

    /**
     * 获取中间件列表
     * @return
     */
    List<AgentMiddleware> getMiddlewares();

    /**
     * 获取记忆配置
     * @return
     */
    AgentMemoryConfig getMemoryConfig();

    /**
     * 获取压缩配置
     * @return
     */
    AgentCompactionConfig getCompactionConfig();

    /**
     * 获取工具结果驱逐配置
     * @return
     */
    AgentToolResultEvictionConfig getEvictionConfig();

    /**
     * 获取权限上下文状态
     * @return
     */
    AgentPermissionContextState getPermissionState();

    /**
     * 获取总超时
     * @return
     */
    Duration getTimeout();

    /**
     * 获取单轮超时
     * @return
     */
    Duration getIterationTimeout();

    /**
     * 获取最大并行工具调用数
     * @return
     */
    int getMaxConcurrentToolCalls();

    /**
     * 获取工具白名单
     * @return
     */
    Set<String> getAllowedTools();

    /**
     * 获取工具黑名单
     * @return
     */
    Set<String> getDeniedTools();

    /**
     * 获取需审批工具集合
     * @return
     */
    Set<String> getRequireApproval();

    /**
     * 获取工具失败策略
     * @return
     */
    HarnessAgentRuntimeBuilder.ToolFailureStrategy getToolFailureStrategy();

    /**
     * 获取最大工具重试次数
     * @return
     */
    int getMaxToolRetries();

    /**
     * 获取上下文Token上限
     * @return
     */
    int getMaxContextTokens();

    /**
     * 获取历史截断策略
     * @return
     */
    HarnessAgentRuntimeBuilder.TruncationStrategy getHistoryTruncationStrategy();

    /**
     * 获取生成选项
     * @return
     */
    AgentGenerateOptions getGenerateOptions();

    /**
     * 获取工具选择策略
     * @return
     */
    AgentToolChoice getToolChoice();

    /**
     * 注入自定义Agent执行循环
     * @param loop 自定义执行循环实现
     * @return
     */
    RuntimeCapabilityAccessor agentLoop(AgentLoop loop);
}
