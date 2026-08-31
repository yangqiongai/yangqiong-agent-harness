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
package com.yangqiong.agent.harness.middleware;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;

import com.yangqiong.agent.harness.engine.AgentRuntimeContext;
import com.yangqiong.agent.harness.config.AgentCompactionConfig;
import com.yangqiong.agent.harness.core.event.AgentEvent;
import com.yangqiong.agent.harness.core.message.AgentContentBlock;
import com.yangqiong.agent.harness.core.message.AgentMessage;
import com.yangqiong.agent.harness.core.message.AgentMessageRole;
import com.yangqiong.agent.harness.core.message.AgentToolResultBlock;
import com.yangqiong.agent.harness.core.message.AgentToolUseBlock;
import com.yangqiong.agent.harness.core.middleware.AgentMiddleware;
import reactor.core.publisher.Flux;

/**
 * 上下文压缩中间件
 * @author yangqiong
 */
public class CompactionMiddleware implements AgentMiddleware {

    /**
     * 压缩配置
     */
    private final AgentCompactionConfig config;

    public CompactionMiddleware(AgentCompactionConfig config) {
        this.config = config != null ? config : AgentCompactionConfig.defaults();
    }

    /**
     * 推理阶段前检查是否需要压缩
     * @param context
     * @param input
     * @param next
     * @return
     */
    @Override
    public Flux<AgentEvent> onReasoning(AgentRuntimeContext context, List<AgentMessage> input,
                                         Function<List<AgentMessage>, Flux<AgentEvent>> next) {
        List<AgentMessage> messages = input;
        if (input != null && input.size() > config.getTriggerMessages()) {
            messages = compact(input);
        }
        return next.apply(messages);
    }

    /**
     * 压缩历史消息
     * @param messages
     * @return
     */
    private List<AgentMessage> compact(List<AgentMessage> messages) {
        int keepCount = config.getKeepMessages();
        int total = messages.size();
        if (total <= keepCount) {
            return messages;
        }
        List<AgentMessage> result = new ArrayList<>();
        boolean hasSystemHead = !messages.isEmpty() && messages.get(0).getRole() == AgentMessageRole.SYSTEM;
        int from;
        if (hasSystemHead) {
            result.add(messages.get(0));
            from = total - keepCount + 1;
            if (from < 1) {
                from = 1;
            }
        } else {
            from = total - keepCount;
            if (from < 0) {
                from = 0;
            }
        }
        int floor = hasSystemHead ? 1 : 0;
        // 边界回退：起点落在TOOL段中间时回退到该段assistant起点，保持工具调用与结果完整配对
        while (from > floor && messages.get(from).getRole() == AgentMessageRole.TOOL) {
            from--;
        }
        // 无法回退时前跳孤立工具结果消息，避免OpenAI因缺少配对的tool_call_id而报错
        while (from < total && messages.get(from).getRole() == AgentMessageRole.TOOL) {
            from++;
        }
        // 前跳保留段内tool_use无结果配对的assistant消息（工具结果缺失场景）
        Set<String> resultIds = collectToolResultIds(messages, from, total);
        while (from < total && hasUnpairedToolUse(messages.get(from), resultIds)) {
            from++;
        }
        if (from < total) {
            result.addAll(messages.subList(from, total));
        }
        return result;
    }

    /**
     * 收集保留段内所有工具结果消息的toolUseId
     * @param messages
     * @param from
     * @param total
     * @return
     */
    private Set<String> collectToolResultIds(List<AgentMessage> messages, int from, int total) {
        Set<String> ids = new HashSet<>();
        for (int i = from; i < total; i++) {
            List<AgentContentBlock> content = messages.get(i).getContent();
            if (content == null) {
                continue;
            }
            for (AgentContentBlock block : content) {
                if (block instanceof AgentToolResultBlock result && result.getToolUseId() != null) {
                    ids.add(result.getToolUseId());
                }
            }
        }
        return ids;
    }

    /**
     * 判断消息是否包含保留段内无结果配对的工具调用
     * @param message
     * @param resultIds
     * @return
     */
    private boolean hasUnpairedToolUse(AgentMessage message, Set<String> resultIds) {
        if (message.getRole() != AgentMessageRole.ASSISTANT || message.getContent() == null) {
            return false;
        }
        for (AgentContentBlock block : message.getContent()) {
            if (block instanceof AgentToolUseBlock use
                    && use.getToolUseId() != null && !resultIds.contains(use.getToolUseId())) {
                return true;
            }
        }
        return false;
    }
}
