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
import java.util.Arrays;
import java.util.List;
import java.util.function.Function;
import java.util.regex.Pattern;

import com.yangqiong.agent.harness.engine.AgentRuntimeContext;
import com.yangqiong.agent.harness.core.event.AgentEvent;
import com.yangqiong.agent.harness.core.event.AgentEventType;
import com.yangqiong.agent.harness.core.message.AgentMessage;
import com.yangqiong.agent.harness.core.message.AgentMessageRole;
import com.yangqiong.agent.harness.core.message.AgentTextBlock;
import com.yangqiong.agent.harness.core.middleware.AgentMiddleware;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;

/**
 * 输入内容安全校验中间件
 * <p>
 * 在用户输入进入LLM前进行安全检查，支持prompt注入关键词检测与敏感词过滤。
 * 命中规则时按配置动作处理：BLOCK中止并返回拒绝消息，WARN记录日志但放行。
 * 与{@link OutputGuardrailMiddleware}对称，补齐输入侧安全能力。
 * </p>
 * @author yangqiong
 */
public class InputGuardrailMiddleware implements AgentMiddleware {

    private static final Logger log = LoggerFactory.getLogger(InputGuardrailMiddleware.class);

    /**
     * 默认prompt注入检测关键词（大小写不敏感）
     */
    private static final List<String> DEFAULT_INJECTION_PATTERNS = Arrays.asList(
            "ignore previous instructions",
            "ignore the above",
            "ignore all previous",
            "disregard the above",
            "disregard previous instructions",
            "you are now",
            "new instructions:",
            "reveal your instructions",
            "show me your system prompt",
            "jailbreak");

    /**
     * 拒绝规则正则模式列表
     */
    private final List<Pattern> denyPatterns;

    /**
     * 命中后动作
     */
    private final GuardrailAction action;

    /**
     * BLOCK模式下的拒绝文案
     */
    private final String rejectionMessage;

    public InputGuardrailMiddleware(List<Pattern> denyPatterns, GuardrailAction action, String rejectionMessage) {
        this.denyPatterns = denyPatterns != null ? new ArrayList<>(denyPatterns) : new ArrayList<>();
        this.action = action != null ? action : GuardrailAction.BLOCK;
        this.rejectionMessage = rejectionMessage != null ? rejectionMessage : "输入内容命中安全策略，已拦截";
    }

    /**
     * 推理阶段拦截，在用户输入进入LLM前检查安全规则
     * @param context
     * @param input
     * @param next
     * @return
     */
    @Override
    public Flux<AgentEvent> onReasoning(AgentRuntimeContext context, List<AgentMessage> input,
                                          Function<List<AgentMessage>, Flux<AgentEvent>> next) {
        for (AgentMessage msg : input) {
            if (msg == null || msg.getRole() != AgentMessageRole.USER) {
                continue;
            }
            String text = extractText(msg);
            if (text == null || text.isEmpty()) {
                continue;
            }
            for (Pattern pattern : denyPatterns) {
                if (pattern.matcher(text).find()) {
                    return handleViolation(pattern, input, next);
                }
            }
        }
        return next.apply(input);
    }

    /**
     * 处理违规输入，按动作决定拦截或放行
     * @param pattern
     * @param input
     * @param next
     * @return
     */
    private Flux<AgentEvent> handleViolation(Pattern pattern, List<AgentMessage> input,
                                               Function<List<AgentMessage>, Flux<AgentEvent>> next) {
        if (action == GuardrailAction.WARN) {
            log.warn("[InputGuardrail] 输入命中安全规则，WARN模式放行: {}", pattern.pattern());
            return next.apply(input);
        }
        log.warn("[InputGuardrail] 输入命中安全规则，BLOCK模式拦截: {}", pattern.pattern());
        return Flux.just(AgentEvent.of(AgentEventType.ERROR,
                        new InputGuardrailViolationException(rejectionMessage + " [规则: " + pattern.pattern() + "]")),
                AgentEvent.completed());
    }

    /**
     * 从消息中提取纯文本
     * @param msg
     * @return
     */
    private String extractText(AgentMessage msg) {
        if (msg.getContent() == null || msg.getContent().isEmpty()) {
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
     * 护栏动作
     */
    public enum GuardrailAction {
        /** 中止并返回拒绝消息 */
        BLOCK,
        /** 记录日志但放行 */
        WARN
    }

    /**
     * 输入护栏违规异常
     */
    public static class InputGuardrailViolationException extends RuntimeException {

        public InputGuardrailViolationException(String message) {
            super(message);
        }
    }

    /**
     * InputGuardrail构建器
     * @author yangqiong
     */
    public static class Builder {

        /**
         * 拒绝正则列表
         */
        private final List<Pattern> patterns = new ArrayList<>();

        /**
         * 命中动作
         */
        private GuardrailAction action = GuardrailAction.BLOCK;

        /**
         * 拒绝文案
         */
        private String rejectionMessage;

        public Builder addPattern(String regex) {
            this.patterns.add(Pattern.compile(regex, Pattern.CASE_INSENSITIVE));
            return this;
        }

        public Builder addPattern(Pattern pattern) {
            this.patterns.add(pattern);
            return this;
        }

        /**
         * 添加默认prompt注入检测规则
         * @return
         */
        public Builder addDefaultInjectionPatterns() {
            for (String keyword : DEFAULT_INJECTION_PATTERNS) {
                this.patterns.add(Pattern.compile(Pattern.quote(keyword), Pattern.CASE_INSENSITIVE));
            }
            return this;
        }

        public Builder action(GuardrailAction action) {
            this.action = action;
            return this;
        }

        public Builder rejectionMessage(String message) {
            this.rejectionMessage = message;
            return this;
        }

        public InputGuardrailMiddleware build() {
            return new InputGuardrailMiddleware(patterns, action, rejectionMessage);
        }
    }
}
