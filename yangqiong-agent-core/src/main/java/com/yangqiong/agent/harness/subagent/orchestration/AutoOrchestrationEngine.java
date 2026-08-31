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
package com.yangqiong.agent.harness.subagent.orchestration;

import com.yangqiong.agent.harness.subagent.SubagentSpawner;
import com.yangqiong.agent.harness.engine.AgentRuntimeContext;
import com.yangqiong.agent.harness.core.AgentRuntime;
import com.yangqiong.agent.harness.core.event.AgentEvent;
import com.yangqiong.agent.harness.core.message.AgentMessage;
import com.yangqiong.agent.harness.core.message.AgentMessageRole;
import com.yangqiong.agent.harness.core.message.AgentTextBlock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 自动编排引擎
 * <p>
 * 根据编排策略执行子代理团队协作，支持顺序执行和并行执行。
 * 使用SubagentResultAggregator汇总结果，按容错策略处理失败。
 * 当注入SubagentSpecGenerator时，支持LLM自动任务分解：任务 → 生成声明 → 策略选择 → 执行 → 汇总。
 * </p>
 * @author yangqiong
 */
public class AutoOrchestrationEngine {

    private static final Logger log = LoggerFactory.getLogger(AutoOrchestrationEngine.class);

    /**
     * 子代理结果汇总器
     */
    private final SubagentResultAggregator aggregator;

    /**
     * 子代理声明生成器（可选，为空时使用调用方传入的声明）
     */
    private final SubagentSpecGenerator specGenerator;

    /**
     * 最大子代理数（LLM动态生成时使用）
     */
    private final int maxSubagents;

    /**
     * 自定义编排策略处理器（按策略名注册，优先级高于内置枚举策略）
     */
    private final Map<String, OrchestrationStrategyHandler> handlers = new ConcurrentHashMap<>();

    public AutoOrchestrationEngine(SubagentResultAggregator aggregator) {
        this(aggregator, null, 5);
    }

    public AutoOrchestrationEngine(SubagentResultAggregator aggregator,
                                     SubagentSpecGenerator specGenerator, int maxSubagents) {
        this.aggregator = aggregator;
        this.specGenerator = specGenerator;
        this.maxSubagents = maxSubagents;
    }

    /**
     * 编排执行子代理团队（使用已有声明列表）
     * @param task 原始任务
     * @param declarations 子代理声明列表
     * @param strategy 编排策略
     * @param spawner 子代理执行器
     * @param parentCtx 父级运行时上下文
     * @param failurePolicy 容错策略
     * @return 编排汇总结果
     */
    public Mono<String> orchestrate(String task, List<SubagentDeclaration> declarations,
                                      OrchestrationStrategy strategy, SubagentSpawner spawner,
                                      AgentRuntimeContext parentCtx,
                                      OrchestrationFailurePolicy failurePolicy) {
        return orchestrate(task, declarations, strategy, spawner, parentCtx, failurePolicy, 1, null);
    }

