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
package com.yangqiong.agent.harness.tool;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

/**
 * 工具输入参数校验器测试
 * @author yangqiong
 */
class ToolInputValidatorTest {

    @Test
    void shouldReturnNullWhenSchemaIsEmpty() {
        assertThat(ToolInputValidator.validate(Map.of(), Map.of("a", 1))).isNull();
    }

    @Test
    void shouldReturnNullWhenSchemaIsNull() {
        assertThat(ToolInputValidator.validate(null, Map.of("a", 1))).isNull();
    }

    @Test
    void shouldReturnNullWhenInputIsNull() {
        Map<String, Object> schema = Map.of("required", List.of("name"));
        assertThat(ToolInputValidator.validate(schema, null)).contains("缺少必填参数: name");
    }

    @Test
    void shouldReturnNullWhenInputIsValid() {
        Map<String, Object> schema = Map.of(
                "required", List.of("name"),
                "properties", Map.of(
                        "name", Map.of("type", "string"),
                        "age", Map.of("type", "integer")));
        Map<String, Object> input = Map.of("name", "张三", "age", 25);
        assertThat(ToolInputValidator.validate(schema, input)).isNull();
    }

    @Test
    void shouldDetectMissingRequiredField() {
        Map<String, Object> schema = Map.of(
                "required", List.of("name", "age"),
                "properties", Map.of(
                        "name", Map.of("type", "string"),
                        "age", Map.of("type", "integer")));
        Map<String, Object> input = Map.of("name", "张三");
        String error = ToolInputValidator.validate(schema, input);
        assertThat(error).contains("缺少必填参数: age");
    }

    @Test
    void shouldDetectNullRequiredField() {
        Map<String, Object> schema = Map.of(
                "required", List.of("name"),
                "properties", Map.of("name", Map.of("type", "string")));
        Map<String, Object> input = new HashMap<>();
        input.put("name", null);
        String error = ToolInputValidator.validate(schema, input);
        assertThat(error).contains("缺少必填参数: name");
    }

    @Test
    void shouldDetectWrongTypeString() {
        Map<String, Object> schema = Map.of(
                "properties", Map.of("name", Map.of("type", "string")));
        Map<String, Object> input = Map.of("name", 123);
        String error = ToolInputValidator.validate(schema, input);
        assertThat(error).contains("参数 name 类型错误");
        assertThat(error).contains("期望string");
        assertThat(error).contains("实际integer");
    }

    @Test
    void shouldDetectWrongTypeInteger() {
        Map<String, Object> schema = Map.of(
                "properties", Map.of("age", Map.of("type", "integer")));
        Map<String, Object> input = Map.of("age", "二十五");
        String error = ToolInputValidator.validate(schema, input);
        assertThat(error).contains("参数 age 类型错误");
        assertThat(error).contains("期望integer");
        assertThat(error).contains("实际string");
    }

    @Test
    void shouldDetectWrongTypeBoolean() {
        Map<String, Object> schema = Map.of(
                "properties", Map.of("flag", Map.of("type", "boolean")));
        Map<String, Object> input = Map.of("flag", "true");
        String error = ToolInputValidator.validate(schema, input);
        assertThat(error).contains("参数 flag 类型错误");
        assertThat(error).contains("期望boolean");
    }

    @Test
    void shouldDetectWrongTypeArray() {
        Map<String, Object> schema = Map.of(
                "properties", Map.of("items", Map.of("type", "array")));
        Map<String, Object> input = Map.of("items", "not-a-list");
        String error = ToolInputValidator.validate(schema, input);
        assertThat(error).contains("参数 items 类型错误");
        assertThat(error).contains("期望array");
    }

    @Test
    void shouldDetectWrongTypeObject() {
        Map<String, Object> schema = Map.of(
                "properties", Map.of("config", Map.of("type", "object")));
        Map<String, Object> input = Map.of("config", "not-a-map");
        String error = ToolInputValidator.validate(schema, input);
        assertThat(error).contains("参数 config 类型错误");
        assertThat(error).contains("期望object");
    }

    @Test
    void shouldDetectNumberTypeMismatch() {
        Map<String, Object> schema = Map.of(
                "properties", Map.of("price", Map.of("type", "number")));
        Map<String, Object> input = Map.of("price", "贵");
        String error = ToolInputValidator.validate(schema, input);
        assertThat(error).contains("参数 price 类型错误");
        assertThat(error).contains("期望number");
    }

