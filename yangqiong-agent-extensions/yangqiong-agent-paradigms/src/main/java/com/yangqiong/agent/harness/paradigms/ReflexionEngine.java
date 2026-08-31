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
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import com.yangqiong.agent.harness.core.event.AgentEvent;
import com.yangqiong.agent.harness.core.event.AgentEventType;
import com.yangqiong.agent.harness.core.event.AgentResultEvent;
import com.yangqiong.agent.harness.core.message.AgentMessage;
import com.yangqiong.agent.harness.core.message.AgentMessageRole;
import com.yangqiong.agent.harness.core.message.AgentToolUseBlock;
import com.yangqiong.agent.harness.core.message.MessageFactory;
import com.yangqiong.agent.harness.core.model.AgentChatResponse;
import com.yangqiong.agent.harness.engine.AbstractAgentLoop;
import com.yangqiong.agent.harness.engine.EngineContext;
import com.yangqiong.agent.harness.paradigms.support.ParadigmOptions;
import com.yangqiong.agent.harness.model.ModelResponseParser;
import reactor.core.publisher.Flux;

/**
 * Reflexion反思范式引擎
 * <p>
 * 每轮先以内层简化ReAct（模型调用加工具执行交替）产出候选答案，再由模型按PASS或FAIL: 原因
 * 格式自评估；评估未通过且反思次数未耗尽时，基于评估原因与历史教训生成反思文本，
 * 以原输入附加反思文本作为下一轮输入重试，直至评估通过或反思次数用尽返回最后一次答案。
 * 继承 AbstractAgentEngine 复用生命周期/中间件/预算基础设施。
 * </p>
 * @author yangqiong
 */
public class ReflexionEngine extends AbstractAgentLoop {

    /**
     * 范式配置
     */
    private final ParadigmOptions options;

    /**
     * 使用默认配置构建
     */
    public ReflexionEngine() {
        this(new ParadigmOptions());
    }

    /**
     * 按范式配置构建
     * @param options
     */
    public ReflexionEngine(ParadigmOptions options) {
        this.options = options != null ? options : new ParadigmOptions();
    }

    /**
     * 运行反思主循环，生命周期管线与错误兜底由父类模板负责
     * @param inputs
     * @param context
     * @return
     */
    @Override
    protected Flux<AgentEvent> doRun(List<AgentMessage> inputs, EngineContext context) {
        return Flux.defer(() -> {
            if (context.getModelCaller() == null || context.getResponseParser() == null) {
                return Flux.just(AgentEvent.of(AgentEventType.ERROR,
                        new IllegalStateException("EngineContext未暴露模型调用器或响应解析器")));
            }
            // 父类模板已在输入头部注入系统提示词，直接作为基准输入
            List<AgentMessage> baseInputs = new ArrayList<>(inputs);
            List<AgentMessage> originalInputs = new ArrayList<>(inputs);
            return reflectionFlux(baseInputs, originalInputs, context, 0, new ArrayList<>());
        });
    }

    /**
     * 反思外层循环：基础执行产出候选答案，评估未通过时携带反思教训重试
     * @param baseInputs 含系统提示词的基准输入
     * @param originalInputs 原始输入（不含系统提示词）
     * @param context
     * @param attempt 当前反思轮次，从0起算
     * @param lessons 历史反思教训累积列表
     * @return
     */
    private Flux<AgentEvent> reflectionFlux(List<AgentMessage> baseInputs, List<AgentMessage> originalInputs,
                                             EngineContext context, int attempt, List<String> lessons) {
        return Flux.defer(() -> {
            List<AgentMessage> roundMessages = new ArrayList<>(baseInputs);
            if (attempt > 0 && !lessons.isEmpty()) {
                roundMessages.add(MessageFactory.createUserMessage(lessons.get(lessons.size() - 1)));
            }
            AtomicReference<AgentMessage> answerRef = new AtomicReference<>();
            return executeBaseFlux(roundMessages, context, 0, answerRef)
                    .concatWith(Flux.defer(() -> {
                        AgentMessage answer = answerRef.get() != null
                                ? answerRef.get()
                                : context.getResponseParser().parseToMessage(null);
                        return evaluateFlux(baseInputs, originalInputs, answer, context, attempt, lessons);
                    }));
        });
    }

