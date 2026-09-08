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
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.yangqiongai.agent.harness.config.AgentToolChoice;
import com.yangqiongai.agent.harness.core.message.AgentChatUsage;
import com.yangqiongai.agent.harness.core.message.AgentContentBlock;
import com.yangqiongai.agent.harness.core.message.AgentImageBlock;
import com.yangqiongai.agent.harness.core.message.AgentMessage;
import com.yangqiongai.agent.harness.core.message.AgentMessageRole;
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
 * Anthropic聊天协议适配器
 * <p>
 * 处理Anthropic原生协议（Claude模型，/v1/messages端点），
 * 系统消息提取到system字段，content采用数组结构，工具采用input_schema命名。
 * </p>
 * @author yangqiong
 */
public class AnthropicChatProtocolAdapter implements AgentModelProtocolAdapter {

    private static final Logger log = LoggerFactory.getLogger(AnthropicChatProtocolAdapter.class);

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final String ENDPOINT_PATH = "/v1/messages";

    private static final String ANTHROPIC_VERSION = "2023-06-01";

    @Override
    public String buildRequestBody(String modelName, List<AgentMessage> messages, List<Map<String, Object>> tools,
                                      AgentGenerateOptions options, AgentGenerateOptions defaultOptions, boolean stream) {
        ObjectNode root = MAPPER.createObjectNode();
        if (modelName != null) {
            root.put("model", modelName);
        }
        // 系统消息提取到顶层system字段
        StringBuilder systemPrompt = new StringBuilder();
        List<AgentMessage> nonSystemMessages = new ArrayList<>();
        if (messages != null) {
            for (AgentMessage msg : messages) {
                if (msg.getRole() == AgentMessageRole.SYSTEM) {
                    if (systemPrompt.length() > 0) {
                        systemPrompt.append("\n\n");
                    }
                    systemPrompt.append(msg.getTextContent());
                } else {
                    nonSystemMessages.add(msg);
                }
            }
        }
        // 上下文缓存开启时system采用块数组并在块上追加cache_control（Anthropic prompt caching）
        boolean cacheEnabled = options != null && options.isCacheControlEnabled();
        if (systemPrompt.length() > 0) {
            if (cacheEnabled) {
                ArrayNode systemArray = MAPPER.createArrayNode();
                ObjectNode block = MAPPER.createObjectNode();
                block.put("type", "text");
                block.put("text", systemPrompt.toString());
                block.putObject("cache_control").put("type", "ephemeral");
                systemArray.add(block);
                root.set("system", systemArray);
            } else {
                root.put("system", systemPrompt.toString());
            }
        }
        // Anthropic消息数组
        ArrayNode messagesArray = MAPPER.createArrayNode();
        // 预扫描脏历史：匿名tool_use块（名称为空）无法通过API校验，需连同其tool_result成对剔除
        Set<String> droppedToolUseIds = collectDroppedToolUseIds(nonSystemMessages);
        for (AgentMessage msg : nonSystemMessages) {
            messagesArray.add(convertMessageToJson(msg, droppedToolUseIds));
        }
        root.set("messages", messagesArray);
        // max_tokens是Anthropic必填字段
        int maxTokens = (options != null && options.getMaxTokens() != null)
                ? options.getMaxTokens() : 4096;
        root.put("max_tokens", maxTokens);
        // 工具数组（Anthropic使用input_schema而非parameters）
        if (tools != null && !tools.isEmpty()) {
            ArrayNode toolsArray = MAPPER.createArrayNode();
            for (Map<String, Object> tool : tools) {
                if (tool != null) {
                    ObjectNode toolNode = convertToolToAnthropicFormat(tool);
                    // 上下文缓存开启时工具定义追加cache_control（Anthropic prompt caching）
                    if (cacheEnabled) {
                        toolNode.putObject("cache_control").put("type", "ephemeral");
                    }
                    toolsArray.add(toolNode);
                }
            }
            root.set("tools", toolsArray);
        }
        // 应用选项
        if (options != null) {
            applyOptions(root, options);
        }
        root.put("stream", stream);
        return root.toString();
    }

