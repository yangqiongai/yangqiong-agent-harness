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
package com.yangqiong.agent.harness.model.protocol;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.yangqiong.agent.harness.core.message.AgentMessage;
import com.yangqiong.agent.harness.core.model.AgentChatResponse;
import com.yangqiong.agent.harness.core.model.AgentGenerateOptions;
import com.yangqiong.agent.harness.core.model.protocol.AgentModelProtocolAdapter;

/**
 * Gemini聊天协议适配器
 * <p>
 * 处理Google Gemini OpenAI兼容协议（/v1beta/openai/chat/completions端点），
 * 请求响应格式与OpenAI一致，认证采用Bearer头。
 * </p>
 * @author yangqiong
 */
public class GeminiChatProtocolAdapter implements AgentModelProtocolAdapter {

    private static final String ENDPOINT_PATH = "/chat/completions";

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /**
     * 复用OpenAI消息与响应处理逻辑（Gemini OpenAI兼容协议与OpenAI格式一致）
     */
    private final OpenAIChatProtocolAdapter delegate = new OpenAIChatProtocolAdapter();

    @Override
    public String buildRequestBody(String modelName, List<AgentMessage> messages, List<Map<String, Object>> tools,
                                      AgentGenerateOptions options, AgentGenerateOptions defaultOptions, boolean stream) {
        String body = delegate.buildRequestBody(modelName, messages, tools, options, defaultOptions, stream);
        if (options == null) {
            return body;
        }
        try {
            ObjectNode root = (ObjectNode) MAPPER.readTree(body);
            // 透传Gemini原生思考预算（2.5系列thinkingConfig.thinkingBudget）
            if (options.getThinkingBudget() != null) {
                ObjectNode thinkingConfig = MAPPER.createObjectNode();
                thinkingConfig.put("thinkingBudget", options.getThinkingBudget());
                root.set("thinkingConfig", thinkingConfig);
            }
            // 透传推理努力等级（Gemini OpenAI兼容端点直接支持）
            if (options.getReasoningEffort() != null) {
                root.put("reasoning_effort", options.getReasoningEffort());
            }
            return root.toString();
        } catch (Exception e) {
            return body;
        }
    }

    @Override
    public AgentChatResponse parseNonStreamResponse(JsonNode root) {
        return delegate.parseNonStreamResponse(root);
    }

    @Override
    public AgentChatResponse parseStreamLine(String line) {
        return delegate.parseStreamLine(line);
    }

    @Override
    public String getEndpointPath() {
        return ENDPOINT_PATH;
    }

    @Override
    public Map<String, String> getHeaders(String apiKey) {
        return Collections.emptyMap();
    }

    @Override
    public boolean useBearerAuth() {
        return true;
    }
}
