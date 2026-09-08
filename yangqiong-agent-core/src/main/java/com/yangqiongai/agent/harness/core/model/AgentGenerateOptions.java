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
package com.yangqiongai.agent.harness.core.model;

import java.util.Map;
import java.util.Objects;

import com.yangqiongai.agent.harness.config.AgentResponseFormat;
import com.yangqiongai.agent.harness.config.AgentToolChoice;

/**
 * Agent生成选项
 * @author yangqiong
 */
public final class AgentGenerateOptions {

    /**
     * 温度参数
     */
    private final Double temperature;

    /**
     * 最大Token数
     */
    private final Integer maxTokens;

    /**
     * Top-P采样参数
     */
    private final Double topP;

    /**
     * 推理努力等级
     */
    private final String reasoningEffort;

    /**
     * 思考预算Token数
     */
    private final Integer thinkingBudget;

    /**
     * 是否流式输出
     */
    private final Boolean stream;

    /**
     * 响应格式（结构化输出）
     */
    private final AgentResponseFormat responseFormat;

    /**
     * 工具选择策略
     */
    private final AgentToolChoice toolChoice;

    /**
     * 是否启用缓存控制
     */
    private final Boolean cacheControlEnabled;

    /**
     * 厂商专属参数透传
     */
    private final Map<String, Object> extraParameters;

    private AgentGenerateOptions(Double temperature, Integer maxTokens, Double topP,
                                 String reasoningEffort, Integer thinkingBudget, Boolean stream,
                                 AgentResponseFormat responseFormat, AgentToolChoice toolChoice,
                                 Boolean cacheControlEnabled, Map<String, Object> extraParameters) {
        this.temperature = temperature;
        this.maxTokens = maxTokens;
        this.topP = topP;
        this.reasoningEffort = reasoningEffort;
        this.thinkingBudget = thinkingBudget;
        this.stream = stream;
        this.responseFormat = responseFormat;
        this.toolChoice = toolChoice;
        this.cacheControlEnabled = cacheControlEnabled;
        this.extraParameters = extraParameters;
    }

    public static Builder builder() {
        return new Builder();
    }

    /**
     * 合并生成选项，newOptions非null字段覆盖current
     * @param newOptions
     * @param current
     * @return
     */
    public static AgentGenerateOptions mergeOptions(AgentGenerateOptions newOptions, AgentGenerateOptions current) {
        if (newOptions == null) {
            return current;
        }
        if (current == null) {
            return newOptions;
        }
        return new AgentGenerateOptions(
                newOptions.temperature != null ? newOptions.temperature : current.temperature,
                newOptions.maxTokens != null ? newOptions.maxTokens : current.maxTokens,
                newOptions.topP != null ? newOptions.topP : current.topP,
                newOptions.reasoningEffort != null ? newOptions.reasoningEffort : current.reasoningEffort,
                newOptions.thinkingBudget != null ? newOptions.thinkingBudget : current.thinkingBudget,
                newOptions.stream != null ? newOptions.stream : current.stream,
                newOptions.responseFormat != null ? newOptions.responseFormat : current.responseFormat,
                newOptions.toolChoice != null ? newOptions.toolChoice : current.toolChoice,
                newOptions.cacheControlEnabled != null ? newOptions.cacheControlEnabled : current.cacheControlEnabled,
                newOptions.extraParameters != null ? newOptions.extraParameters : current.extraParameters
        );
    }

    /**
     * 获取温度参数
     * @return
     */
    public Double getTemperature() {
        return temperature;
    }

    /**
     * 获取最大Token数
     * @return
     */
    public Integer getMaxTokens() {
        return maxTokens;
    }

    /**
     * 获取Top-P采样参数
     * @return
     */
    public Double getTopP() {
        return topP;
    }

    /**
     * 获取推理努力等级
     * @return
     */
    public String getReasoningEffort() {
        return reasoningEffort;
    }

    /**
     * 获取思考预算Token数
     * @return
     */
    public Integer getThinkingBudget() {
        return thinkingBudget;
    }

    /**
     * 是否流式输出
     * @return
     */
    public boolean isStream() {
        return Boolean.TRUE.equals(stream);
    }

    /**
     * 获取响应格式
     * @return
     */
    public AgentResponseFormat getResponseFormat() {
        return responseFormat;
    }

