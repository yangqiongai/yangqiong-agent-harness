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
package com.yangqiongai.agent.harness.core.event;

import com.yangqiongai.agent.harness.core.message.AgentToolUseBlock;

import java.util.List;

/**
 * 需要人工确认事件
 * @author yangqiong
 */
public class RequireUserConfirmEvent extends AgentEvent {

    /**
     * 待确认的工具调用列表
     */
    private final List<AgentToolUseBlock> pendingToolCalls;

    public RequireUserConfirmEvent(List<AgentToolUseBlock> pendingToolCalls) {
        super(AgentEventType.REQUIRE_USER_CONFIRM, pendingToolCalls);
        this.pendingToolCalls = pendingToolCalls;
    }

    /**
     * 获取待确认的工具调用列表
     * @return
     */
    public List<AgentToolUseBlock> getPendingToolCalls() {
        return pendingToolCalls;
    }
}
