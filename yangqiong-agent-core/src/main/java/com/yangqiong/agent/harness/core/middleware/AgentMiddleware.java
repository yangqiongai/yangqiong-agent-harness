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
package com.yangqiong.agent.harness.core.middleware;

import com.yangqiong.agent.harness.engine.AgentRuntimeContext;
import com.yangqiong.agent.harness.core.message.AgentMessage;
import com.yangqiong.agent.harness.core.message.AgentToolResultBlock;

import java.util.Map;

/**
 * Agent中间件
 * <p>
 * 继承{@link AgentReactiveMiddleware}的洋葱模型响应式钩子，并补充同步钩子。
 * 所有方法均有默认透传实现，实现类按需覆写即可。
 * </p>
 * @author yangqiong
 */
public interface AgentMiddleware extends AgentReactiveMiddleware {

    /**
     * 系统提示词处理钩子，返回可能修改后的提示词
     * @param systemPrompt
     * @param context
     * @return
     */
    default String onSystemPrompt(String systemPrompt, AgentRuntimeContext context) {
        return systemPrompt;
    }

    /**
     * 工具调用前拦截钩子，返回可能修改后的输入
     * @param toolName
     * @param input
     * @param context
     * @return
     */
    default Map<String, Object> onToolCall(String toolName, Map<String, Object> input, AgentRuntimeContext context) {
        return input;
    }

    /**
     * 工具调用后拦截钩子，返回可能修改后的结果
     * @param toolName
     * @param result
     * @param context
     * @return
     */
    default AgentToolResultBlock onToolResult(String toolName, AgentToolResultBlock result, AgentRuntimeContext context) {
        return result;
    }

    /**
     * 消息处理钩子，返回可能修改后的消息
     * @param message
     * @param context
     * @return
     */
    default AgentMessage onMessage(AgentMessage message, AgentRuntimeContext context) {
        return message;
    }

    /**
     * 错误处理钩子，由provider在reactive流程的onErrorResume中调用
     * <p>
     * phase取值：agent / reasoning / acting / modelCall，用于区分错误发生阶段。
     * 实现方可根据phase决定日志级别或特殊处理（如TOOL_FAILURE仅warn）。
     * </p>
     * @param throwable
     * @param context
     * @param phase
     */
    default void onError(Throwable throwable, AgentRuntimeContext context, String phase) {
    }
}