    /**
     * 获取工具选择策略
     * @return
     */
    public AgentToolChoice getToolChoice() {
        return toolChoice;
    }

    /**
     * 是否启用缓存控制
     * @return
     */
    public boolean isCacheControlEnabled() {
        return Boolean.TRUE.equals(cacheControlEnabled);
    }

    /**
     * 获取厂商专属参数透传
     * @return
     */
    public Map<String, Object> getExtraParameters() {
        return extraParameters;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        AgentGenerateOptions that = (AgentGenerateOptions) o;
        return Objects.equals(temperature, that.temperature)
                && Objects.equals(maxTokens, that.maxTokens)
                && Objects.equals(topP, that.topP)
                && Objects.equals(reasoningEffort, that.reasoningEffort)
                && Objects.equals(thinkingBudget, that.thinkingBudget)
                && Objects.equals(stream, that.stream)
                && Objects.equals(responseFormat, that.responseFormat)
                && Objects.equals(toolChoice, that.toolChoice)
                && Objects.equals(cacheControlEnabled, that.cacheControlEnabled)
                && Objects.equals(extraParameters, that.extraParameters);
    }

    @Override
    public int hashCode() {
        return Objects.hash(temperature, maxTokens, topP, reasoningEffort, thinkingBudget, stream, responseFormat, toolChoice, cacheControlEnabled, extraParameters);
    }

    @Override
    public String toString() {
        return "AgentGenerateOptions{temperature=" + temperature
                + ", maxTokens=" + maxTokens
                + ", topP=" + topP
                + ", reasoningEffort='" + reasoningEffort + "'"
                + ", thinkingBudget=" + thinkingBudget
                + ", stream=" + stream
                + ", responseFormat=" + responseFormat
                + ", toolChoice=" + toolChoice
                + ", cacheControlEnabled=" + cacheControlEnabled + "}";
    }

    /**
     * 生成选项构建器
     * @author yangqiong
     */
    public static class Builder {

        /**
         * 温度参数
         */
        private Double temperature;

        /**
         * 最大Token数
         */
        private Integer maxTokens;

        /**
         * Top-P采样参数
         */
        private Double topP;

        /**
         * 推理努力等级
         */
        private String reasoningEffort;

        /**
         * 思考预算Token数
         */
        private Integer thinkingBudget;

        /**
         * 是否流式输出
         */
        private Boolean stream;

        /**
         * 响应格式
         */
        private AgentResponseFormat responseFormat;

        /**
         * 工具选择策略
         */
        private AgentToolChoice toolChoice;

        /**
         * 是否启用缓存控制
         */
        private Boolean cacheControlEnabled;

        /**
         * 厂商专属参数透传
         */
        private Map<String, Object> extraParameters;

        public Builder temperature(Double temperature) {
            this.temperature = temperature;
            return this;
        }

        public Builder maxTokens(Integer maxTokens) {
            this.maxTokens = maxTokens;
            return this;
        }

        public Builder topP(Double topP) {
            this.topP = topP;
            return this;
        }

        public Builder reasoningEffort(String reasoningEffort) {
            this.reasoningEffort = reasoningEffort;
            return this;
        }

        public Builder thinkingBudget(Integer thinkingBudget) {
            this.thinkingBudget = thinkingBudget;
            return this;
        }

        public Builder stream(Boolean stream) {
            this.stream = stream;
            return this;
        }

        public Builder responseFormat(AgentResponseFormat responseFormat) {
            this.responseFormat = responseFormat;
            return this;
        }

        public Builder toolChoice(AgentToolChoice toolChoice) {
            this.toolChoice = toolChoice;
            return this;
        }

        public Builder cacheControlEnabled(Boolean cacheControlEnabled) {
            this.cacheControlEnabled = cacheControlEnabled;
            return this;
        }

        public Builder extraParameters(Map<String, Object> extraParameters) {
            this.extraParameters = extraParameters;
            return this;
        }

        public AgentGenerateOptions build() {
            return new AgentGenerateOptions(temperature, maxTokens, topP, reasoningEffort,
                    thinkingBudget, stream, responseFormat, toolChoice, cacheControlEnabled,
                    extraParameters);
        }
    }
}