    @Test
    void shouldAcceptIntegerAsNumber() {
        Map<String, Object> schema = Map.of(
                "properties", Map.of("price", Map.of("type", "number")));
        Map<String, Object> input = Map.of("price", 100);
        assertThat(ToolInputValidator.validate(schema, input)).isNull();
    }

    @Test
    void shouldDetectStringMinLengthViolation() {
        Map<String, Object> schema = Map.of(
                "properties", Map.of(
                        "name", Map.of("type", "string", "minLength", 3)));
        Map<String, Object> input = Map.of("name", "ab");
        String error = ToolInputValidator.validate(schema, input);
        assertThat(error).contains("参数 name 长度不足");
        assertThat(error).contains("最小3");
    }

    @Test
    void shouldDetectStringMaxLengthViolation() {
        Map<String, Object> schema = Map.of(
                "properties", Map.of(
                        "name", Map.of("type", "string", "maxLength", 5)));
        Map<String, Object> input = Map.of("name", "张三李四王五");
        String error = ToolInputValidator.validate(schema, input);
        assertThat(error).contains("参数 name 长度超限");
        assertThat(error).contains("最大5");
    }

    @Test
    void shouldDetectEnumViolation() {
        Map<String, Object> schema = Map.of(
                "properties", Map.of(
                        "color", Map.of("type", "string", "enum", List.of("red", "green", "blue"))));
        Map<String, Object> input = Map.of("color", "yellow");
        String error = ToolInputValidator.validate(schema, input);
        assertThat(error).contains("参数 color 值不在允许范围内");
    }

    @Test
    void shouldAcceptValidEnumValue() {
        Map<String, Object> schema = Map.of(
                "properties", Map.of(
                        "color", Map.of("type", "string", "enum", List.of("red", "green", "blue"))));
        Map<String, Object> input = Map.of("color", "red");
        assertThat(ToolInputValidator.validate(schema, input)).isNull();
    }

    @Test
    void shouldDetectArrayMinItemsViolation() {
        Map<String, Object> schema = Map.of(
                "properties", Map.of(
                        "tags", Map.of("type", "array", "minItems", 2)));
        Map<String, Object> input = Map.of("tags", List.of("a"));
        String error = ToolInputValidator.validate(schema, input);
        assertThat(error).contains("参数 tags 元素不足");
        assertThat(error).contains("最少2");
    }

    @Test
    void shouldDetectArrayMaxItemsViolation() {
        Map<String, Object> schema = Map.of(
                "properties", Map.of(
                        "tags", Map.of("type", "array", "maxItems", 3)));
        Map<String, Object> input = Map.of("tags", List.of("a", "b", "c", "d"));
        String error = ToolInputValidator.validate(schema, input);
        assertThat(error).contains("参数 tags 元素超限");
        assertThat(error).contains("最多3");
    }

    @Test
    void shouldDetectNumberMinimumViolation() {
        Map<String, Object> schema = Map.of(
                "properties", Map.of(
                        "age", Map.of("type", "integer", "minimum", 0)));
        Map<String, Object> input = Map.of("age", -1);
        String error = ToolInputValidator.validate(schema, input);
        assertThat(error).contains("参数 age 值过小");
    }

    @Test
    void shouldDetectNumberMaximumViolation() {
        Map<String, Object> schema = Map.of(
                "properties", Map.of(
                        "age", Map.of("type", "integer", "maximum", 150)));
        Map<String, Object> input = Map.of("age", 200);
        String error = ToolInputValidator.validate(schema, input);
        assertThat(error).contains("参数 age 值过大");
    }

    @Test
    void shouldCollectMultipleErrors() {
        Map<String, Object> schema = Map.of(
                "required", List.of("name", "age"),
                "properties", Map.of(
                        "name", Map.of("type", "string"),
                        "age", Map.of("type", "integer"),
                        "email", Map.of("type", "string")));
        Map<String, Object> input = Map.of("age", "不是数字", "email", 123);
        String error = ToolInputValidator.validate(schema, input);
        assertThat(error).contains("缺少必填参数: name");
        assertThat(error).contains("参数 age 类型错误");
        assertThat(error).contains("参数 email 类型错误");
    }

    @Test
    void shouldNotValidateFieldNotInSchema() {
        Map<String, Object> schema = Map.of(
                "properties", Map.of("name", Map.of("type", "string")));
        Map<String, Object> input = Map.of("name", "张三", "extra", "额外字段");
        assertThat(ToolInputValidator.validate(schema, input)).isNull();
    }
}
