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
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yangqiongai.agent.harness.core.event.AgentEvent;
import com.yangqiongai.agent.harness.core.event.AgentEventType;
import com.yangqiongai.agent.harness.core.event.AgentResultEvent;
import com.yangqiongai.agent.harness.core.message.AgentContentBlock;
import com.yangqiongai.agent.harness.core.message.AgentMessage;
import com.yangqiongai.agent.harness.core.message.AgentMessageRole;
import com.yangqiongai.agent.harness.core.message.AgentToolResultBlock;
import com.yangqiongai.agent.harness.core.message.AgentToolUseBlock;
import com.yangqiongai.agent.harness.core.message.MessageFactory;
import com.yangqiongai.agent.harness.engine.AbstractAgentLoop;
import com.yangqiongai.agent.harness.engine.EngineContext;
import com.yangqiongai.agent.harness.paradigms.support.ParadigmOptions;
import com.yangqiongai.agent.harness.paradigms.support.PlanParser;
import com.yangqiongai.agent.harness.paradigms.support.PlanStep;
import reactor.core.publisher.Flux;

/**
 * 计划-执行范式引擎
 * <p>
 * 规划→按序执行→汇总：先由模型一次性输出JSON数组计划，步骤分为工具步与推理步，按计划顺序逐步执行；
 * 工具步构造工具调用块交由工具执行器执行，推理步调用模型结合前序结果推理，
 * 计划步数超出maxSteps时截断保护；全部步骤完成后以原始任务与各步骤结果统一调用模型产出最终答案。
 * 规划解析失败时降级为单轮直接执行：原始输入再调一次模型，有工具调用则执行后汇总，等价单轮ReAct。
 * 继承 AbstractAgentEngine 复用生命周期/中间件/预算基础设施，循环主体以AgentResultEvent或ERROR事件收口。
 * </p>
 * @author yangqiong
 */
public class PlanExecuteEngine extends AbstractAgentLoop {

    /**
     * JSON序列化器，构建提示词时序列化工具Schema
     */
    private static final ObjectMapper MAPPER = new ObjectMapper();

    /**
     * 规划阶段模型调用轮次
     */
    private static final int ITERATION_PLAN = 1;

    /**
     * 规划解析失败后降级单步直接执行的模型调用轮次
     */
    private static final int ITERATION_DIRECT = 1;

    /**
     * 推理步的模型调用轮次
     */
    private static final int ITERATION_REASON = 2;

    /**
     * 汇总阶段的模型调用轮次
     */
    private static final int ITERATION_SUMMARY = 3;

    /**
     * 范式配置
     */
    private final ParadigmOptions options;

    /**
     * 使用默认配置构建
     */
    public PlanExecuteEngine() {
        this(new ParadigmOptions());
    }

    /**
     * 使用指定配置构建
     * @param options
     */
    public PlanExecuteEngine(ParadigmOptions options) {
        this.options = options != null ? options : new ParadigmOptions();
    }

    /**
     * 执行计划-执行主循环：规划→按序执行→汇总，生命周期管线与错误兜底由父类模板负责
     * @param inputs
     * @param context
     * @return
     */
    @Override
    protected Flux<AgentEvent> doRun(List<AgentMessage> inputs, EngineContext context) {
        return Flux.defer(() -> {
            if (context.getModelCaller() == null || context.getToolExecutor() == null
                    || context.getResponseParser() == null) {
                return Flux.just(AgentEvent.of(AgentEventType.ERROR,
                        new IllegalStateException("EngineContext未暴露模型调用器、工具执行器或响应解析器")));
            }
            String task = extractTask(inputs);
            List<Map<String, Object>> toolSchemas = context.getAllToolSchemas(task);
            return planPhase(inputs, context, task, toolSchemas);
        });
    }

