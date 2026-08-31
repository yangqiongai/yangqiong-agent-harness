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
package com.yangqiong.agent.harness.core.event;

import java.util.Objects;

/**
 * Agent思考块增量事件
 * @author yangqiong
 */
public final class AgentThinkingBlockDeltaEvent extends AgentEvent {

    /**
     * 思考增量内容
     */
    private final String delta;

    public AgentThinkingBlockDeltaEvent(String delta) {
        super(AgentEventType.THINKING_BLOCK_DELTA, delta);
        this.delta = delta;
    }

    /**
     * 获取思考增量内容
     * @return
     */
    public String getDelta() {
        return delta;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        if (!super.equals(o)) return false;
        AgentThinkingBlockDeltaEvent that = (AgentThinkingBlockDeltaEvent) o;
        return Objects.equals(delta, that.delta);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), delta);
    }

    @Override
    public String toString() {
        return "AgentThinkingBlockDeltaEvent{delta='" + delta + "'}";
    }
}
