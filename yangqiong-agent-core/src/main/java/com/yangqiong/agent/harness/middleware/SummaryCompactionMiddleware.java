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
import java.util.List;
import java.util.function.Function;

import com.yangqiong.agent.harness.core.message.MessageFactory;
import com.yangqiong.agent.harness.model.ModelCaller;
import com.yangqiong.agent.harness.engine.AgentRuntimeContext;
import com.yangqiong.agent.harness.config.AgentCompactionConfig;
import com.yangqiong.agent.harness.core.event.AgentEvent;
import com.yangqiong.agent.harness.core.memory.SessionMemory;
import com.yangqiong.agent.harness.core.memory.SessionSummary;
import com.yangqiong.agent.harness.core.message.AgentMessage;
import com.yangqiong.agent.harness.core.message.AgentMessageRole;
import com.yangqiong.agent.harness.core.message.AgentTextBlock;
import com.yangqiong.agent.harness.core.middleware.AgentMiddleware;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * 基于LLM摘要的上下文压缩中间件
 * <p>
 * 当历史消息数超过阈值时，调用LLM对中间消息生成摘要，保留系统提示与最近若干轮对话。
 * 与{@link CompactionMiddleware}（截断式）互斥使用，本中间件实现{@link AgentMiddleware}。
 * 当注入{@link SessionMemory}时启用增量摘要：仅摘要自上次摘要以来的新消息并复用运行摘要，
 * 避免每轮全量重算的LLM开销与信息损失；未注入时退化为全量摘要。
 * </p>
 * @author yangqiong
 */
public class SummaryCompactionMiddleware implements AgentMiddleware {

    /**
     * 压缩配置
     */
    private final AgentCompactionConfig config;

    /**
     * 模型调用器，用于生成摘要
     */
    private final ModelCaller modelCaller;

    /**
     * 会话级短期记忆，启用增量摘要；为null时退化为全量摘要
     */
    private final SessionMemory sessionMemory;

    /**
     * 摘要系统提示词
     */
    private static final String SUMMARY_SYSTEM_PROMPT =
            "你是对话摘要助手。请将以下对话历史压缩为一份简洁的摘要，保留关键事实、决策与未完结的任务，不要添加任何主观推测。";

    public SummaryCompactionMiddleware(AgentCompactionConfig config, ModelCaller modelCaller) {
        this(config, modelCaller, null);
    }

    /**
     * 启用增量摘要的构造器
     * @param config
     * @param modelCaller
     * @param sessionMemory 为null时退化为全量摘要
     */
    public SummaryCompactionMiddleware(AgentCompactionConfig config, ModelCaller modelCaller,
                                         SessionMemory sessionMemory) {
        this.config = config != null ? config : AgentCompactionConfig.defaults();
        this.modelCaller = modelCaller;
        this.sessionMemory = sessionMemory;
    }

    /**
     * 推理阶段前检查是否需要摘要压缩
     * @param context
     * @param input
     * @param next
     * @return
     */
    @Override
    public Flux<AgentEvent> onReasoning(AgentRuntimeContext context, List<AgentMessage> input,
                                         Function<List<AgentMessage>, Flux<AgentEvent>> next) {
        if (modelCaller == null || input == null || input.size() <= config.getTriggerMessages()) {
            return next.apply(input);
        }
        String sessionId = context != null ? context.getSessionId() : null;
        String scopeId = context != null ? context.getScopeId() : null;
        return summarizeMiddle(input, scopeId, sessionId)
                .flatMapMany(compacted -> next.apply(compacted));
    }

