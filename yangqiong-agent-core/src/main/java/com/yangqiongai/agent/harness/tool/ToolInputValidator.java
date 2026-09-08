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
package com.yangqiongai.agent.harness.tool;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * 工具输入参数Schema校验器
 * <p>基于工具的JSON Schema定义，在执行前校验LLM返回的参数是否合法</p>
 * @author yangqiong
 */
public final class ToolInputValidator {

    private ToolInputValidator() {
    }

    /**
     * 校验工具输入参数是否符合Schema定义
     * @param parameters 工具的JSON Schema定义
     * @param input LLM返回的输入参数
     * @return 校验失败返回错误信息，校验通过返回null
     */
    public static String validate(Map<String, Object> parameters, Map<String, Object> input) {
        if (parameters == null || parameters.isEmpty()) {
            return null;
        }
        if (input == null) {
            input = Map.of();
        }

        List<String> errors = new ArrayList<>();

        Object requiredObj = parameters.get("required");
        if (requiredObj instanceof Collection) {
            @SuppressWarnings("unchecked")
            Collection<String> required = (Collection<String>) requiredObj;
            for (String field : required) {
                if (!input.containsKey(field) || input.get(field) == null) {
                    errors.add("缺少必填参数: " + field);
                }
            }
        }

        Object propertiesObj = parameters.get("properties");
        if (propertiesObj instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> properties = (Map<String, Object>) propertiesObj;
            for (Map.Entry<String, Object> entry : properties.entrySet()) {
                String fieldName = entry.getKey();
                if (!input.containsKey(fieldName) || input.get(fieldName) == null) {
                    continue;
                }
                Object fieldSchema = entry.getValue();
                if (!(fieldSchema instanceof Map)) {
                    continue;
                }
                @SuppressWarnings("unchecked")
                Map<String, Object> schema = (Map<String, Object>) fieldSchema;
                Object value = input.get(fieldName);
                String typeError = checkType(fieldName, value, schema);
                if (typeError != null) {
                    errors.add(typeError);
                    continue;
                }
                String constraintError = checkConstraints(fieldName, value, schema);
                if (constraintError != null) {
                    errors.add(constraintError);
                }
            }
        }

        if (errors.isEmpty()) {
            return null;
        }
        return String.join("; ", errors);
    }

    /**
     * 校验字段类型
     * @param fieldName
     * @param value
     * @param schema
     * @return
     */
    @SuppressWarnings("unchecked")
    private static String checkType(String fieldName, Object value, Map<String, Object> schema) {
        Object typeObj = schema.get("type");
        if (typeObj == null) {
            return null;
        }
        String expectedType = typeObj.toString();

        switch (expectedType) {
            case "string":
                if (!(value instanceof String)) {
                    return String.format("参数 %s 类型错误: 期望string, 实际%s", fieldName, getTypeName(value));
                }
                break;
            case "integer":
                if (!(value instanceof Integer) && !(value instanceof Long)) {
                    return String.format("参数 %s 类型错误: 期望integer, 实际%s", fieldName, getTypeName(value));
                }
                break;
            case "number":
                if (!(value instanceof Number)) {
                    return String.format("参数 %s 类型错误: 期望number, 实际%s", fieldName, getTypeName(value));
                }
                break;
            case "boolean":
                if (!(value instanceof Boolean)) {
                    return String.format("参数 %s 类型错误: 期望boolean, 实际%s", fieldName, getTypeName(value));
                }
                break;
            case "array":
                if (!(value instanceof List)) {
                    return String.format("参数 %s 类型错误: 期望array, 实际%s", fieldName, getTypeName(value));
                }
                break;
            case "object":
                if (!(value instanceof Map)) {
                    return String.format("参数 %s 类型错误: 期望object, 实际%s", fieldName, getTypeName(value));
                }
                break;
            default:
                break;
        }
        return null;
    }

    /**
     * 校验字段约束
     * @param fieldName
     * @param value
     * @param schema
     * @return
     */
    @SuppressWarnings("unchecked")
    private static String checkConstraints(String fieldName, Object value, Map<String, Object> schema) {
        Object typeObj = schema.get("type");
        String type = typeObj != null ? typeObj.toString() : "";

        if ("string".equals(type) && value instanceof String) {
            String str = (String) value;
            Object minLength = schema.get("minLength");
            if (minLength instanceof Number && str.length() < ((Number) minLength).intValue()) {
                return String.format("参数 %s 长度不足: 最小%d, 实际%d", fieldName, ((Number) minLength).intValue(), str.length());
            }
            Object maxLength = schema.get("maxLength");
            if (maxLength instanceof Number && str.length() > ((Number) maxLength).intValue()) {
                return String.format("参数 %s 长度超限: 最大%d, 实际%d", fieldName, ((Number) maxLength).intValue(), str.length());
            }
            Object enumObj = schema.get("enum");
            if (enumObj instanceof Collection) {
                Collection<String> allowed = (Collection<String>) enumObj;
                if (!allowed.contains(str)) {
                    return String.format("参数 %s 值不在允许范围内: 允许%s, 实际%s", fieldName, allowed, str);
                }
            }
        }

        if ("array".equals(type) && value instanceof List) {
            List<?> list = (List<?>) value;
            Object minItems = schema.get("minItems");
            if (minItems instanceof Number && list.size() < ((Number) minItems).intValue()) {
                return String.format("参数 %s 元素不足: 最少%d, 实际%d", fieldName, ((Number) minItems).intValue(), list.size());
            }
            Object maxItems = schema.get("maxItems");
            if (maxItems instanceof Number && list.size() > ((Number) maxItems).intValue()) {
                return String.format("参数 %s 元素超限: 最多%d, 实际%d", fieldName, ((Number) maxItems).intValue(), list.size());
            }
        }

        if ("integer".equals(type) || "number".equals(type)) {
            if (value instanceof Number) {
                Number num = (Number) value;
                Object minimum = schema.get("minimum");
                if (minimum instanceof Number && num.doubleValue() < ((Number) minimum).doubleValue()) {
                    return String.format("参数 %s 值过小: 最小%s, 实际%s", fieldName, minimum, num);
                }
                Object maximum = schema.get("maximum");
                if (maximum instanceof Number && num.doubleValue() > ((Number) maximum).doubleValue()) {
                    return String.format("参数 %s 值过大: 最大%s, 实际%s", fieldName, maximum, num);
                }
            }
        }

        return null;
    }

    /**
     * 获取值的类型名称
     * @param value
     * @return
     */
    private static String getTypeName(Object value) {
        if (value == null) {
            return "null";
        }
        Class<?> clazz = value.getClass();
        if (clazz == String.class) {
            return "string";
        }
        if (clazz == Integer.class || clazz == Long.class) {
            return "integer";
        }
        if (clazz == Double.class || clazz == Float.class) {
            return "number";
        }
        if (clazz == Boolean.class) {
            return "boolean";
        }
        if (List.class.isAssignableFrom(clazz)) {
            return "array";
        }
        if (Map.class.isAssignableFrom(clazz)) {
            return "object";
        }
        return clazz.getSimpleName();
    }
}
