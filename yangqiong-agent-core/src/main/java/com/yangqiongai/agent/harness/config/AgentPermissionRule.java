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
package com.yangqiongai.agent.harness.config;

import java.util.Objects;

/**
 * Agent权限规则
 * @author yangqiong
 */
public final class AgentPermissionRule {

    private final String toolName;

    private final AgentPermissionMode mode;

    private AgentPermissionRule(String toolName, AgentPermissionMode mode) {
        this.toolName = toolName;
        this.mode = mode;
    }

    public static Builder builder() {
        return new Builder();
    }

    /**
     * 获取工具名称
     * @return
     */
    public String getToolName() {
        return toolName;
    }

    /**
     * 获取权限模式
     * @return
     */
    public AgentPermissionMode getMode() {
        return mode;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        AgentPermissionRule that = (AgentPermissionRule) o;
        return Objects.equals(toolName, that.toolName) && mode == that.mode;
    }

    @Override
    public int hashCode() {
        return Objects.hash(toolName, mode);
    }

    @Override
    public String toString() {
        return "AgentPermissionRule{toolName='" + toolName + "', mode=" + mode + "}";
    }

    /**
     * 权限规则构建器
     * @author yangqiong
     */
    public static class Builder {

        private String toolName;

        private AgentPermissionMode mode;

        public Builder toolName(String toolName) {
            this.toolName = toolName;
            return this;
        }

        public Builder mode(AgentPermissionMode mode) {
            this.mode = mode;
            return this;
        }

        public AgentPermissionRule build() {
            return new AgentPermissionRule(toolName, mode);
        }
    }
}