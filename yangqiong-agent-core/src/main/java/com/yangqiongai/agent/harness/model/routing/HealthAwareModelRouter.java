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
package com.yangqiongai.agent.harness.model.routing;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import com.yangqiongai.agent.harness.core.message.AgentMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 健康感知模型路由
 * @author yangqiong
 */
public class HealthAwareModelRouter implements ModelRouter {

    private static final Logger log = LoggerFactory.getLogger(HealthAwareModelRouter.class);

    /**
     * 任务类型命中加成分值
     */
    private static final double TASK_AFFINITY_BONUS = 0.2d;

    /**
     * 任务类型命中加成上限
     */
    private static final double TASK_AFFINITY_MAX = 0.25d;

    /**
     * 中文每Token字符数
     */
    private static final double CJK_CHARS_PER_TOKEN = 1.5d;

    /**
     * 非中文每Token字符数
     */
    private static final double OTHER_CHARS_PER_TOKEN = 4.0d;

    /**
     * 模型健康登记
     */
    private final ModelHealthRegistry registry;

    /**
     * 路由权重配置
     */
    private final RoutingWeights weights;

    public HealthAwareModelRouter(ModelHealthRegistry registry, RoutingWeights weights) {
        this.registry = Objects.requireNonNull(registry, "模型健康登记不能为空");
        this.weights = weights == null ? RoutingWeights.DEFAULT : weights;
    }

    /**
     * 按任务类型与输入消息选择最优候选，过滤不可用候选并记录排除原因，全部被排除时回退首个可用候选
     * @param taskType
     * @param inputMessages
     * @param candidates
     * @return
     */
    @Override
    public RouteDecision select(String taskType, List<AgentMessage> inputMessages, List<RouteCandidate> candidates) {
        Map<String, String> excluded = new LinkedHashMap<>();
        if (candidates == null || candidates.isEmpty()) {
            return RouteDecision.empty(excluded);
        }
        long requiredTokens = estimateTokens(inputMessages);
        List<RouteCandidate> eligible = collectEligible(candidates, requiredTokens, excluded);
        if (eligible.isEmpty()) {
            log.warn("[Routing] 全部候选被排除，回退首个可用候选，排除原因: {}", excluded);
            return fallback(candidates, excluded);
        }
        return selectBest(taskType, requiredTokens, eligible, excluded);
    }

    /**
     * 过滤模型实例缺失、不健康与上下文窗口不足的候选
     * @param candidates
     * @param requiredTokens
     * @param excluded
     * @return
     */
    private List<RouteCandidate> collectEligible(List<RouteCandidate> candidates, long requiredTokens,
                                                 Map<String, String> excluded) {
        List<RouteCandidate> eligible = new ArrayList<>();
        for (RouteCandidate candidate : candidates) {
            if (candidate == null) {
                continue;
            }
            if (candidate.getModel() == null) {
                putExcluded(excluded, candidate.getModelCode(), "模型实例缺失");
                continue;
            }
            if (!registry.isHealthy(candidate.getModelCode())) {
                putExcluded(excluded, candidate.getModelCode(), "模型不健康处于冷却期");
                continue;
            }
            if (candidate.getContextWindowTokens() < requiredTokens) {
                putExcluded(excluded, candidate.getModelCode(), "上下文窗口不足");
                continue;
            }
            eligible.add(candidate);
        }
        return eligible;
    }

    /**
     * 对合格候选加权打分并选出最高分者
     * @param taskType
     * @param requiredTokens
     * @param eligible
     * @param excluded
     * @return
     */
    private RouteDecision selectBest(String taskType, long requiredTokens, List<RouteCandidate> eligible,
                                     Map<String, String> excluded) {
        double minCost = eligible.stream().mapToDouble(RouteCandidate::getCostPerMillionTokens).min().orElse(0.0d);
        double minLatency = eligible.stream().mapToDouble(RouteCandidate::getAvgLatencyMs).min().orElse(0.0d);
        RouteCandidate best = null;
        double bestScore = Double.NEGATIVE_INFINITY;
        for (RouteCandidate candidate : eligible) {
            double score = scoreCandidate(candidate, minCost, minLatency, requiredTokens, taskType);
            if (score > bestScore) {
                best = candidate;
                bestScore = score;
            }
        }
        return RouteDecision.of(best, bestScore, excluded);
    }