    /**
     * 规划阶段：携带原始任务与工具Schema产出全部步骤的JSON数组计划
     * <p>
     * 解析失败（PlanParser返回null）时降级为单步直接执行，计划步数超出maxSteps时截断保护。
     * </p>
     * @param inputs
     * @param context
     * @param task
     * @param toolSchemas
     * @return
     */
    private Flux<AgentEvent> planPhase(List<AgentMessage> inputs, EngineContext context,
                                       String task, List<Map<String, Object>> toolSchemas) {
        List<AgentMessage> messages = buildPromptMessages(context, buildPlanPrompt(task, toolSchemas));
        // 单轮迭代超时包裹规划阶段模型调用流，空闲超限时中断并发射ERROR
        return withIterationTimeout(context, withModelCallEvents(context, messages, toolSchemas,
                ITERATION_PLAN, response -> {
            String planText = context.getResponseParser().parseToMessage(response).getTextContent();
            List<PlanStep> steps = PlanParser.parse(planText);
            return Flux.defer(() -> {
                if (steps == null) {
                    return fallbackSingleStep(inputs, context, task, toolSchemas);
                }
                return executePlannedSteps(context, task, limitToMaxSteps(steps));
            });
        }));
    }

    /**
     * 降级单步直接执行：原始输入再调一次模型，有工具调用则执行后汇总，无工具调用直接以响应收尾
     * @param inputs
     * @param context
     * @param task
     * @param toolSchemas
     * @return
     */
    private Flux<AgentEvent> fallbackSingleStep(List<AgentMessage> inputs, EngineContext context,
                                                String task, List<Map<String, Object>> toolSchemas) {
        List<AgentMessage> messages = buildFallbackMessages(inputs);
        // 单轮迭代超时包裹降级路径模型调用流
        return withIterationTimeout(context, withModelCallEvents(context, messages, toolSchemas,
                ITERATION_DIRECT, response -> {
            List<AgentToolUseBlock> toolCalls = context.getResponseParser().extractToolCalls(response);
            if (toolCalls.isEmpty()) {
                return Flux.just(new AgentResultEvent(
                        context.getResponseParser().parseToMessage(response),
                        context.getAccumulatedUsage(), null));
            }
            List<StepOutcome> outcomes = new CopyOnWriteArrayList<>();
            return executeFallbackTools(toolCalls, outcomes, context)
                    .concatWith(Flux.defer(() -> summaryPhase(context, task, outcomes)));
        }));
    }

    /**
     * 降级路径批量执行模型返回的工具调用
     * <p>
     * 工具调用事件对、onActing中间件包裹与连续失败熔断由父类withToolExecution统一负责，
     * 工具结果按调用次序记入步骤结果清单。
     * </p>
     * @param toolCalls
     * @param outcomes
     * @param context
     * @return
     */
    private Flux<AgentEvent> executeFallbackTools(List<AgentToolUseBlock> toolCalls,
                                                  List<StepOutcome> outcomes, EngineContext context) {
        return withToolExecution(context, toolCalls, toolResults -> {
            for (int i = 0; i < toolResults.size(); i++) {
                outcomes.add(new StepOutcome(i + 1,
                        toolCalls.get(i).getToolName(),
                        extractToolResultText(toolResults.get(i))));
            }
            return Flux.empty();
        });
    }

    /**
     * 计划执行入口：按计划顺序逐步执行各步骤，完成后进入汇总
     * @param context
     * @param task
     * @param steps
     * @return
     */
    private Flux<AgentEvent> executePlannedSteps(EngineContext context, String task, List<PlanStep> steps) {
        List<StepOutcome> outcomes = new CopyOnWriteArrayList<>();
        return executeSteps(steps, outcomes, context)
                .concatWith(Flux.defer(() -> summaryPhase(context, task, outcomes)));
    }

    /**
     * 按计划顺序串行执行步骤，工具步经工具执行器执行，推理步调用模型推理
     * @param steps
     * @param outcomes
     * @param context
     * @return
     */
    private Flux<AgentEvent> executeSteps(List<PlanStep> steps, List<StepOutcome> outcomes, EngineContext context) {
        if (steps.isEmpty()) {
            return Flux.empty();
        }
        PlanStep current = steps.get(0);
        List<PlanStep> remaining = steps.subList(1, steps.size());
        Flux<AgentEvent> currentFlux = PlanStep.TYPE_TOOL.equals(current.getType())
                ? executeToolStep(current, outcomes, context)
                : executeReasonStep(current, outcomes, context);
        return currentFlux.concatWith(Flux.defer(() -> executeSteps(remaining, outcomes, context)));
    }

