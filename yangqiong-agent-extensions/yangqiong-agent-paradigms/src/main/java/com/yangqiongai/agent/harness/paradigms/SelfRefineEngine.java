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
package com.yangqiongai.agent.harness.paradigms;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import com.yangqiongai.agent.harness.core.event.AgentEvent;
import com.yangqiongai.agent.harness.core.event.AgentEventType;
import com.yangqiongai.agent.harness.core.event.AgentResultEvent;
import com.yangqiongai.agent.harness.core.message.AgentContentBlock;
import com.yangqiongai.agent.harness.core.message.AgentMessage;
import com.yangqiongai.agent.harness.core.message.AgentMessageRole;
import com.yangqiongai.agent.harness.core.message.AgentTextBlock;
import com.yangqiongai.agent.harness.core.message.AgentToolUseBlock;
import com.yangqiongai.agent.harness.core.message.MessageFactory;
import com.yangqiongai.agent.harness.core.model.AgentChatResponse;
import com.yangqiongai.agent.harness.engine.AbstractAgentLoop;
import com.yangqiongai.agent.harness.engine.EngineContext;
import com.yangqiongai.agent.harness.paradigms.support.ParadigmOptions;
import com.yangqiongai.agent.harness.model.ModelResponseParser;

import reactor.core.publisher.Flux;

/**
 * Self-Refine自我精炼范式引擎
 * <p>
 * 先经带工具的基础循环生成回答初稿，再循环执行批评与修订：
 * 批评文本含SATISFIED且不含UNSATISFIED时以当前稿为最终结果，否则按批评意见修订出新稿继续受评，
 * 达maxRefinements上限时以最后一版作为最终结果。
 * 继承 AbstractAgentLoop 复用生命周期/中间件/预算基础设施。
 * </p>
 * @author yangqiong
 */
public class SelfRefineEngine extends AbstractAgentLoop {

    /**
     * 批评通过标记，批评文本含此标记且不含UNSATISFIED时表示当前稿无需修订
     */
    private static final String SATISFIED_MARKER = "SATISFIED";

    /**
     * 批评修订标记，批评文本含此标记表示需要按后续意见修订
     */
    private static final String UNSATISFIED_MARKER = "UNSATISFIED";

    /**
     * 范式配置
     */
    private final ParadigmOptions options;

    /**
     * 使用全默认配置构建
     */
    public SelfRefineEngine() {
        this(new ParadigmOptions());
    }

    /**
     * 按指定配置构建
     * @param options
     */
    public SelfRefineEngine(ParadigmOptions options) {
        this.options = options != null ? options : new ParadigmOptions();
    }

    /**
     * 运行Self-Refine主循环：生成初稿后循环批评与修订，产出最终精炼结果，生命周期管线由父类模板负责
     * @param inputs
     * @param context
     * @return
     */
    @Override
    protected Flux<AgentEvent> doRun(List<AgentMessage> inputs, EngineContext context) {
        return Flux.defer(() -> {
            if (context.getModelCaller() == null || context.getToolExecutor() == null
                    || context.getResponseParser() == null) {
                return Flux.just(AgentEvent.of(AgentEventType.ERROR, new IllegalStateException(
                        "EngineContext未暴露模型调用器、工具执行器或响应解析器，无法执行Self-Refine循环")));
            }
            List<AgentMessage> conversation = buildConversation(inputs);
            String question = extractQuestion(inputs);
            AtomicInteger callSeq = new AtomicInteger(0);
            AtomicReference<AgentMessage> draftRef = new AtomicReference<>();
            // 生成阶段：带工具的基础循环产出初稿
            return toolLoop(new ArrayList<>(conversation), 0, context, callSeq, draftRef)
                    .concatWith(Flux.defer(() ->
                            refineLoop(conversation, question, draftRef, 0, context, callSeq)));
        });
    }

    /**
     * 发起一次模型调用，经父类助手发射MODEL_CALL_START/END事件并把合并响应写入responseRef
     * <p>
     * 单次模型调用流以父类withIterationTimeout包裹，空闲超过单轮迭代超时时中断并发射ERROR。
     * </p>
     * @param messages
     * @param toolSchemas
     * @param context
     * @param callSeq
     * @param responseRef
     * @return
     */
    private Flux<AgentEvent> callModel(List<AgentMessage> messages, List<Map<String, Object>> toolSchemas,
                                       EngineContext context, AtomicInteger callSeq,
                                       AtomicReference<AgentChatResponse> responseRef) {
        // 单轮迭代超时包裹单次模型调用流
        return withIterationTimeout(context, withModelCallEvents(context, messages, toolSchemas,
                callSeq.incrementAndGet(), response -> {
                    responseRef.set(response);
                    return Flux.empty();
                }));
    }

