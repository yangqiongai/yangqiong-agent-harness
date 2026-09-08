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

/**
 * Agent模型工厂
 * @author yangqiong
 */
public interface AgentModelFactory {

    /**
     * 按模型编码获取模型
     * @param modelCode
     * @param options
     * @return
     */
    AgentModel getModel(String modelCode, AgentGenerateOptions options);

    /**
     * 按完整配置获取模型
     * @param provider
     * @param apiKey
     * @param modelCode
     * @param options
     * @return
     */
    AgentModel getModelByConfig(String provider, String apiKey, String modelCode, AgentGenerateOptions options);
}
