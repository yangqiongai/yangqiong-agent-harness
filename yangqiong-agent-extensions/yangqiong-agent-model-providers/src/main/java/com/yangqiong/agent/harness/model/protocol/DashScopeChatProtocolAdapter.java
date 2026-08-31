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

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.yangqiong.agent.harness.core.message.AgentChatUsage;
import com.yangqiong.agent.harness.core.message.AgentContentBlock;
import com.yangqiong.agent.harness.core.message.AgentMessage;
import com.yangqiong.agent.harness.core.message.AgentTextBlock;
import com.yangqiong.agent.harness.core.message.AgentToolUseBlock;
import com.yangqiong.agent.harness.core.model.AgentChatResponse;
import com.yangqiong.agent.harness.core.model.AgentGenerateOptions;
import com.yangqiong.agent.harness.core.model.protocol.AgentModelProtocolAdapter;
import com.yangqiong.agent.harness.model.ModelResponseParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * DashScope聊天协议适配器
 * <p>
 * 处理DashScope原生协议（/api/v1/services/aigc/text-generation/generation端点），
 * 请求体采用input.messages嵌套结构，options包装到parameters字段。
 * </p>
 * @author yangqiong
 */
public class DashScopeChatProtocolAdapter implements AgentModelProtocolAdapter {

    private static final Logger log = LoggerFactory.getLogger(DashScopeChatProtocolAdapter.class);

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /**
     * 复用OpenAI消息转换逻辑
     */
    private final OpenAIChatProtocolAdapter delegate = new OpenAIChatProtocolAdapter();

    private static final String ENDPOINT_PATH = "/api/v1/services/aigc/text-generation/generation";

    @Override
    public String buildRequestBody(String modelName, List<AgentMessage> messages, List<Map<String, Object>> tools,
                                      AgentGenerateOptions options, AgentGenerateOptions defaultOptions, boolean stream) {
        ObjectNode root = MAPPER.createObjectNode();
        if (modelName != null) {
            root.put("model", modelName);
        }
        // DashScope采用input.messages嵌套结构
        ObjectNode input = MAPPER.createObjectNode();
        ArrayNode messagesArray = MAPPER.createArrayNode();
        if (messages != null) {
            for (AgentMessage msg : messages) {
                messagesArray.add(delegate.convertMessageToJson(msg));
            }
        }
        input.set("messages", messagesArray);
        root.set("input", input);
        // DashScope原生协议通过input.tools传递工具
        if (tools != null && !tools.isEmpty()) {
            ArrayNode toolsArray = MAPPER.createArrayNode();
            for (Map<String, Object> tool : tools) {
                if (tool != null) {
                    normalizeToolParameters(tool);
                    toolsArray.add(MAPPER.valueToTree(tool));
                }
            }
            input.set("tools", toolsArray);
        }
        // options包装到parameters字段
        ObjectNode parameters = MAPPER.createObjectNode();
        if (options != null) {
            applyOptions(parameters, options);
        }
        parameters.put("stream", stream);
        parameters.put("incremental_output", stream);
        root.set("parameters", parameters);
        return root.toString();
    }

    /**
     * 应用生成选项到parameters节点
     * @param parameters
     * @param options
     */
    private void applyOptions(ObjectNode parameters, AgentGenerateOptions options) {
        if (options.getTemperature() != null) {
            parameters.put("temperature", options.getTemperature());
        }
        if (options.getMaxTokens() != null) {
            parameters.put("max_tokens", options.getMaxTokens());
        }
        if (options.getTopP() != null) {
            parameters.put("top_p", options.getTopP());
        }
        if (options.getToolChoice() != null) {
            parameters.put("tool_choice", options.getToolChoice().name().toLowerCase());
        }
        if (options.getResponseFormat() != null && "json_schema".equals(options.getResponseFormat().getType())) {
            ObjectNode formatNode = MAPPER.createObjectNode();
            formatNode.put("type", "json_schema");
            if (options.getResponseFormat().getJsonSchema() != null) {
                ObjectNode schemaNode = MAPPER.createObjectNode();
                schemaNode.put("name", options.getResponseFormat().getJsonSchema().getName() != null
                        ? options.getResponseFormat().getJsonSchema().getName() : "result");
                schemaNode.set("schema", MAPPER.valueToTree(
                        options.getResponseFormat().getJsonSchema().getSchema()));
                schemaNode.put("strict", false);
                formatNode.set("json_schema", schemaNode);
            }
            parameters.set("response_format", formatNode);
        }
        // 透传厂商专属参数
        Map<String, Object> extra = options.getExtraParameters();
        if (extra != null) {
            for (Map.Entry<String, Object> entry : extra.entrySet()) {
                if (entry.getValue() != null) {
                    parameters.set(entry.getKey(), MAPPER.valueToTree(entry.getValue()));
                }
            }
        }
    }

    /**
     * 规范化工具parameters为JSON Schema格式
     * @param tool
     */
    @SuppressWarnings("unchecked")
    private void normalizeToolParameters(Map<String, Object> tool) {
        Object functionObj = tool.get("function");
        if (!(functionObj instanceof Map)) {
            return;
        }
        Map<String, Object> function = (Map<String, Object>) functionObj;
        Object params = function.get("parameters");
        if (!(params instanceof Map)) {
            Map<String, Object> emptySchema = new HashMap<>();
            emptySchema.put("type", "object");
            emptySchema.put("properties", new HashMap<>());
            function.put("parameters", emptySchema);
            return;
        }
        Map<String, Object> paramsMap = (Map<String, Object>) params;
        Object type = paramsMap.get("type");
        if ("object".equals(type)) {
            return;
        }
        Map<String, Object> normalized = new HashMap<>();
        normalized.put("type", "object");
        normalized.put("properties", paramsMap);
        function.put("parameters", normalized);
    }