    /**
     * 基础执行内层循环：模型调用与工具执行交替，直至产出无工具调用的候选答案，步数只受maxSteps约束
     * @param messages 本轮工作消息列表，随执行追加
     * @param context
     * @param step 当前内层步数
     * @param answerRef 候选答案输出容器
     * @return
     */
    private Flux<AgentEvent> executeBaseFlux(List<AgentMessage> messages, EngineContext context,
                                              int step, AtomicReference<AgentMessage> answerRef) {
        return Flux.defer(() -> {
            if (step >= options.getMaxSteps()) {
                answerRef.set(lastAssistantMessage(messages, context));
                return Flux.<AgentEvent>empty();
            }
            AtomicReference<AgentChatResponse> responseRef = new AtomicReference<>();
            return callModelFlux(messages, safeToolSchemas(context), context, step, responseRef)
                    .concatWith(Flux.defer(() -> {
                        AgentMessage assistant = context.getResponseParser().parseToMessage(responseRef.get());
                        messages.add(assistant);
                        List<AgentToolUseBlock> toolCalls =
                                context.getResponseParser().extractToolCalls(responseRef.get());
                        // 无工具调用或未暴露工具执行器时，当前回复即为本轮候选答案
                        if (toolCalls.isEmpty() || context.getToolExecutor() == null) {
                            answerRef.set(assistant);
                            return Flux.<AgentEvent>empty();
                        }
                        // 工具事件发射、onActing中间件包裹与连续失败熔断由父类withToolExecution统一负责
                        return withToolExecution(context, toolCalls, toolResults -> {
                            messages.addAll(toolResults);
                            return executeBaseFlux(messages, context, step + 1, answerRef);
                        });
                    }));
        });
    }

    /**
     * 评估候选答案：评估通过或反思次数耗尽时以当前答案发射AgentResultEvent终止
     * @param baseInputs
     * @param originalInputs
     * @param answer
     * @param context
     * @param attempt
     * @param lessons
     * @return
     */
    private Flux<AgentEvent> evaluateFlux(List<AgentMessage> baseInputs, List<AgentMessage> originalInputs,
                                           AgentMessage answer, EngineContext context, int attempt,
                                           List<String> lessons) {
        AtomicReference<AgentChatResponse> evalRef = new AtomicReference<>();
        List<AgentMessage> evalMessages = new ArrayList<>(originalInputs);
        evalMessages.add(answer);
        evalMessages.add(MessageFactory.createUserMessage(buildEvaluationPrompt(
                extractUserText(originalInputs), answer.getTextContent())));
        return callModelFlux(evalMessages, List.of(), context, attempt, evalRef)
                .concatWith(Flux.defer(() -> {
                    String verdict = extractText(evalRef.get(), context.getResponseParser());
                    // 评估通过或反思次数用尽，均以当前答案收口且不报错
                    if (isPass(verdict) || attempt >= options.getMaxReflections()) {
                        return Flux.just(buildResultEvent(answer, context));
                    }
                    return reflectAndRetryFlux(baseInputs, originalInputs, context, attempt, lessons,
                            extractFailReason(verdict));
                }));
    }

    /**
     * 生成反思文本并作为附加用户消息注入下一轮输入后重试
     * @param baseInputs
     * @param originalInputs
     * @param context
     * @param attempt
     * @param lessons
     * @param reason 评估失败原因
     * @return
     */
    private Flux<AgentEvent> reflectAndRetryFlux(List<AgentMessage> baseInputs,
                                                  List<AgentMessage> originalInputs, EngineContext context,
                                                  int attempt, List<String> lessons, String reason) {
        AtomicReference<AgentChatResponse> reflectRef = new AtomicReference<>();
        List<AgentMessage> reflectMessages = new ArrayList<>();
        reflectMessages.add(MessageFactory.createUserMessage(buildReflectionPrompt(reason, lessons)));
        return callModelFlux(reflectMessages, List.of(), context, attempt, reflectRef)
                .concatWith(Flux.defer(() -> {
                    String reflectionText = extractText(reflectRef.get(), context.getResponseParser());
                    if (reflectionText == null || reflectionText.isBlank()) {
                        reflectionText = "评估未通过: " + reason;
                    }
                    // 反思文本累积为历史教训，仅注入下一轮模型输入，不写入上下文内部状态
                    lessons.add(reflectionText);
                    return reflectionFlux(baseInputs, originalInputs, context, attempt + 1, lessons);
                }));
    }

    /**
     * 发起单次模型调用，经父类助手发射MODEL_CALL_START/END事件并把合并响应写入responseRef
     * <p>
     * 单次模型调用流以父类withIterationTimeout包裹，空闲超过单轮迭代超时时中断并发射ERROR。
     * </p>
     * @param messages
     * @param toolSchemas
     * @param context
     * @param iter 事件携带的迭代标识，仅在本地计数
     * @param responseRef 响应输出容器
     * @return
     */
    private Flux<AgentEvent> callModelFlux(List<AgentMessage> messages, List<Map<String, Object>> toolSchemas,
                                            EngineContext context, int iter,
                                            AtomicReference<AgentChatResponse> responseRef) {
        // 单轮迭代超时包裹单次模型调用流
        return withIterationTimeout(context, withModelCallEvents(context, messages, toolSchemas, iter,
                response -> {
                    responseRef.set(response);
                    return Flux.empty();
                }));
    }

