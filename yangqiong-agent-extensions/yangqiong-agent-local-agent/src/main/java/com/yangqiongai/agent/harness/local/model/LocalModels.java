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
package com.yangqiongai.agent.harness.local.model;

import com.yangqiongai.agent.harness.model.HttpChatModel;
import com.yangqiongai.agent.harness.model.OllamaChatModel;
import com.yangqiongai.agent.harness.model.OpenAIChatModel;

/**
 * 本地模型工厂
 * <p>
 * 面向常见本地推理服务的便捷构造入口：Ollama走原生协议（无需鉴权），
 * LM Studio与llama.cpp等走OpenAI兼容协议（apiKey以占位符填充，本地服务通常忽略鉴权头）。
 * </p>
 * @author yangqiong
 */
public final class LocalModels {

    /**
     * LM Studio默认基础地址
     */
    private static final String LM_STUDIO_BASE_URL = "http://localhost:1234/v1";

    /**
     * llama.cpp默认基础地址
     */
    private static final String LLAMA_CPP_BASE_URL = "http://localhost:8080/v1";

    /**
     * 本地服务apiKey占位符
     */
    private static final String LOCAL_API_KEY_PLACEHOLDER = "local";

    /**
     * 默认请求超时（秒）
     */
    private static final int DEFAULT_TIMEOUT_SECONDS = 120;

    private LocalModels() {
    }

    /**
     * 构造Ollama聊天模型
     * @param baseUrl
     * @param model
     * @return
     */
    public static OllamaChatModel ollama(String baseUrl, String model) {
        return new OllamaChatModel(baseUrl, null, model, null, DEFAULT_TIMEOUT_SECONDS);
    }

    /**
     * 构造LM Studio聊天模型
     * @param model
     * @return
     */
    public static OpenAIChatModel lmStudio(String model) {
        return openAiCompatible(LM_STUDIO_BASE_URL, model);
    }

    /**
     * 构造llama.cpp聊天模型
     * @param model
     * @return
     */
    public static OpenAIChatModel llamaCpp(String model) {
        return openAiCompatible(LLAMA_CPP_BASE_URL, model);
    }

    /**
     * 构造OpenAI兼容协议聊天模型（本地服务无需鉴权时用占位密钥）
     * @param baseUrl
     * @param model
     * @return
     */
    public static OpenAIChatModel openAiCompatible(String baseUrl, String model) {
        return openAiCompatible(baseUrl, model, null);
    }

    /**
     * 构造OpenAI兼容协议聊天模型，apiKey为空时使用本地占位密钥
     * @param baseUrl
     * @param model
     * @param apiKey 远端服务的鉴权密钥，本地服务可传null
     * @return
     */
    public static OpenAIChatModel openAiCompatible(String baseUrl, String model, String apiKey) {
        String effectiveKey = (apiKey == null || apiKey.isBlank()) ? LOCAL_API_KEY_PLACEHOLDER : apiKey;
        return new OpenAIChatModel(baseUrl, effectiveKey, model, null, DEFAULT_TIMEOUT_SECONDS);
    }

    /**
     * 依据端点类型分发构造聊天模型
     * @param endpoint
     * @param model
     * @return
     */
    public static HttpChatModel fromEndpoint(LocalModelEndpoint endpoint, String model) {
        if (endpoint.type() == LocalModelEndpointType.OLLAMA) {
            return ollama(endpoint.baseUrl(), model);
        }
        return openAiCompatible(endpoint.baseUrl(), model);
    }
}
