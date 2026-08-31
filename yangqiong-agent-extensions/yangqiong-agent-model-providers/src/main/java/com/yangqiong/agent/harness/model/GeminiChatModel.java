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

import com.yangqiong.agent.harness.core.model.AgentGenerateOptions;
import com.yangqiong.agent.harness.model.protocol.GeminiChatProtocolAdapter;

/**
 * Gemini聊天模型
 * <p>
 * 组合{@link HttpChatModel} + {@link GeminiChatProtocolAdapter}，
 * 通过Gemini OpenAI兼容协议访问Gemini模型。
 * </p>
 * @author yangqiong
 */
public class GeminiChatModel extends HttpChatModel {

    /**
     * 构造Gemini聊天模型
     * @param baseUrl
     * @param apiKey
     * @param modelName
     * @param defaultOptions
     * @param timeoutSeconds
     */
    public GeminiChatModel(String baseUrl, String apiKey, String modelName,
                           AgentGenerateOptions defaultOptions, int timeoutSeconds) {
        super(baseUrl, apiKey, modelName, defaultOptions, timeoutSeconds, new GeminiChatProtocolAdapter());
    }
}