    /**
     * 读取工具Schema列表，未暴露时返回空列表
     * @param context
     * @return
     */
    private List<Map<String, Object>> safeToolSchemas(EngineContext context) {
        List<Map<String, Object>> schemas = context.getAllToolSchemas();
        return schemas != null ? schemas : List.of();
    }

    /**
     * 查找消息列表中最后一条assistant消息，缺失时返回空assistant消息
     * @param messages
     * @param context
     * @return
     */
    private AgentMessage lastAssistantMessage(List<AgentMessage> messages, EngineContext context) {
        for (int i = messages.size() - 1; i >= 0; i--) {
            AgentMessage message = messages.get(i);
            if (message != null && message.getRole() == AgentMessageRole.ASSISTANT) {
                return message;
            }
        }
        return context.getResponseParser().parseToMessage(null);
    }

    /**
     * 构建AgentResultEvent结果事件，usage为各次模型调用的累计合并值
     * @param answer
     * @param context
     * @return
     */
    private AgentEvent buildResultEvent(AgentMessage answer, EngineContext context) {
        return new AgentResultEvent(answer, context.getAccumulatedUsage());
    }

    /**
     * 提取模型响应文本内容
     * @param response
     * @param parser
     * @return
     */
    private String extractText(AgentChatResponse response, ModelResponseParser parser) {
        return parser.parseToMessage(response).getTextContent();
    }

    /**
     * 提取消息列表中的用户文本，多个用户消息以换行拼接
     * @param messages
     * @return
     */
    private String extractUserText(List<AgentMessage> messages) {
        StringBuilder text = new StringBuilder();
        for (AgentMessage message : messages) {
            if (message == null || message.getRole() != AgentMessageRole.USER) {
                continue;
            }
            String content = message.getTextContent();
            if (!content.isBlank()) {
                if (text.length() > 0) {
                    text.append("\n");
                }
                text.append(content);
            }
        }
        return text.toString();
    }

    /**
     * 判断评估结论是否通过，容错规则为文本含PASS且不含FAIL
     * @param verdict
     * @return
     */
    private boolean isPass(String verdict) {
        if (verdict == null || verdict.isBlank()) {
            return false;
        }
        String normalized = verdict.toUpperCase(Locale.ROOT);
        return normalized.contains("PASS") && !normalized.contains("FAIL");
    }

    /**
     * 提取评估失败原因，取FAIL之后的文本，兼容半角与全角冒号
     * @param verdict
     * @return
     */
    private String extractFailReason(String verdict) {
        String trimmed = verdict != null ? verdict.trim() : "";
        if (trimmed.isEmpty()) {
            return "评估输出为空";
        }
        int failIdx = trimmed.toUpperCase(Locale.ROOT).indexOf("FAIL");
        if (failIdx >= 0) {
            String reason = trimmed.substring(failIdx + 4).trim();
            if (reason.startsWith(":") || reason.startsWith("：")) {
                reason = reason.substring(1).trim();
            }
            if (!reason.isEmpty()) {
                return reason;
            }
        }
        return trimmed;
    }

    /**
     * 构建评估提示词，要求按PASS或FAIL: 原因格式输出
     * @param question
     * @param answer
     * @return
     */
    private String buildEvaluationPrompt(String question, String answer) {
        return "请评估以下回答是否正确且完整地解决了用户问题。\n"
                + "\n用户问题:\n" + question
                + "\n待评估回答:\n" + answer
                + "\n请严格按以下格式输出评估结论，不要输出任何其他内容:\nPASS\n或\nFAIL: 失败原因";
    }

    /**
     * 构建反思提示词，综合评估失败原因与历史教训
     * @param reason
     * @param lessons
     * @return
     */
    private String buildReflectionPrompt(String reason, List<String> lessons) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("你是反思总结助手，此前的回答未通过评估，请总结教训指导后续改进。\n");
        prompt.append("\n本次评估失败原因:\n").append(reason);
        prompt.append("\n\n历史教训:\n");
        if (lessons.isEmpty()) {
            prompt.append("无\n");
        } else {
            for (String lesson : lessons) {
                prompt.append("- ").append(lesson).append("\n");
            }
        }
        prompt.append("\n请输出简明反思总结，指出下次回答必须避免的问题与改进方向。");
        return prompt.toString();
    }
}