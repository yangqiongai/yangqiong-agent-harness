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
package com.yangqiongai.agent.harness.middleware;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import com.yangqiongai.agent.harness.core.event.AgentEvent;
import com.yangqiongai.agent.harness.core.event.AgentEventType;
import com.yangqiongai.agent.harness.core.message.AgentMessage;
import com.yangqiongai.agent.harness.core.message.AgentMessageRole;
import com.yangqiongai.agent.harness.core.message.AgentTextBlock;
import com.yangqiongai.agent.harness.core.middleware.AgentMiddleware;
import com.yangqiongai.agent.harness.engine.AgentRuntimeContext;
import com.yangqiongai.agent.harness.guardrail.ContentModerationPolicy;
import com.yangqiongai.agent.harness.guardrail.ModerationVerdict;
import com.yangqiongai.agent.harness.guardrail.NoopContentModerationPolicy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;

/**
 * 内容审查中间件
 * <p>
 * 在用户输入进入LLM前对消息内容进行审查，支持PASS/BLOCK/MASK三种裁决。
 * 命中BLOCK时中止并返回拒绝消息，命中MASK时对消息文本做脱敏替换。
 * 未注入审查策略时默认使用{@link NoopContentModerationPolicy}放行所有内容。
 * </p>
 * @author yangqiong
 */
public class ContentModerationMiddleware implements AgentMiddleware {

    private static final Logger log = LoggerFactory.getLogger(ContentModerationMiddleware.class);

    /**
     * 内容审查策略
     */
    private final ContentModerationPolicy policy;

    /**
     * BLOCK模式下的拒绝文案
     */
    private final String rejectionMessage;

    /**
     * 全参构造
     * @param policy
     * @param rejectionMessage
     */
    public ContentModerationMiddleware(ContentModerationPolicy policy, String rejectionMessage) {
        this.policy = policy != null ? policy : new NoopContentModerationPolicy();
        this.rejectionMessage = rejectionMessage != null ? rejectionMessage : "输入内容命中安全审查策略，已拦截";
    }

    /**
     * 构造
     * @param policy
     */
    public ContentModerationMiddleware(ContentModerationPolicy policy) {
        this(policy, null);
    }

    /**
     * 推理阶段拦截，在用户输入进入LLM前执行内容审查
     * @param context
     * @param input
     * @param next
     * @return
     */
    @Override
    public Flux<AgentEvent> onReasoning(AgentRuntimeContext context, List<AgentMessage> input,
                                          Function<List<AgentMessage>, Flux<AgentEvent>> next) {
        List<AgentMessage> working = new ArrayList<>(input);
        for (int i = 0; i < working.size(); i++) {
            AgentMessage msg = working.get(i);
            if (msg == null || msg.getRole() != AgentMessageRole.USER) {
                continue;
            }
            ModerationVerdict verdict = policy.moderate(msg);
            if (verdict.isPass()) {
                continue;
            }
            if (verdict.isBlock()) {
                log.warn("[ContentModeration] 输入命中审查策略，BLOCK拦截: {}", verdict.getReason());
                return Flux.just(AgentEvent.of(AgentEventType.ERROR,
                        new ContentModerationException(rejectionMessage + " [原因: " + verdict.getReason() + "]")),
                        AgentEvent.completed());
            }
            if (verdict.isMask()) {
                log.warn("[ContentModeration] 输入命中审查策略，MASK脱敏: {}", verdict.getReason());
                working.set(i, maskMessage(msg, verdict.getReason()));
            }
        }
        return next.apply(working);
    }

    /**
     * 对消息进行脱敏处理
     * @param msg
     * @param reason
     * @return
     */
    private AgentMessage maskMessage(AgentMessage msg, String reason) {
        return AgentMessage.builder()
                .name(msg.getName())
                .role(msg.getRole())
                .content(List.of(AgentTextBlock.builder()
                        .text("[内容已脱敏，原因: " + reason + "]")
                        .build()))
                .chatUsage(msg.getChatUsage())
                .latency(msg.getLatency())
                .build();
    }

    /**
     * 内容审查异常
     */
    public static class ContentModerationException extends RuntimeException {

        /**
         * 构造
         * @param message
         */
        public ContentModerationException(String message) {
            super(message);
        }
    }
}