    /**
     * 批评-修订循环：批评通过或达修订上限时以当前稿为结果，否则按批评修订后继续受评
     * @param conversation
     * @param question
     * @param draftRef
     * @param refinements
     * @param context
     * @param callSeq
     * @return
     */
    private Flux<AgentEvent> refineLoop(List<AgentMessage> conversation, String question,
                                        AtomicReference<AgentMessage> draftRef, int refinements,
                                        EngineContext context, AtomicInteger callSeq) {
        AtomicReference<AgentChatResponse> critiqueRef = new AtomicReference<>();
        List<AgentMessage> critiqueMessages = new ArrayList<>(conversation);
        critiqueMessages.add(MessageFactory.createUserMessage(
                buildCritiquePrompt(question, messageText(draftRef.get()))));
        return callModel(critiqueMessages, List.of(), context, callSeq, critiqueRef)
                .concatWith(Flux.defer(() -> {
                    String critique = textOf(critiqueRef.get());
                    if (isSatisfied(critique) || refinements >= options.getMaxRefinements()) {
                        return Flux.just(resultEvent(draftRef.get(), context));
                    }
                    // 修订阶段：携带原稿与批评意见产出新稿
                    AtomicReference<AgentChatResponse> reviseRef = new AtomicReference<>();
                    List<AgentMessage> reviseMessages = new ArrayList<>(conversation);
                    reviseMessages.add(MessageFactory.createUserMessage(
                            buildRevisePrompt(question, messageText(draftRef.get()),
                                    extractFeedback(critique))));
                    return callModel(reviseMessages, List.of(), context, callSeq, reviseRef)
                            .concatWith(Flux.defer(() -> {
                                AgentMessage revised =
                                        context.getResponseParser().parseToMessage(reviseRef.get());
                                // 修订输出为空时保留原稿，避免精炼结果退化
                                if (!revised.getTextContent().isBlank()) {
                                    draftRef.set(revised);
                                }
                                return refineLoop(conversation, question, draftRef,
                                        refinements + 1, context, callSeq);
                            }));
                }));
    }

    /**
     * 带工具的基础循环：模型返回工具调用则执行后继续，无工具调用时产出当前稿，受maxSteps上限保护
     * @param messages
     * @param step
     * @param context
     * @param callSeq
     * @param answerRef
     * @return
     */
    private Flux<AgentEvent> toolLoop(List<AgentMessage> messages, int step, EngineContext context,
                                      AtomicInteger callSeq, AtomicReference<AgentMessage> answerRef) {
        if (step >= options.getMaxSteps()) {
            // 步数耗尽防死循环，以最近一条助手消息兜底作为当前稿
            answerRef.set(latestAssistantOrEmpty(messages));
            return Flux.empty();
        }
        AtomicReference<AgentChatResponse> responseRef = new AtomicReference<>();
        return callModel(messages, context.getAllToolSchemas(), context, callSeq, responseRef)
                .concatWith(Flux.defer(() -> {
                    AgentChatResponse response = responseRef.get();
                    ModelResponseParser parser = context.getResponseParser();
                    AgentMessage assistant = parser.parseToMessage(response);
                    if (!parser.hasToolCalls(response)) {
                        answerRef.set(assistant);
                        return Flux.empty();
                    }
                    List<AgentToolUseBlock> toolCalls = parser.extractToolCalls(response);
                    messages.add(assistant);
                    // 工具事件发射、onActing中间件包裹与连续失败熔断由父类withToolExecution统一负责，结果入列后续接下一轮
                    return withToolExecution(context, toolCalls, toolResults -> {
                        messages.addAll(toolResults);
                        return toolLoop(messages, step + 1, context, callSeq, answerRef);
                    });
                }));
    }

    /**
     * 构造最终结果事件，空结果兜底为空内容助手消息
     * @param result
     * @param context
     * @return
     */
    private AgentResultEvent resultEvent(AgentMessage result, EngineContext context) {
        AgentMessage message = result != null ? result
                : AgentMessage.builder().role(AgentMessageRole.ASSISTANT).build();
        return new AgentResultEvent(message, context.getAccumulatedUsage());
    }

