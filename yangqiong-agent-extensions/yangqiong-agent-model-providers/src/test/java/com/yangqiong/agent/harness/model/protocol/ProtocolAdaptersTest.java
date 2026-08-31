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

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yangqiong.agent.harness.config.AgentJsonSchema;
import com.yangqiong.agent.harness.config.AgentResponseFormat;
import com.yangqiong.agent.harness.core.message.AgentContentBlock;
import com.yangqiong.agent.harness.core.message.AgentImageBlock;
import com.yangqiong.agent.harness.core.message.AgentMessage;
import com.yangqiong.agent.harness.core.message.AgentMessageRole;
import com.yangqiong.agent.harness.core.message.AgentTextBlock;
import com.yangqiong.agent.harness.core.message.AgentThinkingBlock;
import com.yangqiong.agent.harness.core.message.AgentToolResultBlock;
import com.yangqiong.agent.harness.core.message.AgentToolUseBlock;
import com.yangqiong.agent.harness.core.model.AgentChatResponse;
import com.yangqiong.agent.harness.core.model.AgentGenerateOptions;
import com.yangqiong.agent.harness.model.ModelResponseParser;

/**
 * 协议适配器单元测试
 * <p>
 * 覆盖OpenAI、DashScope、Ollama、Anthropic四种协议适配器的请求构建与响应解析。
 * </p>
 * @author yangqiong
 */
class ProtocolAdaptersTest {

    /**
     * JSON映射器
     */
    private static final ObjectMapper MAPPER = new ObjectMapper();

    /**
     * 构建用户消息
     * @param text
     * @return
     */
    private AgentMessage userMessage(String text) {
        return AgentMessage.builder()
                .name("user")
                .role(AgentMessageRole.USER)
                .content(List.of(AgentTextBlock.builder().text(text).build()))
                .build();
    }

    /**
     * 构建系统消息
     * @param text
     * @return
     */
    private AgentMessage systemMessage(String text) {
        return AgentMessage.builder()
                .name("system")
                .role(AgentMessageRole.SYSTEM)
                .content(List.of(AgentTextBlock.builder().text(text).build()))
                .build();
    }

    /**
     * 构建助手消息
     * @param text
     * @return
     */
    private AgentMessage assistantMessage(String text) {
        return AgentMessage.builder()
                .name("assistant")
                .role(AgentMessageRole.ASSISTANT)
                .content(List.of(AgentTextBlock.builder().text(text).build()))
                .build();
    }

    /**
     * 构建工具结果消息
     * @param toolUseId
     * @param result
     * @return
     */
    private AgentMessage toolResultMessage(String toolUseId, String result) {
        return AgentMessage.builder()
                .name("tool")
                .role(AgentMessageRole.TOOL)
                .content(List.of(AgentToolResultBlock.of(toolUseId,
                        List.of(AgentTextBlock.builder().text(result).build()))))
                .build();
    }

    /**
     * 构建简单工具定义
     * @param name
     * @param description
     * @param params 简化格式参数
     * @return
     */
    private Map<String, Object> simpleTool(String name, String description, Map<String, Object> params) {
        Map<String, Object> function = new LinkedHashMap<>();
        function.put("name", name);
        function.put("description", description);
        function.put("parameters", params);
        Map<String, Object> tool = new LinkedHashMap<>();
        tool.put("type", "function");
        tool.put("function", function);
        return tool;
    }

    /**
     * 构建标准JSON Schema工具定义
     * @param name
     * @param description
     * @return
     */
    private Map<String, Object> standardTool(String name, String description) {
        Map<String, Object> properties = new LinkedHashMap<>();
        Map<String, Object> fieldSchema = new LinkedHashMap<>();
        fieldSchema.put("type", "string");
        fieldSchema.put("description", "参数");
        properties.put("arg", fieldSchema);
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("type", "object");
        params.put("properties", properties);
        return simpleTool(name, description, params);
    }

    @Nested
    @DisplayName("OpenAIChatProtocolAdapter")
    class OpenAIAdapterTest {

        private final OpenAIChatProtocolAdapter adapter = new OpenAIChatProtocolAdapter();

        @Test
        @DisplayName("buildRequestBody包含model和messages字段")
        void buildRequestBody_shouldContainModelAndMessages() throws Exception {
            String body = adapter.buildRequestBody("gpt-4o-mini",
                    List.of(userMessage("你好")), null, null, null, false);
            JsonNode root = MAPPER.readTree(body);
            assertThat(root.get("model").asText()).isEqualTo("gpt-4o-mini");
            assertThat(root.get("messages").isArray()).isTrue();
            assertThat(root.get("messages").get(0).get("role").asText()).isEqualTo("user");
            assertThat(root.get("messages").get(0).get("content").asText()).isEqualTo("你好");
            assertThat(root.get("stream").asBoolean()).isFalse();
        }

        @Test
        @DisplayName("流式模式设置stream_options.include_usage")
        void buildRequestBody_streamMode_shouldSetStreamOptions() throws Exception {
            String body = adapter.buildRequestBody("gpt-4o", List.of(userMessage("hi")), null, null, null, true);
            JsonNode root = MAPPER.readTree(body);
            assertThat(root.get("stream").asBoolean()).isTrue();
            assertThat(root.get("stream_options").get("include_usage").asBoolean()).isTrue();
        }

        @Test
        @DisplayName("应用生成选项temperature和max_tokens")
        void buildRequestBody_shouldApplyOptions() throws Exception {
            AgentGenerateOptions options = AgentGenerateOptions.builder()
                    .temperature(0.7)
                    .maxTokens(100)
                    .topP(0.9)
                    .build();
            String body = adapter.buildRequestBody("gpt-4o", List.of(userMessage("hi")), null, options, null, false);
            JsonNode root = MAPPER.readTree(body);
            assertThat(root.get("temperature").asDouble()).isEqualTo(0.7);
            assertThat(root.get("max_tokens").asInt()).isEqualTo(100);
            assertThat(root.get("top_p").asDouble()).isEqualTo(0.9);
        }

        @Test
        @DisplayName("简化格式工具参数被规范化为object schema")
        void buildRequestBody_simplifiedToolParams_shouldBeNormalized() throws Exception {
            Map<String, Object> params = new LinkedHashMap<>();
            Map<String, Object> argSchema = new LinkedHashMap<>();
            argSchema.put("type", "string");
            params.put("arg", argSchema);
            Map<String, Object> tool = simpleTool("calc", "计算器", params);
            String body = adapter.buildRequestBody("gpt-4o", List.of(userMessage("hi")),
                    List.of(tool), null, null, false);
            JsonNode root = MAPPER.readTree(body);
            JsonNode function = root.get("tools").get(0).get("function");
            JsonNode parameters = function.get("parameters");
            assertThat(parameters.get("type").asText()).isEqualTo("object");
            assertThat(parameters.get("properties").has("arg")).isTrue();
        }

        @Test
        @DisplayName("标准格式工具参数保持原样")
        void buildRequestBody_standardToolParams_shouldRemainUnchanged() throws Exception {
            Map<String, Object> tool = standardTool("calc", "计算器");
            String body = adapter.buildRequestBody("gpt-4o", List.of(userMessage("hi")),
                    List.of(tool), null, null, false);
            JsonNode root = MAPPER.readTree(body);
            JsonNode parameters = root.get("tools").get(0).get("function").get("parameters");
            assertThat(parameters.get("type").asText()).isEqualTo("object");
            assertThat(parameters.get("properties").has("arg")).isTrue();
        }

