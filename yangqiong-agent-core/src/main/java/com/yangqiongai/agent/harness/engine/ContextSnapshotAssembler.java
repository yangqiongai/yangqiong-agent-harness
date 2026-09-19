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
package com.yangqiongai.agent.harness.engine;

import java.util.ArrayList;
import java.util.List;

import com.yangqiongai.agent.harness.core.message.AgentMessage;
import com.yangqiongai.agent.harness.core.message.AgentMessageRole;
import com.yangqiongai.agent.harness.model.ContextMessage;
import com.yangqiongai.agent.harness.model.ContextSnapshot;

/**
 * 上下文快照组装
 * <p>
 * 将引擎组装好的上下文消息转换为带来源标注的快照，头部连续system消息中
 * 最后一条为系统提示词、其余为记忆注入（记忆中间件按前插方式注入），
 * tool角色标注为工具结果，其余标注为入参历史。单条内容超长时截断并置位标记。
 * </p>
 * @author yangqiong
 */
public final class ContextSnapshotAssembler {

    /**
     * 追踪ID的运行时上下文属性键
     */
    private static final String ATTR_TRACE_ID = "harness.traceId";

    /**
     * 任务ID的运行时上下文属性键
     */
    private static final String ATTR_TASK_ID = "taskId";

    /**
     * 代理编码的运行时上下文属性键
     */
    private static final String ATTR_AGENT_CODE = "agentCode";

    private ContextSnapshotAssembler() {
    }

    /**
     * 组装上下文快照
     * @param context 引擎上下文
     * @param messages 本次调用组装好的上下文消息
     * @param modelCode 本次调用的模型编码
     * @param callSeq 调用序号（从1开始）
     * @param maxChars 单条消息内容最大字符数，超过则截断
     * @return
     */
    public static ContextSnapshot build(EngineContext context, List<AgentMessage> messages,
                                        String modelCode, int callSeq, int maxChars) {
        List<ContextMessage> converted = convert(messages, maxChars);
        return new ContextSnapshot(
                attr(context, ATTR_TASK_ID),
                attr(context, ATTR_TRACE_ID),
                context.getRuntimeContext().getSessionId(),
                attr(context, ATTR_AGENT_CODE),
                context.getRuntimeContext().getScopeId(),
                modelCode,
                callSeq,
                converted);
    }

    /**
     * 转换引擎消息为带来源标注的快照消息
     * @param messages 引擎消息列表
     * @param maxChars 单条消息内容最大字符数
     * @return
     */
    static List<ContextMessage> convert(List<AgentMessage> messages, int maxChars) {
        List<ContextMessage> result = new ArrayList<>(messages.size());
        int leadingSystemEnd = leadingSystemBlockEnd(messages);
        for (int i = 0; i < messages.size(); i++) {
            AgentMessage message = messages.get(i);
            result.add(toContextMessage(message, i, leadingSystemEnd, maxChars));
        }
        return result;
    }

    /**
     * 定位头部连续system消息块的结束下标（不含），无头部system消息时返回0
     * @param messages 引擎消息列表
     * @return
     */
    private static int leadingSystemBlockEnd(List<AgentMessage> messages) {
        int end = 0;
        while (end < messages.size() && messages.get(end).getRole() == AgentMessageRole.SYSTEM) {
            end++;
        }
        return end;
    }

    /**
     * 单条消息转换为快照消息并按来源标注
     * @param message 引擎消息
     * @param index 消息下标
     * @param leadingSystemEnd 头部system块结束下标
     * @param maxChars 单条消息内容最大字符数
     * @return
     */
    private static ContextMessage toContextMessage(AgentMessage message, int index,
                                                   int leadingSystemEnd, int maxChars) {
        String source;
        if (message.getRole() == AgentMessageRole.TOOL) {
            source = ContextMessage.SOURCE_TOOL_RESULT;
        } else if (message.getRole() == AgentMessageRole.SYSTEM) {
            // 头部system块内最后一条是系统提示词，更早的是记忆中间件前插的记忆消息
            source = (index < leadingSystemEnd - 1)
                    ? ContextMessage.SOURCE_MEMORY_INJECT
                    : ContextMessage.SOURCE_SYSTEM_PROMPT;
        } else {
            source = ContextMessage.SOURCE_HISTORY;
        }
        String content = message.getTextContent() != null ? message.getTextContent() : "";
        if (content.length() > maxChars) {
            return new ContextMessage(message.getRole().name(), content.substring(0, maxChars), source, true);
        }
        return new ContextMessage(message.getRole().name(), content, source, false);
    }

    /**
     * 读取运行时上下文字符串属性，缺失时返回null
     * @param context 引擎上下文
     * @param key 属性键
     * @return
     */
    private static String attr(EngineContext context, String key) {
        Object value = context.getRuntimeContext().get(key);
        return value != null ? String.valueOf(value) : null;
    }
}
