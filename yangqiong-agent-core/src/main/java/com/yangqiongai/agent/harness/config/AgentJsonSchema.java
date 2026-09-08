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

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Agent JSON Schema定义
 * @author yangqiong
 */
public final class AgentJsonSchema {

    private final String name;

    private final Map<String, Object> schema;

    private final Boolean strict;

    private AgentJsonSchema(String name, Map<String, Object> schema, Boolean strict) {
        this.name = name;
        this.schema = schema != null ? Map.copyOf(schema) : Map.of();
        this.strict = strict;
    }

    public static Builder builder() {
        return new Builder();
    }

    /**
     * 获取Schema名称
     * @return
     */
    public String getName() {
        return name;
    }

    /**
     * 获取Schema定义
     * @return
     */
    public Map<String, Object> getSchema() {
        return schema;
    }

    /**
     * 是否严格模式
     * @return
     */
    public boolean isStrict() {
        return Boolean.TRUE.equals(strict);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        AgentJsonSchema that = (AgentJsonSchema) o;
        return Objects.equals(name, that.name)
                && Objects.equals(schema, that.schema)
                && Objects.equals(strict, that.strict);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, schema, strict);
    }

    @Override
    public String toString() {
        return "AgentJsonSchema{name='" + name + "', strict=" + strict + "}";
    }

    /**
     * JSON Schema构建器
     * @author yangqiong
     */
    public static class Builder {

        private String name;

        private Map<String, Object> schema = new HashMap<>();

        private Boolean strict;

        public Builder name(String name) {
            this.name = name;
            return this;
        }

        public Builder schema(Map<String, Object> schema) {
            this.schema = schema != null ? new HashMap<>(schema) : new HashMap<>();
            return this;
        }

        public Builder strict(Boolean strict) {
            this.strict = strict;
            return this;
        }

        public AgentJsonSchema build() {
            return new AgentJsonSchema(name, schema, strict);
        }
    }
}