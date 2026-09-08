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
package com.yangqiongai.agent.harness.starter.autoconfigure;

import java.util.List;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;

import com.yangqiongai.agent.harness.core.model.AgentModel;
import com.yangqiongai.agent.harness.core.model.registry.AgentModelRegistry;
import com.yangqiongai.agent.harness.model.HarnessModelFactory;
import com.yangqiongai.agent.harness.model.HarnessModelProperties;
import com.yangqiongai.agent.harness.model.provider.AnthropicModelProvider;
import com.yangqiongai.agent.harness.model.provider.DashScopeModelProvider;
import com.yangqiongai.agent.harness.model.provider.GeminiModelProvider;
import com.yangqiongai.agent.harness.model.provider.OllamaModelProvider;
import com.yangqiongai.agent.harness.model.provider.OpenAIModelProvider;

/**
 * 模型自动装配
 * <p>
 * 基于 ai.harness.model 配置构建模型注册表、模型工厂与默认模型Bean。
 * 自动注册 OpenAI/Anthropic/DashScope/Ollama 四种协议提供方。
 * </p>
 * @author yangqiong
 */
@AutoConfiguration
@ConditionalOnProperty(prefix = "ai.harness.model", name = "enabled", havingValue = "true", matchIfMissing = true)
public class AgentModelAutoConfiguration {

    /**
     * 模型配置属性Bean，绑定 ai.harness.model 前缀
     * <p>
     * 直接复用核心模块{@link HarnessModelProperties}，保证配置单一来源。
     * </p>
     * @return
     */
    @Bean
    @ConfigurationProperties(prefix = "ai.harness.model")
    public HarnessModelProperties harnessModelProperties() {
        return new HarnessModelProperties();
    }

    /**
     * 模型注册表Bean，注册全部内置协议提供方
     * @param properties
     * @return
     */
    @Bean
    @ConditionalOnMissingBean
    public AgentModelRegistry agentModelRegistry(HarnessModelProperties properties) {
        AgentModelRegistry registry = new AgentModelRegistry(properties.getDefaultProvider());
        registry.registerProviders(List.of(
                new OpenAIModelProvider(),
                new AnthropicModelProvider(),
                new DashScopeModelProvider(),
                new OllamaModelProvider(),
                new GeminiModelProvider()
        ));
        return registry;
    }

    /**
     * 模型工厂Bean，按 modelCode 解析并创建模型实例
     * @param properties
     * @param registry
     * @return
     */
    @Bean
    @ConditionalOnMissingBean
    public HarnessModelFactory harnessModelFactory(HarnessModelProperties properties,
                                                   AgentModelRegistry registry) {
        return new HarnessModelFactory(properties, registry);
    }

    /**
     * 默认模型Bean，供运行时构建与直接注入使用
     * @param factory
     * @param properties
     * @return
     */
    @Bean
    @ConditionalOnMissingBean(AgentModel.class)
    public AgentModel defaultAgentModel(HarnessModelFactory factory, HarnessModelProperties properties) {
        return factory.getModel(properties.getModelName(), null);
    }
}