    /**
     * 计算候选综合得分：成本、延迟、上下文匹配、健康加权求和后叠加任务类型加成
     * @param candidate
     * @param minCost
     * @param minLatency
     * @param requiredTokens
     * @param taskType
     * @return
     */
    private double scoreCandidate(RouteCandidate candidate, double minCost, double minLatency,
                                  long requiredTokens, String taskType) {
        double costScore = normalize(minCost, candidate.getCostPerMillionTokens());
        double latencyScore = normalize(minLatency, candidate.getAvgLatencyMs());
        double contextFitScore = requiredTokens <= 0 ? 1.0d
                : Math.min(1.0d, candidate.getContextWindowTokens() / (double) requiredTokens);
        contextFitScore = Math.max(0.0d, Math.min(1.0d, contextFitScore));
        double healthScore = registry.healthScore(candidate.getModelCode());
        double total = weights.getCostWeight() * costScore
                + weights.getLatencyWeight() * latencyScore
                + weights.getContextFitWeight() * contextFitScore
                + weights.getHealthWeight() * healthScore;
        if (taskType != null && !taskType.isBlank()
                && candidate.getTaskTypes().contains(taskType)) {
            total += Math.min(TASK_AFFINITY_BONUS, TASK_AFFINITY_MAX);
        }
        return total;
    }

    /**
     * 归一化得分：候选集中最小值除以该候选值并收敛到0-1
     * @param minValue
     * @param value
     * @return
     */
    private double normalize(double minValue, double value) {
        if (value <= 0.0d || minValue <= 0.0d) {
            return 1.0d;
        }
        return Math.min(1.0d, minValue / value);
    }

    /**
     * 全部候选被排除时回退到首个模型实例可用的候选
     * @param candidates
     * @param excluded
     * @return
     */
    private RouteDecision fallback(List<RouteCandidate> candidates, Map<String, String> excluded) {
        for (RouteCandidate candidate : candidates) {
            if (candidate != null && candidate.getModel() != null) {
                return RouteDecision.of(candidate, 0.0d, excluded);
            }
        }
        return RouteDecision.empty(excluded);
    }

    /**
     * 记录排除原因，模型编码为空时不记录
     * @param excluded
     * @param modelCode
     * @param reason
     */
    private void putExcluded(Map<String, String> excluded, String modelCode, String reason) {
        if (modelCode != null && !modelCode.isBlank()) {
            excluded.put(modelCode, reason);
        }
    }

    /**
     * 按消息文本估算所需Token数，中文按1.5字符每Token、其余按4字符每Token累加
     * @param messages
     * @return
     */
    static long estimateTokens(List<AgentMessage> messages) {
        if (messages == null || messages.isEmpty()) {
            return 0L;
        }
        long cjkChars = 0L;
        long otherChars = 0L;
        for (AgentMessage message : messages) {
            String text = message == null ? "" : message.getTextContent();
            if (text == null || text.isEmpty()) {
                continue;
            }
            for (int i = 0; i < text.length(); i++) {
                if (isCjkChar(text.charAt(i))) {
                    cjkChars++;
                } else {
                    otherChars++;
                }
            }
        }
        return Math.round(cjkChars / CJK_CHARS_PER_TOKEN + otherChars / OTHER_CHARS_PER_TOKEN);
    }

    /**
     * 判断字符是否为中文字符
     * @param c
     * @return
     */
    private static boolean isCjkChar(char c) {
        return Character.UnicodeScript.of(c) == Character.UnicodeScript.HAN;
    }
}
