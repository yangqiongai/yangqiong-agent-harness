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
package com.yangqiong.agent.harness.core;

import com.yangqiong.agent.harness.engine.AgentRuntimeContext;
import com.yangqiong.agent.harness.core.event.AgentEvent;
import com.yangqiong.agent.harness.core.event.ClarificationAnswer;
import com.yangqiong.agent.harness.core.event.ConfirmResult;
import com.yangqiong.agent.harness.core.interruption.AgentInterruptControl;
import com.yangqiong.agent.harness.core.interruption.AgentInterruptSource;
import com.yangqiong.agent.harness.core.message.AgentMessage;
import com.yangqiong.agent.harness.core.message.AgentMessageRole;
import com.yangqiong.agent.harness.core.message.AgentTextBlock;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * Agent运行时
 * @author yangqiong
 */
public interface AgentRuntime {

    /**
     * 同步调用Agent
     * @param inputs
     * @param context
     * @return
     */
    Mono<AgentMessage> call(List<AgentMessage> inputs, AgentRuntimeContext context);

    /**
     * 流式输出Agent事件
     * @param inputs
     * @param context
     * @return
     */
    Flux<AgentEvent> stream(List<AgentMessage> inputs, AgentRuntimeContext context);

    /**
     * 获取Agent名称
     * @return
     */
    String getName();

    /**
     * 主动中断当前Agent执行，ReAct循环会在下轮检测到中断并终止
     * @param context
     */
    default void interrupt(AgentRuntimeContext context) {
        AgentInterruptControl control = context.getInterruptControl();
        if (control == null) {
            return;
        }
        control.trigger(AgentInterruptSource.USER, AgentMessage.builder()
                .role(AgentMessageRole.USER)
                .content(List.of(AgentTextBlock.builder().text("用户主动中断").build()))
                .build());
    }

    /**
     * 恢复因人工确认暂停的Agent执行
     * @param confirmResults
     * @param context
     * @return
     */
    default Flux<AgentEvent> resume(List<ConfirmResult> confirmResults, AgentRuntimeContext context) {
        return Flux.error(new UnsupportedOperationException("当前运行时不支持resume操作"));
    }

    /**
     * 恢复因用户澄清暂停的Agent执行
     * @param answers
     * @param context
     * @return
     */
    default Flux<AgentEvent> resumeWithClarification(List<ClarificationAnswer> answers, AgentRuntimeContext context) {
        return Flux.error(new UnsupportedOperationException("当前运行时不支持resumeWithClarification操作"));
    }

    /**
     * 从持久化检查点续跑：以最近一次落盘快照恢复会话并与模型续接
     * <p>
     * 需在装配时通过checkpointStore/agentRunStore注入持久化存储，且上下文提供
     * 与上次运行完全一致的scopeId、sessionId。未装配存储时回退到内存检查点管理器。
     * </p>
     * @param context
     * @return
     */
    default Flux<AgentEvent> resumeFromCheckpoint(AgentRuntimeContext context) {
        return Flux.error(new UnsupportedOperationException("当前运行时不支持resumeFromCheckpoint操作"));
    }

    /**
     * 关闭运行时持有的底层资源（如MCP连接、stdio子进程），应用停机时调用
     * @return
     */
    default Mono<Void> close() {
        return Mono.empty();
    }
}