    /**
     * 对中间消息生成摘要，保留系统提示与尾部最近若干条消息
     * <p>
     * 注入{@link SessionMemory}且sessionId非空时启用增量摘要：仅摘要自上次摘要序号以来的新消息，
     * 无新消息时复用旧摘要并跳过LLM调用。
     * </p>
     * @param messages
     * @param scopeId
     * @param sessionId
     * @return
     */
    private Mono<List<AgentMessage>> summarizeMiddle(List<AgentMessage> messages, String scopeId, String sessionId) {
        int total = messages.size();
        int keepTail = config.getKeepMessages();
        boolean hasSystemHead = !messages.isEmpty() && messages.get(0).getRole() == AgentMessageRole.SYSTEM;

        int headEnd = hasSystemHead ? 1 : 0;
        int tailStart = Math.max(headEnd, total - keepTail);
        if (tailStart <= headEnd) {
            return Mono.just(messages);
        }
        // 跳过孤立的工具结果消息：其对应的assistant工具调用已被划入摘要区间，
        // 保留孤立的tool消息会导致OpenAI API因缺少配对的tool_call_id而报错
        while (tailStart < total && messages.get(tailStart).getRole() == AgentMessageRole.TOOL) {
            tailStart++;
        }
        if (tailStart >= total) {
            return Mono.just(messages);
        }

        final int effectiveTailStart = tailStart;
        // 增量摘要：仅摘要自上次摘要序号以来的新消息
        boolean incremental = sessionMemory != null && sessionId != null;
        SessionSummary prev = incremental ? sessionMemory.loadSummary(scopeId, sessionId) : null;
        int summarizeFrom = prev != null
                ? Math.max(headEnd, Math.min(prev.getSummarizedMessageCount(), effectiveTailStart))
                : headEnd;
        List<AgentMessage> newMiddle = new ArrayList<>(messages.subList(summarizeFrom, effectiveTailStart));

        // 无新消息且已有旧摘要：复用旧摘要，跳过LLM调用
        if (newMiddle.isEmpty() && prev != null && prev.getSummary() != null) {
            return Mono.just(buildCompacted(messages, hasSystemHead, prev.getSummary(), effectiveTailStart));
        }

        String conversationText = renderMessages(newMiddle);
        String userContent = prev != null && prev.getSummary() != null
                ? "前序摘要：\n" + prev.getSummary() + "\n\n新增对话：\n" + conversationText
                : "请摘要以下对话：\n\n" + conversationText;

        AgentMessage summaryRequest = MessageFactory.createSystemMessage(SUMMARY_SYSTEM_PROMPT);
        AgentMessage userRequest = MessageFactory.createUserMessage(userContent);

        return modelCaller.call(List.of(summaryRequest, userRequest), List.of())
                .map(resp -> AgentMessage.builder()
                        .role(AgentMessageRole.ASSISTANT)
                        .content(resp.getContent())
                        .build())
                .map(summaryMsg -> {
                    String summaryText = extractText(summaryMsg);
                    if (incremental) {
                        sessionMemory.saveSummary(scopeId, sessionId, summaryText, effectiveTailStart);
                    }
                    return buildCompacted(messages, hasSystemHead, summaryText, effectiveTailStart);
                })
                .onErrorResume(e -> Mono.just(messages));
    }

    /**
     * 构建压缩后的消息列表：系统头 + 摘要 + 尾部保留消息
     * @param messages
     * @param hasSystemHead
     * @param summary
     * @param effectiveTailStart
     * @return
     */
    private List<AgentMessage> buildCompacted(List<AgentMessage> messages, boolean hasSystemHead,
                                                String summary, int effectiveTailStart) {
        List<AgentMessage> result = new ArrayList<>();
        if (hasSystemHead) {
            result.add(messages.get(0));
        }
        result.add(MessageFactory.createSystemMessage("历史对话摘要：\n" + summary));
        result.addAll(messages.subList(effectiveTailStart, messages.size()));
        return result;
    }

    /**
     * 将消息列表渲染为纯文本
     * @param messages
     * @return
     */
    private String renderMessages(List<AgentMessage> messages) {
        StringBuilder sb = new StringBuilder();
        for (AgentMessage msg : messages) {
            sb.append("[").append(msg.getRole()).append("] ")
                    .append(extractText(msg)).append("\n");
        }
        return sb.toString();
    }

    /**
     * 从消息中提取纯文本内容
     * @param msg
     * @return
     */
    private String extractText(AgentMessage msg) {
        if (msg == null || msg.getContent() == null || msg.getContent().isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (Object block : msg.getContent()) {
            if (block instanceof AgentTextBlock textBlock) {
                sb.append(textBlock.getText());
            }
        }
        return sb.toString();
    }
}