    /**
     * 取消息列表中最近一条助手消息，不存在时返回空内容助手消息
     * @param messages
     * @return
     */
    private AgentMessage latestAssistantOrEmpty(List<AgentMessage> messages) {
        for (int i = messages.size() - 1; i >= 0; i--) {
            if (messages.get(i).getRole() == AgentMessageRole.ASSISTANT) {
                return messages.get(i);
            }
        }
        return AgentMessage.builder().role(AgentMessageRole.ASSISTANT).build();
    }

    /**
     * 构造基础对话：直接复用父类模板已注入系统提示词的输入消息
     * @param inputs
     * @return
     */
    private List<AgentMessage> buildConversation(List<AgentMessage> inputs) {
        List<AgentMessage> conversation = new ArrayList<>();
        if (inputs != null) {
            conversation.addAll(inputs);
        }
        return conversation;
    }

    /**
     * 提取输入中最后一条非空用户消息文本作为原问题
     * @param inputs
     * @return
     */
    private String extractQuestion(List<AgentMessage> inputs) {
        if (inputs != null) {
            for (int i = inputs.size() - 1; i >= 0; i--) {
                AgentMessage message = inputs.get(i);
                if (message.getRole() == AgentMessageRole.USER) {
                    String text = message.getTextContent();
                    if (text != null && !text.isBlank()) {
                        return text;
                    }
                }
            }
        }
        return "";
    }

    /**
     * 构造批评提示词，要求输出SATISFIED或以UNSATISFIED开头附具体修订建议
     * @param question
     * @param draft
     * @return
     */
    private String buildCritiquePrompt(String question, String draft) {
        return "你是一名严格的审稿人，请审阅下面针对原始问题给出的回答草稿。\n"
                + "若草稿已足够好、无需修改，请输出：SATISFIED\n"
                + "否则请以 UNSATISFIED 开头，输出具体的修订建议。\n"
                + "原始问题：\n" + question + "\n\n"
                + "回答草稿：\n" + draft;
    }

    /**
     * 构造修订提示词，携带原问题、当前稿与批评意见
     * @param question
     * @param draft
     * @param feedback
     * @return
     */
    private String buildRevisePrompt(String question, String draft, String feedback) {
        return "你是一名认真的作者，请根据审阅意见修订下面的回答草稿，直接输出修订后的完整回答，不要输出其他内容。\n"
                + "原始问题：\n" + question + "\n\n"
                + "回答草稿：\n" + draft + "\n\n"
                + "审阅意见：\n" + feedback;
    }

    /**
     * 判断批评文本是否满意，文本含SATISFIED且不含UNSATISFIED即视为满意，忽略大小写
     * @param critique
     * @return
     */
    private boolean isSatisfied(String critique) {
        if (critique == null || critique.isBlank()) {
            return false;
        }
        String normalized = critique.toUpperCase(Locale.ROOT);
        return normalized.contains(SATISFIED_MARKER) && !normalized.contains(UNSATISFIED_MARKER);
    }

    /**
     * 提取修订意见：剥离前导UNSATISFIED标记与其后的分隔符号，其余原文返回
     * @param critique
     * @return
     */
    private String extractFeedback(String critique) {
        if (critique == null || critique.isBlank()) {
            return "";
        }
        String trimmed = critique.trim();
        if (trimmed.toUpperCase(Locale.ROOT).startsWith(UNSATISFIED_MARKER)) {
            return trimmed.substring(UNSATISFIED_MARKER.length())
                    .replaceFirst("^[\\s:：-]+", "").trim();
        }
        return trimmed;
    }

    /**
     * 提取消息文本内容，空消息返回空串
     * @param message
     * @return
     */
    private String messageText(AgentMessage message) {
        return message != null ? message.getTextContent() : "";
    }

    /**
     * 提取响应中全部文本块内容
     * @param response
     * @return
     */
    private String textOf(AgentChatResponse response) {
        if (response == null) {
            return "";
        }
        StringBuilder text = new StringBuilder();
        for (AgentContentBlock block : response.getContent()) {
            if (block instanceof AgentTextBlock textBlock && textBlock.getText() != null) {
                text.append(textBlock.getText());
            }
        }
        return text.toString();
    }
}