        @Test
        @DisplayName("null parameters被规范化为空object schema")
        void buildRequestBody_nullParams_shouldBeNormalizedToEmptyObject() throws Exception {
            Map<String, Object> function = new LinkedHashMap<>();
            function.put("name", "noop");
            function.put("description", "无参数");
            Map<String, Object> tool = new LinkedHashMap<>();
            tool.put("type", "function");
            tool.put("function", function);
            String body = adapter.buildRequestBody("gpt-4o", List.of(userMessage("hi")),
                    List.of(tool), null, null, false);
            JsonNode root = MAPPER.readTree(body);
            JsonNode parameters = root.get("tools").get(0).get("function").get("parameters");
            assertThat(parameters.get("type").asText()).isEqualTo("object");
            assertThat(parameters.get("properties").isObject()).isTrue();
        }

        @Test
        @DisplayName("助手工具调用消息且无文本时content为null")
        void buildRequestBody_assistantWithToolCallsAndNoText_contentShouldBeNull() throws Exception {
            AgentToolUseBlock toolUse = new AgentToolUseBlock("calc", "call_123", new HashMap<>());
            AgentMessage msg = AgentMessage.builder()
                    .name("assistant")
                    .role(AgentMessageRole.ASSISTANT)
                    .content(List.of(toolUse))
                    .build();
            String body = adapter.buildRequestBody("gpt-4o", List.of(msg), null, null, null, false);
            JsonNode root = MAPPER.readTree(body);
            JsonNode messageNode = root.get("messages").get(0);
            assertThat(messageNode.get("role").asText()).isEqualTo("assistant");
            assertThat(messageNode.get("content").isNull()).isTrue();
            assertThat(messageNode.get("tool_calls").isArray()).isTrue();
            assertThat(messageNode.get("tool_calls").get(0).get("function").get("name").asText()).isEqualTo("calc");
        }

        @Test
        @DisplayName("工具结果消息转换为tool角色")
        void buildRequestBody_toolResultMessage_shouldBeToolRole() throws Exception {
            AgentMessage msg = toolResultMessage("call_123", "42");
            String body = adapter.buildRequestBody("gpt-4o", List.of(msg), null, null, null, false);
            JsonNode root = MAPPER.readTree(body);
            JsonNode messageNode = root.get("messages").get(0);
            assertThat(messageNode.get("role").asText()).isEqualTo("tool");
            assertThat(messageNode.get("tool_call_id").asText()).isEqualTo("call_123");
        }

        @Test
        @DisplayName("解析非流式文本响应")
        void parseNonStreamResponse_shouldExtractText() {
            String json = "{\"choices\":[{\"message\":{\"role\":\"assistant\",\"content\":\"你好\"}}],"
                    + "\"usage\":{\"prompt_tokens\":5,\"completion_tokens\":3}}";
            AgentChatResponse response = adapter.parseNonStreamResponse(parse(json));
            assertThat(response.getContent()).hasSize(1);
            AgentContentBlock block = response.getContent().get(0);
            assertThat(block).isInstanceOf(AgentTextBlock.class);
            assertThat(((AgentTextBlock) block).getText()).isEqualTo("你好");
            assertThat(response.getChatUsage()).isNotNull();
            assertThat(response.getChatUsage().getPromptTokens()).isEqualTo(5);
            assertThat(response.getChatUsage().getCompletionTokens()).isEqualTo(3);
        }

        @Test
        @DisplayName("解析非流式工具调用响应")
        void parseNonStreamResponse_shouldExtractToolCall() {
            String json = "{\"choices\":[{\"message\":{\"role\":\"assistant\","
                    + "\"tool_calls\":[{\"id\":\"call_1\",\"function\":{\"name\":\"calc\","
                    + "\"arguments\":\"{\\\"x\\\":1}\"}}]}}]}";
            AgentChatResponse response = adapter.parseNonStreamResponse(parse(json));
            assertThat(response.getContent()).hasSize(1);
            AgentContentBlock block = response.getContent().get(0);
            assertThat(block).isInstanceOf(AgentToolUseBlock.class);
            AgentToolUseBlock toolUse = (AgentToolUseBlock) block;
            assertThat(toolUse.getToolName()).isEqualTo("calc");
            assertThat(toolUse.getToolUseId()).isEqualTo("call_1");
            assertThat(toolUse.getInput().get("x")).isEqualTo(1);
        }

        @Test
        @DisplayName("解析流式文本delta行")
        void parseStreamLine_shouldExtractTextDelta() {
            String line = "data: {\"choices\":[{\"delta\":{\"content\":\"你好\"}}]}";
            AgentChatResponse response = adapter.parseStreamLine(line);
            assertThat(response).isNotNull();
            assertThat(response.getContent()).hasSize(1);
            assertThat(((AgentTextBlock) response.getContent().get(0)).getText()).isEqualTo("你好");
        }

        @Test
        @DisplayName("解析流式工具调用delta，保存原始参数分片")
        void parseStreamLine_shouldExtractToolCallDeltaWithRawArgs() {
            String line = "data: {\"choices\":[{\"delta\":{\"tool_calls\":[{\"index\":0,\"id\":\"call_1\","
                    + "\"function\":{\"name\":\"calc\",\"arguments\":\"{\\\"x\\\"\"}}]}}]}";
            AgentChatResponse response = adapter.parseStreamLine(line);
            assertThat(response).isNotNull();
            AgentToolUseBlock toolUse = (AgentToolUseBlock) response.getContent().get(0);
            assertThat(toolUse.getToolName()).isEqualTo("calc");
            assertThat(toolUse.getToolUseId()).isEqualTo("call_stream_0");
            // 原始参数分片保存在RAW_ARGS_KEY
            assertThat(toolUse.getInput().containsKey("__raw_args__")).isTrue();
        }

        @Test
        @DisplayName("空行和[DONE]标记返回null")
        void parseStreamLine_emptyOrDone_shouldReturnNull() {
            assertThat(adapter.parseStreamLine(null)).isNull();
            assertThat(adapter.parseStreamLine("")).isNull();
            assertThat(adapter.parseStreamLine("data: [DONE]")).isNull();
        }

        @Test
        @DisplayName("非data前缀行返回null")
        void parseStreamLine_nonDataLine_shouldReturnNull() {
            assertThat(adapter.parseStreamLine("event: message")).isNull();
        }

        @Test
        @DisplayName("流式usage在最后chunk解析")
        void parseStreamLine_shouldExtractUsage() {
            String line = "data: {\"choices\":[],\"usage\":{\"prompt_tokens\":10,\"completion_tokens\":5}}";
            AgentChatResponse response = adapter.parseStreamLine(line);
            assertThat(response).isNotNull();
            assertThat(response.getChatUsage()).isNotNull();
            assertThat(response.getChatUsage().getPromptTokens()).isEqualTo(10);
        }

        @Test
        @DisplayName("端点路径为/chat/completions")
        void getEndpointPath_shouldReturnChatCompletionsPath() {
            assertThat(adapter.getEndpointPath()).isEqualTo("/chat/completions");
        }

        @Test
        @DisplayName("使用Bearer认证")
        void useBearerAuth_shouldReturnTrue() {
            assertThat(adapter.useBearerAuth()).isTrue();
        }

        @Test
        @DisplayName("getHeaders返回空Map")
        void getHeaders_shouldReturnEmptyMap() {
            assertThat(adapter.getHeaders("sk-xxx")).isEmpty();
        }

        @Test
        @DisplayName("思考块不出现在请求消息中")
        void buildRequestBody_thinkingBlock_shouldBeSkipped() throws Exception {
            AgentMessage msg = AgentMessage.builder()
                    .name("assistant")
                    .role(AgentMessageRole.ASSISTANT)
                    .content(List.of(
                            AgentThinkingBlock.builder().thinking("内部思考").build(),
                            AgentTextBlock.builder().text("回答").build()))
                    .build();
            String body = adapter.buildRequestBody("gpt-4o", List.of(msg), null, null, null, false);
            JsonNode root = MAPPER.readTree(body);
            // content应只包含"回答"，思考块被跳过
            assertThat(root.get("messages").get(0).get("content").asText()).isEqualTo("回答");
        }

