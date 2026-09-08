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
package com.yangqiongai.agent.harness.core.tool;

import java.util.Map;

/**
 * Agent工具规格定义
 * @author yangqiong
 */
public final class AgentToolSpec {

    /**
     * 工具名称
     */
    private final String name;

    /**
     * 工具描述
     */
    private final String description;

    /**
     * 工具参数定义
     */
    private final Map<String, Object> parameters;

    private AgentToolSpec(String name, String description, Map<String, Object> parameters) {
        this.name = name;
        this.description = description;
        this.parameters = parameters != null ? Map.copyOf(parameters) : Map.of();
    }

    public static Builder builder() {
        return new Builder();
    }

    /**
     * 获取工具名称
     * @return
     */
    public String getName() {
        return name;
    }

    /**
     * 获取工具描述
     * @return
     */
    public String getDescription() {
        return description;
    }

    /**
     * 获取工具参数定义
     * @return
     */
    public Map<String, Object> getParameters() {
        return parameters;
    }

    /**
     * 工具规格构建器
     * @author yangqiong
     */
    public static class Builder {

        /**
         * 工具名称
         */
        private String name;

        /**
         * 工具描述
         */
        private String description;

        /**
         * 工具参数定义
         */
        private Map<String, Object> parameters;

        public Builder name(String name) {
            this.name = name;
            return this;
        }

        public Builder description(String description) {
            this.description = description;
            return this;
        }

        public Builder parameters(Map<String, Object> parameters) {
            this.parameters = parameters;
            return this;
        }

        public AgentToolSpec build() {
            return new AgentToolSpec(name, description, parameters);
        }
    }
}
