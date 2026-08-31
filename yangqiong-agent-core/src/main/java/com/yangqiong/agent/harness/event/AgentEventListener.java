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
package com.yangqiong.agent.harness.event;

import com.yangqiong.agent.harness.core.event.AgentEvent;
import com.yangqiong.agent.harness.core.event.AgentEventType;

/**
 * Agent事件监听器
 * <p>
 * 通过{@link EventBus}注册后，运行时所有Agent事件自动广播给监听器，
 * 供告警通知、计费统计、审计下沉等旁路消费，无需自建中间件。
 * </p>
 * @author yangqiong
 */
public interface AgentEventListener {

    /**
     * 是否对该类型事件感兴趣，默认监听全部事件
     * @param type
     * @return
     */
    default boolean isInterestedIn(AgentEventType type) {
        return true;
    }

    /**
     * 处理Agent事件
     * @param event
     */
    void onEvent(AgentEvent event);
}