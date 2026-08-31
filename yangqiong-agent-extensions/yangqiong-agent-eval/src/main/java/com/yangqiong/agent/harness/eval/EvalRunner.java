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
package com.yangqiong.agent.harness.eval;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import com.yangqiong.agent.harness.core.AgentRuntime;
import com.yangqiong.agent.harness.core.message.AgentChatUsage;
import com.yangqiong.agent.harness.engine.AgentRuntimeContext;
import com.yangqiong.agent.harness.core.message.MessageFactory;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * 评测执行
 * @author yangqiong
 */
public final class EvalRunner {

    /**
     * 单条用例执行超时时间
     */
    private static final Duration CASE_TIMEOUT = Duration.ofMinutes(5);

    /**
     * 用例通过阈值：综合得分满分视为通过
     */
    private static final double PASS_THRESHOLD = 1.0;

    /**
     * 被评测的Agent运行时
     */
    private final AgentRuntime runtime;

    /**
     * 构建评测执行器
     * @param runtime
     * @return
     */
    public EvalRunner(AgentRuntime runtime) {
        this.runtime = runtime;
    }

    /**
     * 串行执行评测数据集
     * @param dataset
     * @return
     */
    public EvalReport run(EvalDataset dataset) {
        return run(dataset, 1, null);
    }

    /**
     * 按指定并发度执行评测数据集，单条用例异常按失败处理，不中断整体评测
     * @param dataset
     * @param concurrency
     * @return
     */
    public EvalReport run(EvalDataset dataset, int concurrency) {
        return run(dataset, concurrency, null);
    }

    /**
     * 按指定并发度与判分器执行评测数据集，判分器为空时不补充判分结果
     * @param dataset
     * @param concurrency
     * @param judge
     * @return
     */
    public EvalReport run(EvalDataset dataset, int concurrency, EvalJudge judge) {
        List<EvalCaseResult> results = Flux.fromIterable(dataset.getCases())
                .flatMap(evalCase -> Mono.fromCallable(() -> executeCase(evalCase, judge)), concurrency)
                .collectList()
                .block();
        return new EvalReport(dataset.getName(), results);
    }

    /**
     * 执行单条评测用例，流式收集事件后评分，异常时返回失败结果
     * @param evalCase
     * @param judge
     * @return
     */
    private EvalCaseResult executeCase(EvalCase evalCase, EvalJudge judge) {
        long startNanos = System.nanoTime();
        TraceRecorder recorder = new TraceRecorder();
        try {
            // 每条用例使用独立scopeId/sessionId，隔离长期记忆与会话状态，防止用例间串扰
            AgentRuntimeContext context = AgentRuntimeContext.builder()
                    .scopeId("eval-case-" + evalCase.getId())
                    .sessionId(evalCase.getId())
                    .build();
            runtime.stream(List.of(MessageFactory.createUserMessage(evalCase.getQuery())), context)
                    .doOnNext(recorder::record)
                    .collectList()
                    .block(CASE_TIMEOUT);
            long latencyMs = Duration.ofNanos(System.nanoTime() - startNanos).toMillis();
            return buildPassedOrFailedResult(evalCase, recorder, latencyMs, judge);
        } catch (Exception e) {
            long latencyMs = Duration.ofNanos(System.nanoTime() - startNanos).toMillis();
            return new EvalCaseResult(evalCase.getId(), false, 0.0, 0.0, 0.0, 0.0,
                    recorder.getToolNames(), recorder.getFinalAnswer(), latencyMs, e.getMessage(), null, null,
                    recorder.getModelCallCount(), 0, 0, 0, recorder.getTotalCostUsd());
        }
    }

    /**
     * 基于轨迹与规则评分生成用例结果，判分器非空时补充判分结果
     * @param evalCase
     * @param recorder
     * @param latencyMs
     * @param judge
     * @return
     */
    private EvalCaseResult buildPassedOrFailedResult(EvalCase evalCase, TraceRecorder recorder, long latencyMs,
                                                     EvalJudge judge) {
        List<String> actualToolNames = recorder.getToolNames();
        String finalAnswer = recorder.getFinalAnswer();
        double toolSelectionAccuracy = RuleScorer.toolSelectionAccuracy(evalCase.getExpectedToolNames(),
                evalCase.getForbiddenToolNames(), actualToolNames);
        double argMatchRate = RuleScorer.argMatchRate(evalCase.getExpectedToolArgs(), recorder.getToolCalls());
        double keywordHitRate = RuleScorer.keywordHitRate(evalCase.getExpectedKeywords(), finalAnswer);
        double score = RuleScorer.compositeScore(evalCase, toolSelectionAccuracy, argMatchRate, keywordHitRate);
        boolean passed = score >= PASS_THRESHOLD;
        String failureReason = passed ? null
                : buildFailureReason(evalCase, toolSelectionAccuracy, argMatchRate, keywordHitRate, actualToolNames);
        AgentChatUsage usage = recorder.getUsage();
        int iterations = recorder.getModelCallCount();
        long promptTokens = usage == null ? 0 : usage.getPromptTokens();
        long completionTokens = usage == null ? 0 : usage.getCompletionTokens();
        long totalTokens = usage == null ? 0 : usage.getTotalTokens();
        if (judge == null) {
            return new EvalCaseResult(evalCase.getId(), passed, score, toolSelectionAccuracy, argMatchRate,
                    keywordHitRate, actualToolNames, finalAnswer, latencyMs, failureReason, null, null,
                    iterations, promptTokens, completionTokens, totalTokens, recorder.getTotalCostUsd());
        }
        JudgeVerdict verdict = judge.judge(evalCase, finalAnswer, actualToolNames);
        return new EvalCaseResult(evalCase.getId(), passed, score, toolSelectionAccuracy, argMatchRate,
                keywordHitRate, actualToolNames, finalAnswer, latencyMs, failureReason, verdict.score(),
                verdict.reasoning(), iterations, promptTokens, completionTokens, totalTokens,
                recorder.getTotalCostUsd());
    }

    /**
     * 合成评分不通过时的可读失败原因，按未达标维度逐项说明
     * @param evalCase
     * @param toolSelectionAccuracy
     * @param argMatchRate
     * @param keywordHitRate
     * @param actualToolNames
     * @return
     */
    private static String buildFailureReason(EvalCase evalCase, double toolSelectionAccuracy, double argMatchRate,
                                             double keywordHitRate, List<String> actualToolNames) {
        List<String> reasons = new ArrayList<>();
        if (toolSelectionAccuracy < 1.0) {
            boolean hasForbidden = evalCase.getForbiddenToolNames() != null
                    && !evalCase.getForbiddenToolNames().isEmpty()
                    && actualToolNames != null
                    && actualToolNames.stream().anyMatch(evalCase.getForbiddenToolNames()::contains);
            if (hasForbidden) {
                reasons.add("调用了禁止的工具");
            } else if (actualToolNames == null || actualToolNames.isEmpty()) {
                reasons.add("未调用期望的工具");
            } else {
                reasons.add("工具调用与期望不符，实际调用: " + actualToolNames);
            }
        }
        if (argMatchRate < 1.0) {
            reasons.add("工具参数与期望不匹配");
        }
        if (keywordHitRate < 1.0) {
            reasons.add("最终答案未命中期望关键词");
        }
        return String.join("；", reasons);
    }
}