        @Test
        @DisplayName("非DeepSeek模型仍发送response_format(json_schema)")
        void buildRequestBody_nonDeepSeek_shouldKeepResponseFormat() throws Exception {
            AgentResponseFormat format = AgentResponseFormat.jsonSchema(AgentJsonSchema.builder()
                    .name("name_schema")
                    .schema(Map.of("type", "object", "properties", Map.of("name", Map.of("type", "string"))))
                    .build());
            AgentGenerateOptions options = AgentGenerateOptions.builder().responseFormat(format).build();
            String body = adapter.buildRequestBody("gpt-4o", List.of(userMessage("hi")), null, options, null, false);
            JsonNode root = MAPPER.readTree(body);
            assertThat(root.get("response_format")).as("非DeepSeek模型应保留response_format").isNotNull();
            assertThat(root.get("response_format").get("type").asText()).isEqualTo("json_schema");
        }

        @Test
        @DisplayName("DeepSeek模型移除response_format并注入系统提示约束")
        void buildRequestBody_deepSeek_shouldDropResponseFormatAndInjectPrompt() throws Exception {
            AgentResponseFormat format = AgentResponseFormat.jsonSchema(AgentJsonSchema.builder()
                    .name("name_schema")
                    .schema(Map.of("type", "object", "properties", Map.of("name", Map.of("type", "string"))))
                    .build());
            AgentGenerateOptions options = AgentGenerateOptions.builder().responseFormat(format).build();
            String body = adapter.buildRequestBody("deepseek-v4-flash", List.of(userMessage("hi")), null, options, null, false);
            JsonNode root = MAPPER.readTree(body);
            // DeepSeek不支持response_format(json_schema)，不应发送
            assertThat(root.has("response_format")).as("DeepSeek模型不应发送response_format").isFalse();
            // 结构化输出约束改为系统提示注入到消息头部
            JsonNode firstMessage = root.get("messages").get(0);
            assertThat(firstMessage.get("role").asText()).isEqualTo("system");
            String systemContent = firstMessage.get("content").asText();
            assertThat(systemContent).as("系统提示应包含JSON Schema约束").contains("JSON Schema")
                    .contains("\"name\"").contains("string");
        }
    }

    @Nested
    @DisplayName("DashScopeChatProtocolAdapter")
    class DashScopeAdapterTest {

        private final DashScopeChatProtocolAdapter adapter = new DashScopeChatProtocolAdapter();

        @Test
        @DisplayName("请求体采用input.messages嵌套结构")
        void buildRequestBody_shouldUseInputMessagesNesting() throws Exception {
            String body = adapter.buildRequestBody("qwen-max",
                    List.of(userMessage("你好")), null, null, null, false);
            JsonNode root = MAPPER.readTree(body);
            assertThat(root.get("model").asText()).isEqualTo("qwen-max");
            assertThat(root.get("input").get("messages").isArray()).isTrue();
            assertThat(root.get("input").get("messages").get(0).get("content").asText()).isEqualTo("你好");
            // parameters节点包含stream
            assertThat(root.get("parameters").get("stream").asBoolean()).isFalse();
        }

        @Test
        @DisplayName("流式模式设置incremental_output")
        void buildRequestBody_streamMode_shouldSetIncrementalOutput() throws Exception {
            String body = adapter.buildRequestBody("qwen-max", List.of(userMessage("hi")), null, null, null, true);
            JsonNode root = MAPPER.readTree(body);
            assertThat(root.get("parameters").get("stream").asBoolean()).isTrue();
            assertThat(root.get("parameters").get("incremental_output").asBoolean()).isTrue();
        }

        @Test
        @DisplayName("工具放到input.tools节点")
        void buildRequestBody_toolsShouldBeInInputTools() throws Exception {
            Map<String, Object> tool = standardTool("calc", "计算器");
            String body = adapter.buildRequestBody("qwen-max", List.of(userMessage("hi")),
                    List.of(tool), null, null, false);
            JsonNode root = MAPPER.readTree(body);
            assertThat(root.get("input").get("tools").isArray()).isTrue();
            assertThat(root.get("input").get("tools").get(0).get("function").get("name").asText()).isEqualTo("calc");
        }

        @Test
        @DisplayName("简化工具参数被规范化")
        void buildRequestBody_simplifiedToolParams_shouldBeNormalized() throws Exception {
            Map<String, Object> params = new LinkedHashMap<>();
            params.put("arg", Map.of("type", "string"));
            Map<String, Object> tool = simpleTool("calc", "计算器", params);
            String body = adapter.buildRequestBody("qwen-max", List.of(userMessage("hi")),
                    List.of(tool), null, null, false);
            JsonNode root = MAPPER.readTree(body);
            JsonNode parameters = root.get("input").get("tools").get(0).get("function").get("parameters");
            assertThat(parameters.get("type").asText()).isEqualTo("object");
            assertThat(parameters.get("properties").has("arg")).isTrue();
        }

        @Test
        @DisplayName("解析非流式响应output.choices[0].message")
        void parseNonStreamResponse_shouldExtractFromOutputChoices() {
            String json = "{\"output\":{\"choices\":[{\"message\":{\"role\":\"assistant\","
                    + "\"content\":\"你好\"}}]},\"usage\":{\"input_tokens\":5,\"output_tokens\":3}}";
            AgentChatResponse response = adapter.parseNonStreamResponse(parse(json));
            assertThat(response.getContent()).hasSize(1);
            assertThat(((AgentTextBlock) response.getContent().get(0)).getText()).isEqualTo("你好");
            assertThat(response.getChatUsage().getPromptTokens()).isEqualTo(5);
            assertThat(response.getChatUsage().getCompletionTokens()).isEqualTo(3);
        }

        @Test
        @DisplayName("解析非流式工具调用")
        void parseNonStreamResponse_shouldExtractToolCall() {
            String json = "{\"output\":{\"choices\":[{\"message\":{\"tool_calls\":[{\"id\":\"c1\","
                    + "\"function\":{\"name\":\"calc\",\"arguments\":\"{}\"}}]}}]}}";
            AgentChatResponse response = adapter.parseNonStreamResponse(parse(json));
            AgentToolUseBlock toolUse = (AgentToolUseBlock) response.getContent().get(0);
            assertThat(toolUse.getToolName()).isEqualTo("calc");
            assertThat(toolUse.getToolUseId()).isEqualTo("c1");
        }

        @Test
        @DisplayName("解析流式delta行")
        void parseStreamLine_shouldExtractDelta() {
            String line = "data: {\"output\":{\"choices\":[{\"delta\":{\"content\":\"你好\"}}]}}";
            AgentChatResponse response = adapter.parseStreamLine(line);
            assertThat(response).isNotNull();
            assertThat(((AgentTextBlock) response.getContent().get(0)).getText()).isEqualTo("你好");
        }

        @Test
        @DisplayName("解析流式usage兼容input_tokens和prompt_tokens")
        void parseStreamLine_usageShouldSupportBothTokenFields() {
            String line = "data: {\"output\":{\"choices\":[]},\"usage\":{\"prompt_tokens\":8,\"completion_tokens\":4}}";
            AgentChatResponse response = adapter.parseStreamLine(line);
            assertThat(response.getChatUsage().getPromptTokens()).isEqualTo(8);
            assertThat(response.getChatUsage().getCompletionTokens()).isEqualTo(4);
        }

        @Test
        @DisplayName("端点路径为DashScope原生generation端点")
        void getEndpointPath_shouldReturnDashScopeEndpoint() {
            assertThat(adapter.getEndpointPath())
                    .isEqualTo("/api/v1/services/aigc/text-generation/generation");
        }

        @Test
        @DisplayName("使用Bearer认证")
        void useBearerAuth_shouldReturnTrue() {
            assertThat(adapter.useBearerAuth()).isTrue();
        }
    }

    @Nested
    @DisplayName("OllamaChatProtocolAdapter")
    class OllamaAdapterTest {

        private final OllamaChatProtocolAdapter adapter = new OllamaChatProtocolAdapter();

