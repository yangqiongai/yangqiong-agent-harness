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
package com.yangqiongai.agent.harness.config;

import java.util.Objects;

/**
 * Agent响应格式
 * @author yangqiong
 */
public final class AgentResponseFormat {

    private final String type;

    private final AgentJsonSchema jsonSchema;

    public AgentResponseFormat(String type, AgentJsonSchema jsonSchema) {
        this.type = type;
        this.jsonSchema = jsonSchema;
    }

    /**
     * 创建JSON Schema响应格式
     * @param schema
     * @return
     */
    public static AgentResponseFormat jsonSchema(AgentJsonSchema schema) {
        return new AgentResponseFormat("json_schema", schema);
    }

    /**
     * 获取格式类型
     * @return
     */
    public String getType() {
        return type;
    }

    /**
     * 获取JSON Schema定义
     * @return
     */
    public AgentJsonSchema getJsonSchema() {
        return jsonSchema;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        AgentResponseFormat that = (AgentResponseFormat) o;
        return Objects.equals(type, that.type) && Objects.equals(jsonSchema, that.jsonSchema);
    }

    @Override
    public int hashCode() {
        return Objects.hash(type, jsonSchema);
    }

    @Override
    public String toString() {
        return "AgentResponseFormat{type='" + type + "', jsonSchema=" + jsonSchema + "}";
    }
}