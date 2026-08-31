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
package com.yangqiong.agent.harness.paradigms;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yangqiong.agent.harness.core.event.AgentEvent;
import com.yangqiong.agent.harness.core.event.AgentEventType;
import com.yangqiong.agent.harness.core.event.AgentResultEvent;
import com.yangqiong.agent.harness.core.message.AgentContentBlock;
import com.yangqiong.agent.harness.core.message.AgentMessage;
import com.yangqiong.agent.harness.core.message.AgentMessageRole;
import com.yangqiong.agent.harness.core.message.AgentTextBlock;
import com.yangqiong.agent.harness.core.message.AgentToolUseBlock;
import com.yangqiong.agent.harness.core.message.MessageFactory;
import com.yangqiong.agent.harness.core.model.AgentChatResponse;
import com.yangqiong.agent.harness.engine.AbstractAgentLoop;
import com.yangqiong.agent.harness.engine.EngineContext;
import com.yangqiong.agent.harness.paradigms.support.ParadigmOptions;
import com.yangqiong.agent.harness.model.ModelResponseParser;

import reactor.core.publisher.Flux;

/**
 * Self-Ask自问自答范式引擎
 * <p>
 * 先将原问题拆解为子问题列表，逐个子问题带工具调用模型收集答案，
 * 最终汇总各子问题答案产出原问题的最终答案；拆解失败时降级为直接回答原问题。
 * 继承 AbstractAgentEngine 复用生命周期/中间件/预算基础设施。
 * </p>
 * @author yangqiong
 */
public class SelfAskEngine extends AbstractAgentLoop {

    /**
     * 子问题解析Jackson实例，只读解析场景线程安全
     */
    private static final ObjectMapper MAPPER = new ObjectMapper();

    /**
     * 范式配置
     */
    private final ParadigmOptions options;

    /**
     * 使用全默认配置构建
     */
    public SelfAskEngine() {
        this(new ParadigmOptions());
    }

    /**
     * 按指定配置构建
     * @param options
     */
    public SelfAskEngine(ParadigmOptions options) {
        this.options = options != null ? options : new ParadigmOptions();
    }

    /**
     * 将子问题数量限制在最大步数内，防止拆解出过多子问题导致循环失控
     * @param subQuestions
     * @return
     */
    private List<String> capByMaxSteps(List<String> subQuestions) {
        int maxSteps = options.getMaxSteps();
        if (subQuestions.size() > maxSteps) {
            return new ArrayList<>(subQuestions.subList(0, Math.max(maxSteps, 0)));
        }
        return subQuestions;
    }

    /**
     * 运行Self-Ask主循环：拆解子问题、逐个带工具作答、汇总最终答案，生命周期管线由父类模板负责
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
                        "EngineContext未暴露模型调用器、工具执行器或响应解析器，无法执行Self-Ask循环")));
            }
            List<AgentMessage> conversation = buildConversation(inputs);
            String question = extractQuestion(inputs);
            AtomicInteger callSeq = new AtomicInteger(0);
            AtomicReference<AgentChatResponse> decomposeResponse = new AtomicReference<>();
            List<AgentMessage> decomposeMessages = new ArrayList<>(conversation);
            decomposeMessages.add(MessageFactory.createUserMessage(buildDecomposePrompt(question)));
            return callModel(decomposeMessages, List.of(), context, callSeq, decomposeResponse)
                    .concatWith(Flux.defer(() -> {
                        // 子问题数量受最大步数保护，防止拆解出过多子问题导致循环失控
                        List<String> subQuestions =
                                capByMaxSteps(parseSubQuestions(textOf(decomposeResponse.get())));
                        if (subQuestions.isEmpty()) {
                            // 拆解失败降级为直接回答原问题
                            return answerDirectly(conversation, context, callSeq);
                        }
                        return answerSubQuestions(conversation, question, subQuestions, 0,
                                new ArrayList<>(), context, callSeq);
                    }));
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
     * 降级路径：在原对话上直接执行带工具的回答循环，以得到的答案为最终结果
     * @param conversation
     * @param context
     * @param callSeq
     * @return
     */
    private Flux<AgentEvent> answerDirectly(List<AgentMessage> conversation, EngineContext context,
                                            AtomicInteger callSeq) {
        AtomicReference<AgentMessage> answerRef = new AtomicReference<>();
        return toolLoop(new ArrayList<>(conversation), 0, context, callSeq, answerRef)
                .concatWith(Flux.defer(() ->
                        Flux.just(resultEvent(answerRef.get(), context))));
    }

    /**
     * 逐个子问题执行带工具的回答循环并收集答案，全部完成后进入汇总阶段
     * @param conversation
     * @param question
     * @param subQuestions
     * @param index
     * @param answers
     * @param context
     * @param callSeq
     * @return
     */
    private Flux<AgentEvent> answerSubQuestions(List<AgentMessage> conversation, String question,
                                                List<String> subQuestions, int index, List<String> answers,
                                                EngineContext context, AtomicInteger callSeq) {
        if (index >= subQuestions.size()) {
            return aggregateAnswer(conversation, question, subQuestions, answers, context, callSeq);
        }
        AtomicReference<AgentMessage> answerRef = new AtomicReference<>();
        List<AgentMessage> messages = new ArrayList<>(conversation);
        messages.add(MessageFactory.createUserMessage(subQuestions.get(index)));
        return toolLoop(messages, 0, context, callSeq, answerRef)
                .concatWith(Flux.defer(() -> {
                    answers.add(messageText(answerRef.get()));
                    return answerSubQuestions(conversation, question, subQuestions, index + 1,
                            answers, context, callSeq);
                }));
    }