    /**
     * 执行单个工具步：构造工具调用块交由父类withToolExecution执行并发射TOOL_CALL事件对
     * @param step
     * @param outcomes
     * @param context
     * @return
     */
    private Flux<AgentEvent> executeToolStep(PlanStep step, List<StepOutcome> outcomes, EngineContext context) {
        return Flux.defer(() -> {
            AgentToolUseBlock toolUse = new AgentToolUseBlock(step.getToolName(),
                    "plan-E" + step.getStepId(), step.getArguments());
            return withToolExecution(context, List.of(toolUse), toolResults -> {
                String resultText = toolResults.isEmpty()
                        ? "" : extractToolResultText(toolResults.get(0));
                outcomes.add(new StepOutcome(step.getStepId(),
                        step.getToolName(), resultText));
                return Flux.empty();
            });
        });
    }

    /**
     * 执行单个推理步：以推理目标与前序步骤结果调用模型，推理文本记录为该步结果
     * @param step
     * @param outcomes
     * @param context
     * @return
     */
    private Flux<AgentEvent> executeReasonStep(PlanStep step, List<StepOutcome> outcomes, EngineContext context) {
        return Flux.defer(() -> {
            List<AgentMessage> messages = buildPromptMessages(context, buildReasonPrompt(step, outcomes));
            // 单轮迭代超时包裹推理步模型调用流
            return withIterationTimeout(context, withModelCallEvents(context, messages, List.of(),
                    ITERATION_REASON, response -> {
                String reasoning = context.getResponseParser().parseToMessage(response).getTextContent();
                outcomes.add(new StepOutcome(step.getStepId(), PlanStep.TYPE_REASON, reasoning));
                return Flux.empty();
            }));
        });
    }

    /**
     * 汇总阶段：以原始任务与各步骤结果清单统一推理，产出最终答案并以AgentResultEvent终止
     * @param context
     * @param task
     * @param outcomes
     * @return
     */
    private Flux<AgentEvent> summaryPhase(EngineContext context, String task, List<StepOutcome> outcomes) {
        List<AgentMessage> messages = buildPromptMessages(context, buildSummaryPrompt(task, outcomes));
        // 单轮迭代超时包裹汇总阶段模型调用流
        return withIterationTimeout(context, withModelCallEvents(context, messages, List.of(),
                ITERATION_SUMMARY, response ->
                Flux.just(new AgentResultEvent(
                        context.getResponseParser().parseToMessage(response),
                        context.getAccumulatedUsage(), null))));
    }


    /**
     * 构建规划提示词：携带原始任务与工具Schema，要求输出工具步或推理步的JSON数组计划
     * @param task
     * @param toolSchemas
     * @return
     */
    private String buildPlanPrompt(String task, List<Map<String, Object>> toolSchemas) {
        StringBuilder sb = new StringBuilder();
        sb.append("你是计划规划器，请将完成用户任务的过程拆解为按序执行的步骤计划。\n");
        sb.append("输出要求：\n");
        sb.append("- 仅输出一个JSON数组，不要输出任何其他文字\n");
        sb.append("- 工具步形如{\"type\":\"tool\",\"tool\":\"工具名\",\"args\":{\"参数名\":\"参数值\"},\"desc\":\"步骤说明\"}\n");
        sb.append("- 推理步形如{\"type\":\"reason\",\"desc\":\"推理目标说明\"}，表示调用模型结合前序结果做中间推理\n");
        sb.append("- 步骤按执行顺序排列，无需规划即可直接回答的任务可输出极简计划\n");
        sb.append("可用工具Schema：\n").append(serializeSchemas(toolSchemas)).append("\n");
        sb.append("用户任务：").append(task);
        return sb.toString();
    }

    /**
     * 构建推理步提示词：推理目标与已执行步骤结果
     * @param step
     * @param outcomes
     * @return
     */
    private String buildReasonPrompt(PlanStep step, List<StepOutcome> outcomes) {
        StringBuilder sb = new StringBuilder();
        sb.append("你是计划执行中的推理器，请基于已执行步骤的结果完成当前推理目标，输出推理结论。\n");
        sb.append("当前推理目标：").append(step.getDescription()).append("\n");
        appendOutcomes(sb, outcomes);
        return sb.toString();
    }

    /**
     * 构建汇总提示词：原始任务与各步骤结果清单
     * @param task
     * @param outcomes
     * @return
     */
    private String buildSummaryPrompt(String task, List<StepOutcome> outcomes) {
        StringBuilder sb = new StringBuilder();
        sb.append("你是计划执行汇总器，请基于以下已执行步骤的结果完成原始任务，直接给出最终答案。\n");
        sb.append("原始任务：").append(task).append("\n");
        appendOutcomes(sb, outcomes);
        return sb.toString();
    }

