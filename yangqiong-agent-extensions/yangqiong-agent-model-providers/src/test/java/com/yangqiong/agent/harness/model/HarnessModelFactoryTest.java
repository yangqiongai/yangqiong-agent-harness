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

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.yangqiong.agent.harness.model.provider.OpenAIModelProvider;
import com.yangqiong.agent.harness.core.model.AgentModel;
import com.yangqiong.agent.harness.core.model.registry.AgentModelRegistry;

/**
 * Harness模型工厂测试
 * @author yangqiong
 */
class HarnessModelFactoryTest {

    private HarnessModelProperties props;

    private AgentModelRegistry registry;

    @BeforeEach
    void setUp() {
        props = new HarnessModelProperties();
        props.setApiKey("test-key");
        props.setBaseUrl("https://api.openai.com/v1");
        props.setModelName("gpt-4o-mini");
        registry = new AgentModelRegistry("openai");
        registry.registerProvider(new OpenAIModelProvider());
    }

    @Test
    void shouldCreateModelFromProperties() {
        HarnessModelFactory factory = new HarnessModelFactory(props, registry);
        AgentModel model = factory.getModel(null, null);
        assertThat(model).isInstanceOf(OpenAIChatModel.class);
        OpenAIChatModel openAiModel = (OpenAIChatModel) model;
        assertThat(openAiModel.getModelName()).isEqualTo("gpt-4o-mini");
        assertThat(openAiModel.getBaseUrl()).isEqualTo("https://api.openai.com/v1");
    }

    @Test
    void shouldUseModelCodeWhenProvided() {
        HarnessModelFactory factory = new HarnessModelFactory(props, registry);
        AgentModel model = factory.getModel("gpt-4o", null);
        OpenAIChatModel openAiModel = (OpenAIChatModel) model;
        assertThat(openAiModel.getModelName()).isEqualTo("gpt-4o");
    }

    @Test
    void shouldCacheModelByName() {
        HarnessModelFactory factory = new HarnessModelFactory(props, registry);
        AgentModel model1 = factory.getModel("same-model", null);
        AgentModel model2 = factory.getModel("same-model", null);
        assertThat(model1).isSameAs(model2);
    }

    @Test
    void shouldCreateModelByConfigWithProviderPrefix() {
        HarnessModelFactory factory = new HarnessModelFactory(props, registry);
        AgentModel model = factory.getModelByConfig("openai", "ds-key", "gpt-4o", null);
        assertThat(model).isInstanceOf(OpenAIChatModel.class);
        OpenAIChatModel openAiModel = (OpenAIChatModel) model;
        assertThat(openAiModel.getModelName()).isEqualTo("gpt-4o");
    }

    @Test
    void shouldResolveDashScopeBaseUrl() {
        HarnessModelProperties props = new HarnessModelProperties();
        props.setBaseUrl("");
        String url = props.resolveBaseUrl("dashscope");
        assertThat(url).contains("dashscope.aliyuncs.com/compatible-mode");
    }

    @Test
    void shouldResolveOllamaBaseUrl() {
        HarnessModelProperties props = new HarnessModelProperties();
        props.setBaseUrl("");
        String url = props.resolveBaseUrl("ollama");
        assertThat(url).contains("localhost:11434");
    }

    @Test
    void shouldClearCache() {
        HarnessModelFactory factory = new HarnessModelFactory(props, registry);
        AgentModel model1 = factory.getModel("test-model", null);
        factory.clearCache();
        AgentModel model2 = factory.getModel("test-model", null);
        assertThat(model1).isNotSameAs(model2);
    }
}
