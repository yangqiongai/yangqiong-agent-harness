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

import java.util.List;

import com.yangqiongai.agent.harness.core.event.AgentEvent;
import com.yangqiongai.agent.harness.core.event.AgentEventType;
import com.yangqiongai.agent.harness.core.event.AgentResultEvent;
import com.yangqiongai.agent.harness.core.event.ClarificationAnswer;
import com.yangqiongai.agent.harness.core.event.ConfirmResult;
import com.yangqiongai.agent.harness.core.message.AgentMessage;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Agent执行循环
 * <p>
 * 抽象Agent的推理-执行主循环形态，默认实现为ReActEngine。
 * 通过{@code HarnessRuntimeBuilder.agentLoop(...)}注入自定义循环（如plan-and-execute、reflexion等范式）。
 * </p>
 * <p>
 * 自定义实现要求：
 * <ul>
 * <li>{@link #run}必须实现，且事件流必须以{@link AgentResultEvent}或ERROR事件终止，
 *     否则默认{@link #call}将无法提取最终消息</li>
 * <li>以下三个恢复方法按需覆写：若循环会产出审批确认、澄清请求或检查点事件，
 *     则对应恢复方法<b>必须</b>实现，否则运行期将抛出UnsupportedOperationException；
 *     若循环形态不涉及某类暂停（如无工具审批），可保留默认抛错以明确不支持</li>
 * </ul>
 * </p>
 * @author yangqiong
 */
public interface AgentLoop {

    /**
     * 运行Agent主循环，产出完整事件流
     * @param inputs
     * @param context
     * @return
     */
    Flux<AgentEvent> run(List<AgentMessage> inputs, EngineContext context);

    /**
     * 同步调用Agent，消费完整事件流并返回最终消息
     * <p>默认实现全量消费事件流</p>
     * @param inputs
     * @param context
     * @return
     */
    default Mono<AgentMessage> call(List<AgentMessage> inputs, EngineContext context) {
        java.util.concurrent.atomic.AtomicReference<AgentEvent> terminal =
                new java.util.concurrent.atomic.AtomicReference<>();
        return run(inputs, context)
                .doOnNext(event -> {
                    if (event instanceof AgentResultEvent || event.getType() == AgentEventType.ERROR) {
                        terminal.set(event);
                    }
                })
                .then(Mono.defer(() -> {
                    AgentEvent event = terminal.get();
                    if (event instanceof AgentResultEvent resultEvent) {
                        return Mono.just(resultEvent.getResult());
                    }
                    Throwable error = event != null && event.getPayload() instanceof Throwable t
                            ? t : new RuntimeException("Agent执行异常");
                    return Mono.error(error);
                }));
    }

    /**
     * 审批确认后恢复执行
     * <p>实现契约：resume仅在循环产出过RequireUserConfirmEvent暂停后由运行时回调；
     * 实现需根据confirmResults定位暂停的工具调用，按用户决策（批准/拒绝）继续或终止，
     * 返回的事件流语义与{@link #run}一致（以AgentResultEvent或ERROR事件终止）。</p>
     * @param confirmResults
     * @param context
     * @return
     */
    default Flux<AgentEvent> resume(List<ConfirmResult> confirmResults, EngineContext context) {
        throw new UnsupportedOperationException("当前AgentLoop不支持审批恢复");
    }

    /**
     * 澄清应答后恢复执行
     * <p>实现契约：resumeWithClarification仅在循环产出过澄清请求暂停后由运行时回调；
     * 实现需将answers注入待澄清的上下文并继续推理，
     * 返回的事件流语义与{@link #run}一致。</p>
     * @param answers
     * @param context
     * @return
     */
    default Flux<AgentEvent> resumeWithClarification(List<ClarificationAnswer> answers, EngineContext context) {
        throw new UnsupportedOperationException("当前AgentLoop不支持澄清恢复");
    }

    /**
     * 从检查点恢复执行
     * <p>实现契约：restoreFromCheckpoint要求context中已携带检查点状态（如恢复的会话与执行位置）；
     * 实现需从context还原循环内部状态后继续执行，
     * 返回的事件流语义与{@link #run}一致。</p>
     * @param context
     * @return
     */
    default Flux<AgentEvent> restoreFromCheckpoint(EngineContext context) {
        throw new UnsupportedOperationException("当前AgentLoop不支持检查点恢复");
    }
}