    /**
     * 转换OpenAI格式工具到Anthropic格式
     * <p>
     * Anthropic工具格式：{ "name": "...", "description": "...", "input_schema": {...} }
     * OpenAI工具格式：{ "type": "function", "function": { "name": "...", "description": "...", "parameters": {...} } }
     * </p>
     * @param tool
     * @return
     */
    @SuppressWarnings("unchecked")
    private ObjectNode convertToolToAnthropicFormat(Map<String, Object> tool) {
        ObjectNode anthropicTool = MAPPER.createObjectNode();
        Object functionObj = tool.get("function");
        Map<String, Object> function;
        if (functionObj instanceof Map) {
            function = (Map<String, Object>) functionObj;
        } else {
            function = tool;
        }
        // 名称和描述
        Object name = function.get("name");
        if (name != null) {
            anthropicTool.put("name", name.toString());
        }
        Object description = function.get("description");
        if (description != null) {
            anthropicTool.put("description", description.toString());
        }
        // input_schema（规范化为JSON Schema格式）
        Object params = function.get("parameters");
        if (params instanceof Map) {
            Map<String, Object> paramsMap = (Map<String, Object>) params;
            Object type = paramsMap.get("type");
            if (!"object".equals(type)) {
                Map<String, Object> normalized = new LinkedHashMap<>();
                normalized.put("type", "object");
                normalized.put("properties", paramsMap);
                anthropicTool.set("input_schema", MAPPER.valueToTree(normalized));
            } else {
                anthropicTool.set("input_schema", MAPPER.valueToTree(paramsMap));
            }
        } else {
            ObjectNode emptySchema = MAPPER.createObjectNode();
            emptySchema.put("type", "object");
            emptySchema.set("properties", MAPPER.createObjectNode());
            anthropicTool.set("input_schema", emptySchema);
        }
        return anthropicTool;
    }

    /**
     * 应用生成选项
     * @param root
     * @param options
     */
    private void applyOptions(ObjectNode root, AgentGenerateOptions options) {
        if (options.getTemperature() != null) {
            root.put("temperature", options.getTemperature());
        }
        if (options.getTopP() != null) {
            root.put("top_p", options.getTopP());
        }
        if (options.getThinkingBudget() != null) {
            // Anthropic扩展思考使用thinking块结构，thinking_budget为其他厂商字段无效
            ObjectNode thinking = MAPPER.createObjectNode();
            thinking.put("type", "enabled");
            thinking.put("budget_tokens", options.getThinkingBudget());
            root.set("thinking", thinking);
        }
        if (options.getToolChoice() != null) {
            applyToolChoice(root, options.getToolChoice());
        }
        // 透传厂商专属参数
        Map<String, Object> extra = options.getExtraParameters();
        if (extra != null) {
            for (Map.Entry<String, Object> entry : extra.entrySet()) {
                if (entry.getValue() != null) {
                    root.set(entry.getKey(), MAPPER.valueToTree(entry.getValue()));
                }
            }
        }
    }

    /**
     * 应用工具选择策略（Anthropic格式）
     * @param root
     * @param toolChoice
     */
    private void applyToolChoice(ObjectNode root, AgentToolChoice toolChoice) {
        ObjectNode choiceNode = MAPPER.createObjectNode();
        switch (toolChoice) {
            case AUTO -> choiceNode.put("type", "auto");
            case NONE -> choiceNode.put("type", "none");
            case REQUIRED -> choiceNode.put("type", "any");
        }
        root.set("tool_choice", choiceNode);
    }

    /**
     * 收集需剔除的匿名工具调用ID
     * <p>
     * 名称空的tool_use块无法通过API校验（name至少1字符），记录其ID用于
     * 序列化时成对剔除该块及引用它的tool_result，防止脏历史导致整轮请求400。
     * </p>
     * @param messages
     * @return
     */
    protected Set<String> collectDroppedToolUseIds(List<AgentMessage> messages) {
        Set<String> droppedIds = new HashSet<>();
        if (messages == null) {
            return droppedIds;
        }
        for (AgentMessage msg : messages) {
            if (msg.getContent() == null) {
                continue;
            }
            for (AgentContentBlock block : msg.getContent()) {
                if (block instanceof AgentToolUseBlock toolUseBlock
                        && (toolUseBlock.getToolName() == null || toolUseBlock.getToolName().isBlank())
                        && toolUseBlock.getToolUseId() != null) {
                    droppedIds.add(toolUseBlock.getToolUseId());
                }
            }
        }
        return droppedIds;
    }

