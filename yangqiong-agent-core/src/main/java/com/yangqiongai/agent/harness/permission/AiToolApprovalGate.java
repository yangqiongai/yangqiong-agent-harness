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
package com.yangqiongai.agent.harness.permission;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yangqiongai.agent.harness.config.AgentPermissionDecision;
import com.yangqiongai.agent.harness.core.message.AgentContentBlock;
import com.yangqiongai.agent.harness.core.message.AgentMessage;
import com.yangqiongai.agent.harness.core.message.AgentTextBlock;
import com.yangqiongai.agent.harness.core.model.AgentChatResponse;
import com.yangqiongai.agent.harness.core.model.AgentGenerateOptions;
import com.yangqiongai.agent.harness.core.model.AgentModel;
import com.yangqiongai.agent.harness.core.message.MessageFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * AI自动审批策略门
 * <p>
 * AUTO审批模式的落地实现：静态名单先行（黑名单拒绝、白名单命中放行、白名单未命中拒绝），
 * 未命中静态规则的工具调用交由审批模型依据工具名与调用入参判定放行或拒绝；
 * 模型调用异常或输出不可解析时按配置回退（默认转人工ASK，可配置为拒绝DENY）。
 * 每次判定复用统一审计出口，仅记录决策结果不落判定理由。
 * </p>
 * @author yangqiong
 */
public class AiToolApprovalGate extends ToolPolicyGate {

    private static final Logger log = LoggerFactory.getLogger(AiToolApprovalGate.class);

    /**
     * 审批判定提示词（模型须仅回答ALLOW或DENY）
     */
    private static final String JUDGE_PROMPT_TEMPLATE = "你是工具调用安全审批员。请判断以下Agent工具调用是否安全放行。\n"
            + "判定原则：只读查询类调用放行；无破坏性副作用的写入放行；"
            + "删除、外发、提权、任意命令执行等高风险操作拒绝。\n"
            + "附加策略：%s\n"
            + "工具名：%s\n"
            + "调用入参：%s\n"
            + "请只回答 ALLOW 或 DENY，不要输出其它内容。";

    /**
     * 审批模型
     */
    private final AgentModel judgeModel;

    /**
     * 静态白名单，命中直接放行，非空且未命中直接拒绝
     */
    private final Set<String> allowedTools;

    /**
     * 静态黑名单，命中直接拒绝
     */
    private final Set<String> deniedTools;

    /**
     * 强制人工审批集合，命中不经模型直接转人工ASK
     */
    private final Set<String> alwaysAskTools;

    /**
     * 审批模型判定失败时的回退决策，true回退人工ASK，false直接拒绝DENY
     */
    private final boolean fallbackAsk;

    /**
     * 附加审批策略描述，拼入判定提示词约束模型
     */
    private final String guidance;

    /**
     * 入参JSON序列化器
     */
    private final ObjectMapper mapper = new ObjectMapper();

    /**
     * 全参构造：无静态名单，判定失败回退人工
     * @param judgeModel 审批模型，不可为空
     */
    public AiToolApprovalGate(AgentModel judgeModel) {
        this(judgeModel, null, null, null, true, null);
    }

    /**
     * 全参构造
     * @param judgeModel 审批模型，不可为空
     * @param allowedTools 静态白名单，命中直接放行，非空且未命中直接拒绝
     * @param deniedTools 静态黑名单，命中直接拒绝
     * @param alwaysAskTools 强制人工审批集合，命中不经模型直接转人工ASK
     * @param fallbackAsk 模型判定失败时是否回退人工，true回退ASK，false拒绝DENY
     * @param guidance 附加审批策略描述，可空
     */
    public AiToolApprovalGate(AgentModel judgeModel, Set<String> allowedTools,
                              Set<String> deniedTools, Set<String> alwaysAskTools,
                              boolean fallbackAsk, String guidance) {
        super(null, null, null, false, null);
        this.judgeModel = Objects.requireNonNull(judgeModel, "judgeModel不能为空");
        this.allowedTools = allowedTools;
        this.deniedTools = deniedTools;
        this.alwaysAskTools = alwaysAskTools;
        this.fallbackAsk = fallbackAsk;
        this.guidance = guidance;
    }

