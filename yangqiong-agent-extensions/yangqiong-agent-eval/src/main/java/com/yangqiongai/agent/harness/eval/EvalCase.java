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

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 评测用例
 * @author yangqiong
 */
public final class EvalCase {

    /**
     * 用例ID
     */
    private final String id;

    /**
     * 用户查询语句
     */
    private final String query;

    /**
     * 期望调用的工具名称列表
     */
    private final List<String> expectedToolNames;

    /**
     * 期望工具调用参数（工具名到参数键值映射，实际参数需包含期望键且值匹配）
     */
    private final Map<String, Map<String, Object>> expectedToolArgs;

    /**
     * 禁止调用的工具名称列表（误调用检测）
     */
    private final List<String> forbiddenToolNames;

    /**
     * 期望最终答案命中的关键词列表
     */
    private final List<String> expectedKeywords;

    /**
     * 扩展元数据
     */
    private final Map<String, Object> metadata;

    private EvalCase(Builder builder) {
        this.id = builder.id;
        this.query = builder.query;
        this.expectedToolNames = builder.expectedToolNames;
        this.expectedToolArgs = builder.expectedToolArgs;
        this.forbiddenToolNames = builder.forbiddenToolNames;
        this.expectedKeywords = builder.expectedKeywords;
        this.metadata = builder.metadata;
    }

    /**
     * 创建构建器
     * @return
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * 获取用例ID
     * @return
     */
    public String getId() {
        return id;
    }

    /**
     * 获取用户查询语句
     * @return
     */
    public String getQuery() {
        return query;
    }

    /**
     * 获取期望调用的工具名称列表
     * @return
     */
    public List<String> getExpectedToolNames() {
        return expectedToolNames;
    }

    /**
     * 获取期望工具调用参数（工具名到参数键值映射）
     * @return
     */
    public Map<String, Map<String, Object>> getExpectedToolArgs() {
        return expectedToolArgs;
    }

    /**
     * 获取禁止调用的工具名称列表
     * @return
     */
    public List<String> getForbiddenToolNames() {
        return forbiddenToolNames;
    }

    /**
     * 获取期望最终答案命中的关键词列表
     * @return
     */
    public List<String> getExpectedKeywords() {
        return expectedKeywords;
    }

    /**
     * 获取扩展元数据
     * @return
     */
    public Map<String, Object> getMetadata() {
        return metadata;
    }

    /**
     * 评测用例构建器
     * @author yangqiong
     */
    public static final class Builder {

        private String id;

        private String query;

        private List<String> expectedToolNames = List.of();

        private Map<String, Map<String, Object>> expectedToolArgs = Map.of();

        private List<String> forbiddenToolNames = List.of();

        private List<String> expectedKeywords = List.of();

        private Map<String, Object> metadata = Map.of();

        private Builder() {
        }

        /**
         * 设置用例ID
         * @param id
         * @return
         */
        public Builder id(String id) {
            this.id = id;
            return this;
        }

        /**
         * 设置用户查询语句（必填）
         * @param query
         * @return
         */
        public Builder query(String query) {
            this.query = query;
            return this;
        }

        /**
         * 设置期望调用的工具名称列表
         * @param expectedToolNames
         * @return
         */
        public Builder expectedToolNames(List<String> expectedToolNames) {
            this.expectedToolNames = expectedToolNames == null ? List.of() : List.copyOf(expectedToolNames);
            return this;
        }

        /**
         * 设置期望工具调用参数（工具名到参数键值映射，值匹配规则：字符串为包含匹配，数值与布尔为相等匹配）
         * @param expectedToolArgs
         * @return
         */
        public Builder expectedToolArgs(Map<String, Map<String, Object>> expectedToolArgs) {
            if (expectedToolArgs == null) {
                this.expectedToolArgs = Map.of();
                return this;
            }
            Map<String, Map<String, Object>> copied = new LinkedHashMap<>();
            expectedToolArgs.forEach((toolName, args) ->
                    copied.put(toolName, args == null ? Map.of() : Map.copyOf(args)));
            this.expectedToolArgs = Collections.unmodifiableMap(copied);
            return this;
        }

        /**
         * 设置禁止调用的工具名称列表（误调用检测，调用任一禁止工具即工具维度得0分）
         * @param forbiddenToolNames
         * @return
         */
        public Builder forbiddenToolNames(List<String> forbiddenToolNames) {
            this.forbiddenToolNames = forbiddenToolNames == null ? List.of() : List.copyOf(forbiddenToolNames);
            return this;
        }

        /**
         * 设置期望最终答案命中的关键词列表
         * @param expectedKeywords
         * @return
         */
        public Builder expectedKeywords(List<String> expectedKeywords) {
            this.expectedKeywords = expectedKeywords == null ? List.of() : List.copyOf(expectedKeywords);
            return this;
        }

        /**
         * 设置扩展元数据
         * @param metadata
         * @return
         */
        public Builder metadata(Map<String, Object> metadata) {
            this.metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
            return this;
        }

        /**
         * 构建评测用例，query为空时抛出异常
         * @return
         */
        public EvalCase build() {
            Objects.requireNonNull(query, "query不能为空");
            if (query.isBlank()) {
                throw new IllegalArgumentException("query不能为空白字符串");
            }
            return new EvalCase(this);
        }
    }
}