    /**
     * 将AgentMessage转换为Anthropic消息JSON
     * @param msg
     * @return
     */
    protected JsonNode convertMessageToJson(AgentMessage msg) {
        return convertMessageToJson(msg, java.util.Collections.emptySet());
    }

    /**
     * 将AgentMessage转换为Anthropic消息JSON（含脏块剔除）
     * @param msg
     * @param droppedToolUseIds 需剔除的匿名tool_use及其tool_result引用ID
     * @return
     */
    protected JsonNode convertMessageToJson(AgentMessage msg, Set<String> droppedToolUseIds) {
        ObjectNode node = MAPPER.createObjectNode();
        String role = msg.getRole() != null ? msg.getRole().name().toLowerCase() : "user";
        // Anthropic仅支持user和assistant角色，tool结果作为user消息的content
        if ("tool".equals(role)) {
            role = "user";
        }
        node.put("role", role);
        // Anthropic content是数组结构
        ArrayNode contentArray = MAPPER.createArrayNode();
        if (msg.getContent() != null) {
            for (AgentContentBlock block : msg.getContent()) {
                if (block instanceof AgentTextBlock textBlock) {
                    if (textBlock.getText() != null && !textBlock.getText().isEmpty()) {
                        ObjectNode textNode = MAPPER.createObjectNode();
                        textNode.put("type", "text");
                        textNode.put("text", textBlock.getText());
                        contentArray.add(textNode);
                    }
                } else if (block instanceof AgentToolUseBlock toolUseBlock) {
                    // 跳过匿名tool_use块（名称为空），API会拒绝此类块
                    if (toolUseBlock.getToolName() == null || toolUseBlock.getToolName().isBlank()) {
                        log.warn("跳过匿名tool_use块, id={}", toolUseBlock.getToolUseId());
                        continue;
                    }
                    ObjectNode toolUseNode = MAPPER.createObjectNode();
                    toolUseNode.put("type", "tool_use");
                    toolUseNode.put("id", toolUseBlock.getToolUseId() != null
                            ? toolUseBlock.getToolUseId() : "toolu_" + System.nanoTime());
                    toolUseNode.put("name", toolUseBlock.getToolName());
                    toolUseNode.set("input", MAPPER.valueToTree(toolUseBlock.getInput()));
                    contentArray.add(toolUseNode);
                } else if (block instanceof AgentToolResultBlock resultBlock) {
                    // 跳过引用已剔除tool_use的孤儿tool_result，保持成对一致性
                    if (resultBlock.getToolUseId() != null && droppedToolUseIds.contains(resultBlock.getToolUseId())) {
                        log.warn("跳过孤儿tool_result块, toolUseId={}", resultBlock.getToolUseId());
                        continue;
                    }
                    ObjectNode toolResultNode = MAPPER.createObjectNode();
                    toolResultNode.put("type", "tool_result");
                    if (resultBlock.getToolUseId() != null) {
                        toolResultNode.put("tool_use_id", resultBlock.getToolUseId());
                    }
                    toolResultNode.put("content", resultBlock.getTextContent());
                    contentArray.add(toolResultNode);
                } else if (block instanceof AgentImageBlock imageBlock) {
                    // 跳过既无url也无base64的空图像块
                    if (!imageBlock.isUrlMode() && !imageBlock.isBase64Mode()) {
                        log.warn("跳过空图像块(无url和base64数据)");
                    } else {
                        // Anthropic图像格式:{type:"image", source:{type:"base64"/"url", ...}}
                        ObjectNode imageNode = MAPPER.createObjectNode();
                        imageNode.put("type", "image");
                        ObjectNode sourceNode = MAPPER.createObjectNode();
                        if (imageBlock.isUrlMode()) {
                            sourceNode.put("type", "url");
                            sourceNode.put("url", imageBlock.getUrl());
                        } else {
                            sourceNode.put("type", "base64");
                            sourceNode.put("media_type", imageBlock.getMediaType() != null
                                    ? imageBlock.getMediaType() : "image/png");
                            sourceNode.put("data", imageBlock.getBase64Data());
                        }
                        imageNode.set("source", sourceNode);
                        contentArray.add(imageNode);
                    }
                } else if (block instanceof AgentThinkingBlock) {
                    // Anthropic不发送思考块给API（thinking由API生成）
                }
            }
        }
        // Anthropic要求content数组非空
        if (contentArray.isEmpty()) {
            ObjectNode emptyText = MAPPER.createObjectNode();
            emptyText.put("type", "text");
            emptyText.put("text", "");
            contentArray.add(emptyText);
        }
        node.set("content", contentArray);
        return node;
    }