        @Test
        @DisplayName("请求体直接使用messages数组无嵌套")
        void buildRequestBody_shouldUseDirectMessagesArray() throws Exception {
            String body = adapter.buildRequestBody("llama3",
                    List.of(userMessage("你好")), null, null, null, false);
            JsonNode root = MAPPER.readTree(body);
            assertThat(root.get("model").asText()).isEqualTo("llama3");
            assertThat(root.get("messages").isArray()).isTrue();
            assertThat(root.get("messages").get(0).get("content").asText()).isEqualTo("你好");
            // 无input/parameters嵌套
            assertThat(root.has("input")).isFalse();
            assertThat(root.has("parameters")).isFalse();
        }

        @Test
        @DisplayName("maxTokens映射到num_predict选项")
        void buildRequestBody_maxTokensShouldMapToNumPredict() throws Exception {
            AgentGenerateOptions options = AgentGenerateOptions.builder()
                    .maxTokens(200)
                    .temperature(0.5)
                    .build();
            String body = adapter.buildRequestBody("llama3", List.of(userMessage("hi")), null, options, null, false);
            JsonNode root = MAPPER.readTree(body);
            assertThat(root.get("options").get("num_predict").asInt()).isEqualTo(200);
            assertThat(root.get("options").get("temperature").asDouble()).isEqualTo(0.5);
        }

        @Test
        @DisplayName("responseFormat(json_schema)映射到原生format字段")
        void buildRequestBody_jsonSchemaResponseFormat_shouldSetFormatField() throws Exception {
            AgentResponseFormat format = AgentResponseFormat.jsonSchema(AgentJsonSchema.builder()
                    .name("name_schema")
                    .schema(Map.of("type", "object", "properties", Map.of("name", Map.of("type", "string"))))
                    .build());
            AgentGenerateOptions options = AgentGenerateOptions.builder().responseFormat(format).build();
            String body = adapter.buildRequestBody("llama3", List.of(userMessage("hi")), null, options, null, false);
            JsonNode root = MAPPER.readTree(body);
            // Ollama原生结构化输出：format字段携带JSON Schema由解码层强制约束
            assertThat(root.get("format")).as("应携带原生format字段").isNotNull();
            assertThat(root.get("format").get("type").asText()).isEqualTo("object");
            assertThat(root.get("format").get("properties").get("name").get("type").asText()).isEqualTo("string");
            assertThat(root.has("response_format")).as("Ollama不应使用response_format字段").isFalse();
        }

        @Test
        @DisplayName("简化工具参数被规范化")
        void buildRequestBody_simplifiedToolParams_shouldBeNormalized() throws Exception {
            Map<String, Object> params = new LinkedHashMap<>();
            params.put("arg", Map.of("type", "string"));
            Map<String, Object> tool = simpleTool("calc", "计算器", params);
            String body = adapter.buildRequestBody("llama3", List.of(userMessage("hi")),
                    List.of(tool), null, null, false);
            JsonNode root = MAPPER.readTree(body);
            JsonNode parameters = root.get("tools").get(0).get("function").get("parameters");
            assertThat(parameters.get("type").asText()).isEqualTo("object");
        }

        @Test
        @DisplayName("解析非流式响应message.content")
        void parseNonStreamResponse_shouldExtractMessageContent() {
            String json = "{\"message\":{\"role\":\"assistant\",\"content\":\"你好\"},\"done\":true}";
            AgentChatResponse response = adapter.parseNonStreamResponse(parse(json));
            assertThat(((AgentTextBlock) response.getContent().get(0)).getText()).isEqualTo("你好");
        }

        @Test
        @DisplayName("解析非流式响应的thinking块")
        void parseNonStreamResponse_shouldExtractThinkingBlock() {
            String json = "{\"message\":{\"role\":\"assistant\",\"content\":\"答案\","
                    + "\"thinking\":\"思考过程\"},\"done\":true}";
            AgentChatResponse response = adapter.parseNonStreamResponse(parse(json));
            assertThat(response.getContent()).hasSize(2);
            assertThat(response.getContent().get(0)).isInstanceOf(AgentTextBlock.class);
            assertThat(response.getContent().get(1)).isInstanceOf(AgentThinkingBlock.class);
            assertThat(((AgentThinkingBlock) response.getContent().get(1)).getThinking()).isEqualTo("思考过程");
        }

        @Test
        @DisplayName("解析非流式工具调用，arguments可为JSON对象")
        void parseNonStreamResponse_shouldExtractToolCallWithObjectArguments() {
            String json = "{\"message\":{\"role\":\"assistant\",\"tool_calls\":[{\"id\":\"c1\","
                    + "\"function\":{\"name\":\"calc\",\"arguments\":{\"x\":1}}}]},\"done\":true}";
            AgentChatResponse response = adapter.parseNonStreamResponse(parse(json));
            AgentToolUseBlock toolUse = (AgentToolUseBlock) response.getContent().get(0);
            assertThat(toolUse.getToolName()).isEqualTo("calc");
            assertThat(toolUse.getInput().get("x")).isEqualTo(1);
        }

        @Test
        @DisplayName("解析非流式usage（done=true时返回）")
        void parseNonStreamResponse_shouldExtractUsageWhenDone() {
            String json = "{\"message\":{\"role\":\"assistant\",\"content\":\"hi\"},"
                    + "\"done\":true,\"prompt_eval_count\":10,\"eval_count\":5}";
            AgentChatResponse response = adapter.parseNonStreamResponse(parse(json));
            assertThat(response.getChatUsage()).isNotNull();
            assertThat(response.getChatUsage().getPromptTokens()).isEqualTo(10);
            assertThat(response.getChatUsage().getCompletionTokens()).isEqualTo(5);
        }

        @Test
        @DisplayName("解析流式JSON行（无data:前缀）")
        void parseStreamLine_shouldParseJsonLineWithoutDataPrefix() {
            String line = "{\"message\":{\"role\":\"assistant\",\"content\":\"你好\"},\"done\":false}";
            AgentChatResponse response = adapter.parseStreamLine(line);
            assertThat(response).isNotNull();
            assertThat(((AgentTextBlock) response.getContent().get(0)).getText()).isEqualTo("你好");
        }

        @Test
        @DisplayName("流式done=true时返回usage")
        void parseStreamLine_doneTrue_shouldReturnUsage() {
            String line = "{\"message\":{\"role\":\"assistant\",\"content\":\"\"},"
                    + "\"done\":true,\"prompt_eval_count\":8,\"eval_count\":4}";
            AgentChatResponse response = adapter.parseStreamLine(line);
            assertThat(response.getChatUsage()).isNotNull();
            assertThat(response.getChatUsage().getPromptTokens()).isEqualTo(8);
        }

        @Test
        @DisplayName("空行返回null")
        void parseStreamLine_empty_shouldReturnNull() {
            assertThat(adapter.parseStreamLine(null)).isNull();
            assertThat(adapter.parseStreamLine("")).isNull();
        }

        @Test
        @DisplayName("端点路径为/api/chat")
        void getEndpointPath_shouldReturnApiChat() {
            assertThat(adapter.getEndpointPath()).isEqualTo("/api/chat");
        }

        @Test
        @DisplayName("不使用Bearer认证")
        void useBearerAuth_shouldReturnFalse() {
            assertThat(adapter.useBearerAuth()).isFalse();
        }
    }

    @Nested
    @DisplayName("AnthropicChatProtocolAdapter")
    class AnthropicAdapterTest {

        private final AnthropicChatProtocolAdapter adapter = new AnthropicChatProtocolAdapter();