    /**
     * 编排执行子代理团队（使用已有声明列表，支持轮次与模型编码）
     * <p>
     * ADAPTIVE策略：注入specGenerator时由LLM根据任务特征选择具体策略，
     * 否则按子代理数量启发式选择；DEBATE/REFLECTION/GROUP_CHAT使用rounds。
     * </p>
     * @param task 原始任务
     * @param declarations 子代理声明列表
     * @param strategy 编排策略
     * @param spawner 子代理执行器
     * @param parentCtx 父级运行时上下文
     * @param failurePolicy 容错策略
     * @param rounds 辩论/反思/群聊轮次
     * @param requestModelCode 策略决策使用的模型编码（可空）
     * @return 编排汇总结果
     */
    public Mono<String> orchestrate(String task, List<SubagentDeclaration> declarations,
                                      OrchestrationStrategy strategy, SubagentSpawner spawner,
                                      AgentRuntimeContext parentCtx,
                                      OrchestrationFailurePolicy failurePolicy,
                                      int rounds, String requestModelCode) {
        if (declarations == null || declarations.isEmpty()) {
            return Mono.just("无可用子代理，编排取消");
        }
        log.info("开始自动编排: task={}, subagentCount={}, strategy={}",
                truncate(task), declarations.size(), strategy);
        if (strategy == OrchestrationStrategy.ADAPTIVE) {
            return selectStrategy(task, declarations, requestModelCode)
                    .flatMap(selected -> orchestrate(task, declarations, selected, spawner,
                            parentCtx, failurePolicy, rounds, requestModelCode));
        }
        if (strategy == OrchestrationStrategy.DEBATE) {
            return orchestrateDebate(task, declarations, spawner, parentCtx, rounds, null);
        }
        if (strategy == OrchestrationStrategy.REFLECTION) {
            return orchestrateReflection(task, declarations, spawner, parentCtx, rounds);
        }
        if (strategy == OrchestrationStrategy.GROUP_CHAT) {
            return orchestrateGroupChat(task, declarations, spawner, parentCtx, rounds);
        }
        if (strategy == OrchestrationStrategy.SEQUENTIAL) {
            return executeSequential(task, declarations, spawner, parentCtx, failurePolicy);
        }
        return executeParallel(task, declarations, spawner, parentCtx, failurePolicy);
    }

    /**
     * 按策略名编排执行子代理团队
     * <p>
     * 已注册自定义处理器时优先调用；否则按内置枚举名分派；未知策略回退并行编排。
     * </p>
     * @param task 原始任务
     * @param declarations 子代理声明列表
     * @param strategyName 策略名（自定义或内置枚举名）
     * @param spawner 子代理执行器
     * @param parentCtx 父级运行时上下文
     * @param failurePolicy 容错策略
     * @param rounds 编排轮次
     * @param requestModelCode 策略决策使用的模型编码（可空）
     * @return 编排汇总结果
     */
    public Mono<String> orchestrate(String task, List<SubagentDeclaration> declarations,
                                      String strategyName, SubagentSpawner spawner,
                                      AgentRuntimeContext parentCtx,
                                      OrchestrationFailurePolicy failurePolicy,
                                      int rounds, String requestModelCode) {
        if (strategyName != null) {
            OrchestrationStrategyHandler handler = handlers.get(strategyName.toUpperCase(Locale.ROOT));
            if (handler != null) {
                log.info("使用自定义编排策略: {}", strategyName);
                return handler.orchestrate(task, declarations, spawner, parentCtx, failurePolicy, rounds);
            }
            try {
                OrchestrationStrategy builtIn = OrchestrationStrategy.valueOf(
                        strategyName.toUpperCase(Locale.ROOT));
                return orchestrate(task, declarations, builtIn, spawner, parentCtx,
                        failurePolicy, rounds, requestModelCode);
            } catch (IllegalArgumentException ignored) {
                // 未知策略回退并行编排
            }
        }
        log.warn("未找到编排策略 {}, 回退并行编排", strategyName);
        return executeParallel(task, declarations, spawner, parentCtx, failurePolicy);
    }

    /**
     * 注册自定义编排策略处理器
     * @param name 策略名（与auto_orchestrate入参strategy比对，忽略大小写）
     * @param handler
     * @return
     */
    public AutoOrchestrationEngine registerHandler(String name, OrchestrationStrategyHandler handler) {
        if (name != null && handler != null) {
            handlers.put(name.toUpperCase(Locale.ROOT), handler);
        }
        return this;
    }

    /**
     * 注销自定义编排策略处理器
     * @param name
     * @return
     */
    public AutoOrchestrationEngine unregisterHandler(String name) {
        if (name != null) {
            handlers.remove(name.toUpperCase(Locale.ROOT));
        }
        return this;
    }

    /**
     * 是否已注册指定策略名
     * @param name
     * @return
     */
    public boolean hasHandler(String name) {
        return name != null && handlers.containsKey(name.toUpperCase(Locale.ROOT));
    }

