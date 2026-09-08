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
package com.yangqiongai.agent.harness.model;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yangqiongai.agent.harness.config.AgentJsonSchema;
import com.yangqiongai.agent.harness.config.AgentResponseFormat;

/**
 * 结构化输出校验器
 * <p>
 * 当配置了json_schema响应格式时，校验模型输出是否符合Schema定义。
 * 校验范围：JSON语法合法性、required字段存在性、字段类型匹配。
 * </p>
 * @author yangqiong
 */
public class StructuredOutputValidator {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /**
     * 校验文本输出是否符合响应格式约束
     * @param text 模型输出的文本内容
     * @param responseFormat 响应格式配置
     * @return 校验失败返回错误描述，通过返回null
     */
    public static String validate(String text, AgentResponseFormat responseFormat) {
        if (text == null || text.isBlank() || responseFormat == null) {
            return null;
        }
        if (!"json_schema".equals(responseFormat.getType())) {
            return null;
        }
        AgentJsonSchema jsonSchema = responseFormat.getJsonSchema();
        if (jsonSchema == null || jsonSchema.getSchema() == null || jsonSchema.getSchema().isEmpty()) {
            return null;
        }
        String jsonText = extractJson(text);
        JsonNode node;
        try {
            node = MAPPER.readTree(jsonText);
        } catch (Exception e) {
            return "输出不是合法JSON: " + e.getMessage();
        }
        List<String> errors = new ArrayList<>();
        validateAgainstSchema(node, jsonSchema.getSchema(), "", errors);
        if (!errors.isEmpty()) {
            return String.join("; ", errors);
        }
        return null;
    }

    /**
     * 从可能包含markdown代码块的文本中提取JSON字符串
     * @param text
     * @return
     */
    private static String extractJson(String text) {
        String trimmed = text.trim();
        if (trimmed.startsWith("```")) {
            int firstNewline = trimmed.indexOf('\n');
            if (firstNewline > 0) {
                trimmed = trimmed.substring(firstNewline + 1);
            }
            int lastFence = trimmed.lastIndexOf("```");
            if (lastFence > 0) {
                trimmed = trimmed.substring(0, lastFence);
            }
            return trimmed.trim();
        }
        return trimmed;
    }

    /**
     * 递归校验JSON节点是否符合Schema定义
     * @param node
     * @param schema
     * @param path
     * @param errors
     */
    @SuppressWarnings("unchecked")
    private static void validateAgainstSchema(JsonNode node, Map<String, Object> schema,
                                                String path, List<String> errors) {
        if (schema == null) {
            return;
        }
        Object type = schema.get("type");
        if (type != null) {
            validateType(node, type.toString(), path, errors);
        }
        // 校验required字段
        Object requiredObj = schema.get("required");
        if (requiredObj instanceof Collection<?> requiredList) {
            for (Object req : requiredList) {
                String fieldName = req.toString();
                if (!node.has(fieldName)) {
                    errors.add(path + "缺少必填字段: " + fieldName);
                }
            }
        }
        // 校验object的properties
        Object propertiesObj = schema.get("properties");
        if (propertiesObj instanceof Map<?, ?> properties && node.isObject()) {
            for (Map.Entry<String, Object> entry : ((Map<String, Object>) properties).entrySet()) {
                String fieldName = entry.getKey();
                if (node.has(fieldName) && entry.getValue() instanceof Map<?, ?> fieldSchema) {
                    validateAgainstSchema(node.get(fieldName),
                            (Map<String, Object>) fieldSchema,
                            path.isEmpty() ? fieldName : path + "." + fieldName,
                            errors);
                }
            }
        }
        // 校验array的items
        Object itemsObj = schema.get("items");
        if (itemsObj instanceof Map<?, ?> itemsSchema && node.isArray()) {
            for (int i = 0; i < node.size(); i++) {
                validateAgainstSchema(node.get(i),
                        (Map<String, Object>) itemsSchema,
                        path + "[" + i + "]",
                        errors);
            }
        }
    }

    /**
     * 校验节点类型
     * @param node
     * @param expectedType
     * @param path
     * @param errors
     */
    private static void validateType(JsonNode node, String expectedType, String path, List<String> errors) {
        boolean match = switch (expectedType) {
            case "string" -> node.isTextual();
            case "number", "integer" -> node.isNumber() || node.isInt();
            case "boolean" -> node.isBoolean();
            case "object" -> node.isObject();
            case "array" -> node.isArray();
            case "null" -> node.isNull();
            default -> true;
        };
        if (!match) {
            errors.add(path + "类型不匹配，期望: " + expectedType + "，实际: " + node.getNodeType());
        }
    }
}
