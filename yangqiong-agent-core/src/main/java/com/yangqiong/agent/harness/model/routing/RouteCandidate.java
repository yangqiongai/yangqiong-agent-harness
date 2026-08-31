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
package com.yangqiong.agent.harness.model.routing;

import java.util.Set;

import com.yangqiong.agent.harness.core.model.AgentModel;

/**
 * 路由候选
 * @author yangqiong
 */
public final class RouteCandidate {

    /**
     * 模型编码
     */
    private final String modelCode;

    /**
     * 模型实例
     */
    private final AgentModel model;

    /**
     * 每百万Token成本
     */
    private final double costPerMillionTokens;

    /**
     * 平均延迟毫秒数
     */
    private final double avgLatencyMs;

    /**
     * 上下文窗口Token数
     */
    private final int contextWindowTokens;

    /**
     * 该模型擅长的任务类型集合
     */
    private final Set<String> taskTypes;

    private RouteCandidate(Builder builder) {
        if (builder.modelCode == null || builder.modelCode.isBlank()) {
            throw new IllegalArgumentException("modelCode不能为空");
        }
        if (builder.model == null) {
            throw new IllegalArgumentException("model不能为空");
        }
        this.modelCode = builder.modelCode;
        this.model = builder.model;
        this.costPerMillionTokens = builder.costPerMillionTokens;
        this.avgLatencyMs = builder.avgLatencyMs;
        this.contextWindowTokens = builder.contextWindowTokens;
        this.taskTypes = builder.taskTypes == null ? Set.of() : Set.copyOf(builder.taskTypes);
    }

    /**
     * 创建路由候选构建器
     * @return
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * 获取模型编码
     * @return
     */
    public String getModelCode() {
        return modelCode;
    }

    /**
     * 获取模型实例
     * @return
     */
    public AgentModel getModel() {
        return model;
    }

    /**
     * 获取每百万Token成本
     * @return
     */
    public double getCostPerMillionTokens() {
        return costPerMillionTokens;
    }

    /**
     * 获取平均延迟毫秒数
     * @return
     */
    public double getAvgLatencyMs() {
        return avgLatencyMs;
    }

    /**
     * 获取上下文窗口Token数
     * @return
     */
    public int getContextWindowTokens() {
        return contextWindowTokens;
    }

    /**
     * 获取擅长任务类型集合
     * @return
     */
    public Set<String> getTaskTypes() {
        return taskTypes;
    }

    @Override
    public String toString() {
        return "RouteCandidate{modelCode='" + modelCode + "', costPerMillionTokens=" + costPerMillionTokens
                + ", avgLatencyMs=" + avgLatencyMs + ", contextWindowTokens=" + contextWindowTokens
                + ", taskTypes=" + taskTypes + "}";
    }

    /**
     * 路由候选构建器
     * @author yangqiong
     */
    public static class Builder {

        /**
         * 模型编码
         */
        private String modelCode;

        /**
         * 模型实例
         */
        private AgentModel model;

        /**
         * 每百万Token成本
         */
        private double costPerMillionTokens = 1.0d;

        /**
         * 平均延迟毫秒数
         */
        private double avgLatencyMs = 1000.0d;

        /**
         * 上下文窗口Token数
         */
        private int contextWindowTokens = 8192;

        /**
         * 擅长任务类型集合
         */
        private Set<String> taskTypes;

        public Builder modelCode(String modelCode) {
            this.modelCode = modelCode;
            return this;
        }

        public Builder model(AgentModel model) {
            this.model = model;
            return this;
        }

        public Builder costPerMillionTokens(double costPerMillionTokens) {
            this.costPerMillionTokens = costPerMillionTokens;
            return this;
        }

        public Builder avgLatencyMs(double avgLatencyMs) {
            this.avgLatencyMs = avgLatencyMs;
            return this;
        }

        public Builder contextWindowTokens(int contextWindowTokens) {
            this.contextWindowTokens = contextWindowTokens;
            return this;
        }

        public Builder taskTypes(Set<String> taskTypes) {
            this.taskTypes = taskTypes;
            return this;
        }

        public RouteCandidate build() {
            return new RouteCandidate(this);
        }
    }
}
