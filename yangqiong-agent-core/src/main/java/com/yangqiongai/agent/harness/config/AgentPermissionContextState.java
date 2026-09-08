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

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Agent权限上下文状态
 * @author yangqiong
 */
public final class AgentPermissionContextState {

    private final AgentPermissionMode mode;

    private final List<AgentPermissionRule> rules;

    private AgentPermissionContextState(AgentPermissionMode mode, List<AgentPermissionRule> rules) {
        this.mode = mode;
        this.rules = rules != null ? List.copyOf(rules) : List.of();
    }

    public static Builder builder() {
        return new Builder();
    }

    /**
     * 获取权限模式
     * @return
     */
    public AgentPermissionMode getMode() {
        return mode;
    }

    /**
     * 获取权限规则列表
     * @return
     */
    public List<AgentPermissionRule> getRules() {
        return rules;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        AgentPermissionContextState that = (AgentPermissionContextState) o;
        return mode == that.mode && Objects.equals(rules, that.rules);
    }

    @Override
    public int hashCode() {
        return Objects.hash(mode, rules);
    }

    @Override
    public String toString() {
        return "AgentPermissionContextState{mode=" + mode + ", ruleCount=" + (rules != null ? rules.size() : 0) + "}";
    }

    /**
     * 权限上下文状态构建器
     * @author yangqiong
     */
    public static class Builder {

        private AgentPermissionMode mode;

        private List<AgentPermissionRule> rules = new ArrayList<>();

        public Builder mode(AgentPermissionMode mode) {
            this.mode = mode;
            return this;
        }

        public Builder rules(List<AgentPermissionRule> rules) {
            this.rules = rules != null ? new ArrayList<>(rules) : new ArrayList<>();
            return this;
        }

        public AgentPermissionContextState build() {
            return new AgentPermissionContextState(mode, rules);
        }
    }
}