    /**
     * 汇总阶段：以原问题与各子问题答案构造汇总提示词，产出最终答案
     * @param conversation
     * @param question
     * @param subQuestions
     * @param answers
     * @param context
     * @param callSeq
     * @return
     */
    private Flux<AgentEvent> aggregateAnswer(List<AgentMessage> conversation, String question,
                                             List<String> subQuestions, List<String> answers,
                                             EngineContext context, AtomicInteger callSeq) {
        List<AgentMessage> messages = new ArrayList<>(conversation);
        messages.add(MessageFactory.createUserMessage(
                buildAggregatePrompt(question, subQuestions, answers)));
        AtomicReference<AgentChatResponse> responseRef = new AtomicReference<>();
        return callModel(messages, List.of(), context, callSeq, responseRef)
                .concatWith(Flux.defer(() -> Flux.just(resultEvent(
                        context.getResponseParser().parseToMessage(responseRef.get()), context))));
    }

    /**
     * 带工具的回答循环：模型返回工具调用则执行后继续，无工具调用时产出答案，受maxSteps上限保护
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
            // 步数耗尽防死循环，以最近一条助手消息兜底作为答案
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
     * 构造子问题拆解提示词，要求模型仅输出子问题JSON字符串数组
     * @param question
     * @return
     */
    private String buildDecomposePrompt(String question) {
        return "请将下面的问题拆解为若干可直接回答的子问题，只输出一个JSON字符串数组，"
                + "数组元素为子问题文本并按回答顺序排列，不要输出数组以外的任何内容。\n"
                + "问题：\n" + question;
    }

    /**
     * 构造汇总提示词，携带原问题与各子问题及对应答案
     * @param question
     * @param subQuestions
     * @param answers
     * @return
     */
    private String buildAggregatePrompt(String question, List<String> subQuestions, List<String> answers) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("请根据以下子问题及对应答案回答原始问题，直接给出最终答案，不要输出无关内容。\n")
                .append("原始问题：\n").append(question).append('\n');
        for (int i = 0; i < subQuestions.size(); i++) {
            prompt.append("子问题：").append(subQuestions.get(i)).append('\n')
                    .append("答案：").append(i < answers.size() ? answers.get(i) : "").append('\n');
        }
        return prompt.toString();
    }

    /**
     * 解析模型输出的子问题JSON数组，兼容markdown围栏、前后缀噪声与对象包装结构，
     * 彻底解析失败时返回空列表供调用方降级
     * @param raw
     * @return
     */
    private List<String> parseSubQuestions(String raw) {
        if (raw == null || raw.isBlank()) {
            return List.of();
        }
        String fragment = extractJsonFragment(raw);
        if (fragment == null) {
            return List.of();
        }
        try {
            return readSubQuestions(MAPPER.readTree(fragment));
        } catch (Exception e) {
            return List.of();
        }
    }

    /**
     * 将JSON节点转换为子问题列表，兼容数组与subQuestions/questions包装对象
     * @param root
     * @return
     */
    private List<String> readSubQuestions(JsonNode root) {
        List<String> subQuestions = new ArrayList<>();
        if (root == null) {
            return subQuestions;
        }
        if (root.isArray()) {
            for (JsonNode node : root) {
                String question = nodeToQuestion(node);
                if (question != null && !question.isBlank()) {
                    subQuestions.add(question.trim());
                }
            }
            return subQuestions;
        }
        if (root.isObject()) {
            JsonNode wrapped = root.get("subQuestions");
            if (wrapped == null) {
                wrapped = root.get("questions");
            }
            if (wrapped != null && wrapped.isArray()) {
                return readSubQuestions(wrapped);
            }
        }
        return subQuestions;
    }

    /**
     * 将JSON元素转换为子问题文本，字符串元素直接取值，对象元素兼容question等常见字段
     * @param node
     * @return
     */
    private String nodeToQuestion(JsonNode node) {
        if (node == null) {
            return null;
        }
        if (node.isTextual()) {
            return node.asText();
        }
        if (node.isObject()) {
            for (String field : new String[]{"question", "subQuestion", "sub_question", "text"}) {
                JsonNode value = node.get(field);
                if (value != null && value.isTextual() && !value.asText().isBlank()) {
                    return value.asText();
                }
            }
        }
        return null;
    }

    /**
     * 截取首个花括号或方括号起的JSON片段，括号配对扫描并跳过字符串字面量内的括号与转义
     * @param text
     * @return
     */
    private String extractJsonFragment(String text) {
        int start = -1;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '{' || c == '[') {
                start = i;
                break;
            }
        }
        if (start < 0) {
            return null;
        }
        int depth = 0;
        boolean inString = false;
        boolean escaped = false;
        for (int i = start; i < text.length(); i++) {
            char c = text.charAt(i);
            if (inString) {
                if (escaped) {
                    escaped = false;
                } else if (c == '\\') {
                    escaped = true;
                } else if (c == '"') {
                    inString = false;
                }
                continue;
            }
            if (c == '"') {
                inString = true;
            } else if (c == '{' || c == '[') {
                depth++;
            } else if (c == '}' || c == ']') {
                depth--;
                if (depth == 0) {
                    return text.substring(start, i + 1);
                }
            }
        }
        // 括号未闭合时截取到文本末尾，交由Jackson判定
        return text.substring(start);
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