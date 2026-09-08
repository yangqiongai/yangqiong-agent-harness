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
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
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
 * ReWoo范式引擎
 * <p>
 * 规划→并行执行→统一推理：先一次性规划全部工具调用步骤，参数值中可用#E1/#E2等引用前序步骤的执行结果；
 * 无依赖的工具步按上下文并发上限并行执行，依赖步骤等待其引用步骤完成后将引用替换为结果文本再执行；
 * 全部结果就绪后统一推理产出最终答案，循环主体以AgentResultEvent或ERROR事件收口。
 * 继承 AbstractAgentLoop 复用生命周期/中间件/预算基础设施。
 * </p>
 * @author yangqiong
 */
public class ReWooEngine extends AbstractAgentLoop {

    /**
     * 步骤结果引用模式，如#E1引用第1步的执行结果文本
     */
    private static final Pattern REFERENCE_PATTERN = Pattern.compile("#E(\\d+)");

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
     * 统一推理阶段的模型调用轮次
     */
    private static final int ITERATION_SOLVE = 2;

    /**
     * 范式配置
     */
    private final ParadigmOptions options;

    /**
     * 使用默认配置构建
     */
    public ReWooEngine() {
        this(new ParadigmOptions());
    }

    /**
     * 使用指定配置构建
     * @param options
     */
    public ReWooEngine(ParadigmOptions options) {
        this.options = options != null ? options : new ParadigmOptions();
    }

    /**
     * 执行ReWoo主循环：规划→依赖解析→并行执行→统一推理，生命周期管线与错误兜底由父类模板负责
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
            String userQuery = extractUserQuery(inputs);
            List<Map<String, Object>> toolSchemas = context.getAllToolSchemas(userQuery);
            return planPhase(inputs, context, userQuery, toolSchemas);
        });
    }

    /**
     * 规划阶段：携带用户目标与工具Schema一次性产出全部工具调用步骤
     * <p>
     * 规划失败（PlanParser返回null）时降级为单步直接执行，规划结果超出maxSteps时截断保护。
     * </p>
     * @param inputs
     * @param context
     * @param userQuery
     * @param toolSchemas
     * @return
     */
    private Flux<AgentEvent> planPhase(List<AgentMessage> inputs, EngineContext context,
                                       String userQuery, List<Map<String, Object>> toolSchemas) {
        List<AgentMessage> messages = buildPromptMessages(context, buildPlanPrompt(userQuery, toolSchemas));
        // 单轮迭代超时包裹规划阶段模型调用流，空闲超限时中断并发射ERROR
        return withIterationTimeout(context, withModelCallEvents(context, messages, toolSchemas,
                ITERATION_PLAN, response -> {
            String planText = context.getResponseParser().parseToMessage(response).getTextContent();
            List<PlanStep> steps = PlanParser.parse(planText);
            return Flux.defer(() -> {
                if (steps == null) {
                    return fallbackSingleStep(inputs, context, userQuery, toolSchemas);
                }
                return executePlannedSteps(context, userQuery, limitToMaxSteps(steps));
            });
        }));
    }

