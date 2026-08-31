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

import java.time.Duration;
import java.util.List;
import java.util.Set;

/**
 * 子代理能力快照
 * <p>
 * 携带父级运行时的能力配置快照，供SubagentSpawner克隆生成子代理运行时。
 * 不含mcpClients（MCP已包装进toolkit）和workspace（当前为空实现）。
 * </p>
 * @author yangqiong
 */
public class SubagentContext {

    private final AgentToolkit toolkit;

    private final AgentSkillBox skillBox;

    private final List<AgentMiddleware> middlewares;

    private final AgentMemoryConfig memoryConfig;

    private final AgentCompactionConfig compactionConfig;

    private final AgentToolResultEvictionConfig evictionConfig;

    private final AgentPermissionContextState permissionState;

    private final Duration timeout;

    private final Duration iterationTimeout;

    private final int maxConcurrentToolCalls;

    private final Set<String> allowedTools;

    private final Set<String> deniedTools;

    private final Set<String> requireApproval;

    private final HarnessAgentRuntimeBuilder.ToolFailureStrategy toolFailureStrategy;

    private final int maxToolRetries;

    private final int maxContextTokens;

    private final HarnessAgentRuntimeBuilder.TruncationStrategy historyTruncationStrategy;

    private final AgentGenerateOptions generateOptions;

    private final AgentToolChoice toolChoice;

    private SubagentContext(Builder b) {
        this.toolkit = b.toolkit;
        this.skillBox = b.skillBox;
        this.middlewares = b.middlewares;
        this.memoryConfig = b.memoryConfig;
        this.compactionConfig = b.compactionConfig;
        this.evictionConfig = b.evictionConfig;
        this.permissionState = b.permissionState;
        this.timeout = b.timeout;
        this.iterationTimeout = b.iterationTimeout;
        this.maxConcurrentToolCalls = b.maxConcurrentToolCalls;
        this.allowedTools = b.allowedTools;
        this.deniedTools = b.deniedTools;
        this.requireApproval = b.requireApproval;
        this.toolFailureStrategy = b.toolFailureStrategy;
        this.maxToolRetries = b.maxToolRetries;
        this.maxContextTokens = b.maxContextTokens;
        this.historyTruncationStrategy = b.historyTruncationStrategy;
        this.generateOptions = b.generateOptions;
        this.toolChoice = b.toolChoice;
    }

    /**
     * 从HarnessRuntimeBuilder捕获能力快照
     * @param builder
     * @return
     */
    public static SubagentContext capture(RuntimeCapabilityAccessor builder) {
        Builder b = new Builder();
        b.toolkit = builder.getToolkit();
        b.skillBox = builder.getSkillBox();
        b.middlewares = builder.getMiddlewares();
        b.memoryConfig = builder.getMemoryConfig();
        b.compactionConfig = builder.getCompactionConfig();
        b.evictionConfig = builder.getEvictionConfig();
        b.permissionState = builder.getPermissionState();
        b.timeout = builder.getTimeout();
        b.iterationTimeout = builder.getIterationTimeout();
        b.maxConcurrentToolCalls = builder.getMaxConcurrentToolCalls();
        b.allowedTools = builder.getAllowedTools();
        b.deniedTools = builder.getDeniedTools();
        b.requireApproval = builder.getRequireApproval();
        b.toolFailureStrategy = builder.getToolFailureStrategy();
        b.maxToolRetries = builder.getMaxToolRetries();
        b.maxContextTokens = builder.getMaxContextTokens();
        b.historyTruncationStrategy = builder.getHistoryTruncationStrategy();
        b.generateOptions = builder.getGenerateOptions();
        b.toolChoice = builder.getToolChoice();
        return b.build();
    }