    /**
     * 自适应策略决策：优先由LLM根据任务特征选择，回退为按子代理数量启发式
     * @param task 原始任务
     * @param declarations 子代理声明列表
     * @param requestModelCode 模型编码（可空）
     * @return
     */
    private Mono<OrchestrationStrategy> selectStrategy(String task, List<SubagentDeclaration> declarations,
                                                       String requestModelCode) {
        Mono<OrchestrationStrategy> selected = specGenerator != null
                ? specGenerator.selectStrategy(task, declarations, requestModelCode)
                : Mono.just(resolveByCount(declarations));
        return selected
                .map(s -> s == null || s == OrchestrationStrategy.ADAPTIVE ? resolveByCount(declarations) : s)
                .defaultIfEmpty(resolveByCount(declarations));
    }

    /**
     * 按子代理数量启发式选择策略
     * @param declarations
     * @return
     */
    private OrchestrationStrategy resolveByCount(List<SubagentDeclaration> declarations) {
        return declarations.size() <= 2
                ? OrchestrationStrategy.SEQUENTIAL : OrchestrationStrategy.PARALLEL;
    }

    /**
     * 编排执行子代理团队（LLM动态分解任务生成声明）
     * <p>
     * 完整闭环：任务 → LLM分解生成声明 → 策略选择 → 执行 → 汇总。
     * 需注入SubagentSpecGenerator，否则回退到预配置声明。
     * </p>
     * @param task 原始任务
     * @param fallbackDeclarations 预配置声明（LLM生成失败时回退使用）
     * @param strategy 编排策略
     * @param spawner 子代理执行器
     * @param parentCtx 父级运行时上下文
     * @param failurePolicy 容错策略
     * @param requestModelCode 声明生成使用的模型编码（可空）
     * @param rounds 辩论/反思/群聊轮次
     * @return 编排汇总结果
     */
    public Mono<String> orchestrateWithGeneration(String task,
                                                    List<SubagentDeclaration> fallbackDeclarations,
                                                    OrchestrationStrategy strategy,
                                                    SubagentSpawner spawner,
                                                    AgentRuntimeContext parentCtx,
                                                    OrchestrationFailurePolicy failurePolicy,
                                                    String requestModelCode,
                                                    int rounds) {
        if (specGenerator == null) {
            log.debug("SubagentSpecGenerator未注入，使用预配置声明");
            return orchestrate(task, fallbackDeclarations, strategy, spawner, parentCtx, failurePolicy);
        }
        log.info("开始LLM自动任务分解: task={}", truncate(task));
        return specGenerator.generateDeclarations(task, maxSubagents,
                    Collections.emptyList(), requestModelCode)
                .flatMap(generated -> {
                    List<SubagentDeclaration> declarations = generated != null && !generated.isEmpty()
                            ? generated : fallbackDeclarations;
                    if (declarations == null || declarations.isEmpty()) {
                        return Mono.just("LLM任务分解未生成有效子代理，编排取消");
                    }
                    log.info("LLM任务分解完成: 生成{}个子代理声明", declarations.size());
                    return orchestrate(task, declarations, strategy, spawner, parentCtx,
                            failurePolicy, rounds, requestModelCode);
                })
                .onErrorResume(e -> {
                    log.warn("LLM任务分解失败，回退到预配置声明", e);
                    return orchestrate(task, fallbackDeclarations, strategy, spawner, parentCtx,
                            failurePolicy, rounds, requestModelCode);
                });
    }

    /**
     * 截断任务描述用于日志
     * @param task
     * @return
     */
    private String truncate(String task) {
        return task != null && task.length() > 100 ? task.substring(0, 100) + "..." : task;
    }