        @Test
        @DisplayName("系统消息提取到顶层system字段")
        void buildRequestBody_shouldExtractSystemMessagesToTopLevel() throws Exception {
            String body = adapter.buildRequestBody("claude-3",
                    List.of(systemMessage("你是助手"), userMessage("你好")), null, null, null, false);
            JsonNode root = MAPPER.readTree(body);
            assertThat(root.get("system").asText()).isEqualTo("你是助手");
            // messages数组不包含系统消息
            assertThat(root.get("messages").isArray()).isTrue();
            assertThat(root.get("messages").has(0)).isTrue();
            assertThat(root.get("messages").get(0).get("role").asText()).isEqualTo("user");
        }

        @Test
        @DisplayName("max_tokens默认4096")
        void buildRequestBody_maxTokensShouldDefaultTo4096() throws Exception {
            String body = adapter.buildRequestBody("claude-3", List.of(userMessage("hi")), null, null, null, false);
            JsonNode root = MAPPER.readTree(body);
            assertThat(root.get("max_tokens").asInt()).isEqualTo(4096);
        }

        @Test
        @DisplayName("max_tokens使用options传入值")
        void buildRequestBody_maxTokensShouldUseOptionsValue() throws Exception {
            AgentGenerateOptions options = AgentGenerateOptions.builder().maxTokens(100).build();
            String body = adapter.buildRequestBody("claude-3", List.of(userMessage("hi")), null, options, null, false);
            JsonNode root = MAPPER.readTree(body);
            assertThat(root.get("max_tokens").asInt()).isEqualTo(100);
        }

        @Test
        @DisplayName("content采用数组结构")
        void buildRequestBody_contentShouldBeArray() throws Exception {
            String body = adapter.buildRequestBody("claude-3", List.of(userMessage("你好")), null, null, null, false);
            JsonNode root = MAPPER.readTree(body);
            JsonNode content = root.get("messages").get(0).get("content");
            assertThat(content.isArray()).isTrue();
            assertThat(content.get(0).get("type").asText()).isEqualTo("text");
            assertThat(content.get(0).get("text").asText()).isEqualTo("你好");
        }

        @Test
        @DisplayName("工具转换为Anthropic格式input_schema")
        void buildRequestBody_toolShouldBeConvertedToInputSchema() throws Exception {
            Map<String, Object> tool = standardTool("calc", "计算器");
            String body = adapter.buildRequestBody("claude-3", List.of(userMessage("hi")),
                    List.of(tool), null, null, false);
            JsonNode root = MAPPER.readTree(body);
            JsonNode anthropicTool = root.get("tools").get(0);
            assertThat(anthropicTool.get("name").asText()).isEqualTo("calc");
            assertThat(anthropicTool.get("description").asText()).isEqualTo("计算器");
            // Anthropic使用input_schema而非parameters
            assertThat(anthropicTool.has("input_schema")).isTrue();
            assertThat(anthropicTool.get("input_schema").get("type").asText()).isEqualTo("object");
        }

        @Test
        @DisplayName("简化格式工具参数被规范化为input_schema")
        void buildRequestBody_simplifiedToolParams_shouldBeNormalized() throws Exception {
            Map<String, Object> params = new LinkedHashMap<>();
            params.put("arg", Map.of("type", "string"));
            Map<String, Object> tool = simpleTool("calc", "计算器", params);
            String body = adapter.buildRequestBody("claude-3", List.of(userMessage("hi")),
                    List.of(tool), null, null, false);
            JsonNode root = MAPPER.readTree(body);
            JsonNode inputSchema = root.get("tools").get(0).get("input_schema");
            assertThat(inputSchema.get("type").asText()).isEqualTo("object");
            assertThat(inputSchema.get("properties").has("arg")).isTrue();
        }

        @Test
        @DisplayName("空content数组会被填充空文本块")
        void buildRequestBody_emptyContent_shouldBeFilledWithEmptyText() throws Exception {
            AgentMessage msg = AgentMessage.builder()
                    .name("user")
                    .role(AgentMessageRole.USER)
                    .content(List.of())
                    .build();
            String body = adapter.buildRequestBody("claude-3", List.of(msg), null, null, null, false);
            JsonNode root = MAPPER.readTree(body);
            JsonNode content = root.get("messages").get(0).get("content");
            assertThat(content.isArray()).isTrue();
            assertThat(content.size()).isEqualTo(1);
            assertThat(content.get(0).get("type").asText()).isEqualTo("text");
        }

        @Test
        @DisplayName("tool角色消息转换为user角色")
        void buildRequestBody_toolRoleMessage_shouldBeConvertedToUserRole() throws Exception {
            AgentMessage msg = toolResultMessage("toolu_1", "结果");
            String body = adapter.buildRequestBody("claude-3", List.of(msg), null, null, null, false);
            JsonNode root = MAPPER.readTree(body);
            assertThat(root.get("messages").get(0).get("role").asText()).isEqualTo("user");
            JsonNode content = root.get("messages").get(0).get("content");
            assertThat(content.get(0).get("type").asText()).isEqualTo("tool_result");
            assertThat(content.get(0).get("tool_use_id").asText()).isEqualTo("toolu_1");
        }

        @Test
        @DisplayName("助手工具调用转换为tool_use content块")
        void buildRequestBody_assistantToolUse_shouldBeConvertedToToolUseBlock() throws Exception {
            Map<String, Object> input = new HashMap<>();
            input.put("x", 1);
            AgentToolUseBlock toolUse = new AgentToolUseBlock("calc", "toolu_1", input);
            AgentMessage msg = AgentMessage.builder()
                    .name("assistant")
                    .role(AgentMessageRole.ASSISTANT)
                    .content(List.of(toolUse))
                    .build();
            String body = adapter.buildRequestBody("claude-3", List.of(msg), null, null, null, false);
            JsonNode root = MAPPER.readTree(body);
            JsonNode contentBlock = root.get("messages").get(0).get("content").get(0);
            assertThat(contentBlock.get("type").asText()).isEqualTo("tool_use");
            assertThat(contentBlock.get("id").asText()).isEqualTo("toolu_1");
            assertThat(contentBlock.get("name").asText()).isEqualTo("calc");
            assertThat(contentBlock.get("input").get("x").asInt()).isEqualTo(1);
        }

        @Test
        @DisplayName("解析非流式文本响应content数组")
        void parseNonStreamResponse_shouldExtractTextFromContentArray() {
            String json = "{\"content\":[{\"type\":\"text\",\"text\":\"你好\"}],"
                    + "\"usage\":{\"input_tokens\":5,\"output_tokens\":3}}";
            AgentChatResponse response = adapter.parseNonStreamResponse(parse(json));
            assertThat(((AgentTextBlock) response.getContent().get(0)).getText()).isEqualTo("你好");
            assertThat(response.getChatUsage().getPromptTokens()).isEqualTo(5);
            assertThat(response.getChatUsage().getCompletionTokens()).isEqualTo(3);
        }

        @Test
        @DisplayName("解析非流式tool_use块")
        void parseNonStreamResponse_shouldExtractToolUseBlock() {
            String json = "{\"content\":[{\"type\":\"tool_use\",\"id\":\"toolu_1\","
                    + "\"name\":\"calc\",\"input\":{\"x\":1}}]}";
            AgentChatResponse response = adapter.parseNonStreamResponse(parse(json));
            AgentToolUseBlock toolUse = (AgentToolUseBlock) response.getContent().get(0);
            assertThat(toolUse.getToolName()).isEqualTo("calc");
            assertThat(toolUse.getToolUseId()).isEqualTo("toolu_1");
            assertThat(toolUse.getInput().get("x")).isEqualTo(1);
        }

        @Test
        @DisplayName("解析非流式thinking块")
        void parseNonStreamResponse_shouldExtractThinkingBlock() {
            String json = "{\"content\":[{\"type\":\"thinking\",\"thinking\":\"思考\"},"
                    + "{\"type\":\"text\",\"text\":\"答案\"}]}";
            AgentChatResponse response = adapter.parseNonStreamResponse(parse(json));
            assertThat(response.getContent()).hasSize(2);
            assertThat(response.getContent().get(0)).isInstanceOf(AgentThinkingBlock.class);
            assertThat(response.getContent().get(1)).isInstanceOf(AgentTextBlock.class);
        }

