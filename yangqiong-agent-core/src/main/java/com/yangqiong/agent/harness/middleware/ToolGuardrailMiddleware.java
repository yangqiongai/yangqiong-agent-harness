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

import java.util.Map;

import com.yangqiong.agent.harness.engine.AgentRuntimeContext;
import com.yangqiong.agent.harness.guardrail.GuardrailFence;
import com.yangqiong.agent.harness.guardrail.GuardrailRule;
import com.yangqiong.agent.harness.guardrail.GuardrailRuleRegistry;
import com.yangqiong.agent.harness.guardrail.GuardrailTarget;
import com.yangqiong.agent.harness.guardrail.GuardrailViolation;
import com.yangqiong.agent.harness.core.message.AgentToolResultBlock;
import com.yangqiong.agent.harness.core.message.AgentTextBlock;
import com.yangqiong.agent.harness.core.middleware.AgentMiddleware;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 工具护栏中间件
 * <p>
 * 全链路护栏的工具侧入口：工具调用参数与工具执行结果统一过护栏规则链。
 * 参数命中BLOCK动作抛出违规异常（转为工具失败走失败策略）；
 * 结果命中BLOCK转为错误占位（工具已执行，事后拦截其内容进入上下文），
 * 命中MASK做脱敏；结果默认加tool-result fencing围栏，标记为不可信数据。
 * </p>
 * @author yangqiong
 */
public class ToolGuardrailMiddleware implements AgentMiddleware {

    private static final Logger log = LoggerFactory.getLogger(ToolGuardrailMiddleware.class);

    /**
     * 护栏规则注册中心
     */
    private final GuardrailRuleRegistry registry;

    /**
     * 是否对工具结果加围栏
     */
    private final boolean fencingEnabled;

    public ToolGuardrailMiddleware(GuardrailRuleRegistry registry) {
        this(registry, true);
    }

    /**
     * 完整构造器
     * @param registry
     * @param fencingEnabled
     */
    public ToolGuardrailMiddleware(GuardrailRuleRegistry registry, boolean fencingEnabled) {
        this.registry = registry != null ? registry : new GuardrailRuleRegistry();
        this.fencingEnabled = fencingEnabled;
    }

    /**
     * 工具调用前校验参数：命中BLOCK动作抛违规异常转为工具失败
     * @param toolName
     * @param input
     * @param context
     * @return
     */
    @Override
    public Map<String, Object> onToolCall(String toolName, Map<String, Object> input,
                                           AgentRuntimeContext context) {
        if (input == null || input.isEmpty()) {
            return input;
        }
        String serialized = serializeInput(input);
        java.util.Optional<GuardrailViolation> violation =
                registry.detect(serialized, GuardrailTarget.TOOL_PARAM);
        if (violation.isPresent() && violation.get().getRule().getAction()
                == GuardrailRule.GuardrailAction.BLOCK) {
            log.warn("[ToolGuardrail] 工具参数命中拦截规则: tool={}, {}",
                    toolName, violation.get().describe());
            throw new ToolGuardrailViolationException("工具参数命中安全策略: " + toolName
                    + " [" + violation.get().getRule().getName() + "]");
        }
        if (violation.isPresent()) {
            log.warn("[ToolGuardrail] 工具参数命中规则放行: tool={}, {}",
                    toolName, violation.get().describe());
        }
        return input;
    }

    /**
     * 工具调用后处理结果：BLOCK转错误占位、MASK脱敏、默认加fencing围栏
     * @param toolName
     * @param result
     * @param context
     * @return
     */
    @Override
    public AgentToolResultBlock onToolResult(String toolName, AgentToolResultBlock result,
                                              AgentRuntimeContext context) {
        if (result == null) {
            return result;
        }
        String text = result.getTextContent();
        if (text == null || text.isEmpty()) {
            return result;
        }
        java.util.Optional<GuardrailViolation> violation =
                registry.detect(text, GuardrailTarget.TOOL_RESULT);
        if (violation.isPresent()
                && violation.get().getRule().getAction() == GuardrailRule.GuardrailAction.BLOCK) {
            log.warn("[ToolGuardrail] 工具结果命中拦截规则转错误占位: tool={}, {}",
                    toolName, violation.get().describe());
            return AgentToolResultBlock.error(result.getToolUseId(),
                    "工具结果命中安全策略已拦截 [" + violation.get().getRule().getName() + "]");
        }
        String processed = registry.mask(text, GuardrailTarget.TOOL_RESULT);
        if (fencingEnabled && !GuardrailFence.isFenced(processed) && !result.isError()) {
            processed = GuardrailFence.fence(processed);
        }
        if (processed.equals(text)) {
            return result;
        }
        return AgentToolResultBlock.of(result.getToolUseId(),
                java.util.List.of(AgentTextBlock.builder().text(processed).build()));
    }

    /**
     * 序列化工具参数用于检测（键值拼接，深度受控防止超大输入）
     * @param input
     * @return
     */
    private String serializeInput(Map<String, Object> input) {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, Object> entry : input.entrySet()) {
            sb.append(entry.getKey()).append('=');
            Object value = entry.getValue();
            sb.append(value != null ? String.valueOf(value) : "null").append('\n');
        }
        return sb.toString();
    }

    /**
     * 工具护栏违规异常
     */
    public static class ToolGuardrailViolationException extends RuntimeException {

        public ToolGuardrailViolationException(String message) {
            super(message);
        }
    }
}
