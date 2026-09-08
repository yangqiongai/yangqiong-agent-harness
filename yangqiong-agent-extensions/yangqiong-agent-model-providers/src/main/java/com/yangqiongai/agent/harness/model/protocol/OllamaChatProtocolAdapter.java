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
package com.yangqiongai.agent.harness.model.protocol;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.yangqiongai.agent.harness.config.AgentResponseFormat;
import com.yangqiongai.agent.harness.core.message.AgentChatUsage;
import com.yangqiongai.agent.harness.core.message.AgentContentBlock;
import com.yangqiongai.agent.harness.core.message.AgentImageBlock;
import com.yangqiongai.agent.harness.core.message.AgentMessage;
import com.yangqiongai.agent.harness.core.message.AgentTextBlock;
import com.yangqiongai.agent.harness.core.message.AgentThinkingBlock;
import com.yangqiongai.agent.harness.core.message.AgentToolResultBlock;
import com.yangqiongai.agent.harness.core.message.AgentToolUseBlock;
import com.yangqiongai.agent.harness.core.model.AgentChatResponse;
import com.yangqiongai.agent.harness.core.model.AgentGenerateOptions;
import com.yangqiongai.agent.harness.core.model.protocol.AgentModelProtocolAdapter;
import com.yangqiongai.agent.harness.model.ModelResponseParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Ollama聊天协议适配器
 * <p>
 * 处理Ollama原生协议（/api/chat端点），直接数组结构（无input/parameters嵌套），
 * 流式响应每行一个完整JSON（非SSE格式）。
 * </p>
 * @author yangqiong
 */
public class OllamaChatProtocolAdapter implements AgentModelProtocolAdapter {

    private static final Logger log = LoggerFactory.getLogger(OllamaChatProtocolAdapter.class);

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final String ENDPOINT_PATH = "/api/chat";

    @Override
    public String buildRequestBody(String modelName, List<AgentMessage> messages, List<Map<String, Object>> tools,
                                      AgentGenerateOptions options, AgentGenerateOptions defaultOptions, boolean stream) {
        ObjectNode root = MAPPER.createObjectNode();
        if (modelName != null) {
            root.put("model", modelName);
        }
        // Ollama直接使用messages数组（无嵌套），content保持纯文本
        ArrayNode messagesArray = MAPPER.createArrayNode();
        if (messages != null) {
            for (AgentMessage msg : messages) {
                messagesArray.add(convertMessageToJson(msg));
            }
        }
        root.set("messages", messagesArray);
        // Ollama图像格式特殊:顶层images字段(base64数组)
        List<String> images = extractImages(messages);
        if (!images.isEmpty()) {
            ArrayNode imagesArray = MAPPER.createArrayNode();
            for (String img : images) {
                imagesArray.add(img);
            }
            root.set("images", imagesArray);
        }
        // 工具数组
        if (tools != null && !tools.isEmpty()) {
            ArrayNode toolsArray = MAPPER.createArrayNode();
            for (Map<String, Object> tool : tools) {
                if (tool != null) {
                    normalizeToolParameters(tool);
                    toolsArray.add(MAPPER.valueToTree(tool));
                }
            }
            root.set("tools", toolsArray);
        }
        // Ollama的options字段
        if (options != null) {
            ObjectNode optionsNode = MAPPER.createObjectNode();
            if (options.getTemperature() != null) {
                optionsNode.put("temperature", options.getTemperature());
            }
            if (options.getTopP() != null) {
                optionsNode.put("top_p", options.getTopP());
            }
            if (options.getMaxTokens() != null) {
                optionsNode.put("num_predict", options.getMaxTokens());
            }
            if (!optionsNode.isEmpty()) {
                root.set("options", optionsNode);
            }
        }
        // Ollama原生结构化输出：format字段直接携带JSON Schema（Ollama 0.5+），
        // 由解码层强制约束输出格式，输出仍由引擎侧StructuredOutputValidator校验兜底
        if (options != null) {
            AgentResponseFormat format = options.getResponseFormat();
            if (format != null && "json_schema".equals(format.getType())
                    && format.getJsonSchema() != null && format.getJsonSchema().getSchema() != null) {
                root.set("format", MAPPER.valueToTree(format.getJsonSchema().getSchema()));
            }
        }
        root.put("stream", stream);
        return root.toString();
    }

    /**
     * 将AgentMessage转换为Ollama消息JSON
     * <p>
     * Ollama的content保持纯文本(拼接所有TextBlock),图像通过顶层images字段传递。
     * </p>
     * @param msg
     * @return
     */
    protected JsonNode convertMessageToJson(AgentMessage msg) {
        ObjectNode node = MAPPER.createObjectNode();
        String role = msg.getRole() != null ? msg.getRole().name().toLowerCase() : "user";
        node.put("role", role);
        // Ollama content保持纯文本
        StringBuilder textContent = new StringBuilder();
        if (msg.getContent() != null) {
            for (AgentContentBlock block : msg.getContent()) {
                if (block instanceof AgentTextBlock textBlock) {
                    textContent.append(textBlock.getText());
                } else if (block instanceof AgentToolResultBlock resultBlock) {
                    textContent.append(resultBlock.getTextContent());
                }
                // 图像块不进入content,由extractImages提取到顶层images字段
            }
        }
        node.put("content", textContent.toString());
        return node;
    }

