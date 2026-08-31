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
package com.yangqiong.agent.harness.memory;

import com.yangqiong.agent.harness.engine.AgentRuntimeContext;
import com.yangqiong.agent.harness.core.message.AgentMessage;

import java.util.ArrayList;
import java.util.List;

/**
 * 历史消息合并器
 * <p>
 * 将上下文中预加载的历史消息合并到输入列表前面，
 * 用于会话持久化恢复场景。
 * </p>
 * @author yangqiong
 */
public final class HistoryMerger {

    /**
     * 属性键：预加载的历史消息
     */
    public static final String ATTR_HISTORY_MESSAGES = "harness.historyMessages";

    private HistoryMerger() {
    }

    /**
     * 合并预加载的历史消息到输入列表前面
     * @param inputs 当前输入消息列表
     * @param context 运行时上下文，包含可能的历史消息属性
     * @return 合并后的消息列表，无历史消息时返回原列表
     */
    @SuppressWarnings("unchecked")
    public static List<AgentMessage> merge(List<AgentMessage> inputs, AgentRuntimeContext context) {
        if (context == null || context.getAttributes() == null) {
            return inputs;
        }
        Object historyObj = context.getAttributes().get(ATTR_HISTORY_MESSAGES);
        if (!(historyObj instanceof List<?> list)) {
            return inputs;
        }
        List<AgentMessage> history = new ArrayList<>();
        for (Object item : list) {
            if (item instanceof AgentMessage msg) {
                history.add(msg);
            }
        }
        if (history.isEmpty()) {
            return inputs;
        }
        List<AgentMessage> combined = new ArrayList<>(history.size() + inputs.size());
        combined.addAll(history);
        combined.addAll(inputs);
        return combined;
    }
}
