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
package com.yangqiongai.agent.harness.core.model.protocol;

import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;
import com.yangqiongai.agent.harness.core.message.AgentMessage;
import com.yangqiongai.agent.harness.core.model.AgentChatResponse;
import com.yangqiongai.agent.harness.core.model.AgentGenerateOptions;

/**
 * 模型协议适配器
 * <p>
 * 隔离不同provider的协议差异，每个provider实现一套适配器。
 * </p>
 * @author yangqiong
 */
public interface AgentModelProtocolAdapter {

    /**
     * 构建请求体（包含model、messages、tools、options、stream等）
     * @param modelName
     * @param messages
     * @param tools
     * @param options
     * @param defaultOptions
     * @param stream
     * @return
     */
    String buildRequestBody(String modelName, List<AgentMessage> messages, List<Map<String, Object>> tools,
                              AgentGenerateOptions options, AgentGenerateOptions defaultOptions, boolean stream);

    /**
     * 解析非流式响应
     * @param root
     * @return
     */
    AgentChatResponse parseNonStreamResponse(JsonNode root);

    /**
     * 解析流式响应行（SSE格式或每行JSON）
     * @param line
     * @return
     */
    AgentChatResponse parseStreamLine(String line);

    /**
     * 获取请求端点路径（如"/chat/completions"、"/api/chat"、"/v1/messages"）
     * @return
     */
    String getEndpointPath();

    /**
     * 获取请求头（provider特有，如Anthropic的anthropic-version头）
     * @param apiKey
     * @return
     */
    Map<String, String> getHeaders(String apiKey);

    /**
     * 判断是否需要Authorization Bearer头
     * @return
     */
    boolean useBearerAuth();
}