    /**
     * 将快照配置写入目标builder（角色字段不覆盖）
     * @param builder
     */
    public void applyTo(RuntimeCapabilityAccessor builder) {
        if (toolkit != null) {
            builder.toolkit(toolkit);
        }
        if (skillBox != null) {
            builder.skillBox(skillBox);
        }
        if (middlewares != null) {
            for (AgentMiddleware mw : middlewares) {
                builder.middleware(mw);
            }
        }
        if (memoryConfig != null) {
            builder.memoryConfig(memoryConfig);
        }
        if (compactionConfig != null) {
            builder.compactionConfig(compactionConfig);
        }
        if (evictionConfig != null) {
            builder.toolResultEvictionConfig(evictionConfig);
        }
        if (permissionState != null) {
            builder.permissionContextState(permissionState);
        }
        if (timeout != null) {
            builder.timeout(timeout);
        }
        if (iterationTimeout != null) {
            builder.iterationTimeout(iterationTimeout);
        }
        builder.maxConcurrentToolCalls(maxConcurrentToolCalls);
        if (allowedTools != null) {
            builder.allowedTools(allowedTools);
        }
        if (deniedTools != null) {
            builder.deniedTools(deniedTools);
        }
        if (requireApproval != null) {
            builder.requireApproval(requireApproval);
        }
        if (toolFailureStrategy != null) {
            builder.toolFailureStrategy(toolFailureStrategy);
        }
        builder.maxToolRetries(maxToolRetries);
        builder.maxContextTokens(maxContextTokens);
        if (historyTruncationStrategy != null) {
            builder.historyTruncationStrategy(historyTruncationStrategy);
        }
        if (generateOptions != null) {
            builder.generateOptions(generateOptions);
        }
        if (toolChoice != null) {
            builder.toolChoice(toolChoice);
        }
    }

    public AgentToolkit getToolkit() {
        return toolkit;
    }

    public AgentSkillBox getSkillBox() {
        return skillBox;
    }

    public List<AgentMiddleware> getMiddlewares() {
        return middlewares;
    }

    public AgentMemoryConfig getMemoryConfig() {
        return memoryConfig;
    }

    public AgentCompactionConfig getCompactionConfig() {
        return compactionConfig;
    }

    public AgentToolResultEvictionConfig getEvictionConfig() {
        return evictionConfig;
    }

    public AgentPermissionContextState getPermissionState() {
        return permissionState;
    }

    public Duration getTimeout() {
        return timeout;
    }

    public Duration getIterationTimeout() {
        return iterationTimeout;
    }

    public int getMaxConcurrentToolCalls() {
        return maxConcurrentToolCalls;
    }

    public Set<String> getAllowedTools() {
        return allowedTools;
    }

    public Set<String> getDeniedTools() {
        return deniedTools;
    }

    public Set<String> getRequireApproval() {
        return requireApproval;
    }

    public HarnessAgentRuntimeBuilder.ToolFailureStrategy getToolFailureStrategy() {
        return toolFailureStrategy;
    }

    public int getMaxToolRetries() {
        return maxToolRetries;
    }

    public int getMaxContextTokens() {
        return maxContextTokens;
    }

    public HarnessAgentRuntimeBuilder.TruncationStrategy getHistoryTruncationStrategy() {
        return historyTruncationStrategy;
    }

    public AgentGenerateOptions getGenerateOptions() {
        return generateOptions;
    }

    public AgentToolChoice getToolChoice() {
        return toolChoice;
    }

    /**
     * SubagentContext构建器
     */
    private static class Builder {

        private AgentToolkit toolkit;

        private AgentSkillBox skillBox;

        private List<AgentMiddleware> middlewares;

        private AgentMemoryConfig memoryConfig;

        private AgentCompactionConfig compactionConfig;

        private AgentToolResultEvictionConfig evictionConfig;

        private AgentPermissionContextState permissionState;

        private Duration timeout;

        private Duration iterationTimeout;

        private int maxConcurrentToolCalls = 1;

        private Set<String> allowedTools;

        private Set<String> deniedTools;

        private Set<String> requireApproval;

        private HarnessAgentRuntimeBuilder.ToolFailureStrategy toolFailureStrategy;

        private int maxToolRetries;

        private int maxContextTokens;

        private HarnessAgentRuntimeBuilder.TruncationStrategy historyTruncationStrategy;

        private AgentGenerateOptions generateOptions;

        private AgentToolChoice toolChoice;

        SubagentContext build() {
            return new SubagentContext(this);
        }
    }
}
