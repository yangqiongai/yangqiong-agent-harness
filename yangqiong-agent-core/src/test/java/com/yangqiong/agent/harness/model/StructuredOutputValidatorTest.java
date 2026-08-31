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
package com.yangqiong.agent.harness.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.yangqiong.agent.harness.config.AgentJsonSchema;
import com.yangqiong.agent.harness.config.AgentResponseFormat;

/**
 * 结构化输出校验器测试
 * @author yangqiong
 */
class StructuredOutputValidatorTest {

    @Test
    void shouldReturnNullWhenTextNull() {
        assertThat(StructuredOutputValidator.validate(null, createObjectSchema())).isNull();
    }

    @Test
    void shouldReturnNullWhenResponseFormatNull() {
        assertThat(StructuredOutputValidator.validate("{}", null)).isNull();
    }

    @Test
    void shouldReturnNullWhenNotJsonSchemaType() {
        AgentResponseFormat format = new AgentResponseFormat("text", null);
        assertThat(StructuredOutputValidator.validate("hello", format)).isNull();
    }

    @Test
    void shouldReturnErrorWhenInvalidJson() {
        AgentResponseFormat format = createObjectSchema();
        String error = StructuredOutputValidator.validate("not a json", format);
        assertThat(error).contains("不是合法JSON");
    }

    @Test
    void shouldPassValidJsonObject() {
        AgentResponseFormat format = createObjectSchema();
        assertThat(StructuredOutputValidator.validate("{\"name\":\"test\",\"age\":18}", format)).isNull();
    }

    @Test
    void shouldDetectMissingRequiredField() {
        AgentResponseFormat format = createObjectSchema();
        String error = StructuredOutputValidator.validate("{\"name\":\"test\"}", format);
        assertThat(error).contains("缺少必填字段").contains("age");
    }

    @Test
    void shouldDetectTypeMismatch() {
        AgentResponseFormat format = createObjectSchema();
        String error = StructuredOutputValidator.validate("{\"name\":123,\"age\":18}", format);
        assertThat(error).contains("类型不匹配");
    }

    @Test
    void shouldExtractJsonFromMarkdownCodeBlock() {
        AgentResponseFormat format = createObjectSchema();
        String markdown = "```json\n{\"name\":\"test\",\"age\":18}\n```";
        assertThat(StructuredOutputValidator.validate(markdown, format)).isNull();
    }

    @Test
    void shouldValidateNestedObject() {
        Map<String, Object> addressSchema = new HashMap<>();
        addressSchema.put("type", "object");
        addressSchema.put("required", List.of("city"));
        Map<String, Object> addressProps = new HashMap<>();
        addressProps.put("city", Map.of("type", "string"));
        addressSchema.put("properties", addressProps);

        Map<String, Object> schema = new HashMap<>();
        schema.put("type", "object");
        schema.put("required", List.of("name", "address"));
        Map<String, Object> props = new HashMap<>();
        props.put("name", Map.of("type", "string"));
        props.put("address", addressSchema);
        schema.put("properties", props);

        AgentJsonSchema jsonSchema = AgentJsonSchema.builder().schema(schema).build();
        AgentResponseFormat format = AgentResponseFormat.jsonSchema(jsonSchema);

        assertThat(StructuredOutputValidator.validate(
                "{\"name\":\"test\",\"address\":{\"city\":\"BJ\"}}", format)).isNull();

        String error = StructuredOutputValidator.validate(
                "{\"name\":\"test\",\"address\":{}}", format);
        assertThat(error).contains("address").contains("缺少必填字段").contains("city");
    }

    @Test
    void shouldValidateArrayItems() {
        Map<String, Object> itemSchema = Map.of("type", "integer");
        Map<String, Object> schema = new HashMap<>();
        schema.put("type", "array");
        schema.put("items", itemSchema);

        AgentJsonSchema jsonSchema = AgentJsonSchema.builder().schema(schema).build();
        AgentResponseFormat format = AgentResponseFormat.jsonSchema(jsonSchema);

        assertThat(StructuredOutputValidator.validate("[1,2,3]", format)).isNull();

        String error = StructuredOutputValidator.validate("[1,\"two\",3]", format);
        assertThat(error).contains("[1]").contains("类型不匹配");
    }

    private AgentResponseFormat createObjectSchema() {
        Map<String, Object> schema = new HashMap<>();
        schema.put("type", "object");
        schema.put("required", List.of("name", "age"));
        Map<String, Object> props = new HashMap<>();
        props.put("name", Map.of("type", "string"));
        props.put("age", Map.of("type", "integer"));
        schema.put("properties", props);
        AgentJsonSchema jsonSchema = AgentJsonSchema.builder().schema(schema).build();
        return AgentResponseFormat.jsonSchema(jsonSchema);
    }
}
