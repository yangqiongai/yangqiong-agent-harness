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

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import com.yangqiongai.agent.harness.core.event.AgentEvent;
import com.yangqiongai.agent.harness.core.message.AgentMessage;
import com.yangqiongai.agent.harness.core.message.AgentToolResultBlock;
import com.yangqiongai.agent.harness.core.message.AgentToolUseBlock;
import com.yangqiongai.agent.harness.core.middleware.AgentMiddleware;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * 中间件链
 * @author yangqiong
 */
public class MiddlewareChain {

    /**
     * 中间件列表
     */
    private final List<AgentMiddleware> middlewares;

    public MiddlewareChain(List<AgentMiddleware> middlewares) {
        this.middlewares = middlewares != null ? middlewares : Collections.emptyList();
    }

    /**
     * 系统提示词同步钩子，依次传递给每个中间件
     * @param prompt
     * @param context
     * @return
     */
    public String applyOnSystemPrompt(String prompt, AgentRuntimeContext context) {
        String result = prompt;
        for (AgentMiddleware middleware : middlewares) {
            result = middleware.onSystemPrompt(result, context);
        }
        return result;
    }

    /**
     * 消息同步钩子，依次传递给每个中间件
     * @param message
     * @param context
     * @return
     */
    public AgentMessage applyOnMessage(AgentMessage message, AgentRuntimeContext context) {
        AgentMessage result = message;
        for (AgentMiddleware middleware : middlewares) {
            result = middleware.onMessage(result, context);
        }
        return result;
    }

    /**
     * 工具调用前同步钩子，依次传递给每个中间件
     * @param toolName
     * @param input
     * @param context
     * @return
     */
    public Map<String, Object> applyOnToolCall(String toolName, Map<String, Object> input, AgentRuntimeContext context) {
        Map<String, Object> result = input;
        for (AgentMiddleware middleware : middlewares) {
            result = middleware.onToolCall(toolName, result, context);
        }
        return result;
    }

    /**
     * 工具结果同步钩子，依次传递给每个中间件
     * @param toolName
     * @param result
     * @param context
     * @return
     */
    public AgentToolResultBlock applyOnToolResult(String toolName, AgentToolResultBlock result, AgentRuntimeContext context) {
        AgentToolResultBlock current = result;
        for (AgentMiddleware middleware : middlewares) {
            current = middleware.onToolResult(toolName, current, context);
        }
        return current;
    }

    /**
     * 错误同步钩子，依次通知每个中间件
     * @param error
     * @param context
     * @param phase
     */
    public void applyOnError(Throwable error, AgentRuntimeContext context, String phase) {
        for (AgentMiddleware middleware : middlewares) {
            middleware.onError(error, context, phase);
        }
    }

    /**
     * Agent整体调用响应式钩子（洋葱模型，逆序包裹）
     * @param context
     * @param input
     * @param next
     * @return
     */
    public Flux<AgentEvent> applyOnAgent(AgentRuntimeContext context, List<AgentMessage> input,
                                          Function<List<AgentMessage>, Flux<AgentEvent>> next) {
        if (middlewares.isEmpty()) {
            return next.apply(input);
        }
        Function<List<AgentMessage>, Flux<AgentEvent>> chain = next;
        for (int i = middlewares.size() - 1; i >= 0; i--) {
            AgentMiddleware middleware = middlewares.get(i);
            Function<List<AgentMessage>, Flux<AgentEvent>> current = chain;
            chain = msgInput -> middleware.onAgent(context, msgInput, current);
        }
        return chain.apply(input);
    }

    /**
     * 推理阶段响应式钩子（洋葱模型，逆序包裹）
     * @param context
     * @param input
     * @param next
     * @return
     */
    public Flux<AgentEvent> applyOnReasoning(AgentRuntimeContext context, List<AgentMessage> input,
                                               Function<List<AgentMessage>, Flux<AgentEvent>> next) {
        if (middlewares.isEmpty()) {
            return next.apply(input);
        }
        Function<List<AgentMessage>, Flux<AgentEvent>> chain = next;
        for (int i = middlewares.size() - 1; i >= 0; i--) {
            AgentMiddleware middleware = middlewares.get(i);
            Function<List<AgentMessage>, Flux<AgentEvent>> current = chain;
            chain = msgInput -> middleware.onReasoning(context, msgInput, current);
        }
        return chain.apply(input);
    }

    /**
     * 执行阶段响应式钩子（洋葱模型，逆序包裹）
     * @param context
     * @param toolCalls
     * @param next
     * @return
     */
    public Flux<AgentEvent> applyOnActing(AgentRuntimeContext context, List<AgentToolUseBlock> toolCalls,
                                           Function<List<AgentToolUseBlock>, Flux<AgentEvent>> next) {
        if (middlewares.isEmpty()) {
            return next.apply(toolCalls);
        }
        Function<List<AgentToolUseBlock>, Flux<AgentEvent>> chain = next;
        for (int i = middlewares.size() - 1; i >= 0; i--) {
            AgentMiddleware middleware = middlewares.get(i);
            Function<List<AgentToolUseBlock>, Flux<AgentEvent>> current = chain;
            chain = calls -> middleware.onActing(context, calls, current);
        }
        return chain.apply(toolCalls);
    }

    /**
     * 模型调用响应式钩子（洋葱模型，逆序包裹）
     * @param context
     * @param input
     * @param next
     * @return
     */
    public Mono<AgentMessage> applyOnModelCall(AgentRuntimeContext context, List<AgentMessage> input,
                                                 Function<List<AgentMessage>, Mono<AgentMessage>> next) {
        if (middlewares.isEmpty()) {
            return next.apply(input);
        }
        Function<List<AgentMessage>, Mono<AgentMessage>> chain = next;
        for (int i = middlewares.size() - 1; i >= 0; i--) {
            AgentMiddleware middleware = middlewares.get(i);
            Function<List<AgentMessage>, Mono<AgentMessage>> current = chain;
            chain = msgInput -> middleware.onModelCall(context, msgInput, current);
        }
        return chain.apply(input);
    }
}