    @Override
    public AgentChatResponse parseNonStreamResponse(JsonNode root) {
        List<AgentContentBlock> blocks = new ArrayList<>();
        JsonNode contentArray = root.get("content");
        if (contentArray != null && contentArray.isArray()) {
            for (JsonNode block : contentArray) {
                String type = block.has("type") ? block.get("type").asText() : "";
                if ("text".equals(type)) {
                    String text = block.has("text") ? block.get("text").asText() : "";
                    if (!text.isEmpty()) {
                        blocks.add(AgentTextBlock.builder().text(text).build());
                    }
                } else if ("thinking".equals(type)) {
                    String thinking = block.has("thinking") ? block.get("thinking").asText() : "";
                    if (!thinking.isEmpty()) {
                        blocks.add(AgentThinkingBlock.builder().thinking(thinking).build());
                    }
                } else if ("tool_use".equals(type)) {
                    String toolUseId = block.has("id") ? block.get("id").asText() : "toolu_" + System.nanoTime();
                    String toolName = block.has("name") ? block.get("name").asText() : "";
                    Map<String, Object> input = parseInputNode(block.get("input"));
                    blocks.add(new AgentToolUseBlock(toolName, toolUseId, input));
                }
            }
        }
        // Anthropic usage：input_tokens / output_tokens
        AgentChatUsage usage = null;
        JsonNode usageNode = root.get("usage");
        if (usageNode != null && !usageNode.isNull()) {
            int inputTokens = usageNode.has("input_tokens") ? usageNode.get("input_tokens").asInt() : 0;
            int outputTokens = usageNode.has("output_tokens") ? usageNode.get("output_tokens").asInt() : 0;
            usage = new AgentChatUsage(inputTokens, outputTokens, inputTokens + outputTokens);
        }
        return new AgentChatResponse(blocks, usage);
    }

    @Override
    public AgentChatResponse parseStreamLine(String line) {
        if (line == null || line.isBlank()) {
            return null;
        }
        // Anthropic SSE格式：event: xxx\ndata: {...}
        if (!line.startsWith("data:")) {
            return null;
        }
        String data = line.substring(5).trim();
        if ("[DONE]".equals(data)) {
            return null;
        }
        try {
            JsonNode root = MAPPER.readTree(data);
            return parseStreamEvent(root);
        } catch (Exception e) {
            log.debug("解析Anthropic SSE JSON失败: {}", data);
            return null;
        }
    }

