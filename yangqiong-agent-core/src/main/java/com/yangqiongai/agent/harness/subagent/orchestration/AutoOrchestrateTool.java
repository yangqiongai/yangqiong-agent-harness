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
package com.yangqiongai.agent.harness.subagent.orchestration;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.yangqiongai.agent.harness.subagent.SubagentSpawner;
import com.yangqiongai.agent.harness.engine.AgentRuntimeContext;
import com.yangqiongai.agent.harness.core.message.AgentTextBlock;
import com.yangqiongai.agent.harness.core.message.AgentToolResultBlock;
import com.yangqiongai.agent.harness.core.tool.AgentTool;
import com.yangqiongai.agent.harness.core.tool.AgentToolCallParam;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

/**
 * 自动编排工具
 * <p>
 * 暴露给主代理LLM调用，LLM自主决定何时启用多子代理协作。
 * 调用后触发AutoOrchestrationEngine执行任务分解、子代理执行、结果汇总。
 * parentCtx由SubagentsMiddleware在运行时注入（非build时）。
 * 递归编排受maxDepth限制，超过深度拒绝执行。
 * </p>
 * @author yangqiong
 */
public class AutoOrchestrateTool implements AgentTool {

    private static final Logger log = LoggerFactory.getLogger(AutoOrchestrateTool.class);

    /**
     * 编排轮次默认上限，未显式配置时生效，避免LLM指定超大轮次导致无限迭代
     */
    private static final int DEFAULT_MAX_ROUNDS = 8;

    /**
     * 子代理执行器
     */
    private final SubagentSpawner spawner;

    /**
     * 子代理声明列表
     */
    private final List<SubagentDeclaration> declarations;

    /**
     * 自动编排引擎
     */
    private final AutoOrchestrationEngine engine;

    /**
     * 最大嵌套深度
     */
    private final int maxDepth;

    /**
     * 是否允许子代理递归编排
     */
    private final boolean allowRecursiveOrchestration;

    /**
     * 子代理声明生成器（可选，为空时使用预配置声明）
     */
    private final SubagentSpecGenerator specGenerator;

    /**
     * LLM动态生成声明时的最大子代理数
     */
    private final int maxSubagents;

    /**
     * 声明生成使用的模型编码（可空，走默认配置）
     */
    private final String generationModelCode;

    /**
     * 默认编排策略（可空，LLM未指定strategy时使用）
     */
    private final OrchestrationStrategy defaultStrategy;

    /**
     * 默认编排轮次（可空，LLM未指定rounds时使用，1为兜底）
     */
    private final Integer defaultRounds;

    /**
     * 编排轮次上限（约束LLM指定或默认轮次，避免无限迭代）
     */
    private final int maxRounds;

    /**
     * 运行时父级上下文（由SubagentsMiddleware注入，volatile保证可见性）
     */
    private volatile AgentRuntimeContext parentCtx;

    public AutoOrchestrateTool(SubagentSpawner spawner, List<SubagentDeclaration> declarations,
                                 int maxDepth, boolean allowRecursiveOrchestration) {
        this(spawner, declarations, maxDepth, allowRecursiveOrchestration, null, 5, null);
    }

    public AutoOrchestrateTool(SubagentSpawner spawner, List<SubagentDeclaration> declarations,
                                 int maxDepth, boolean allowRecursiveOrchestration,
                                 SubagentSpecGenerator specGenerator, int maxSubagents,
                                 String generationModelCode) {
        this(spawner, declarations, maxDepth, allowRecursiveOrchestration, specGenerator,
                maxSubagents, generationModelCode, null, null, DEFAULT_MAX_ROUNDS);
    }

    public AutoOrchestrateTool(SubagentSpawner spawner, List<SubagentDeclaration> declarations,
                                 int maxDepth, boolean allowRecursiveOrchestration,
                                 SubagentSpecGenerator specGenerator, int maxSubagents,
                                 String generationModelCode,
                                 OrchestrationStrategy defaultStrategy, Integer defaultRounds,
                                 int maxRounds) {
        this.spawner = spawner;
        this.declarations = declarations;
        this.engine = new AutoOrchestrationEngine(new SubagentResultAggregator(), specGenerator, maxSubagents);
        this.maxDepth = maxDepth;
        this.allowRecursiveOrchestration = allowRecursiveOrchestration;
        this.specGenerator = specGenerator;
        this.maxSubagents = maxSubagents;
        this.generationModelCode = generationModelCode;
        this.defaultStrategy = defaultStrategy;
        this.defaultRounds = defaultRounds;
        this.maxRounds = maxRounds > 0 ? maxRounds : DEFAULT_MAX_ROUNDS;
    }