    /**
     * 追加已执行步骤结果清单到提示词
     * @param sb
     * @param outcomes
     */
    private void appendOutcomes(StringBuilder sb, List<StepOutcome> outcomes) {
        if (outcomes.isEmpty()) {
            sb.append("无已执行步骤。\n");
            return;
        }
        sb.append("已执行步骤结果：\n");
        for (StepOutcome outcome : outcomes) {
            sb.append("#E").append(outcome.getStepId())
                    .append(" [").append(outcome.getToolName()).append("] ")
                    .append(outcome.getResultText()).append("\n");
        }
    }

    /**
     * 序列化工具Schema列表，失败时回退toString
     * @param toolSchemas
     * @return
     */
    private String serializeSchemas(List<Map<String, Object>> toolSchemas) {
        if (toolSchemas == null || toolSchemas.isEmpty()) {
            return "[]";
        }
        try {
            return MAPPER.writeValueAsString(toolSchemas);
        } catch (Exception e) {
            return toolSchemas.toString();
        }
    }

    /**
     * 构建提示词消息：可选系统提示词加用户提示词
     * @param context
     * @param prompt
     * @return
     */
    private List<AgentMessage> buildPromptMessages(EngineContext context, String prompt) {
        List<AgentMessage> messages = new ArrayList<>();
        String systemPrompt = context.getSystemPrompt();
        if (systemPrompt != null && !systemPrompt.isBlank()) {
            messages.add(MessageFactory.createSystemMessage(systemPrompt));
        }
        messages.add(MessageFactory.createUserMessage(prompt));
        return messages;
    }

    /**
     * 构建降级路径消息：直接复用父类模板已注入系统提示词的原始输入
     * @param inputs
     * @return
     */
    private List<AgentMessage> buildFallbackMessages(List<AgentMessage> inputs) {
        return new ArrayList<>(inputs);
    }

    /**
     * 提取原始任务：优先取最后一条用户消息文本，无用户消息时拼接全部文本
     * @param inputs
     * @return
     */
    private String extractTask(List<AgentMessage> inputs) {
        String lastUser = null;
        for (AgentMessage message : inputs) {
            if (message.getRole() == AgentMessageRole.USER) {
                lastUser = message.getTextContent();
            }
        }
        if (lastUser != null && !lastUser.isBlank()) {
            return lastUser;
        }
        return inputs.stream()
                .map(AgentMessage::getTextContent)
                .filter(text -> text != null && !text.isBlank())
                .collect(Collectors.joining("\n"));
    }

    /**
     * 按maxSteps截断计划，超出部分丢弃
     * @param steps
     * @return
     */
    private List<PlanStep> limitToMaxSteps(List<PlanStep> steps) {
        int maxSteps = options.getMaxSteps();
        if (steps.size() <= maxSteps) {
            return steps;
        }
        return steps.subList(0, maxSteps);
    }

    /**
     * 提取工具结果消息的文本内容，优先取工具结果块的文本
     * @param toolResult
     * @return
     */
    private String extractToolResultText(AgentMessage toolResult) {
        if (toolResult == null) {
            return "";
        }
        for (AgentContentBlock block : toolResult.getContent()) {
            if (block instanceof AgentToolResultBlock resultBlock) {
                return resultBlock.getTextContent();
            }
        }
        return toolResult.getTextContent();
    }


    /**
     * 步骤执行结果
     * @author yangqiong
     */
    private static final class StepOutcome {

        /**
         * 步骤序号
         */
        private final int stepId;

        /**
         * 步骤执行体名称，工具步为工具名，推理步固定为reason
         */
        private final String toolName;

        /**
         * 结果文本
         */
        private final String resultText;

        private StepOutcome(int stepId, String toolName, String resultText) {
            this.stepId = stepId;
            this.toolName = toolName;
            this.resultText = resultText;
        }

        /**
         * 获取步骤序号
         * @return
         */
        private int getStepId() {
            return stepId;
        }

        /**
         * 获取步骤执行体名称
         * @return
         */
        private String getToolName() {
            return toolName;
        }

        /**
         * 获取结果文本
         * @return
         */
        private String getResultText() {
            return resultText;
        }
    }
}