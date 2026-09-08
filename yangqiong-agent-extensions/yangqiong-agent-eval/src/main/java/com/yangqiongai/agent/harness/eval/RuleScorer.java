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
package com.yangqiongai.agent.harness.eval;

import java.util.List;
import java.util.Map;

import com.yangqiongai.agent.harness.core.message.AgentToolUseBlock;

/**
 * 规则评分
 * <p>
 * 评分维度：工具选择准确率、参数匹配率、关键词命中率。
 * 综合得分按用例期望维度加权：仅关键词时为关键词命中率；工具+关键词为0.6/0.4；
 * 工具+参数为0.6/0.4；三维齐全为0.4/0.3/0.3。调用任一禁止工具时工具维度得0分。
 * </p>
 * @author yangqiong
 */
public final class RuleScorer {

    /**
     * 工具维度权重（两维场景）
     */
    private static final double TOOL_WEIGHT = 0.6;

    /**
     * 次要维度权重（两维场景）
     */
    private static final double SECONDARY_WEIGHT = 0.4;

    /**
     * 三维场景下工具维度权重
     */
    private static final double TOOL_WEIGHT_3D = 0.4;

    /**
     * 三维场景下参数维度权重
     */
    private static final double ARGS_WEIGHT_3D = 0.3;

    /**
     * 三维场景下关键词维度权重
     */
    private static final double KEYWORD_WEIGHT_3D = 0.3;

    private RuleScorer() {
    }

    /**
     * 计算期望工具命中率，实际调用列表包含期望名称即命中，无期望项时返回1.0
     * @param expectedToolNames
     * @param actualToolNames
     * @return
     */
    public static double toolSelectionAccuracy(List<String> expectedToolNames, List<String> actualToolNames) {
        return toolSelectionAccuracy(expectedToolNames, List.of(), actualToolNames);
    }

    /**
     * 计算期望工具命中率并叠加误调用检测，调用任一禁止工具时直接返回0分
     * @param expectedToolNames
     * @param forbiddenToolNames
     * @param actualToolNames
     * @return
     */
    public static double toolSelectionAccuracy(List<String> expectedToolNames, List<String> forbiddenToolNames,
                                               List<String> actualToolNames) {
        boolean hasExpected = expectedToolNames != null && !expectedToolNames.isEmpty();
        boolean hasForbidden = forbiddenToolNames != null && !forbiddenToolNames.isEmpty();
        if (!hasExpected && !hasForbidden) {
            return 1.0;
        }
        if (actualToolNames == null || actualToolNames.isEmpty()) {
            return hasExpected ? 0.0 : 1.0;
        }
        if (hasForbidden && actualToolNames.stream().anyMatch(forbiddenToolNames::contains)) {
            return 0.0;
        }
        if (!hasExpected) {
            return 1.0;
        }
        long hitCount = expectedToolNames.stream().filter(actualToolNames::contains).count();
        return (double) hitCount / expectedToolNames.size();
    }

    /**
     * 计算期望参数匹配率，实际调用参数需包含期望键且值匹配（字符串包含匹配、数值与布尔相等匹配），无期望项时返回1.0
     * @param expectedToolArgs
     * @param actualToolCalls
     * @return
     */
    public static double argMatchRate(Map<String, Map<String, Object>> expectedToolArgs,
                                      List<AgentToolUseBlock> actualToolCalls) {
        if (expectedToolArgs == null || expectedToolArgs.isEmpty()) {
            return 1.0;
        }
        if (actualToolCalls == null || actualToolCalls.isEmpty()) {
            return 0.0;
        }
        long hitCount = expectedToolArgs.entrySet().stream()
                .filter(entry -> argsMatched(entry.getKey(), entry.getValue(), actualToolCalls))
                .count();
        return (double) hitCount / expectedToolArgs.size();
    }

    /**
     * 判断某工具的实际调用参数是否满足全部期望键值（取该工具最后一次调用）
     * @param toolName
     * @param expectedArgs
     * @param actualToolCalls
     * @return
     */
    private static boolean argsMatched(String toolName, Map<String, Object> expectedArgs,
                                       List<AgentToolUseBlock> actualToolCalls) {
        AgentToolUseBlock lastCall = null;
        for (AgentToolUseBlock call : actualToolCalls) {
            if (toolName.equals(call.getToolName())) {
                lastCall = call;
            }
        }
        if (lastCall == null || lastCall.getInput() == null) {
            return false;
        }
        for (Map.Entry<String, Object> expected : expectedArgs.entrySet()) {
            Object actualValue = lastCall.getInput().get(expected.getKey());
            if (!argValueMatched(actualValue, expected.getValue())) {
                return false;
            }
        }
        return true;
    }

