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
package com.yangqiongai.agent.harness.durable;

import java.util.ArrayList;
import java.util.List;

/**
 * Agent运行记录
 * <p>
 * 持久执行的最小运行单元：runId 全局唯一，scopeId 为租户标识，
 * 状态迁移受 {@link AgentRunState} 状态机约束并留有迁移历史。
 * </p>
 * @author yangqiong
 */
public class AgentRunRecord {

    /**
     * 运行ID
     */
    private final String runId;

    /**
     * 租户标识
     */
    private final String scopeId;

    /**
     * 会话ID
     */
    private final String sessionId;

    /**
     * 用户ID
     */
    private final String userId;

    /**
     * Agent名称
     */
    private final String agentName;

    /**
     * 创建时间戳
     */
    private final long createdAt;

    /**
     * 最近更新时间戳
     */
    private long updatedAt;

    /**
     * 乐观锁版本号
     */
    private long version;

    /**
     * 当前运行状态
     */
    private AgentRunState state;

    /**
     * 失败原因（终态FAILED时记录）
     */
    private String error;

    /**
     * 状态迁移历史
     */
    private final List<StateTransition> transitions;

    public AgentRunRecord(String runId, String scopeId, String sessionId, String userId, String agentName) {
        this.runId = runId;
        this.scopeId = scopeId;
        this.sessionId = sessionId;
        this.userId = userId;
        this.agentName = agentName;
        this.createdAt = System.currentTimeMillis();
        this.updatedAt = this.createdAt;
        this.version = 0L;
        this.state = AgentRunState.CREATED;
        this.transitions = new ArrayList<>();
        this.transitions.add(new StateTransition(this.state, this.createdAt, null));
    }

    /**
     * 迁移到目标状态，非法迁移抛出异常
     * @param target
     * @param reason
     * @return
     */
    public synchronized AgentRunRecord transitionTo(AgentRunState target, String reason) {
        if (!state.canTransitionTo(target)) {
            throw new IllegalStateException(
                    "非法状态迁移: " + state + " -> " + target + ", runId=" + runId);
        }
        this.state = target;
        this.updatedAt = System.currentTimeMillis();
        this.version++;
        if (target == AgentRunState.FAILED && reason != null) {
            this.error = reason;
        }
        this.transitions.add(new StateTransition(target, this.updatedAt, reason));
        return this;
    }

    /**
     * 尝试按期望版本做乐观锁替换
     * @param expectedVersion
     * @param updater
     * @return
     */
    public synchronized boolean casUpdate(long expectedVersion, Runnable updater) {
        if (this.version != expectedVersion) {
            return false;
        }
        updater.run();
        this.version++;
        this.updatedAt = System.currentTimeMillis();
        return true;
    }

    public String getRunId() {
        return runId;
    }

    public String getScopeId() {
        return scopeId;
    }

    public String getSessionId() {
        return sessionId;
    }

    public String getUserId() {
        return userId;
    }

    public String getAgentName() {
        return agentName;
    }

    public long getCreatedAt() {
        return createdAt;
    }

    public long getUpdatedAt() {
        return updatedAt;
    }

    public synchronized long getVersion() {
        return version;
    }

    public synchronized AgentRunState getState() {
        return state;
    }

    public synchronized String getError() {
        return error;
    }

    public synchronized List<StateTransition> getTransitions() {
        return new ArrayList<>(transitions);
    }

    /**
     * 状态迁移记录
     * @author yangqiong
     */
    public static class StateTransition {

        /**
         * 迁移后状态
         */
        private final AgentRunState state;

        /**
         * 迁移时间戳
         */
        private final long timestamp;

        /**
         * 迁移原因
         */
        private final String reason;

        StateTransition(AgentRunState state, long timestamp, String reason) {
            this.state = state;
            this.timestamp = timestamp;
            this.reason = reason;
        }

        public AgentRunState getState() {
            return state;
        }

        public long getTimestamp() {
            return timestamp;
        }

        public String getReason() {
            return reason;
        }
    }
}