        @Test
        @DisplayName("解析流式text_delta事件")
        void parseStreamLine_shouldExtractTextDelta() {
            String line = "data: {\"type\":\"content_block_delta\","
                    + "\"delta\":{\"type\":\"text_delta\",\"text\":\"你好\"}}";
            AgentChatResponse response = adapter.parseStreamLine(line);
            assertThat(response).isNotNull();
            assertThat(((AgentTextBlock) response.getContent().get(0)).getText()).isEqualTo("你好");
        }

        @Test
        @DisplayName("解析流式thinking_delta事件")
        void parseStreamLine_shouldExtractThinkingDelta() {
            String line = "data: {\"type\":\"content_block_delta\","
                    + "\"delta\":{\"type\":\"thinking_delta\",\"thinking\":\"思考\"}}";
            AgentChatResponse response = adapter.parseStreamLine(line);
            assertThat(response).isNotNull();
            assertThat(response.getContent().get(0)).isInstanceOf(AgentThinkingBlock.class);
        }

        @Test
        @DisplayName("解析流式content_block_start事件创建工具调用")
        void parseStreamLine_shouldHandleContentBlockStartForToolUse() {
            String line = "data: {\"type\":\"content_block_start\",\"index\":0,"
                    + "\"content_block\":{\"type\":\"tool_use\",\"id\":\"toolu_1\",\"name\":\"calc\"}}";
            AgentChatResponse response = adapter.parseStreamLine(line);
            assertThat(response).isNotNull();
            AgentToolUseBlock toolUse = (AgentToolUseBlock) response.getContent().get(0);
            assertThat(toolUse.getToolName()).isEqualTo("calc");
            assertThat(toolUse.getToolUseId()).isEqualTo("call_stream_0");
        }

        @Test
        @DisplayName("解析流式input_json_delta事件保存分片")
        void parseStreamLine_shouldHandleInputJsonDelta() {
            String line = "data: {\"type\":\"content_block_delta\","
                    + "\"delta\":{\"type\":\"input_json_delta\",\"partial_json\":\"{\\\"x\\\"\"}}";
            AgentChatResponse response = adapter.parseStreamLine(line);
            assertThat(response).isNotNull();
            AgentToolUseBlock toolUse = (AgentToolUseBlock) response.getContent().get(0);
            assertThat(toolUse.getInput().containsKey("__raw_args__")).isTrue();
        }

        @Test
        @DisplayName("input_json_delta按index派生稳定id与start对齐")
        void parseStreamLine_inputJsonDelta_shouldKeyByBlockIndex() {
            String line = "data: {\"type\":\"content_block_delta\",\"index\":0,"
                    + "\"delta\":{\"type\":\"input_json_delta\",\"partial_json\":\"{\\\"query\\\":\\\"自我介绍\\\"}\"}}";
            AgentChatResponse response = adapter.parseStreamLine(line);
            AgentToolUseBlock toolUse = (AgentToolUseBlock) response.getContent().get(0);
            assertThat(toolUse.getToolUseId()).isEqualTo("call_stream_0");
        }

        @Test
        @DisplayName("回归：Anthropic流式工具调用分片与start聚合为单次调用")
        void mergeResponses_anthropicStreamFragments_shouldMergeIntoSingleToolCall() {
            AgentChatResponse start = adapter.parseStreamLine("data: {\"type\":\"content_block_start\",\"index\":0,"
                    + "\"content_block\":{\"type\":\"tool_use\",\"id\":\"toolu_01Rx\",\"name\":\"memory_search\"}}");
            AgentChatResponse delta1 = adapter.parseStreamLine("data: {\"type\":\"content_block_delta\",\"index\":0,"
                    + "\"delta\":{\"type\":\"input_json_delta\",\"partial_json\":\"{\\\"query\\\":\"}}");
            AgentChatResponse delta2 = adapter.parseStreamLine("data: {\"type\":\"content_block_delta\",\"index\":0,"
                    + "\"delta\":{\"type\":\"input_json_delta\",\"partial_json\":\"\\\"自我介绍\\\"}\"}}");

            ModelResponseParser parser = new ModelResponseParser();
            AgentChatResponse merged = parser.mergeResponses(
                    List.of(start, delta1, delta2).stream().filter(java.util.Objects::nonNull).toList());

            // 修复前：start与delta的稳定id不一致导致拆成两个工具调用（其中一个是匿名残块）
            List<AgentToolUseBlock> toolUses = merged.getContent().stream()
                    .filter(AgentToolUseBlock.class::isInstance)
                    .map(AgentToolUseBlock.class::cast)
                    .toList();
            assertThat(toolUses).hasSize(1);
            AgentToolUseBlock toolUse = toolUses.get(0);
            assertThat(toolUse.getToolName()).isEqualTo("memory_search");
            assertThat(toolUse.getToolUseId()).isEqualTo("toolu_01Rx");
            assertThat(toolUse.getInput().get("query")).isEqualTo("自我介绍");
        }

        @Test
        @DisplayName("回归：序列化时成对剔除匿名tool_use及其孤儿tool_result")
        void buildRequestBody_blankNameToolUse_shouldBeDroppedWithOrphanResult() throws Exception {
            AgentToolUseBlock anonymous = new AgentToolUseBlock("", "call_stream_delta",
                    Map.of("query", "自我介绍"));
            AgentMessage assistantMsg = AgentMessage.builder()
                    .name("assistant")
                    .role(AgentMessageRole.ASSISTANT)
                    .content(List.of(anonymous))
                    .build();
            AgentMessage resultMsg = AgentMessage.builder()
                    .name("tool")
                    .role(AgentMessageRole.TOOL)
                    .content(List.of(AgentToolResultBlock.of("call_stream_delta", List.of())))
                    .build();

            String body = adapter.buildRequestBody("claude-3",
                    List.of(userMessage("你好"), assistantMsg, resultMsg), null, null, null, false);
            JsonNode root = MAPPER.readTree(body);

            // 助手消息只剩文本（无tool_use），tool角色消息的孤儿tool_result被剔除（仅剩空文本填充块）
            JsonNode assistantContent = root.get("messages").get(1).get("content");
            assertThat(assistantContent.toString()).doesNotContain("tool_use");
            JsonNode resultContent = root.get("messages").get(2).get("content");
            assertThat(resultContent.toString()).doesNotContain("tool_result");
        }

        @Test
        @DisplayName("解析message_start事件的input_tokens")
        void parseStreamLine_shouldHandleMessageStartUsage() {
            String line = "data: {\"type\":\"message_start\","
                    + "\"message\":{\"usage\":{\"input_tokens\":10}}}";
            AgentChatResponse response = adapter.parseStreamLine(line);
            assertThat(response.getChatUsage()).isNotNull();
            assertThat(response.getChatUsage().getPromptTokens()).isEqualTo(10);
        }

        @Test
        @DisplayName("解析message_delta事件的output_tokens")
        void parseStreamLine_shouldHandleMessageDeltaUsage() {
            String line = "data: {\"type\":\"message_delta\",\"usage\":{\"output_tokens\":5}}";
            AgentChatResponse response = adapter.parseStreamLine(line);
            assertThat(response.getChatUsage()).isNotNull();
            assertThat(response.getChatUsage().getCompletionTokens()).isEqualTo(5);
        }

        @Test
        @DisplayName("端点路径为/v1/messages")
        void getEndpointPath_shouldReturnV1Messages() {
            assertThat(adapter.getEndpointPath()).isEqualTo("/v1/messages");
        }

        @Test
        @DisplayName("不使用Bearer认证")
        void useBearerAuth_shouldReturnFalse() {
            assertThat(adapter.useBearerAuth()).isFalse();
        }

