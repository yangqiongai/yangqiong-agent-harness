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
package com.yangqiong.agent.harness.local.model;

import java.util.List;

import com.yangqiong.agent.harness.model.HttpChatModel;
import com.yangqiong.agent.harness.model.OllamaChatModel;
import com.yangqiong.agent.harness.model.OpenAIChatModel;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 本地模型工厂测试
 * @author yangqiong
 */
class LocalModelsTest {

    @Test
    void shouldCreateOllamaModel() {
        OllamaChatModel model = LocalModels.ollama("http://localhost:11434", "llama3");
        assertThat(model).isInstanceOf(OllamaChatModel.class);
        assertThat(model.getModelName()).isEqualTo("llama3");
        assertThat(model.getBaseUrl()).isEqualTo("http://localhost:11434");
    }

    @Test
    void shouldCreateLmStudioModel() {
        OpenAIChatModel model = LocalModels.lmStudio("qwen2.5");
        assertThat(model).isInstanceOf(OpenAIChatModel.class);
        assertThat(model.getModelName()).isEqualTo("qwen2.5");
        assertThat(model.getBaseUrl()).isEqualTo("http://localhost:1234/v1");
    }

    @Test
    void shouldCreateLlamaCppModel() {
        OpenAIChatModel model = LocalModels.llamaCpp("qwen2.5");
        assertThat(model).isInstanceOf(OpenAIChatModel.class);
        assertThat(model.getModelName()).isEqualTo("qwen2.5");
        assertThat(model.getBaseUrl()).isEqualTo("http://localhost:8080/v1");
    }

    @Test
    void shouldCreateOpenAiCompatibleModel() {
        OpenAIChatModel model = LocalModels.openAiCompatible("http://localhost:9999/v1", "qwen2.5");
        assertThat(model).isInstanceOf(OpenAIChatModel.class);
        assertThat(model.getModelName()).isEqualTo("qwen2.5");
        assertThat(model.getBaseUrl()).isEqualTo("http://localhost:9999/v1");
    }

    @Test
    void shouldCreateModelFromOllamaEndpoint() {
        LocalModelEndpoint endpoint = new LocalModelEndpoint(
                LocalModelEndpointType.OLLAMA, "http://localhost:11434", List.of("llama3"));
        HttpChatModel model = LocalModels.fromEndpoint(endpoint, "llama3");
        assertThat(model).isInstanceOf(OllamaChatModel.class);
        assertThat(model.getModelName()).isEqualTo("llama3");
    }

    @Test
    void shouldCreateModelFromOpenAiCompatibleEndpoint() {
        LocalModelEndpoint endpoint = new LocalModelEndpoint(
                LocalModelEndpointType.OPENAI_COMPATIBLE, "http://localhost:1234/v1", List.of("qwen2.5"));
        HttpChatModel model = LocalModels.fromEndpoint(endpoint, "qwen2.5");
        assertThat(model).isInstanceOf(OpenAIChatModel.class);
        assertThat(model.getModelName()).isEqualTo("qwen2.5");
    }
}
