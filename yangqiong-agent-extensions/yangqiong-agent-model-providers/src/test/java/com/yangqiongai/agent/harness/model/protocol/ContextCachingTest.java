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

import static org.assertj.core.api.Assertions.assertThat;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yangqiongai.agent.harness.core.message.AgentMessage;
import com.yangqiongai.agent.harness.core.message.AgentMessageRole;
import com.yangqiongai.agent.harness.core.message.AgentTextBlock;
import com.yangqiongai.agent.harness.core.model.AgentGenerateOptions;

/**
 * 上下文缓存（Anthropic prompt caching）测试
 * <p>
 * 验证开启缓存控制时，Anthropic请求体的system块与工具定义追加cache_control标记，
 * 关闭时不追加，其他协议适配器不受影响。
 * </p>
 * @author yangqiong
 */
class ContextCachingTest {

    /**
     * JSON映射器
     */
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final AnthropicChatProtocolAdapter adapter = new AnthropicChatProtocolAdapter();

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
     * 构建工具定义
     * @param name
     * @param description
     * @return
     */
    private Map<String, Object> tool(String name, String description) {
        Map<String, Object> properties = new LinkedHashMap<>();
        Map<String, Object> fieldSchema = new LinkedHashMap<>();
        fieldSchema.put("type", "string");
        properties.put("arg", fieldSchema);
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("type", "object");
        params.put("properties", properties);
        Map<String, Object> function = new LinkedHashMap<>();
        function.put("name", name);
        function.put("description", description);
        function.put("parameters", params);
        Map<String, Object> tool = new LinkedHashMap<>();
        tool.put("type", "function");
        tool.put("function", function);
        return tool;
    }

    @Test
    @DisplayName("开启缓存控制时system块追加cache_control")
    void buildRequestBody_cacheEnabled_shouldAddCacheControlToSystem() throws Exception {
        AgentGenerateOptions options = AgentGenerateOptions.builder().cacheControlEnabled(true).build();
        String body = adapter.buildRequestBody("claude-3-5-sonnet",
                List.of(systemMessage("你是智能助手"), userMessage("你好")), null, options, null, false);
        JsonNode root = MAPPER.readTree(body);
        JsonNode system = root.get("system");
        assertThat(system.isArray()).isTrue();
        JsonNode block = system.get(0);
        assertThat(block.get("type").asText()).isEqualTo("text");
        assertThat(block.has("text")).isTrue();
        assertThat(block.get("cache_control").get("type").asText()).isEqualTo("ephemeral");
    }

    @Test
    @DisplayName("开启缓存控制时工具定义追加cache_control")
    void buildRequestBody_cacheEnabled_shouldAddCacheControlToTools() throws Exception {
        AgentGenerateOptions options = AgentGenerateOptions.builder().cacheControlEnabled(true).build();
        String body = adapter.buildRequestBody("claude-3-5-sonnet",
                List.of(userMessage("你好")), List.of(tool("calc", "计算器")), options, null, false);
        JsonNode root = MAPPER.readTree(body);
        JsonNode tools = root.get("tools");
        assertThat(tools.isArray()).isTrue();
        assertThat(tools.get(0).get("cache_control").get("type").asText()).isEqualTo("ephemeral");
    }

    @Test
    @DisplayName("开启缓存控制时system和工具均无cache_control")
    void buildRequestBody_cacheDisabled_shouldNotAddCacheControl() throws Exception {
        AgentGenerateOptions options = AgentGenerateOptions.builder().build();
        String body = adapter.buildRequestBody("claude-3-5-sonnet",
                List.of(systemMessage("你是智能助手"), userMessage("你好")),
                List.of(tool("calc", "计算器")), options, null, false);
        JsonNode root = MAPPER.readTree(body);
        assertThat(root.get("system").isTextual()).isTrue();
        assertThat(root.get("system").asText()).isEqualTo("你是智能助手");
        assertThat(root.get("tools").get(0).has("cache_control")).isFalse();
    }

    @Test
    @DisplayName("选项为空时不追加cache_control")
    void buildRequestBody_nullOptions_shouldNotAddCacheControl() throws Exception {
        String body = adapter.buildRequestBody("claude-3-5-sonnet",
                List.of(systemMessage("你是智能助手"), userMessage("你好")),
                List.of(tool("calc", "计算器")), null, null, false);
        JsonNode root = MAPPER.readTree(body);
        assertThat(root.get("system").isTextual()).isTrue();
        assertThat(root.get("tools").get(0).has("cache_control")).isFalse();
    }
}
