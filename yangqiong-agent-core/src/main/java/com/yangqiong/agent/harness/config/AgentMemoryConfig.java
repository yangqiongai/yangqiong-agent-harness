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
package com.yangqiong.agent.harness.config;

/**
 * Agent记忆配置
 * <p>
 * 仅承载L1上下文管理策略：消息压缩（{@link AgentCompactionConfig}）与工具结果驱逐
 * （{@link AgentToolResultEvictionConfig}）。
 * </p>
 * <p>
 * L2短期记忆（会话摘要/情节）与L3长期记忆（向量库/知识图谱）的运行时实现
 * 属于SPI扩展，不在此配置类中声明，而通过 {@code HarnessRuntimeBuilder}
 * 的 {@code sessionMemory(SessionMemory)} 与 {@code longTermMemory(AgentLongTermMemory)}
 * 注入；本配置类仅决定压缩/驱逐阈值，不混淆"配置"与"运行时实现"边界。
 * </p>
 * @author yangqiong
 */
public final class AgentMemoryConfig {

    private final AgentCompactionConfig compactionConfig;

    private final AgentToolResultEvictionConfig toolResultEvictionConfig;

    private AgentMemoryConfig(AgentCompactionConfig compactionConfig,
                              AgentToolResultEvictionConfig toolResultEvictionConfig) {
        this.compactionConfig = compactionConfig;
        this.toolResultEvictionConfig = toolResultEvictionConfig;
    }

    /**
     * 创建默认记忆配置
     * @return
     */
    public static AgentMemoryConfig defaults() {
        return new AgentMemoryConfig(AgentCompactionConfig.defaults(), null);
    }

    public static Builder builder() {
        return new Builder();
    }

    /**
     * 获取消息压缩配置
     * @return
     */
    public AgentCompactionConfig getCompactionConfig() {
        return compactionConfig;
    }

    /**
     * 获取工具结果驱逐配置
     * @return
     */
    public AgentToolResultEvictionConfig getToolResultEvictionConfig() {
        return toolResultEvictionConfig;
    }

    @Override
    public String toString() {
        return "AgentMemoryConfig{compactionConfig=" + compactionConfig
                + ", toolResultEvictionConfig=" + toolResultEvictionConfig + "}";
    }

    /**
     * 记忆配置构建器
     * @author yangqiong
     */
    public static class Builder {

        private AgentCompactionConfig compactionConfig;

        private AgentToolResultEvictionConfig toolResultEvictionConfig;

        public Builder compactionConfig(AgentCompactionConfig compactionConfig) {
            this.compactionConfig = compactionConfig;
            return this;
        }

        public Builder toolResultEvictionConfig(AgentToolResultEvictionConfig toolResultEvictionConfig) {
            this.toolResultEvictionConfig = toolResultEvictionConfig;
            return this;
        }

        public AgentMemoryConfig build() {
            return new AgentMemoryConfig(compactionConfig, toolResultEvictionConfig);
        }
    }
}