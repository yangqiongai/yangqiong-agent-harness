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

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.yangqiong.agent.harness.core.event.AgentEvent;

/**
 * 事件监听器注册中心
 * <p>
 * 维护{@link AgentEventListener}列表，运行时通过反射式订阅将内部事件广播给监听器。
 * 监听器异常被隔离（不影响Agent主流程），注册/注销线程安全。
 * </p>
 * @author yangqiong
 */
public class EventBus {

    private static final Logger log = LoggerFactory.getLogger(EventBus.class);

    /**
     * 监听器列表
     */
    private final List<AgentEventListener> listeners = new CopyOnWriteArrayList<>();

    /**
     * 注册监听器
     * @param listener
     * @return
     */
    public EventBus register(AgentEventListener listener) {
        if (listener != null) {
            listeners.add(listener);
        }
        return this;
    }

    /**
     * 注销监听器
     * @param listener
     * @return
     */
    public EventBus unregister(AgentEventListener listener) {
        if (listener != null) {
            listeners.remove(listener);
        }
        return this;
    }

    /**
     * 已注册监听器数量
     * @return
     */
    public int listenerCount() {
        return listeners.size();
    }

    /**
     * 广播事件给全部感兴趣监听器，监听器异常隔离不影响其他监听器
     * @param event
     */
    public void publish(AgentEvent event) {
        if (event == null) {
            return;
        }
        for (AgentEventListener listener : listeners) {
            try {
                if (listener.isInterestedIn(event.getType())) {
                    listener.onEvent(event);
                }
            } catch (Exception e) {
                log.warn("[EventBus] 监听器处理异常已隔离: {}", listener.getClass().getSimpleName(), e);
            }
        }
    }
}