        @Test
        @DisplayName("getHeaders包含x-api-key和anthropic-version")
        void getHeaders_shouldContainApiKeyAndVersion() {
            Map<String, String> headers = adapter.getHeaders("sk-ant-xxx");
            assertThat(headers.get("x-api-key")).isEqualTo("sk-ant-xxx");
            assertThat(headers.get("anthropic-version")).isEqualTo("2023-06-01");
        }

        @Test
        @DisplayName("apiKey为空时getHeaders仍包含anthropic-version")
        void getHeaders_emptyApiKey_shouldStillContainVersion() {
            Map<String, String> headers = adapter.getHeaders(null);
            assertThat(headers).containsKey("anthropic-version");
            assertThat(headers).doesNotContainKey("x-api-key");
        }

        @Test
        @DisplayName("多个系统消息合并到system字段")
        void buildRequestBody_multipleSystemMessages_shouldBeMerged() throws Exception {
            String body = adapter.buildRequestBody("claude-3",
                    List.of(systemMessage("规则1"), systemMessage("规则2"), userMessage("hi")),
                    null, null, null, false);
            JsonNode root = MAPPER.readTree(body);
            assertThat(root.get("system").asText()).isEqualTo("规则1\n\n规则2");
        }
    }

    /**
     * 多模态图像输入测试
     */
    @Nested
    @DisplayName("多模态图像输入")
    class ImageInputTest {

        /**
         * 构建带URL图像的用户消息
         * @param text
         * @param imageUrl
         * @return
         */
        private AgentMessage userMessageWithImageUrl(String text, String imageUrl) {
            List<AgentContentBlock> blocks = new ArrayList<>();
            if (text != null && !text.isEmpty()) {
                blocks.add(AgentTextBlock.builder().text(text).build());
            }
            blocks.add(AgentImageBlock.builder().url(imageUrl).build());
            return AgentMessage.builder()
                    .name("user")
                    .role(AgentMessageRole.USER)
                    .content(blocks)
                    .build();
        }

        /**
         * 构建带Base64图像的用户消息
         * @param text
         * @param base64Data
         * @param mediaType
         * @return
         */
        private AgentMessage userMessageWithImageBase64(String text, String base64Data, String mediaType) {
            List<AgentContentBlock> blocks = new ArrayList<>();
            if (text != null && !text.isEmpty()) {
                blocks.add(AgentTextBlock.builder().text(text).build());
            }
            blocks.add(AgentImageBlock.builder().base64Data(base64Data).mediaType(mediaType).build());
            return AgentMessage.builder()
                    .name("user")
                    .role(AgentMessageRole.USER)
                    .content(blocks)
                    .build();
        }

        @Test
        @DisplayName("OpenAI:无图像时content为字符串(向后兼容)")
        void openAi_noImage_contentShouldBeString() throws Exception {
            OpenAIChatProtocolAdapter adapter = new OpenAIChatProtocolAdapter();
            String body = adapter.buildRequestBody("gpt-4o", List.of(userMessage("描述图片")), null, null, null, false);
            JsonNode root = MAPPER.readTree(body);
            JsonNode messageNode = root.get("messages").get(0);
            assertThat(messageNode.get("content").isTextual()).isTrue();
            assertThat(messageNode.get("content").asText()).isEqualTo("描述图片");
        }

        @Test
        @DisplayName("OpenAI:URL图像时content为数组,包含image_url块")
        void openAi_urlImage_contentShouldBeArrayWithImageUrl() throws Exception {
            OpenAIChatProtocolAdapter adapter = new OpenAIChatProtocolAdapter();
            AgentMessage msg = userMessageWithImageUrl("描述这张图", "https://example.com/img.png");
            String body = adapter.buildRequestBody("gpt-4o", List.of(msg), null, null, null, false);
            JsonNode root = MAPPER.readTree(body);
            JsonNode messageNode = root.get("messages").get(0);
            assertThat(messageNode.get("content").isArray()).isTrue();
            JsonNode contentArray = messageNode.get("content");
            assertThat(contentArray).hasSize(2);
            // 第一个块为文本
            assertThat(contentArray.get(0).get("type").asText()).isEqualTo("text");
            assertThat(contentArray.get(0).get("text").asText()).isEqualTo("描述这张图");
            // 第二个块为image_url
            assertThat(contentArray.get(1).get("type").asText()).isEqualTo("image_url");
            assertThat(contentArray.get(1).get("image_url").get("url").asText()).isEqualTo("https://example.com/img.png");
        }

        @Test
        @DisplayName("OpenAI:Base64图像转为data URI格式")
        void openAi_base64Image_shouldConvertToDataUri() throws Exception {
            OpenAIChatProtocolAdapter adapter = new OpenAIChatProtocolAdapter();
            AgentMessage msg = userMessageWithImageBase64("看图", "iVBORw0KGgo=", "image/png");
            String body = adapter.buildRequestBody("gpt-4o", List.of(msg), null, null, null, false);
            JsonNode root = MAPPER.readTree(body);
            JsonNode contentArray = root.get("messages").get(0).get("content");
            JsonNode imageBlock = contentArray.get(1);
            assertThat(imageBlock.get("type").asText()).isEqualTo("image_url");
            String url = imageBlock.get("image_url").get("url").asText();
            assertThat(url).startsWith("data:image/png;base64,");
            assertThat(url).endsWith("iVBORw0KGgo=");
        }

        @Test
        @DisplayName("OpenAI:assistant消息含图像和工具调用,工具走tool_calls,图像和文本进content数组")
        void openAi_assistantWithImageAndToolCalls_mixedContent() throws Exception {
            OpenAIChatProtocolAdapter adapter = new OpenAIChatProtocolAdapter();
            AgentToolUseBlock toolUse = new AgentToolUseBlock("calc", "call_123", new HashMap<>());
            AgentImageBlock image = AgentImageBlock.builder().url("https://example.com/img.png").build();
            AgentTextBlock text = AgentTextBlock.builder().text("分析中").build();
            AgentMessage msg = AgentMessage.builder()
                    .name("assistant")
                    .role(AgentMessageRole.ASSISTANT)
                    .content(List.of(text, image, toolUse))
                    .build();
            String body = adapter.buildRequestBody("gpt-4o", List.of(msg), null, null, null, false);
            JsonNode root = MAPPER.readTree(body);
            JsonNode messageNode = root.get("messages").get(0);
            // content应为数组,包含文本和图像
            assertThat(messageNode.get("content").isArray()).isTrue();
            // tool_calls应存在
            assertThat(messageNode.get("tool_calls").isArray()).isTrue();
            assertThat(messageNode.get("tool_calls").get(0).get("function").get("name").asText()).isEqualTo("calc");
        }

        @Test
        @DisplayName("OpenAI:tool角色消息含图像时,content保持字符串(图像被忽略)")
        void openAi_toolRoleWithImage_contentRemainsString() throws Exception {
            OpenAIChatProtocolAdapter adapter = new OpenAIChatProtocolAdapter();
            AgentImageBlock image = AgentImageBlock.builder().url("https://example.com/img.png").build();
            AgentToolResultBlock result = AgentToolResultBlock.of("call_123",
                    List.of(AgentTextBlock.builder().text("结果").build()));
            AgentMessage msg = AgentMessage.builder()
                    .name("tool")
                    .role(AgentMessageRole.TOOL)
                    .content(List.of(result, image))
                    .build();
            String body = adapter.buildRequestBody("gpt-4o", List.of(msg), null, null, null, false);
            JsonNode root = MAPPER.readTree(body);
            JsonNode messageNode = root.get("messages").get(0);
            // tool角色不支持content数组,应为字符串
            assertThat(messageNode.get("content").isTextual()).isTrue();
            assertThat(messageNode.get("role").asText()).isEqualTo("tool");
        }