    /**
     * 顺序执行：子代理按序依次执行，前一个的输出作为后一个的输入
     * @param task
     * @param declarations
     * @param spawner
     * @param parentCtx
     * @param failurePolicy
     * @return
     */
    private Mono<String> executeSequential(String task, List<SubagentDeclaration> declarations,
                                            SubagentSpawner spawner, AgentRuntimeContext parentCtx,
                                            OrchestrationFailurePolicy failurePolicy) {
        List<SubagentResult> results = new ArrayList<>();
        SubagentDeclaration first = declarations.get(0);
        Mono<SubagentResult> chain = spawner.spawn(first, task, parentCtx)
                .map(result -> {
                    results.add(result);
                    return result;
                });
        for (int i = 1; i < declarations.size(); i++) {
            final int idx = i;
            chain = chain.flatMap(prevResult -> {
                if (failurePolicy == OrchestrationFailurePolicy.FAIL_FAST && prevResult.isFailure()) {
                    return Mono.just(prevResult);
                }
                String nextInput = prevResult.isSuccess()
                        ? prevResult.output() : task + "\n（前序子代理失败，使用原始任务重试）";
                SubagentDeclaration decl = declarations.get(idx);
                return spawner.spawn(decl, nextInput, parentCtx)
                        .map(result -> {
                            results.add(result);
                            return result;
                        });
            });
        }
        return chain.then(Mono.fromSupplier(() -> aggregator.aggregate(results, failurePolicy)));
    }

    /**
     * 并行执行：子代理同时执行，结果统一汇总
     * @param task
     * @param declarations
     * @param spawner
     * @param parentCtx
     * @param failurePolicy
     * @return
     */
    private Mono<String> executeParallel(String task, List<SubagentDeclaration> declarations,
                                          SubagentSpawner spawner, AgentRuntimeContext parentCtx,
                                          OrchestrationFailurePolicy failurePolicy) {
        List<Mono<SubagentResult>> monos = new ArrayList<>();
        for (SubagentDeclaration decl : declarations) {
            monos.add(spawner.spawn(decl, task, parentCtx));
        }
        return Flux.fromIterable(monos)
                .flatMap(mono -> mono, declarations.size())
                .collectList()
                .map(results -> aggregator.aggregate(results, failurePolicy));
    }

    /**
     * 辩论编排：每轮所有参与者独立作答，裁判综合/选出立场并给出反馈，下一轮注入反馈再答，达轮次上限后裁判产出最终结论
     * @param task 原始任务
     * @param declarations 参与者声明列表
     * @param spawner 子代理执行器
     * @param parentCtx 父级运行时上下文
     * @param rounds 辩论轮次
     * @param judge 辩论裁判（为空时使用默认确定性裁判）
     * @return 辩论最终结论
     */
    public Mono<String> orchestrateDebate(String task, List<SubagentDeclaration> declarations,
                                            SubagentSpawner spawner, AgentRuntimeContext parentCtx,
                                            int rounds, DebateJudge judge) {
        if (declarations == null || declarations.isEmpty()) {
            return Mono.just("无可用子代理，编排取消");
        }
        DebateJudge effectiveJudge = judge != null ? judge : new DefaultDebateJudge();
        int effectiveRounds = Math.max(1, rounds);
        log.info("开始辩论编排: task={}, participants={}, rounds={}",
                truncate(task), declarations.size(), effectiveRounds);
        return debateRound(task, declarations, spawner, parentCtx,
                effectiveRounds, effectiveJudge, "", 0);
    }

    /**
     * 反思编排：执行者作答、评审者批判、执行者据批判修订，重复至轮次上限产出最终修订结论
     * @param task 原始任务
     * @param declarations 声明列表，declarations[0]为执行者、declarations[1]为评审者
     * @param spawner 子代理执行器
     * @param parentCtx 父级运行时上下文
     * @param rounds 反思轮次
     * @return 最终修订结论
     */
    public Mono<String> orchestrateReflection(String task, List<SubagentDeclaration> declarations,
                                                SubagentSpawner spawner, AgentRuntimeContext parentCtx,
                                                int rounds) {
        if (declarations == null || declarations.size() < 2) {
            return Mono.just("反思编排至少需要2个子代理（执行者与评审者），编排取消");
        }
        SubagentDeclaration executor = declarations.get(0);
        SubagentDeclaration critic = declarations.get(1);
        int effectiveRounds = Math.max(1, rounds);
        log.info("开始反思编排: task={}, executor={}, critic={}, rounds={}",
                truncate(task), executor.getName(), critic.getName(), effectiveRounds);
        return reflectionRound(task, executor, critic, spawner, parentCtx, effectiveRounds, 0, null);
    }