    /**
     * 注入运行时父级上下文
     * @param ctx
     */
    public void setParentCtx(AgentRuntimeContext ctx) {
        this.parentCtx = ctx;
    }

    /**
     * 注册自定义编排策略处理器，按策略名触发
     * @param name 策略名
     * @param handler
     * @return
     */
    public AutoOrchestrateTool registerOrchestrationHandler(String name,
            OrchestrationStrategyHandler handler) {
        engine.registerHandler(name, handler);
        return this;
    }

    /**
     * 获取工具名称
     * @return
     */
    @Override
    public String getName() {
        return "auto_orchestrate";
    }

    /**
     * 获取工具描述
     * @return
     */
    @Override
    public String getDescription() {
        return "自动编排多子代理协作完成复杂任务。将任务分解给多个子代理并行或顺序执行，并汇总结果。"
                + "适用于需要多角色协作的复杂任务。";
    }

    /**
     * 获取工具参数定义
     * @return
     */
    @Override
    public Map<String, Object> getParameters() {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("type", "object");
        Map<String, Object> properties = new LinkedHashMap<>();
        Map<String, Object> taskProp = new LinkedHashMap<>();
        taskProp.put("type", "string");
        taskProp.put("description", "需要编排协作完成的任务描述");
        properties.put("task", taskProp);
        Map<String, Object> strategyProp = new LinkedHashMap<>();
        strategyProp.put("type", "string");
        strategyProp.put("enum", List.of("SEQUENTIAL", "PARALLEL", "ADAPTIVE",
                "DEBATE", "REFLECTION", "GROUP_CHAT"));
        strategyProp.put("description", "编排策略：顺序、并行、自适应、辩论、反思或群聊，默认ADAPTIVE");
        properties.put("strategy", strategyProp);
        Map<String, Object> failureProp = new LinkedHashMap<>();
        failureProp.put("type", "string");
        failureProp.put("enum", List.of("FAIL_FAST", "CONTINUE_ON_FAILURE"));
        failureProp.put("description", "容错策略：快速失败或继续执行，默认按编排模式自动选择");
        properties.put("failurePolicy", failureProp);
        Map<String, Object> roundsProp = new LinkedHashMap<>();
        roundsProp.put("type", "integer");
        roundsProp.put("description", "辩论/反思/群聊的迭代轮次，默认1，仅DEBATE/REFLECTION/GROUP_CHAT策略有效");
        properties.put("rounds", roundsProp);
        Map<String, Object> judgeModelProp = new LinkedHashMap<>();
        judgeModelProp.put("type", "string");
        judgeModelProp.put("description", "辩论裁判模型编码，可选，仅DEBATE策略有效，为空时使用默认裁判");
        properties.put("judgeModelCode", judgeModelProp);
        params.put("properties", properties);
        params.put("required", List.of("task"));
        return params;
    }

