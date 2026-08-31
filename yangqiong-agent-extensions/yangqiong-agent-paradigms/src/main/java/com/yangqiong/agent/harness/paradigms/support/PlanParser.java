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
package com.yangqiong.agent.harness.paradigms.support;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * 计划JSON容错解析器
 * @author yangqiong
 */
public final class PlanParser {

    /**
     * Jackson实例，只读解析场景线程安全
     */
    private static final ObjectMapper MAPPER = new ObjectMapper();

    /**
     * 工具类禁止实例化
     */
    private PlanParser() {
    }

    /**
     * 解析模型输出的计划文本为步骤列表，兼容markdown围栏包裹、前后缀噪声与字段别名，
     * 彻底失败或无可执行步骤时返回null供调用方降级
     * @param raw
     * @return
     */
    public static List<PlanStep> parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String candidate = stripFences(raw);
        List<PlanStep> steps = parseJson(candidate);
        if (steps == null && !candidate.equals(raw)) {
            // 围栏剥离未得到有效计划时回退原始文本，最大化容错
            steps = parseJson(raw);
        }
        return steps;
    }

    /**
     * 剥离markdown围栏，支持多行与嵌套围栏，无围栏时原样返回
     * @param raw
     * @return
     */
    private static String stripFences(String raw) {
        String current = raw;
        while (current.contains("```")) {
            String extracted = extractFirstFenceBlock(current);
            if (extracted == null || extracted.length() >= current.length()) {
                break;
            }
            current = extracted;
        }
        return current;
    }

    /**
     * 提取首个围栏块内容，闭合围栏缺失时取开栏之后的全部文本
     * @param text
     * @return
     */
    private static String extractFirstFenceBlock(String text) {
        int open = text.indexOf("```");
        if (open < 0) {
            return null;
        }
        int contentStart = open + 3;
        int lineBreak = text.indexOf('\n', contentStart);
        if (lineBreak >= 0) {
            String tag = text.substring(contentStart, lineBreak).trim();
            // 语言标识行（如 ```json）整行跳过
            if (!tag.isEmpty() && tag.matches("[A-Za-z0-9+-]+")) {
                contentStart = lineBreak + 1;
            }
        }
        int close = text.indexOf("```", contentStart);
        return close >= 0 ? text.substring(contentStart, close) : text.substring(contentStart);
    }

    /**
     * 截取首个花括号或方括号起的JSON片段，括号配对扫描并跳过字符串字面量内的括号与转义
     * @param text
     * @return
     */
    private static String extractJsonFragment(String text) {
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
     * 提取JSON片段并用Jackson解析为步骤列表
     * @param text
     * @return
     */
    private static List<PlanStep> parseJson(String text) {
        String fragment = extractJsonFragment(text);
        if (fragment == null) {
            return null;
        }
        try {
            JsonNode root = MAPPER.readTree(fragment);
            return toSteps(root);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 将JSON根节点转换为步骤列表，数组按元素顺序编号，单个对象视为单步计划，
     * 空数组或无有效元素返回null
     * @param root
     * @return
     */
    private static List<PlanStep> toSteps(JsonNode root) {
        if (root == null || !root.isContainerNode() || root.isEmpty()) {
            return null;
        }
        if (root.isArray()) {
            List<PlanStep> steps = new ArrayList<>();
            int stepId = 0;
            for (JsonNode element : root) {
                stepId++;
                PlanStep step = toStep(element, stepId);
                if (step != null) {
                    steps.add(step);
                }
            }
            return steps.isEmpty() ? null : steps;
        }
        PlanStep single = toStep(root, 1);
        return single == null ? null : new ArrayList<>(List.of(single));
    }

    /**
     * 将单个JSON对象转换为计划步骤，工具名兼容tool/toolName、参数兼容args/arguments、
     * 描述兼容desc/description，类型缺失或未知时按是否携带工具名判定
     * @param node
     * @param stepId
     * @return
     */
    private static PlanStep toStep(JsonNode node, int stepId) {
        if (node == null || !node.isObject()) {
            return null;
        }
        String type = textOf(node, "type");
        String toolName = textOf(node, "tool") != null ? textOf(node, "tool") : textOf(node, "toolName");
        String description = textOf(node, "desc") != null ? textOf(node, "desc") : textOf(node, "description");
        String normalizedType = type == null ? "" : type.trim().toLowerCase(Locale.ROOT);
        boolean toolStep = PlanStep.TYPE_TOOL.equals(normalizedType)
                || (!PlanStep.TYPE_REASON.equals(normalizedType) && toolName != null);
        if (toolStep) {
            return PlanStep.ofTool(stepId, toolName, readArguments(node), description);
        }
        return PlanStep.ofReason(stepId, description);
    }

    /**
     * 读取工具调用参数，缺失或非对象时返回空Map
     * @param node
     * @return
     */
    private static Map<String, Object> readArguments(JsonNode node) {
        JsonNode argsNode = node.path("args");
        if (!argsNode.isObject()) {
            argsNode = node.path("arguments");
        }
        Map<String, Object> arguments = new LinkedHashMap<>();
        if (argsNode.isObject()) {
            argsNode.fields().forEachRemaining(entry ->
                    arguments.put(entry.getKey(), MAPPER.convertValue(entry.getValue(), Object.class)));
        }
        return arguments;
    }

    /**
     * 读取文本字段值，缺失、空值或空白时返回null
     * @param node
     * @param field
     * @return
     */
    private static String textOf(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull()) {
            return null;
        }
        String text = value.asText();
        return text == null || text.isBlank() ? null : text.trim();
    }
}