    /**
     * 从所有消息中提取Base64图像数据
     * <p>
     * Ollama要求图像以base64格式放在顶层images数组。URL模式的图像需要下载转换,
     * 首版仅支持base64,URL模式记录警告并跳过。
     * </p>
     * @param messages
     * @return
     */
    private List<String> extractImages(List<AgentMessage> messages) {
        List<String> images = new ArrayList<>();
        if (messages == null) {
            return images;
        }
        for (AgentMessage msg : messages) {
            if (msg.getContent() == null) {
                continue;
            }
            for (AgentContentBlock block : msg.getContent()) {
                if (block instanceof AgentImageBlock imageBlock) {
                    if (imageBlock.isBase64Mode()) {
                        images.add(imageBlock.getBase64Data());
                    } else if (imageBlock.isUrlMode()) {
                        log.warn("Ollama暂不支持URL图像,请转换为base64后重试,URL: {}",
                                imageBlock.getUrl().substring(0, Math.min(50, imageBlock.getUrl().length())));
                    }
                }
            }
        }
        return images;
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
        JsonNode message = root.get("message");
        if (message != null) {
            JsonNode content = message.get("content");
            if (content != null && !content.isNull() && !content.asText().isEmpty()) {
                blocks.add(AgentTextBlock.builder().text(content.asText()).build());
            }
            // Ollama的思考过程字段
            JsonNode thinking = message.get("thinking");
            if (thinking != null && !thinking.isNull() && !thinking.asText().isEmpty()) {
                blocks.add(AgentThinkingBlock.builder().thinking(thinking.asText()).build());
            }
            JsonNode toolCalls = message.get("tool_calls");
            if (toolCalls != null && toolCalls.isArray()) {
                for (JsonNode tc : toolCalls) {
                    String toolCallId = tc.has("id") && !tc.get("id").isNull()
                            ? tc.get("id").asText() : "call_" + System.nanoTime();
                    JsonNode function = tc.get("function");
                    if (function != null) {
                        String toolName = function.has("name") ? function.get("name").asText() : "";
                        JsonNode argsNode = function.has("arguments") ? function.get("arguments") : null;
                        Map<String, Object> input = parseArgumentsNode(argsNode);
                        blocks.add(new AgentToolUseBlock(toolName, toolCallId, input));
                    }
                }
            }
        }
        // Ollama的usage字段
        AgentChatUsage usage = parseUsage(root);
        return new AgentChatResponse(blocks, usage);
    }

    @Override
    public AgentChatResponse parseStreamLine(String line) {
        if (line == null || line.isBlank()) {
            return null;
        }
        // Ollama流式响应为每行一个完整JSON（无data:前缀）
        try {
            JsonNode root = MAPPER.readTree(line);
            return parseChunkResponse(root);
        } catch (Exception e) {
            log.debug("解析Ollama流式JSON失败: {}", line);
            return null;
        }
    }

    /**
     * 解析Ollama流式chunk响应
     * <p>
     * Ollama流式响应结构：{ "message": { "role": "assistant", "content": "...", "tool_calls": [...] }, "done": false }
     * </p>
     * @param root
     * @return
     */
    protected AgentChatResponse parseChunkResponse(JsonNode root) {
        List<AgentContentBlock> blocks = new ArrayList<>();
        JsonNode message = root.get("message");
        if (message != null) {
            JsonNode content = message.get("content");
            if (content != null && !content.isNull() && !content.asText().isEmpty()) {
                blocks.add(AgentTextBlock.builder().text(content.asText()).build());
            }
            JsonNode thinking = message.get("thinking");
            if (thinking != null && !thinking.isNull() && !thinking.asText().isEmpty()) {
                blocks.add(AgentThinkingBlock.builder().thinking(thinking.asText()).build());
            }
            JsonNode toolCalls = message.get("tool_calls");
            if (toolCalls != null && toolCalls.isArray()) {
                for (JsonNode tc : toolCalls) {
                    int index = tc.has("index") ? tc.get("index").asInt() : -1;
                    String realId = tc.has("id") && !tc.get("id").isNull()
                            ? tc.get("id").asText() : null;
                    JsonNode function = tc.get("function");
                    if (function != null) {
                        String toolName = function.has("name") ? function.get("name").asText() : "";
                        // Ollama的arguments可能是JSON对象而非字符串
                        String argsStr = "";
                        if (function.has("arguments")) {
                            JsonNode argsNode = function.get("arguments");
                            if (argsNode.isTextual()) {
                                argsStr = argsNode.asText();
                            } else {
                                argsStr = argsNode.toString();
                            }
                        }
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
        // Ollama在done=true时返回usage
        AgentChatUsage usage = parseUsage(root);
        if (blocks.isEmpty() && usage == null) {
            return null;
        }
        return new AgentChatResponse(blocks, usage);
    }

    /**
     * 解析Ollama的usage信息
     * @param root
     * @return
     */
    private AgentChatUsage parseUsage(JsonNode root) {
        JsonNode doneNode = root.get("done");
        if (doneNode == null || !doneNode.asBoolean()) {
            return null;
        }
        int inputTokens = root.has("prompt_eval_count") ? root.get("prompt_eval_count").asInt() : 0;
        int outputTokens = root.has("eval_count") ? root.get("eval_count").asInt() : 0;
        int totalTokens = inputTokens + outputTokens;
        if (inputTokens == 0 && outputTokens == 0) {
            return null;
        }
        return new AgentChatUsage(inputTokens, outputTokens, totalTokens);
    }

    /**
     * 解析工具调用参数JSON节点
     * @param argsNode
     * @return
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> parseArgumentsNode(JsonNode argsNode) {
        if (argsNode == null || argsNode.isNull()) {
            return new HashMap<>();
        }
        if (argsNode.isTextual()) {
            return parseArguments(argsNode.asText());
        }
        try {
            return MAPPER.treeToValue(argsNode, Map.class);
        } catch (Exception e) {
            Map<String, Object> result = new HashMap<>();
            result.put("_raw", argsNode.toString());
            return result;
        }
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
        return false;
    }
}
