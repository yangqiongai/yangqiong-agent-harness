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
package com.yangqiong.agent.harness.durable;

/**
 * Agent运行状态
 * <p>
 * 统一运行生命周期状态机：CREATED→RUNNING→WAITING_APPROVAL→SUCCEEDED/FAILED/CANCELLED。
 * 终态不可再迁移，非法迁移由{@link AgentRunRecord}拒绝。
 * </p>
 * @author yangqiong
 */
public enum AgentRunState {

    /**
     * 已创建未启动
     */
    CREATED,

    /**
     * 运行中
     */
    RUNNING,

    /**
     * 等待人工审批
     */
    WAITING_APPROVAL,

    /**
     * 成功结束（终态）
     */
    SUCCEEDED,

    /**
     * 失败结束（终态）
     */
    FAILED,

    /**
     * 已取消（终态）
     */
    CANCELLED;

    /**
     * 判断是否为终态
     * @return
     */
    public boolean isTerminal() {
        return this == SUCCEEDED || this == FAILED || this == CANCELLED;
    }

    /**
     * 判断能否迁移到目标状态
     * @param target
     * @return
     */
    public boolean canTransitionTo(AgentRunState target) {
        if (target == null || this == target) {
            return false;
        }
        if (isTerminal()) {
            return false;
        }
        switch (this) {
            case CREATED:
                return target == RUNNING || target == CANCELLED;
            case RUNNING:
                return target == WAITING_APPROVAL || target == SUCCEEDED
                        || target == FAILED || target == CANCELLED;
            case WAITING_APPROVAL:
                return target == RUNNING || target == CANCELLED;
            default:
                return false;
        }
    }
}
