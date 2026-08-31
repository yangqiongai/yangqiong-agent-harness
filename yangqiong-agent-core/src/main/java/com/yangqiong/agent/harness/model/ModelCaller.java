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

import java.util.Collections;
import java.util.List;
import java.util.Map;

import com.yangqiong.agent.harness.core.message.AgentMessage;
import com.yangqiong.agent.harness.core.model.AgentChatResponse;
import com.yangqiong.agent.harness.core.model.AgentGenerateOptions;
import com.yangqiong.agent.harness.core.model.AgentModel;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * 模型调用器
 * @author yangqiong
 */
public class ModelCaller {

    /**
     * Agent模型
     */
    private final AgentModel model;

    /**
     * 生成选项
     */
    private AgentGenerateOptions generateOptions;

    public ModelCaller(AgentModel model) {
        this.model = model;
    }

    /**
     * 设置生成选项
     * @param generateOptions
     */
    public void setGenerateOptions(AgentGenerateOptions generateOptions) {
        this.generateOptions = generateOptions;
    }

    /**
     * 获取生成选项
     * @return
     */
    protected AgentGenerateOptions getGenerateOptions() {
        return generateOptions;
    }

    /**
     * 获取模型名称，供可观测事件标识
     * @return
     */
    public String modelName() {
        return model.modelName();
    }

    /**
     * 同步调用模型
     * @param messages
     * @param toolSchemas
     * @return
     */
    public Mono<AgentChatResponse> call(List<AgentMessage> messages, List<Map<String, Object>> toolSchemas) {
        List<Map<String, Object>> schemas = toolSchemas != null ? toolSchemas : Collections.emptyList();
        return Mono.fromCallable(() -> model.generate(messages, schemas, generateOptions));
    }

    /**
     * 流式调用模型
     * @param messages
     * @param toolSchemas
     * @return
     */
    public Flux<AgentChatResponse> stream(List<AgentMessage> messages, List<Map<String, Object>> toolSchemas) {
        List<Map<String, Object>> schemas = toolSchemas != null ? toolSchemas : Collections.emptyList();
        return model.stream(messages, schemas, generateOptions);
    }
}
