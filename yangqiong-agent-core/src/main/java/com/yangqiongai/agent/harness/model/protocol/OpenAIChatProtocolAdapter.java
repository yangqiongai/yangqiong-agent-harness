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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.yangqiongai.agent.harness.config.AgentResponseFormat;
import com.yangqiongai.agent.harness.config.AgentToolChoice;
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
 * OpenAI协议适配器
 * <p>
 * 处理OpenAI兼容协议（/v1/chat/completions端点）。
 * 从OpenAICompatibleModel完整迁移协议处理逻辑。
 * </p>
 * @author yangqiong
 */
public class OpenAIChatProtocolAdapter implements AgentModelProtocolAdapter {

    private static final Logger log = LoggerFactory.getLogger(OpenAIChatProtocolAdapter.class);

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final String CHAT_COMPLETIONS_PATH = "/chat/completions";

    @Override
    public String buildRequestBody(String modelName, List<AgentMessage> messages, List<Map<String, Object>> tools,
                                      AgentGenerateOptions options, AgentGenerateOptions defaultOptions, boolean stream) {
        ObjectNode root = MAPPER.createObjectNode();
        if (modelName != null) {
            root.put("model", modelName);
        }
        // 构建消息数组
        ArrayNode messagesArray = MAPPER.createArrayNode();
        if (messages != null) {
            for (AgentMessage msg : messages) {
                messagesArray.add(convertMessageToJson(msg));
            }
        }
        root.set("messages", messagesArray);
        // 构建工具数组，规范化parameters为OpenAI JSON Schema格式
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
        // 设置生成选项
        if (options != null) {
            applyOptions(root, options);
        }
        // DeepSeek开放接口不支持response_format(json_schema)，返回400错误。
        // 对DeepSeek模型移除response_format，改以系统提示约束结构化输出，
        // 输出格式仍由引擎侧StructuredOutputValidator校验并自动重试修正。
        if (isDeepSeek(modelName) && options != null) {
            if (root.has("response_format")) {
                root.remove("response_format");
            }
            AgentResponseFormat format = options.getResponseFormat();
            if (format != null && "json_schema".equals(format.getType())) {
                injectStructuredOutputPrompt(root, format);
            }
        }
        root.put("stream", stream);
        // 流式模式下请求usage
        if (stream) {
            ObjectNode streamOptions = MAPPER.createObjectNode();
            streamOptions.put("include_usage", true);
            root.set("stream_options", streamOptions);
        }
        return root.toString();
    }

    /**
     * 判断模型是否为DeepSeek模型
     * <p>
     * DeepSeek开放接口不支持response_format的json_schema类型，需走系统提示约束。
     * </p>
     * @param modelName
     * @return
     */
    private boolean isDeepSeek(String modelName) {
        return modelName != null && modelName.toLowerCase().contains("deepseek");
    }