    /**
     * 群聊编排：各参与者经消息中枢轮转广播，各代理看到前序发言后继续
     * @param task 原始任务
     * @param declarations 参与者声明列表
     * @param spawner 子代理执行器
     * @param parentCtx 父级运行时上下文
     * @param rounds 讨论轮次
     * @return 群聊讨论记录
     */
    public Mono<String> orchestrateGroupChat(String task, List<SubagentDeclaration> declarations,
                                               SubagentSpawner spawner, AgentRuntimeContext parentCtx,
                                               int rounds) {
        if (declarations == null || declarations.isEmpty()) {
            return Mono.just("无可用子代理，编排取消");
        }
        int effectiveRounds = Math.max(1, rounds);
        log.info("开始群聊编排: task={}, participants={}, rounds={}",
                truncate(task), declarations.size(), effectiveRounds);
        MsgHub hub = new MsgHub();
        for (SubagentDeclaration declaration : declarations) {
            hub.register(new SubagentRuntimeAdapter(declaration, spawner, parentCtx));
        }
        return hub.roundRobin(task, parentCtx, effectiveRounds)
                .map(discussion -> formatGroupChat(task, discussion));
    }

    /**
     * 辩论单轮执行
     * @param task
     * @param declarations
     * @param spawner
     * @param parentCtx
     * @param totalRounds
     * @param judge
     * @param previousFeedback
     * @param currentRound
     * @return
     */
    private Mono<String> debateRound(String task, List<SubagentDeclaration> declarations,
                                     SubagentSpawner spawner, AgentRuntimeContext parentCtx,
                                     int totalRounds, DebateJudge judge,
                                     String previousFeedback, int currentRound) {
        String roundInput = buildDebateInput(task, previousFeedback);
        List<Mono<SubagentResult>> monos = new ArrayList<>();
        for (SubagentDeclaration declaration : declarations) {
            String participantInput = roundInput + buildDebateParticipantConstraint(declaration);
            monos.add(spawner.spawn(declaration, participantInput, parentCtx));
        }
        return Flux.fromIterable(monos)
                .flatMap(mono -> mono, declarations.size())
                .collectList()
                .flatMap(results -> {
                    String judgement = judge.judge(task, results, previousFeedback);
                    if (currentRound + 1 >= totalRounds) {
                        return Mono.just(judgement);
                    }
                    return debateRound(task, declarations, spawner, parentCtx,
                            totalRounds, judge, judgement, currentRound + 1);
                });
    }

    /**
     * 构建辩论输入：任务附加前一轮裁判反馈
     * @param task
     * @param previousFeedback
     * @return
     */
    private String buildDebateInput(String task, String previousFeedback) {
        if (previousFeedback == null || previousFeedback.isBlank()) {
            return task;
        }
        return task + "\n\n裁判反馈（请参考后调整你的立场并重新作答）：\n" + previousFeedback;
    }

    /**
     * 构建辩论参与者发言约束：仅代表自身立场，不替他人发言、不输出裁判结论
     * @param declaration
     * @return
     */
    private String buildDebateParticipantConstraint(SubagentDeclaration declaration) {
        return "\n\n【发言约束】你只以【" + declaration.getName() + "】的身份作答，"
                + "只陈述你这一方的观点与论据，不要替其他参与者发言，"
                + "也不要输出裁判汇总或最终结论——辩论裁决与最终结论由裁判统一给出。";
    }

    /**
     * 反思单轮执行
     * @param task
     * @param executor
     * @param critic
     * @param spawner
     * @param parentCtx
     * @param totalRounds
     * @param currentRound
     * @param critique
     * @return
     */
    private Mono<String> reflectionRound(String task, SubagentDeclaration executor,
                                         SubagentDeclaration critic, SubagentSpawner spawner,
                                         AgentRuntimeContext parentCtx, int totalRounds,
                                         int currentRound, String critique) {
        return spawner.spawn(executor, buildExecutorInput(task, critique), parentCtx)
                .flatMap(executorResult -> {
                    if (currentRound + 1 >= totalRounds) {
                        return Mono.just(buildReflectionFinal(executorResult, critique));
                    }
                    return spawner.spawn(critic, buildCriticInput(executorResult), parentCtx)
                            .flatMap(criticResult -> reflectionRound(task, executor, critic, spawner,
                                    parentCtx, totalRounds, currentRound + 1, criticResult.output()));
                });
    }