    /**
     * 异步调用工具
     * @param param
     * @return
     */
    @Override
    public Mono<AgentToolResultBlock> callAsync(AgentToolCallParam param) {
        // 检查递归编排深度限制
        int currentDepth = SubagentSpawner.parseDepth(
                parentCtx != null ? parentCtx.getSessionId() : null);
        if (!allowRecursiveOrchestration && currentDepth > 0) {
            log.warn("递归编排被禁用，当前深度={}", currentDepth);
            return Mono.just(AgentToolResultBlock.error("子代理不允许递归编排（allow-recursive-orchestration=false）"));
        }
        if (currentDepth >= maxDepth) {
            log.warn("超过最大嵌套深度限制: current={}, max={}", currentDepth, maxDepth);
            return Mono.just(AgentToolResultBlock.error(
                    "超过最大嵌套深度限制（max-depth=" + maxDepth + "），拒绝递归编排"));
        }
        long startTime = System.currentTimeMillis();
        String task = "";
        OrchestrationStrategy strategy = defaultStrategy != null ? defaultStrategy : OrchestrationStrategy.ADAPTIVE;
        OrchestrationFailurePolicy failurePolicy = null;
        int rounds = defaultRounds != null ? Math.max(1, defaultRounds) : 1;
        String judgeModelCode = null;
        String customStrategyName = null;
        if (param != null && param.getInput() != null) {
            Object taskValue = param.getInput().get("task");
            if (taskValue != null) {
                task = String.valueOf(taskValue);
            }
            Object strategyValue = param.getInput().get("strategy");
            if (strategyValue instanceof String s && !s.isBlank()) {
                try {
                    strategy = OrchestrationStrategy.valueOf(s.toUpperCase());
                } catch (IllegalArgumentException e) {
                    // 非内置策略：命中已注册处理器时触发自定义编排策略
                    if (engine.hasHandler(s)) {
                        customStrategyName = s;
                    }
                }
            }
            Object failureValue = param.getInput().get("failurePolicy");
            if (failureValue instanceof String f && !f.isBlank()) {
                try {
                    failurePolicy = OrchestrationFailurePolicy.valueOf(f.toUpperCase());
                } catch (IllegalArgumentException e) {
                    // 使用默认值
                }
            }
            Object roundsValue = param.getInput().get("rounds");
            if (roundsValue instanceof Number n) {
                rounds = Math.max(1, n.intValue());
            }
            Object judgeValue = param.getInput().get("judgeModelCode");
            if (judgeValue instanceof String j && !j.isBlank()) {
                judgeModelCode = j;
            }
        }
        // rounds受上限约束，避免无限迭代
        rounds = Math.min(rounds, maxRounds);
        // failurePolicy 未指定时按策略自动选择
        if (failurePolicy == null) {
            failurePolicy = resolveDefaultFailurePolicy(strategy);
        }
        final String finalTask = task;
        final OrchestrationStrategy finalStrategy = strategy;
        final OrchestrationFailurePolicy finalFailurePolicy = failurePolicy;
        final int finalRounds = rounds;
        Mono<String> orchestrateMono;
        if (strategy == OrchestrationStrategy.DEBATE) {
            if (judgeModelCode != null) {
                log.debug("辩论裁判模型编码: {}（当前使用默认确定性裁判）", judgeModelCode);
            }
            orchestrateMono = engine.orchestrateDebate(finalTask, declarations, spawner,
                    parentCtx, finalRounds, null);
        } else if (strategy == OrchestrationStrategy.REFLECTION) {
            orchestrateMono = engine.orchestrateReflection(finalTask, declarations, spawner,
                    parentCtx, finalRounds);
        } else if (strategy == OrchestrationStrategy.GROUP_CHAT) {
            orchestrateMono = engine.orchestrateGroupChat(finalTask, declarations, spawner,
                    parentCtx, finalRounds);
        } else if (customStrategyName != null) {
            orchestrateMono = engine.orchestrate(finalTask, declarations, customStrategyName, spawner,
                    parentCtx, finalFailurePolicy, finalRounds, generationModelCode);
        } else {
            // 已传静态声明时直接使用预配置声明，不交给LLM重新生成；
            // 未传声明时由specGenerator按任务动态分解（ADAPTIVE仍可由LLM选择策略）
            orchestrateMono = (specGenerator != null && (declarations == null || declarations.isEmpty()))
                    ? engine.orchestrateWithGeneration(finalTask, declarations, finalStrategy, spawner,
                            parentCtx, finalFailurePolicy, generationModelCode, finalRounds)
                    : engine.orchestrate(finalTask, declarations, finalStrategy, spawner,
                            parentCtx, finalFailurePolicy, finalRounds, generationModelCode);
        }
        return orchestrateMono
                .doOnNext(result -> {
                    long elapsed = System.currentTimeMillis() - startTime;
                    if (result == null || result.isBlank()) {
                        log.info("自动编排完成: strategy={}, 耗时={}ms, 结果为空", finalStrategy, elapsed);
                    } else {
                        log.info("自动编排完成: strategy={}, 耗时={}ms, 结果长度={}, 结论:\n{}",
                                finalStrategy, elapsed, result.length(), truncate(result, 500));
                    }
                })
                .map(result -> AgentToolResultBlock.of(List.of(
                        AgentTextBlock.builder().text(result).build())))
                .onErrorResume(e -> Mono.just(AgentToolResultBlock.error(
                        e.getMessage() != null ? e.getMessage() : "自动编排执行异常")));
    }

    /**
     * 截断超长文本用于日志输出
     * @param text
     * @param maxLen
     * @return
     */
    private String truncate(String text, int maxLen) {
        if (text == null || text.length() <= maxLen) {
            return text;
        }
        return text.substring(0, maxLen) + "\n...(已截断，完整结果长度=" + text.length() + ")";
    }

    /**
     * 按编排策略自动选择默认容错策略
     * <p>
     * SEQUENTIAL默认FAIL_FAST，PARALLEL默认CONTINUE_ON_FAILURE，
     * ADAPTIVE默认CONTINUE_ON_FAILURE（倾向并行）
     * </p>
     * @param strategy
     * @return
     */
    private OrchestrationFailurePolicy resolveDefaultFailurePolicy(OrchestrationStrategy strategy) {
        if (strategy == OrchestrationStrategy.SEQUENTIAL) {
            return OrchestrationFailurePolicy.FAIL_FAST;
        }
        return OrchestrationFailurePolicy.CONTINUE_ON_FAILURE;
    }
}
