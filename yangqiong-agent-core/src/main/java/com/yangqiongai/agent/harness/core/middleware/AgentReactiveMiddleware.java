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
package com.yangqiongai.agent.harness.core.middleware;

import com.yangqiongai.agent.harness.engine.AgentRuntimeContext;
import com.yangqiongai.agent.harness.core.event.AgentEvent;
import com.yangqiongai.agent.harness.core.message.AgentMessage;
import com.yangqiongai.agent.harness.core.message.AgentToolUseBlock;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.function.Function;

/**
 * Agent响应式中间件
 * <p>
 * 提供LLM调用前后的响应式拦截钩子，所有方法默认透传到下一层。
 * 仅需响应式拦截的组件可实现此接口，无需实现同步钩子。
 * </p>
 * @author yangqiong
 */
public interface AgentReactiveMiddleware {

    /**
     * Agent整体调用拦截（洋葱模型最外层）
     * <p>
     * 可用于全局追踪、限流、缓存等。默认透传到下一层。
     * </p>
     * @param context
     * @param input
     * @param next
     * @return
     */
    default Flux<AgentEvent> onAgent(AgentRuntimeContext context, List<AgentMessage> input,
                                      Function<List<AgentMessage>, Flux<AgentEvent>> next) {
        return next.apply(input);
    }

    /**
     * 推理阶段拦截（LLM调用前后）
     * <p>
     * 可用于Token计量、推理缓存、prompt改写等。默认透传到下一层。
     * </p>
     * @param context
     * @param input
     * @param next
     * @return
     */
    default Flux<AgentEvent> onReasoning(AgentRuntimeContext context, List<AgentMessage> input,
                                          Function<List<AgentMessage>, Flux<AgentEvent>> next) {
        return next.apply(input);
    }

    /**
     * 执行阶段拦截（批量工具调用）
     * <p>
     * 可用于并行控制、超时管理、权限校验等。默认透传到下一层。
     * </p>
     * @param context
     * @param toolCalls
     * @param next
     * @return
     */
    default Flux<AgentEvent> onActing(AgentRuntimeContext context, List<AgentToolUseBlock> toolCalls,
                                       Function<List<AgentToolUseBlock>, Flux<AgentEvent>> next) {
        return next.apply(toolCalls);
    }

    /**
     * 原始LLM调用拦截
     * <p>
     * 可用于模型路由、fallback、结果缓存等。默认透传到下一层。
     * </p>
     * @param context
     * @param input
     * @param next
     * @return
     */
    default Mono<AgentMessage> onModelCall(AgentRuntimeContext context, List<AgentMessage> input,
                                            Function<List<AgentMessage>, Mono<AgentMessage>> next) {
        return next.apply(input);
    }
}