    /**
     * AI自动审批判定：静态名单短路后交审批模型判定
     * @param toolName
     * @param toolCallId
     * @param scopeId
     * @param runId
     * @param toolInput
     * @return
     */
    @Override
    public AgentPermissionDecision evaluate(String toolName, String toolCallId,
                                             String scopeId, String runId, Map<String, Object> toolInput) {
        // 静态名单先行：黑名单拒绝、白名单命中放行、白名单非空未命中拒绝，均不经模型
        AgentPermissionDecision decision = staticEvaluate(toolName);
        if (decision != null) {
            audit(toolName, toolCallId, scopeId, runId, decision);
            return decision;
        }
        decision = judgeByModel(toolName, toolInput);
        audit(toolName, toolCallId, scopeId, runId, decision);
        return decision;
    }

    /**
     * 静态名单判定，未命中静态规则时返回null交由模型判定
     * @param toolName
     * @return
     */
    private AgentPermissionDecision staticEvaluate(String toolName) {
        if (toolName == null) {
            return AgentPermissionDecision.DENY;
        }
        if (deniedTools != null && deniedTools.contains(toolName)) {
            return AgentPermissionDecision.DENY;
        }
        // 强制人工审批集合优先级最高，命中不经模型直接转人工
        if (alwaysAskTools != null && alwaysAskTools.contains(toolName)) {
            return AgentPermissionDecision.ASK;
        }
        if (allowedTools != null && !allowedTools.isEmpty()) {
            return allowedTools.contains(toolName)
                    ? AgentPermissionDecision.ALLOW : AgentPermissionDecision.DENY;
        }
        return null;
    }

    /**
     * 调用审批模型判定，异常或输出不可解析时按配置回退
     * @param toolName
     * @param toolInput
     * @return
     */
    private AgentPermissionDecision judgeByModel(String toolName, Map<String, Object> toolInput) {
        try {
            String prompt = String.format(JUDGE_PROMPT_TEMPLATE,
                    guidance == null || guidance.isBlank() ? "无" : guidance,
                    toolName,
                    serializeInput(toolInput));
            AgentMessage judgeMessage = MessageFactory.createUserMessage(prompt);
            AgentChatResponse response = judgeModel.generate(List.of(judgeMessage), null,
                    AgentGenerateOptions.builder().temperature(0.0D).build());
            return parseDecision(response);
        } catch (Exception e) {
            log.warn("AI审批模型判定失败，按回退策略处理: tool={}, error={}", toolName, e.getMessage());
            return fallbackAsk ? AgentPermissionDecision.ASK : AgentPermissionDecision.DENY;
        }
    }

    /**
     * 解析模型回复中的ALLOW/DENY关键词
     * @param response
     * @return
     */
    private AgentPermissionDecision parseDecision(AgentChatResponse response) {
        if (response == null || response.getContent() == null) {
            return fallbackDecision();
        }
        StringBuilder text = new StringBuilder();
        for (AgentContentBlock block : response.getContent()) {
            if (block instanceof AgentTextBlock textBlock && textBlock.getText() != null) {
                text.append(textBlock.getText());
            }
        }
        String normalized = text.toString().toUpperCase(Locale.ROOT);
        if (normalized.contains("ALLOW")) {
            return AgentPermissionDecision.ALLOW;
        }
        if (normalized.contains("DENY")) {
            return AgentPermissionDecision.DENY;
        }
        log.warn("AI审批输出不可解析，按回退策略处理: output={}", text);
        return fallbackDecision();
    }

    /**
     * 获取判定失败时的回退决策
     * @return
     */
    private AgentPermissionDecision fallbackDecision() {
        return fallbackAsk ? AgentPermissionDecision.ASK : AgentPermissionDecision.DENY;
    }

    /**
     * 序列化工具入参，失败时退化为toString
     * @param toolInput
     * @return
     */
    private String serializeInput(Map<String, Object> toolInput) {
        if (toolInput == null || toolInput.isEmpty()) {
            return "{}";
        }
        try {
            return mapper.writeValueAsString(toolInput);
        } catch (Exception e) {
            return String.valueOf(toolInput);
        }
    }
}