    /**
     * 向消息数组头部注入结构化输出系统提示约束
     * <p>
     * DeepSeek不支持response_format(json_schema)，改为通过系统提示要求模型输出严格JSON，
     * 输出格式由引擎侧StructuredOutputValidator校验并自动重试修正。
     * </p>
     * @param root
     * @param format
     */
    private void injectStructuredOutputPrompt(ObjectNode root, AgentResponseFormat format) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("请严格按照JSON Schema格式输出，只输出合法的JSON对象，")
                .append("不要包含任何多余的文字、markdown代码块或解释。JSON Schema如下：\n");
        if (format.getJsonSchema() != null && format.getJsonSchema().getSchema() != null) {
            try {
                prompt.append(MAPPER.writeValueAsString(format.getJsonSchema().getSchema()));
            } catch (Exception e) {
                prompt.append(format.getJsonSchema().getSchema().toString());
            }
        }
        ObjectNode systemNode = MAPPER.createObjectNode();
        systemNode.put("role", "system");
        systemNode.put("content", prompt.toString());
        ArrayNode messagesArray = root.has("messages") ? (ArrayNode) root.get("messages") : null;
        if (messagesArray != null) {
            messagesArray.insert(0, systemNode);
        }
    }

    /**
     * 应用生成选项到请求体
     * @param root
     * @param options
     */
    protected void applyOptions(ObjectNode root, AgentGenerateOptions options) {
        if (options.getTemperature() != null) {
            root.put("temperature", options.getTemperature());
        }
        if (options.getMaxTokens() != null) {
            root.put("max_tokens", options.getMaxTokens());
        }
        if (options.getTopP() != null) {
            root.put("top_p", options.getTopP());
        }
        if (options.getToolChoice() != null) {
            applyToolChoice(root, options.getToolChoice());
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
            root.set("response_format", formatNode);
        }
        // 透传厂商专属参数
        mergeExtraParameters(root, options);
    }

    /**
     * 合并厂商专属参数到请求体
     * @param root
     * @param options
     */
    protected void mergeExtraParameters(ObjectNode root, AgentGenerateOptions options) {
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
     * 应用工具选择策略到请求体
     * @param root
     * @param toolChoice
     */
    protected void applyToolChoice(ObjectNode root, AgentToolChoice toolChoice) {
        root.put("tool_choice", toolChoice.name().toLowerCase());
    }

    /**
     * 规范化工具parameters为OpenAI JSON Schema格式
     * <p>
     * OpenAI要求parameters必须是type:"object"的JSON Schema，包含properties字段。
     * 上层工具实现可能返回简化格式（直接字段定义），此方法做兜底规范化。
     * </p>
     * @param tool
     */
    @SuppressWarnings("unchecked")
    protected void normalizeToolParameters(Map<String, Object> tool) {
        Object functionObj = tool.get("function");
        if (!(functionObj instanceof Map)) {
            return;
        }
        Map<String, Object> function = (Map<String, Object>) functionObj;
        Object params = function.get("parameters");
        // parameters为空，设为标准空对象schema
        if (!(params instanceof Map)) {
            Map<String, Object> emptySchema = new LinkedHashMap<>();
            emptySchema.put("type", "object");
            emptySchema.put("properties", new LinkedHashMap<>());
            function.put("parameters", emptySchema);
            return;
        }
        Map<String, Object> paramsMap = (Map<String, Object>) params;
        Object type = paramsMap.get("type");
        // 已经是标准object schema，原样保留
        if ("object".equals(type)) {
            return;
        }
        // 简化格式（直接字段定义），包装为标准object schema
        Map<String, Object> normalized = new LinkedHashMap<>();
        normalized.put("type", "object");
        normalized.put("properties", paramsMap);
        function.put("parameters", normalized);
    }

    /**
     * 将AgentMessage转换为OpenAI消息JSON
     * <p>
     * 当消息包含图像块时,content输出为数组结构(OpenAI多模态格式);
     * 无图像块时保持字符串格式,确保向后兼容。
     * </p>
     * @param msg
     * @return
     */
    protected JsonNode convertMessageToJson(AgentMessage msg) {
        ObjectNode node = MAPPER.createObjectNode();
        String role = msg.getRole() != null ? msg.getRole().name().toLowerCase() : "user";
        node.put("role", role);
        // 提取文本内容和工具调用
        StringBuilder textContent = new StringBuilder();
        List<AgentToolUseBlock> toolCalls = new ArrayList<>();
        List<AgentImageBlock> imageBlocks = new ArrayList<>();
        String toolCallId = null;
        if (msg.getContent() != null) {
            for (AgentContentBlock block : msg.getContent()) {
                if (block instanceof AgentTextBlock textBlock) {
                    textContent.append(textBlock.getText());
                } else if (block instanceof AgentImageBlock imageBlock) {
                    // tool角色不支持图像,仅收集非tool角色的图像
                    imageBlocks.add(imageBlock);
                } else if (block instanceof AgentToolUseBlock toolUseBlock) {
                    toolCalls.add(toolUseBlock);
                } else if (block instanceof AgentToolResultBlock resultBlock) {
                    textContent.append(resultBlock.getTextContent());
                    if (resultBlock.getToolUseId() != null) {
                        toolCallId = resultBlock.getToolUseId();
                    }
                } else if (block instanceof AgentThinkingBlock thinkingBlock) {
                    // 思考块不发送给API（大多数API不支持）
                    String thinking = thinkingBlock.getThinking();
                    if (thinking != null) {
                        log.debug("跳过思考块: {}", thinking.substring(0, Math.min(50, thinking.length())));
                    }
                }
            }
        }
        // 工具结果消息使用tool角色
        if (toolCallId != null) {
            node.put("role", "tool");
            node.put("tool_call_id", toolCallId);
        }
        // 有图像块且非tool角色时,content输出为数组(OpenAI多模态格式)
        boolean useContentArray = !imageBlocks.isEmpty() && toolCallId == null;
        if (useContentArray) {
            ArrayNode contentArray = MAPPER.createArrayNode();
            if (textContent.length() > 0) {
                ObjectNode textNode = MAPPER.createObjectNode();
                textNode.put("type", "text");
                textNode.put("text", textContent.toString());
                contentArray.add(textNode);
            }
            for (AgentImageBlock imageBlock : imageBlocks) {
                // 跳过既无url也无base64的空图像块
                if (!imageBlock.isUrlMode() && !imageBlock.isBase64Mode()) {
                    log.warn("跳过空图像块(无url和base64数据)");
                    continue;
                }
                ObjectNode imageNode = MAPPER.createObjectNode();
                imageNode.put("type", "image_url");
                ObjectNode imageUrlNode = MAPPER.createObjectNode();
                if (imageBlock.isUrlMode()) {
                    imageUrlNode.put("url", imageBlock.getUrl());
                } else {
                    // Base64转为data URI格式(OpenAI官方推荐)
                    String mediaType = imageBlock.getMediaType() != null ? imageBlock.getMediaType() : "image/png";
                    String dataUri = "data:" + mediaType + ";base64," + imageBlock.getBase64Data();
                    imageUrlNode.put("url", dataUri);
                }
                imageNode.set("image_url", imageUrlNode);
                contentArray.add(imageNode);
            }
            // 所有图像块都被跳过且无文本时,回退为字符串格式避免空数组
            if (contentArray.isEmpty()) {
                node.put("content", textContent.toString());
            } else {
                node.set("content", contentArray);
            }
        } else {
            // 无图像时保持字符串格式,确保向后兼容
            // assistant消息有工具调用且无文本内容时，content应为null而非空字符串，
            // 部分OpenAI兼容API（如Anthropic兼容接口）会拒绝空字符串content
            if (!toolCalls.isEmpty() && "assistant".equals(role) && textContent.length() == 0) {
                node.putNull("content");
            } else {
                node.put("content", textContent.toString());
            }
        }
        // 助手消息的工具调用
        if (!toolCalls.isEmpty() && "assistant".equals(role)) {
            ArrayNode toolCallsArray = MAPPER.createArrayNode();
            for (AgentToolUseBlock toolCall : toolCalls) {
                ObjectNode callNode = MAPPER.createObjectNode();
                callNode.put("id", toolCall.getToolUseId() != null ? toolCall.getToolUseId() : "call_" + System.nanoTime());
                callNode.put("type", "function");
                ObjectNode functionNode = MAPPER.createObjectNode();
                functionNode.put("name", toolCall.getToolName());
                functionNode.put("arguments", toolCall.getInput() != null
                        ? MAPPER.valueToTree(toolCall.getInput()).toString() : "{}");
                callNode.set("function", functionNode);
                toolCallsArray.add(callNode);
            }
            node.set("tool_calls", toolCallsArray);
        }
        return node;
    }

    /**
     * 解析SSE流式响应行
     * @param line
     * @return
     */
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
            log.debug("解析SSE JSON失败: {}", data);
            return null;
        }
    }

    /**
     * 解析流式chunk响应
     * <p>
     * 流式工具调用的arguments是JSON字符串分片，无法逐片解析，因此将原始分片存入
     * {@link ModelResponseParser#RAW_ARGS_KEY}，由ModelResponseParser.mergeResponses拼接后统一解析。
     * 同一工具调用的后续分片缺少id，仅有index，故用index派生稳定id确保分片可聚合。
     * </p>
     * @param root
     * @return
     */
    protected AgentChatResponse parseChunkResponse(JsonNode root) {
        List<AgentContentBlock> blocks = new ArrayList<>();
        JsonNode choices = root.get("choices");
        if (choices != null && choices.isArray() && !choices.isEmpty()) {
            JsonNode choice = choices.get(0);
            JsonNode delta = choice.get("delta");
            if (delta != null) {
                // 文本内容
                JsonNode content = delta.get("content");
                if (content != null && !content.isNull() && !content.asText().isEmpty()) {
                    blocks.add(AgentTextBlock.builder().text(content.asText()).build());
                }
                // 工具调用
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
                            // 按index派生稳定id，确保同一工具调用的分片可聚合
                            String stableId = index >= 0
                                    ? "call_stream_" + index
                                    : (realId != null ? realId : "call_" + System.nanoTime());
                            Map<String, Object> input = new HashMap<>();
                            // 保存真实id，合并完成后恢复
                            if (realId != null) {
                                input.put(ModelResponseParser.TOOL_CALL_ID_KEY, realId);
                            }
                            // 保存原始参数分片，合并后统一解析
                            input.put(ModelResponseParser.RAW_ARGS_KEY, argsStr);
                            blocks.add(new AgentToolUseBlock(toolName, stableId, input));
                        }
                    }
                }
            }
        }
        // 解析usage（部分API在最后一个chunk返回）
        AgentChatUsage usage = null;
        JsonNode usageNode = root.get("usage");
        if (usageNode != null && !usageNode.isNull()) {
            int inputTokens = usageNode.has("prompt_tokens") ? usageNode.get("prompt_tokens").asInt() : 0;
            int outputTokens = usageNode.has("completion_tokens") ? usageNode.get("completion_tokens").asInt() : 0;
            usage = new AgentChatUsage(inputTokens, outputTokens, inputTokens + outputTokens);
        }
        if (blocks.isEmpty() && usage == null) {
            return null;
        }
        return new AgentChatResponse(blocks, usage);
    }

    /**
     * 解析非流式完整响应
     * <p>
     * 非流式响应中工具调用arguments是完整JSON字符串，可直接解析为Map。
     * </p>
     * @param root
     * @return
     */
    @Override
    public AgentChatResponse parseNonStreamResponse(JsonNode root) {
        List<AgentContentBlock> blocks = new ArrayList<>();
        JsonNode choices = root.get("choices");
        if (choices != null && choices.isArray() && !choices.isEmpty()) {
            JsonNode choice = choices.get(0);
            JsonNode message = choice.get("message");
            if (message != null) {
                // 文本内容
                JsonNode content = message.get("content");
                if (content != null && !content.isNull() && !content.asText().isEmpty()) {
                    blocks.add(AgentTextBlock.builder().text(content.asText()).build());
                }
                // 工具调用（arguments为完整JSON字符串）
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
        // 解析usage
        AgentChatUsage usage = null;
        JsonNode usageNode = root.get("usage");
        if (usageNode != null && !usageNode.isNull()) {
            int inputTokens = usageNode.has("prompt_tokens") ? usageNode.get("prompt_tokens").asInt() : 0;
            int outputTokens = usageNode.has("completion_tokens") ? usageNode.get("completion_tokens").asInt() : 0;
            usage = new AgentChatUsage(inputTokens, outputTokens, inputTokens + outputTokens);
        }
        return new AgentChatResponse(blocks, usage);
    }

    /**
     * 解析工具调用参数JSON
     * @param argsStr
     * @return
     */
    @SuppressWarnings("unchecked")
    protected Map<String, Object> parseArguments(String argsStr) {
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
        return CHAT_COMPLETIONS_PATH;
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
