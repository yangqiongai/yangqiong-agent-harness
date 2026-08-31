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
package com.yangqiong.agent.harness.core.interruption;

import com.yangqiong.agent.harness.core.message.AgentMessage;

/**
 * 内存中断控制
 * <p>
 * 基于原子布尔标志记录中断状态，线程安全，适用于测试与快速原型。
 * </p>
 * @author yangqiong
 */
public class SimpleAgentInterruptControl implements AgentInterruptControl {

    /**
     * 中断状态标志
     */
    private final java.util.concurrent.atomic.AtomicBoolean interrupted = new java.util.concurrent.atomic.AtomicBoolean(false);

    /**
     * 最近一次中断来源
     */
    private volatile AgentInterruptSource lastSource;

    /**
     * 最近一次中断消息
     */
    private volatile AgentMessage lastMessage;

    /**
     * 触发中断，记录来源与消息并置位中断状态
     * @param source
     * @param message
     */
    @Override
    public void trigger(AgentInterruptSource source, AgentMessage message) {
        this.lastSource = source;
        this.lastMessage = message;
        interrupted.set(true);
    }

    /**
     * 查询是否已被中断
     * @return
     */
    @Override
    public boolean isInterrupted() {
        return interrupted.get();
    }

    /**
     * 重置中断状态，清空记录
     */
    @Override
    public void reset() {
        interrupted.set(false);
        this.lastSource = null;
        this.lastMessage = null;
    }

    /**
     * 最近一次中断来源，未中断时为null
     * @return
     */
    public AgentInterruptSource getLastSource() {
        return lastSource;
    }

    /**
     * 最近一次中断消息，未中断时为null
     * @return
     */
    public AgentMessage getLastMessage() {
        return lastMessage;
    }
}