    /**
     * 降级单步直接执行：原始输入直接调模型一次，执行其返回的工具调用后再统一汇总
     * @param inputs
     * @param context
     * @param userQuery
     * @param toolSchemas
     * @return
     */
    private Flux<AgentEvent> fallbackSingleStep(List<AgentMessage> inputs, EngineContext context,
                                               String userQuery, List<Map<String, Object>> toolSchemas) {
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
                    .concatWith(Flux.defer(() -> solvePhase(context, userQuery, outcomes)));
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
     * 计划执行入口：解析步骤间依赖并按波次调度工具步，完成后进入统一推理
     * @param context
     * @param userQuery
     * @param steps
     * @return
     */
    private Flux<AgentEvent> executePlannedSteps(EngineContext context, String userQuery, List<PlanStep> steps) {
        return Flux.defer(() -> {
            List<PlanStep> toolSteps = steps.stream()
                    .filter(step -> PlanStep.TYPE_TOOL.equals(step.getType()))
                    .collect(Collectors.toList());
            Map<Integer, Set<Integer>> dependencies = buildDependencies(toolSteps);
            Map<Integer, String> results = new ConcurrentHashMap<>();
            List<StepOutcome> outcomes = new CopyOnWriteArrayList<>();
            return executeWaves(toolSteps, dependencies, results, outcomes, context)
                    .concatWith(Flux.defer(() -> solvePhase(context, userQuery, outcomes)));
        });
    }

    /**
     * 扫描工具步参数值中的#E引用，建立步骤到被引用步骤的依赖表
     * <p>
     * 仅保留指向其他可执行工具步的引用，引用自身或不存在步骤的引用文本在执行时原样保留。
     * </p>
     * @param toolSteps
     * @return
     */
    private Map<Integer, Set<Integer>> buildDependencies(List<PlanStep> toolSteps) {
        Set<Integer> executableIds = toolSteps.stream()
                .map(PlanStep::getStepId)
                .collect(Collectors.toSet());
        Map<Integer, Set<Integer>> dependencies = new LinkedHashMap<>();
        for (PlanStep step : toolSteps) {
            Set<Integer> references = new LinkedHashSet<>();
            collectReferences(step.getArguments(), references);
            references.remove(step.getStepId());
            references.retainAll(executableIds);
            dependencies.put(step.getStepId(), references);
        }
        return dependencies;
    }

    /**
     * 按波次调度执行工具步：依赖全部就绪的步骤进入当前波次并行执行，完成后推进下一波次
     * <p>
     * 波内并发度受上下文maxConcurrentToolCalls约束；出现互相等待的循环引用时跳过剩余步骤避免死锁。
     * </p>
     * @param pending
     * @param dependencies
     * @param results
     * @param outcomes
     * @param context
     * @return
     */
    private Flux<AgentEvent> executeWaves(List<PlanStep> pending, Map<Integer, Set<Integer>> dependencies,
                                          Map<Integer, String> results, List<StepOutcome> outcomes,
                                          EngineContext context) {
        if (pending.isEmpty()) {
            return Flux.empty();
        }
        List<PlanStep> ready = new ArrayList<>();
        for (PlanStep step : pending) {
            if (results.keySet().containsAll(dependencies.get(step.getStepId()))) {
                ready.add(step);
            }
        }
        if (ready.isEmpty()) {
            return Flux.empty();
        }
        List<PlanStep> remaining = new ArrayList<>(pending);
        remaining.removeAll(ready);
        int concurrency = resolveConcurrency(context);
        return Flux.fromIterable(ready)
                .flatMap(step -> executeToolStep(step, results, outcomes, context), concurrency)
                .concatWith(Flux.defer(() ->
                        executeWaves(remaining, dependencies, results, outcomes, context)));
    }

    /**
     * 执行单个工具步：先替换参数中的步骤引用，再交由父类withToolExecution批量执行并发射TOOL_CALL事件对
     * @param step
     * @param results
     * @param outcomes
     * @param context
     * @return
     */
    private Flux<AgentEvent> executeToolStep(PlanStep step, Map<Integer, String> results,
                                            List<StepOutcome> outcomes, EngineContext context) {
        return Flux.defer(() -> {
            @SuppressWarnings("unchecked")
            Map<String, Object> resolvedArgs = (Map<String, Object>) resolveReferences(step.getArguments(), results);
            AgentToolUseBlock toolUse = new AgentToolUseBlock(step.getToolName(),
                    "rewoo-E" + step.getStepId(), resolvedArgs);
            return withToolExecution(context, List.of(toolUse), toolResults -> {
                String resultText = toolResults.isEmpty()
                        ? "" : extractToolResultText(toolResults.get(0));
                results.put(step.getStepId(), resultText);
                outcomes.add(new StepOutcome(step.getStepId(),
                        step.getToolName(), resultText));
                return Flux.empty();
            });
        });
    }

    /**
     * 汇总阶段：以原始问题与各步骤结果清单统一推理，产出最终答案并以AgentResultEvent终止
     * @param context
     * @param userQuery
     * @param outcomes
     * @return
     */
    private Flux<AgentEvent> solvePhase(EngineContext context, String userQuery, List<StepOutcome> outcomes) {
        List<AgentMessage> messages = buildPromptMessages(context, buildSolvePrompt(userQuery, outcomes));
        // 单轮迭代超时包裹统一推理阶段模型调用流
        return withIterationTimeout(context, withModelCallEvents(context, messages, List.of(),
                ITERATION_SOLVE, response ->
                Flux.just(new AgentResultEvent(
                        context.getResponseParser().parseToMessage(response),
                        context.getAccumulatedUsage(), null))));
    }


    /**
     * 构建ReWOO规划提示词：携带用户目标与工具Schema，要求一次性输出全部步骤JSON数组
     * @param userQuery
     * @param toolSchemas
     * @return
     */
    private String buildPlanPrompt(String userQuery, List<Map<String, Object>> toolSchemas) {
        StringBuilder sb = new StringBuilder();
        sb.append("你是ReWOO规划器，请一次性规划完成用户目标所需的全部工具调用步骤，")
                .append("禁止在规划之后再调用模型做中间推理。\n");
        sb.append("输出要求：\n");
        sb.append("- 仅输出一个JSON数组，不要输出任何其他文字\n");
        sb.append("- 每个元素形如{\"tool\":\"工具名\",\"args\":{\"参数名\":\"参数值\"},\"description\":\"步骤说明\"}\n");
        sb.append("- 参数值中可使用#E1、#E2等引用第N步的执行结果文本\n");
        sb.append("- 无相互依赖的步骤将被并行执行，无需为并行性调整计划\n");
        sb.append("可用工具Schema：\n").append(serializeSchemas(toolSchemas)).append("\n");
        sb.append("用户目标：").append(userQuery);
        return sb.toString();
    }

    /**
     * 构建统一推理提示词：原始问题与各步骤结果清单
     * @param userQuery
     * @param outcomes
     * @return
     */
    private String buildSolvePrompt(String userQuery, List<StepOutcome> outcomes) {
        StringBuilder sb = new StringBuilder();
        sb.append("你是ReWOO求解器，请基于以下已执行步骤的结果回答原始问题，直接给出最终答案。\n");
        sb.append("原始问题：").append(userQuery).append("\n");
        if (outcomes.isEmpty()) {
            sb.append("无已执行步骤。\n");
        } else {
            sb.append("已执行步骤结果：\n");
            outcomes.stream()
                    .sorted(Comparator.comparingInt(StepOutcome::getStepId))
                    .forEach(outcome -> sb.append("#E").append(outcome.getStepId())
                            .append(" [").append(outcome.getToolName()).append("] ")
                            .append(outcome.getResultText()).append("\n"));
        }
        return sb.toString();
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
     * 提取用户目标：优先取最后一条用户消息文本，无用户消息时拼接全部文本
     * @param inputs
     * @return
     */
    protected String extractUserQuery(List<AgentMessage> inputs) {
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
     * 递归收集参数值中的步骤引用编号
     * @param value
     * @param references
     */
    private void collectReferences(Object value, Set<Integer> references) {
        if (value instanceof String text) {
            Matcher matcher = REFERENCE_PATTERN.matcher(text);
            while (matcher.find()) {
                references.add(Integer.parseInt(matcher.group(1)));
            }
        } else if (value instanceof Map<?, ?> map) {
            for (Object entryValue : map.values()) {
                collectReferences(entryValue, references);
            }
        } else if (value instanceof List<?> list) {
            for (Object element : list) {
                collectReferences(element, references);
            }
        }
    }

    /**
     * 递归替换参数值中的步骤引用为已完成的工具结果文本，未完成的引用原样保留
     * @param value
     * @param results
     * @return
     */
    private Object resolveReferences(Object value, Map<Integer, String> results) {
        if (value instanceof String text) {
            return replaceReferences(text, results);
        }
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> resolved = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                resolved.put(String.valueOf(entry.getKey()), resolveReferences(entry.getValue(), results));
            }
            return resolved;
        }
        if (value instanceof List<?> list) {
            List<Object> resolved = new ArrayList<>();
            for (Object element : list) {
                resolved.add(resolveReferences(element, results));
            }
            return resolved;
        }
        return value;
    }

    /**
     * 替换文本中的步骤引用，无对应结果的引用保留原文本
     * @param text
     * @param results
     * @return
     */
    private String replaceReferences(String text, Map<Integer, String> results) {
        Matcher matcher = REFERENCE_PATTERN.matcher(text);
        StringBuilder sb = new StringBuilder();
        while (matcher.find()) {
            int stepId = Integer.parseInt(matcher.group(1));
            String replacement = results.get(stepId);
            matcher.appendReplacement(sb,
                    Matcher.quoteReplacement(replacement != null ? replacement : matcher.group()));
        }
        matcher.appendTail(sb);
        return sb.toString();
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
     * 解析有效并发度，非正数回退为1
     * @param context
     * @return
     */
    private int resolveConcurrency(EngineContext context) {
        int max = context.getMaxConcurrentToolCalls();
        return max > 0 ? max : 1;
    }

    /**
     * 工具步执行结果
     * @author yangqiong
     */
    private static final class StepOutcome {

        /**
         * 步骤序号
         */
        private final int stepId;

        /**
         * 工具名称
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
         * 获取工具名称
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