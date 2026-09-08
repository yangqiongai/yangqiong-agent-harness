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

import java.util.List;
import java.util.Map;

import com.yangqiongai.agent.harness.config.AgentJsonSchema;
import com.yangqiongai.agent.harness.config.AgentResponseFormat;
import com.yangqiongai.agent.harness.config.AgentToolChoice;
import com.yangqiongai.agent.harness.core.model.AgentGenerateOptions;

/**
 * 生成选项构建器
 * @author yangqiong
 */
public class GenerateOptionsBuilder {

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
     * 工具选择策略
     */
    private AgentToolChoice toolChoice;

    /**
     * 响应格式
     */
    private AgentResponseFormat responseFormat;

    /**
     * JSON Schema定义
     */
    private AgentJsonSchema jsonSchema;

    /**
     * 停止序列
     */
    private List<String> stop;

    /**
     * 厂商专属参数透传
     */
    private Map<String, Object> extraParameters;

    /**
     * 合并两个生成选项，override非空字段覆盖base
     * @param base
     * @param override
     * @return
     */
    public static AgentGenerateOptions merge(AgentGenerateOptions base, AgentGenerateOptions override) {
        return AgentGenerateOptions.mergeOptions(override, base);
    }

    /**
     * 设置温度参数
     * @param temperature
     * @return
     */
    public GenerateOptionsBuilder temperature(Double temperature) {
        this.temperature = temperature;
        return this;
    }

    /**
     * 设置最大Token数
     * @param maxTokens
     * @return
     */
    public GenerateOptionsBuilder maxTokens(Integer maxTokens) {
        this.maxTokens = maxTokens;
        return this;
    }

    /**
     * 设置Top-P采样参数
     * @param topP
     * @return
     */
    public GenerateOptionsBuilder topP(Double topP) {
        this.topP = topP;
        return this;
    }

    /**
     * 设置工具选择策略
     * @param toolChoice
     * @return
     */
    public GenerateOptionsBuilder toolChoice(AgentToolChoice toolChoice) {
        this.toolChoice = toolChoice;
        return this;
    }

    /**
     * 设置响应格式
     * @param responseFormat
     * @return
     */
    public GenerateOptionsBuilder responseFormat(AgentResponseFormat responseFormat) {
        this.responseFormat = responseFormat;
        return this;
    }

    /**
     * 设置JSON Schema定义
     * @param jsonSchema
     * @return
     */
    public GenerateOptionsBuilder jsonSchema(AgentJsonSchema jsonSchema) {
        this.jsonSchema = jsonSchema;
        return this;
    }

    /**
     * 设置停止序列
     * @param stop
     * @return
     */
    public GenerateOptionsBuilder stop(List<String> stop) {
        this.stop = stop;
        return this;
    }

    /**
     * 设置厂商专属参数透传
     * @param extraParameters
     * @return
     */
    public GenerateOptionsBuilder extraParameters(Map<String, Object> extraParameters) {
        this.extraParameters = extraParameters;
        return this;
    }

    /**
     * 构建生成选项
     * @return
     */
    public AgentGenerateOptions build() {
        AgentResponseFormat effectiveFormat = responseFormat;
        if (effectiveFormat == null && jsonSchema != null) {
            effectiveFormat = new AgentResponseFormat("json_schema", jsonSchema);
        }
        return AgentGenerateOptions.builder()
                .temperature(temperature)
                .maxTokens(maxTokens)
                .topP(topP)
                .toolChoice(toolChoice)
                .responseFormat(effectiveFormat)
                .extraParameters(extraParameters)
                .build();
    }

    /**
     * 获取工具选择策略
     * @return
     */
    public AgentToolChoice getToolChoice() {
        return toolChoice;
    }

    /**
     * 获取响应格式
     * @return
     */
    public AgentResponseFormat getResponseFormat() {
        return responseFormat;
    }

    /**
     * 获取JSON Schema定义
     * @return
     */
    public AgentJsonSchema getJsonSchema() {
        return jsonSchema;
    }

    /**
     * 获取停止序列
     * @return
     */
    public List<String> getStop() {
        return stop;
    }
}
