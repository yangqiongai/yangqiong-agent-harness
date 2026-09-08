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
import java.util.regex.Pattern;

import com.yangqiongai.agent.harness.engine.AgentRuntimeContext;
import com.yangqiongai.agent.harness.core.event.AgentEvent;
import com.yangqiongai.agent.harness.core.event.AgentEventType;
import com.yangqiongai.agent.harness.core.message.AgentMessage;
import com.yangqiongai.agent.harness.core.message.AgentTextBlock;
import com.yangqiongai.agent.harness.core.middleware.AgentReactiveMiddleware;
import reactor.core.publisher.Flux;

/**
 * 输出内容安全校验中间件
 * <p>
 * 在Agent最终输出前对内容进行Guardrail校验，支持敏感词过滤与正则规则匹配。
 * 命中规则时将输出替换为安全提示，并发射ERROR事件供上层感知。
 * </p>
 * @author yangqiong
 */
public class OutputGuardrailMiddleware implements AgentReactiveMiddleware {

    /**
     * 敏感词正则模式列表
     */
    private final List<Pattern> denyPatterns;

    /**
     * 命中后的替换文案
     */
    private final String replacement;

    /**
     * 是否中止流（true时命中即发ERROR事件并终止，false时仅替换内容继续）
     */
    private final boolean abortOnHit;

    public OutputGuardrailMiddleware(List<Pattern> denyPatterns, String replacement, boolean abortOnHit) {
        this.denyPatterns = denyPatterns != null ? new ArrayList<>(denyPatterns) : new ArrayList<>();
        this.replacement = replacement != null ? replacement : "[内容已被安全策略过滤]";
        this.abortOnHit = abortOnHit;
    }

    /**
     * Agent整体输出拦截，在next之后对AGENT_RESULT事件做内容校验
     * @param context
     * @param input
     * @param next
     * @return
     */
    @Override
    public Flux<AgentEvent> onAgent(AgentRuntimeContext context, List<AgentMessage> input,
                                      Function<List<AgentMessage>, Flux<AgentEvent>> next) {
        return next.apply(input)
                .map(this::inspectEvent);
    }

    /**
     * 检查事件内容是否命中敏感规则
     * @param event
     * @return
     */
    private AgentEvent inspectEvent(AgentEvent event) {
        if (event.getType() != AgentEventType.AGENT_RESULT) {
            return event;
        }
        Object payload = event.getPayload();
        if (!(payload instanceof AgentMessage msg)) {
            return event;
        }
        String text = extractText(msg);
        if (text == null || text.isEmpty()) {
            return event;
        }
        for (Pattern pattern : denyPatterns) {
            if (pattern.matcher(text).find()) {
                return handleViolation(event, msg, pattern);
            }
        }
        return event;
    }

    /**
     * 处理违规内容
     * @param originalEvent
     * @param originalMsg
     * @param pattern
     * @return
     */
    private AgentEvent handleViolation(AgentEvent originalEvent, AgentMessage originalMsg, Pattern pattern) {
        if (abortOnHit) {
            return AgentEvent.of(AgentEventType.ERROR,
                    new GuardrailViolationException("输出内容命中安全规则: " + pattern.pattern()));
        }
        // 仅替换内容，保留事件结构
        AgentMessage safeMsg = AgentMessage.builder()
                .role(originalMsg.getRole())
                .content(List.of(AgentTextBlock.builder().text(replacement).build()))
                .build();
        return AgentEvent.of(AgentEventType.AGENT_RESULT, safeMsg);
    }

    /**
     * 从消息中提取纯文本
     * @param msg
     * @return
     */
    private String extractText(AgentMessage msg) {
        if (msg == null || msg.getContent() == null || msg.getContent().isEmpty()) {
            return null;
        }
        StringBuilder sb = new StringBuilder();
        for (Object block : msg.getContent()) {
            if (block instanceof AgentTextBlock textBlock) {
                sb.append(textBlock.getText());
            }
        }
        return sb.toString();
    }

    /**
     * Guardrail违规异常
     */
    public static class GuardrailViolationException extends RuntimeException {

        public GuardrailViolationException(String message) {
            super(message);
        }
    }

    /**
     * Guardrail构建器
     * @author yangqiong
     */
    public static class Builder {

        /**
         * 敏感词正则列表
         */
        private List<Pattern> patterns = new ArrayList<>();

        /**
         * 替换文案
         */
        private String replacement;

        /**
         * 命中是否中止
         */
        private boolean abortOnHit = false;

        public Builder addPattern(String regex) {
            this.patterns.add(Pattern.compile(regex));
            return this;
        }

        public Builder addPattern(Pattern pattern) {
            this.patterns.add(pattern);
            return this;
        }

        public Builder replacement(String text) {
            this.replacement = text;
            return this;
        }

        public Builder abortOnHit(boolean abort) {
            this.abortOnHit = abort;
            return this;
        }

        public OutputGuardrailMiddleware build() {
            return new OutputGuardrailMiddleware(patterns, replacement, abortOnHit);
        }
    }
}