    /**
     * 匹配单个参数值：字符串包含匹配，数值与布尔相等匹配，其余类型相等匹配
     * @param actualValue
     * @param expectedValue
     * @return
     */
    private static boolean argValueMatched(Object actualValue, Object expectedValue) {
        if (actualValue == null || expectedValue == null) {
            return false;
        }
        if (expectedValue instanceof Number expectedNumber && actualValue instanceof Number actualNumber) {
            return expectedNumber.doubleValue() == actualNumber.doubleValue();
        }
        if (expectedValue instanceof Boolean expectedBoolean) {
            return expectedBoolean.equals(actualValue);
        }
        return actualValue.toString().contains(expectedValue.toString());
    }

    /**
     * 计算期望关键词命中率，最终答案包含期望关键词即命中，无期望项时返回1.0
     * @param expectedKeywords
     * @param finalAnswer
     * @return
     */
    public static double keywordHitRate(List<String> expectedKeywords, String finalAnswer) {
        if (expectedKeywords == null || expectedKeywords.isEmpty()) {
            return 1.0;
        }
        if (finalAnswer == null || finalAnswer.isEmpty()) {
            return 0.0;
        }
        long hitCount = expectedKeywords.stream().filter(finalAnswer::contains).count();
        return (double) hitCount / expectedKeywords.size();
    }

    /**
     * 计算综合得分（兼容旧签名）：0.6*工具命中率+0.4*关键词命中率，无工具期望时退化为关键词命中率
     * @param expectedToolNames
     * @param toolSelectionAccuracy
     * @param keywordHitRate
     * @return
     */
    public static double compositeScore(List<String> expectedToolNames, double toolSelectionAccuracy,
                                        double keywordHitRate) {
        boolean hasTool = expectedToolNames != null && !expectedToolNames.isEmpty();
        return compositeScore(hasTool, false, false, true, toolSelectionAccuracy, 1.0, keywordHitRate);
    }

    /**
     * 计算综合得分，按用例期望维度动态加权
     * @param evalCase
     * @param toolSelectionAccuracy
     * @param argMatchRate
     * @param keywordHitRate
     * @return
     */
    public static double compositeScore(EvalCase evalCase, double toolSelectionAccuracy,
                                        double argMatchRate, double keywordHitRate) {
        boolean hasTool = evalCase.getExpectedToolNames() != null && !evalCase.getExpectedToolNames().isEmpty();
        boolean hasArgs = evalCase.getExpectedToolArgs() != null && !evalCase.getExpectedToolArgs().isEmpty();
        boolean hasForbidden = evalCase.getForbiddenToolNames() != null && !evalCase.getForbiddenToolNames().isEmpty();
        boolean hasKeyword = evalCase.getExpectedKeywords() != null && !evalCase.getExpectedKeywords().isEmpty();
        return compositeScore(hasTool, hasArgs, hasForbidden, hasKeyword,
                toolSelectionAccuracy, argMatchRate, keywordHitRate);
    }

    /**
     * 计算综合得分（按期望维度组合选择权重）
     * @param hasExpectedTool
     * @param hasExpectedArgs
     * @param hasForbiddenTool
     * @param hasExpectedKeyword
     * @param toolSelectionAccuracy
     * @param argMatchRate
     * @param keywordHitRate
     * @return
     */
    private static double compositeScore(boolean hasExpectedTool, boolean hasExpectedArgs, boolean hasForbiddenTool,
                                         boolean hasExpectedKeyword, double toolSelectionAccuracy,
                                         double argMatchRate, double keywordHitRate) {
        // 纯关键词或纯对话场景，退化为关键词命中率
        if (!hasExpectedTool && !hasExpectedArgs && !hasForbiddenTool) {
            return keywordHitRate;
        }
        // 工具+参数+关键词三维齐全场景
        if (hasExpectedArgs && hasExpectedKeyword) {
            return TOOL_WEIGHT_3D * toolSelectionAccuracy + ARGS_WEIGHT_3D * argMatchRate
                    + KEYWORD_WEIGHT_3D * keywordHitRate;
        }
        // 工具+参数场景
        if (hasExpectedArgs) {
            return TOOL_WEIGHT * toolSelectionAccuracy + SECONDARY_WEIGHT * argMatchRate;
        }
        // 工具+关键词（或仅禁用工具检测）场景
        return TOOL_WEIGHT * toolSelectionAccuracy + SECONDARY_WEIGHT * keywordHitRate;
    }
}