    @Override
    public AgentChatResponse parseNonStreamResponse(JsonNode root) {
        List<AgentContentBlock> blocks = new ArrayList<>();
        // DashScope响应结构：output.choices[0].message
        JsonNode output = root.get("output");
        if (output != null) {
            JsonNode choices = output.get("choices");
            if (choices != null && choices.isArray() && !choices.isEmpty()) {
                JsonNode choice = choices.get(0);
                JsonNode message = choice.get("message");
                if (message != null) {
                    JsonNode content = message.get("content");
                    if (content != null && !content.isNull() && !content.asText().isEmpty()) {
                        blocks.add(AgentTextBlock.builder().text(content.asText()).build());
                    }
                    JsonNode toolCalls = message.get("tool_calls");
                    if (toolCalls != null && toolCalls.isArray()) {
                        for (JsonNode tc : toolCalls) {
                            String toolCallId = tc.has("id") && !tc.get("id").isNull()
                                    ? tc.get("id").asText() : "call_" + System.nanoTime();
                            JsonNode function = tc.get("function");
                            if (function != null) {
                                String toolName = function.has("name") ? function.get("name").asText() : "";
                                String argsStr = function.has("arguments") ? function.get("arguments").asText() : "{}";
                                Map<String, Object> input = parseArguments(argsStr);
                                blocks.add(new AgentToolUseBlock(toolName, toolCallId, input));
                            }
                        }
                    }
                }
            }
        }
        // 解析usage（DashScope的usage在顶层）
        AgentChatUsage usage = parseUsage(root.get("usage"));
        return new AgentChatResponse(blocks, usage);
    }

    @Override
    public AgentChatResponse parseStreamLine(String line) {
        if (line == null || line.isBlank()) {
            return null;
        }
        if (!line.startsWith("data:")) {
            return null;
        }
        String data = line.substring(5).trim();
        if ("[DONE]".equals(data)) {
            return null;
        }
        try {
            JsonNode root = MAPPER.readTree(data);
            return parseChunkResponse(root);
        } catch (Exception e) {
            log.debug("解析DashScope SSE JSON失败: {}", data);
            return null;
        }
    }

    /**
     * 解析DashScope流式chunk响应
     * <p>
     * DashScope流式响应结构：{ "output": { "choices": [{ "delta": {...} }] }, "usage": {...} }
     * </p>
     * @param root
     * @return
     */
    protected AgentChatResponse parseChunkResponse(JsonNode root) {
        List<AgentContentBlock> blocks = new ArrayList<>();
        JsonNode output = root.get("output");
        if (output != null) {
            JsonNode choices = output.get("choices");
            if (choices != null && choices.isArray() && !choices.isEmpty()) {
                JsonNode choice = choices.get(0);
                JsonNode delta = choice.get("delta");
                if (delta != null) {
                    JsonNode content = delta.get("content");
                    if (content != null && !content.isNull() && !content.asText().isEmpty()) {
                        blocks.add(AgentTextBlock.builder().text(content.asText()).build());
                    }
                    JsonNode toolCalls = delta.get("tool_calls");
                    if (toolCalls != null && toolCalls.isArray()) {
                        for (JsonNode tc : toolCalls) {
                            int index = tc.has("index") ? tc.get("index").asInt() : -1;
                            String realId = tc.has("id") && !tc.get("id").isNull()
                                    ? tc.get("id").asText() : null;
                            JsonNode function = tc.get("function");
                            if (function != null) {
                                String toolName = function.has("name") ? function.get("name").asText() : "";
                                String argsStr = function.has("arguments") ? function.get("arguments").asText() : "";
                                String stableId = index >= 0
                                        ? "call_stream_" + index
                                        : (realId != null ? realId : "call_" + System.nanoTime());
                                Map<String, Object> inputMap = new HashMap<>();
                                if (realId != null) {
                                    inputMap.put(ModelResponseParser.TOOL_CALL_ID_KEY, realId);
                                }
                                inputMap.put(ModelResponseParser.RAW_ARGS_KEY, argsStr);
                                blocks.add(new AgentToolUseBlock(toolName, stableId, inputMap));
                            }
                        }
                    }
                }
            }
        }
        AgentChatUsage usage = parseUsage(root.get("usage"));
        if (blocks.isEmpty() && usage == null) {
            return null;
        }
        return new AgentChatResponse(blocks, usage);
    }

    /**
     * 解析usage节点
     * @param usageNode
     * @return
     */
    private AgentChatUsage parseUsage(JsonNode usageNode) {
        if (usageNode == null || usageNode.isNull()) {
            return null;
        }
        int inputTokens = usageNode.has("input_tokens") ? usageNode.get("input_tokens").asInt()
                : (usageNode.has("prompt_tokens") ? usageNode.get("prompt_tokens").asInt() : 0);
        int outputTokens = usageNode.has("output_tokens") ? usageNode.get("output_tokens").asInt()
                : (usageNode.has("completion_tokens") ? usageNode.get("completion_tokens").asInt() : 0);
        int totalTokens = usageNode.has("total_tokens") ? usageNode.get("total_tokens").asInt()
                : inputTokens + outputTokens;
        return new AgentChatUsage(inputTokens, outputTokens, totalTokens);
    }

    /**
     * 解析工具调用参数JSON
     * @param argsStr
     * @return
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> parseArguments(String argsStr) {
        if (argsStr == null || argsStr.isBlank()) {
            return new HashMap<>();
        }
        try {
            return MAPPER.readValue(argsStr, Map.class);
        } catch (Exception e) {
            Map<String, Object> result = new HashMap<>();
            result.put("_raw", argsStr);
            return result;
        }
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
