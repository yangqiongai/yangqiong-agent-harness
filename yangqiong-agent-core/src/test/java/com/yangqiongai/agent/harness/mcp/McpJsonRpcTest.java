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
package com.yangqiongai.agent.harness.mcp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/**
 * MCP JSON-RPC报文构建与解析测试
 * @author yangqiong
 */
class McpJsonRpcTest {

    @Test
    void buildRequestContainsIdAndMethod() {
        JsonNode node = McpJsonRpc.request(7, "tools/list", null);
        assertThat(node.get("jsonrpc").asText()).isEqualTo("2.0");
        assertThat(node.get("id").asLong()).isEqualTo(7);
        assertThat(node.get("method").asText()).isEqualTo("tools/list");
        assertThat(node.has("params")).isFalse();
    }

    @Test
    void buildNotificationHasNoId() {
        JsonNode node = McpJsonRpc.notification("notifications/initialized");
        assertThat(node.has("id")).isFalse();
        assertThat(node.get("method").asText()).isEqualTo("notifications/initialized");
    }

    @Test
    void isResponseRecognizesResultAndError() throws Exception {
        JsonNode result = McpJsonRpc.mapper().readTree("{\"jsonrpc\":\"2.0\",\"id\":1,\"result\":{}}");
        JsonNode error = McpJsonRpc.mapper().readTree("{\"jsonrpc\":\"2.0\",\"id\":1,\"error\":{}}");
        JsonNode request = McpJsonRpc.mapper().readTree("{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"x\"}");
        assertThat(McpJsonRpc.isResponse(result)).isTrue();
        assertThat(McpJsonRpc.isResponse(error)).isTrue();
        assertThat(McpJsonRpc.isResponse(request)).isFalse();
    }

    @Test
    void extractResultThrowsRpcExceptionOnError() throws Exception {
        JsonNode error = McpJsonRpc.mapper().readTree(
                "{\"jsonrpc\":\"2.0\",\"id\":1,\"error\":{\"code\":-32000,\"message\":\"工具不存在\"}}");
        assertThatThrownBy(() -> McpJsonRpc.extractResult(error, "tools/call"))
                .isInstanceOf(McpRpcException.class)
                .hasMessageContaining("工具不存在");
    }

    @Test
    void sseAccumulatorParsesMultiLineDataEvent() {
        McpJsonRpc.SseEventAccumulator accumulator = new McpJsonRpc.SseEventAccumulator();
        assertThat(accumulator.feed("event: message")).isEmpty();
        assertThat(accumulator.feed("data: {\"jsonrpc\":\"2.0\",").isPresent()).isFalse();
        assertThat(accumulator.feed("data: \"id\":1,\"result\":{}}")).isEmpty();
        Optional<McpJsonRpc.SseEvent> event = accumulator.feed("");
        assertThat(event).isPresent();
        assertThat(event.get().event()).isEqualTo("message");
        assertThat(event.get().parseJson()).isPresent();
        assertThat(event.get().parseJson().get().get("id").asInt()).isEqualTo(1);
    }

    @Test
    void sseAccumulatorIgnoresCommentAndKeepsEndpointRawData() {
        McpJsonRpc.SseEventAccumulator accumulator = new McpJsonRpc.SseEventAccumulator();
        assertThat(accumulator.feed(": keep-alive")).isEmpty();
        assertThat(accumulator.feed("event: endpoint")).isEmpty();
        Optional<McpJsonRpc.SseEvent> event = accumulator.feed("data: /message?sessionId=abc\r");
        assertThat(event).isEmpty();
        event = accumulator.feed("");
        assertThat(event).isPresent();
        assertThat(event.get().event()).isEqualTo("endpoint");
        assertThat(event.get().data()).isEqualTo("/message?sessionId=abc");
        assertThat(event.get().parseJson()).isEmpty();
    }
}