        @Test
        @DisplayName("Anthropic:URL图像输出source.type=url")
        void anthropic_urlImage_sourceTypeShouldBeUrl() throws Exception {
            AnthropicChatProtocolAdapter adapter = new AnthropicChatProtocolAdapter();
            AgentMessage msg = userMessageWithImageUrl("分析图", "https://example.com/img.jpg");
            String body = adapter.buildRequestBody("claude-3-5-sonnet", List.of(msg), null, null, null, false);
            JsonNode root = MAPPER.readTree(body);
            JsonNode contentArray = root.get("messages").get(0).get("content");
            assertThat(contentArray.isArray()).isTrue();
            // 找到image块
            JsonNode imageBlock = null;
            for (JsonNode block : contentArray) {
                if ("image".equals(block.get("type").asText())) {
                    imageBlock = block;
                    break;
                }
            }
            assertThat(imageBlock).isNotNull();
            assertThat(imageBlock.get("source").get("type").asText()).isEqualTo("url");
            assertThat(imageBlock.get("source").get("url").asText()).isEqualTo("https://example.com/img.jpg");
        }

        @Test
        @DisplayName("Anthropic:Base64图像输出source.type=base64")
        void anthropic_base64Image_sourceTypeShouldBeBase64() throws Exception {
            AnthropicChatProtocolAdapter adapter = new AnthropicChatProtocolAdapter();
            AgentMessage msg = userMessageWithImageBase64("看图", "iVBORw0KGgo=", "image/png");
            String body = adapter.buildRequestBody("claude-3-5-sonnet", List.of(msg), null, null, null, false);
            JsonNode root = MAPPER.readTree(body);
            JsonNode contentArray = root.get("messages").get(0).get("content");
            JsonNode imageBlock = null;
            for (JsonNode block : contentArray) {
                if ("image".equals(block.get("type").asText())) {
                    imageBlock = block;
                    break;
                }
            }
            assertThat(imageBlock).isNotNull();
            assertThat(imageBlock.get("source").get("type").asText()).isEqualTo("base64");
            assertThat(imageBlock.get("source").get("media_type").asText()).isEqualTo("image/png");
            assertThat(imageBlock.get("source").get("data").asText()).isEqualTo("iVBORw0KGgo=");
        }

        @Test
        @DisplayName("Ollama:Base64图像提取到顶层images数组")
        void ollama_base64Image_shouldBeInTopLevelImagesArray() throws Exception {
            OllamaChatProtocolAdapter adapter = new OllamaChatProtocolAdapter();
            AgentMessage msg = userMessageWithImageBase64("描述图", "iVBORw0KGgo=", "image/png");
            String body = adapter.buildRequestBody("llama3.2-vision", List.of(msg), null, null, null, false);
            JsonNode root = MAPPER.readTree(body);
            // images数组应在顶层
            assertThat(root.has("images")).isTrue();
            assertThat(root.get("images").isArray()).isTrue();
            assertThat(root.get("images").get(0).asText()).isEqualTo("iVBORw0KGgo=");
            // content应保持纯文本
            JsonNode messageNode = root.get("messages").get(0);
            assertThat(messageNode.get("content").isTextual()).isTrue();
            assertThat(messageNode.get("content").asText()).isEqualTo("描述图");
        }

        @Test
        @DisplayName("Ollama:URL图像被跳过(不支持URL模式)")
        void ollama_urlImage_shouldBeSkipped() throws Exception {
            OllamaChatProtocolAdapter adapter = new OllamaChatProtocolAdapter();
            AgentMessage msg = userMessageWithImageUrl("描述图", "https://example.com/img.png");
            String body = adapter.buildRequestBody("llama3.2-vision", List.of(msg), null, null, null, false);
            JsonNode root = MAPPER.readTree(body);
            // URL图像不支持,images字段不应存在
            assertThat(root.has("images")).isFalse();
            // content仍为文本
            JsonNode messageNode = root.get("messages").get(0);
            assertThat(messageNode.get("content").asText()).isEqualTo("描述图");
        }

        @Test
        @DisplayName("Ollama:多个图像合并到images数组")
        void ollama_multipleImages_shouldBeMerged() throws Exception {
            OllamaChatProtocolAdapter adapter = new OllamaChatProtocolAdapter();
            AgentMessage msg1 = userMessageWithImageBase64("图1", "base64data1", "image/png");
            AgentMessage msg2 = userMessageWithImageBase64("图2", "base64data2", "image/jpeg");
            String body = adapter.buildRequestBody("llama3.2-vision", List.of(msg1, msg2), null, null, null, false);
            JsonNode root = MAPPER.readTree(body);
            assertThat(root.get("images").isArray()).isTrue();
            assertThat(root.get("images")).hasSize(2);
            assertThat(root.get("images").get(0).asText()).isEqualTo("base64data1");
            assertThat(root.get("images").get(1).asText()).isEqualTo("base64data2");
        }

        @Test
        @DisplayName("DashScope:复用OpenAI转换,有图像时content为数组")
        void dashScope_imageContent_shouldBeArray() throws Exception {
            DashScopeChatProtocolAdapter adapter = new DashScopeChatProtocolAdapter();
            AgentMessage msg = userMessageWithImageUrl("描述图", "https://example.com/img.png");
            String body = adapter.buildRequestBody("qwen-vl-max", List.of(msg), null, null, null, false);
            JsonNode root = MAPPER.readTree(body);
            // DashScope采用input.messages嵌套结构
            JsonNode contentArray = root.get("input").get("messages").get(0).get("content");
            assertThat(contentArray.isArray()).isTrue();
            // 找到image_url块
            JsonNode imageBlock = null;
            for (JsonNode block : contentArray) {
                if ("image_url".equals(block.get("type").asText())) {
                    imageBlock = block;
                    break;
                }
            }
            assertThat(imageBlock).isNotNull();
            assertThat(imageBlock.get("image_url").get("url").asText()).isEqualTo("https://example.com/img.png");
        }
    }

    /**
     * Gemini协议适配器测试
     */
    @Nested
    @DisplayName("GeminiChatProtocolAdapter")
    class GeminiAdapterTest {

        @Test
        @DisplayName("透传thinkingBudget与reasoningEffort")
        void passthrough_thinkingAndReasoning() throws Exception {
            GeminiChatProtocolAdapter adapter = new GeminiChatProtocolAdapter();
            AgentGenerateOptions options = AgentGenerateOptions.builder()
                    .thinkingBudget(16384)
                    .reasoningEffort("medium")
                    .extraParameters(Map.of("safetySettings",
                            Map.of("category", "HARM_CATEGORY_HARASSMENT", "threshold", "BLOCK_NONE")))
                    .build();
            String body = adapter.buildRequestBody("gemini-2.5-flash",
                    List.of(userMessage("帮我写一个冒泡排序")), null, options, null, false);
            JsonNode root = parse(body);
            // 思考预算透传为Gemini原生thinkingConfig结构
            assertThat(root.has("thinkingConfig")).isTrue();
            assertThat(root.get("thinkingConfig").get("thinkingBudget").asInt()).isEqualTo(16384);
            // 推理努力等级透传
            assertThat(root.get("reasoning_effort").asText()).isEqualTo("medium");
            // 厂商专属参数经extraParameters透传
            assertThat(root.get("safetySettings").get("category").asText()).isEqualTo("HARM_CATEGORY_HARASSMENT");
        }

        @Test
        @DisplayName("未设置时不注入thinkingConfig与reasoning_effort")
        void noThinkingOption_shouldNotInject() throws Exception {
            GeminiChatProtocolAdapter adapter = new GeminiChatProtocolAdapter();
            String body = adapter.buildRequestBody("gemini-2.5-flash",
                    List.of(userMessage("你好")), null, null, null, false);
            JsonNode root = parse(body);
            assertThat(root.has("thinkingConfig")).isFalse();
            assertThat(root.has("reasoning_effort")).isFalse();
        }
    }

    /**
     * 将JSON字符串解析为JsonNode
     * @param json
     * @return
     */
    private JsonNode parse(String json) {
        try {
            return MAPPER.readTree(json);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