    /**
     * 解析Anthropic流式事件
     * <p>
     * Anthropic事件类型：message_start、content_block_start、content_block_delta、
     * content_block_stop、message_delta、message_stop
     * </p>
     * @param root
     * @return
     */
    protected AgentChatResponse parseStreamEvent(JsonNode root) {
        String type = root.has("type") ? root.get("type").asText() : "";
        List<AgentContentBlock> blocks = new ArrayList<>();
        AgentChatUsage usage = null;
        switch (type) {
            case "content_block_start" -> {
                JsonNode indexNode = root.get("index");
                JsonNode contentBlock = root.get("content_block");
                if (contentBlock != null) {
                    String blockType = contentBlock.has("type") ? contentBlock.get("type").asText() : "";
                    int index = indexNode != null ? indexNode.asInt() : -1;
                    String stableId = index >= 0 ? "call_stream_" + index : "toolu_" + System.nanoTime();
                    if ("tool_use".equals(blockType)) {
                        String realId = contentBlock.has("id") && !contentBlock.get("id").isNull()
                                ? contentBlock.get("id").asText() : null;
                        String toolName = contentBlock.has("name") ? contentBlock.get("name").asText() : "";
                        Map<String, Object> inputMap = new HashMap<>();
                        if (realId != null) {
                            inputMap.put(ModelResponseParser.TOOL_CALL_ID_KEY, realId);
                        }
                        inputMap.put(ModelResponseParser.RAW_ARGS_KEY, "");
                        blocks.add(new AgentToolUseBlock(toolName, stableId, inputMap));
                    }
                }
            }
            case "content_block_delta" -> {
                JsonNode delta = root.get("delta");
                if (delta != null) {
                    String deltaType = delta.has("type") ? delta.get("type").asText() : "";
                    if ("text_delta".equals(deltaType)) {
                        String text = delta.has("text") ? delta.get("text").asText() : "";
                        if (!text.isEmpty()) {
                            blocks.add(AgentTextBlock.builder().text(text).build());
                        }
                    } else if ("thinking_delta".equals(deltaType)) {
                        String thinking = delta.has("thinking") ? delta.get("thinking").asText() : "";
                        if (!thinking.isEmpty()) {
                            blocks.add(AgentThinkingBlock.builder().thinking(thinking).build());
                        }
                    } else if ("input_json_delta".equals(deltaType)) {
                        String partialJson = delta.has("partial_json") ? delta.get("partial_json").asText() : "";
                        if (!partialJson.isEmpty()) {
                            Map<String, Object> inputMap = new HashMap<>();
                            inputMap.put(ModelResponseParser.RAW_ARGS_KEY, partialJson);
                            // fix  对齐分片正确聚合，不再产生匿名块,按content_block索引派生稳定id，与content_block_start对齐确保分片可聚合
                            int index = root.has("index") ? root.get("index").asInt() : -1;
                            String stableId = index >= 0 ? "call_stream_" + index : "call_stream_delta";
                            blocks.add(new AgentToolUseBlock("", stableId, inputMap));
                        }
                    }
                }
            }
            case "message_delta" -> {
                JsonNode usageNode = root.get("usage");
                if (usageNode != null && !usageNode.isNull()) {
                    int outputTokens = usageNode.has("output_tokens") ? usageNode.get("output_tokens").asInt() : 0;
                    usage = new AgentChatUsage(0, outputTokens, outputTokens);
                }
            }
            case "message_start" -> {
                JsonNode message = root.get("message");
                if (message != null) {
                    JsonNode usageNode = message.get("usage");
                    if (usageNode != null && !usageNode.isNull()) {
                        int inputTokens = usageNode.has("input_tokens") ? usageNode.get("input_tokens").asInt() : 0;
                        usage = new AgentChatUsage(inputTokens, 0, inputTokens);
                    }
                }
            }
            default -> {
                // 其他事件类型不处理
            }
        }
        if (blocks.isEmpty() && usage == null) {
            return null;
        }
        return new AgentChatResponse(blocks, usage);
    }

    /**
     * 解析input节点为Map
     * @param inputNode
     * @return
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> parseInputNode(JsonNode inputNode) {
        if (inputNode == null || inputNode.isNull()) {
            return new HashMap<>();
        }
        try {
            return MAPPER.treeToValue(inputNode, Map.class);
        } catch (Exception e) {
            Map<String, Object> result = new HashMap<>();
            result.put("_raw", inputNode.toString());
            return result;
        }
    }

    @Override
    public String getEndpointPath() {
        return ENDPOINT_PATH;
    }

    @Override
    public Map<String, String> getHeaders(String apiKey) {
        Map<String, String> headers = new LinkedHashMap<>();
        if (apiKey != null && !apiKey.isBlank()) {
            headers.put("x-api-key", apiKey);
        }
        headers.put("anthropic-version", ANTHROPIC_VERSION);
        return headers;
    }

    @Override
    public boolean useBearerAuth() {
        return false;
    }
}