    /**
     * 构建执行者输入：任务附加评审意见
     * @param task
     * @param critique
     * @return
     */
    private String buildExecutorInput(String task, String critique) {
        if (critique == null || critique.isBlank()) {
            return task;
        }
        return task + "\n\n评审意见（请针对批判意见修订你的回答）：\n" + critique;
    }

    /**
     * 构建评审者输入：执行者回答作为评审对象
     * @param executorResult
     * @return
     */
    private String buildCriticInput(SubagentResult executorResult) {
        return "请评审以下执行者的回答，指出问题并给出改进意见：\n"
                + (executorResult.output() != null ? executorResult.output() : "");
    }

    /**
     * 构建反思最终结论：执行者最新回答附加修订标记
     * @param executorResult
     * @param critique
     * @return
     */
    private String buildReflectionFinal(SubagentResult executorResult, String critique) {
        String output = executorResult.output() != null ? executorResult.output() : "";
        if (critique != null && !critique.isBlank()) {
            output = output + "\n\n（已根据评审意见修订完成）";
        }
        return output;
    }

    /**
     * 格式化群聊讨论记录
     * @param task
     * @param discussion
     * @return
     */
    private String formatGroupChat(String task, List<AgentMessage> discussion) {
        StringBuilder sb = new StringBuilder();
        sb.append("群聊讨论完成：\n");
        sb.append("讨论主题：").append(task).append("\n");
        int seq = 0;
        for (AgentMessage message : discussion) {
            String text = message.getTextContent();
            if (text == null || text.isBlank()) {
                continue;
            }
            seq++;
            sb.append(seq).append(". ").append(text).append("\n");
        }
        return sb.toString();
    }

    /**
     * 将消息列表转换为文本
     * @param messages
     * @return
     */
    private static String toText(List<AgentMessage> messages) {
        if (messages == null || messages.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (AgentMessage message : messages) {
            String text = message.getTextContent();
            if (text != null && !text.isBlank()) {
                sb.append(text).append("\n");
            }
        }
        return sb.toString().trim();
    }

    /**
     * 群聊参与者适配器：将子代理声明桥接到消息中枢的Agent运行时
     * <p>
     * 每次发言将前序讨论历史作为输入交给子代理执行器执行。
     * </p>
     */
    private static class SubagentRuntimeAdapter implements AgentRuntime {

        /**
         * 子代理声明
         */
        private final SubagentDeclaration declaration;

        /**
         * 子代理执行器
         */
        private final SubagentSpawner spawner;

        /**
         * 父级运行时上下文
         */
        private final AgentRuntimeContext parentCtx;

        SubagentRuntimeAdapter(SubagentDeclaration declaration, SubagentSpawner spawner,
                               AgentRuntimeContext parentCtx) {
            this.declaration = declaration;
            this.spawner = spawner;
            this.parentCtx = parentCtx;
        }

        @Override
        public Mono<AgentMessage> call(List<AgentMessage> inputs, AgentRuntimeContext context) {
            String input = toText(inputs);
            return spawner.spawn(declaration, input, parentCtx)
                    .map(result -> AgentMessage.builder()
                            .role(AgentMessageRole.ASSISTANT)
                            .content(Collections.singletonList(AgentTextBlock.builder()
                                    .text(result.output() != null ? result.output() : "")
                                    .build()))
                            .build())
                    .onErrorResume(e -> Mono.just(AgentMessage.builder()
                            .role(AgentMessageRole.ASSISTANT)
                            .content(Collections.singletonList(AgentTextBlock.builder()
                                    .text("发言失败: " + (e.getMessage() != null ? e.getMessage() : "未知错误"))
                                    .build()))
                            .build()));
        }

        @Override
        public Flux<AgentEvent> stream(List<AgentMessage> inputs, AgentRuntimeContext context) {
            return Flux.empty();
        }

        @Override
        public String getName() {
            return declaration.getName();
        }
    }
}
