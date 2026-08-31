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

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Collections;
import java.util.List;

import com.yangqiong.agent.harness.core.event.AgentEvent;
import com.yangqiong.agent.harness.core.event.AgentTextBlockDeltaEvent;
import com.yangqiong.agent.harness.core.event.AgentThinkingBlockDeltaEvent;
import com.yangqiong.agent.harness.core.event.AgentToolCallDeltaEvent;
import com.yangqiong.agent.harness.core.message.AgentChatUsage;
import com.yangqiong.agent.harness.core.message.AgentContentBlock;
import com.yangqiong.agent.harness.core.message.AgentMessage;
import com.yangqiong.agent.harness.core.message.AgentMessageRole;
import com.yangqiong.agent.harness.core.message.AgentTextBlock;
import com.yangqiong.agent.harness.core.message.AgentThinkingBlock;
import com.yangqiong.agent.harness.core.message.AgentToolUseBlock;
import com.yangqiong.agent.harness.core.model.AgentChatResponse;
import org.junit.jupiter.api.Test;

/**
 * 模型响应解析器测试
 * @author yangqiong
 */
class ModelResponseParserTest {

    private final ModelResponseParser parser = new ModelResponseParser();

    @Test
    void shouldParseToMessageFromTextContent() {
        AgentTextBlock textBlock = AgentTextBlock.builder().text("hello").build();
        AgentChatResponse response = new AgentChatResponse(List.of(textBlock), null);
        AgentMessage msg = parser.parseToMessage(response);
        assertThat(msg.getRole()).isEqualTo(AgentMessageRole.ASSISTANT);
        assertThat(msg.getContent()).hasSize(1);
    }

    @Test
    void shouldReturnEmptyMessageWhenResponseIsNull() {
        AgentMessage msg = parser.parseToMessage(null);
        assertThat(msg.getRole()).isEqualTo(AgentMessageRole.ASSISTANT);
    }

    @Test
    void shouldDetectToolCalls() {
        AgentToolUseBlock toolUse = new AgentToolUseBlock("tool", "id1", Collections.emptyMap());
        AgentChatResponse response = new AgentChatResponse(List.of(toolUse), null);
        assertThat(parser.hasToolCalls(response)).isTrue();
    }

    @Test
    void shouldReturnFalseWhenNoToolCalls() {
        AgentTextBlock textBlock = AgentTextBlock.builder().text("hello").build();
        AgentChatResponse response = new AgentChatResponse(List.of(textBlock), null);
        assertThat(parser.hasToolCalls(response)).isFalse();
    }

    @Test
    void shouldReturnFalseWhenResponseIsNull() {
        assertThat(parser.hasToolCalls(null)).isFalse();
    }

    @Test
    void shouldExtractToolCalls() {
        AgentToolUseBlock toolUse = new AgentToolUseBlock("tool", "id1", Collections.emptyMap());
        AgentTextBlock textBlock = AgentTextBlock.builder().text("text").build();
        AgentChatResponse response = new AgentChatResponse(List.of(textBlock, toolUse), null);
        List<AgentToolUseBlock> toolCalls = parser.extractToolCalls(response);
        assertThat(toolCalls).hasSize(1);
        assertThat(toolCalls.get(0).getToolName()).isEqualTo("tool");
    }

    @Test
    void shouldReturnEmptyListWhenNoToolCalls() {
        AgentTextBlock textBlock = AgentTextBlock.builder().text("hello").build();
        AgentChatResponse response = new AgentChatResponse(List.of(textBlock), null);
        List<AgentToolUseBlock> toolCalls = parser.extractToolCalls(response);
        assertThat(toolCalls).isEmpty();
    }

    @Test
    void shouldReturnEmptyListWhenResponseIsNull() {
        List<AgentToolUseBlock> toolCalls = parser.extractToolCalls(null);
        assertThat(toolCalls).isEmpty();
    }

    @Test
    void shouldParseTextDeltaToEvent() {
        AgentTextBlock textBlock = AgentTextBlock.builder().text("delta").build();
        AgentChatResponse delta = new AgentChatResponse(List.of(textBlock), null);
        AgentEvent event = parser.parseDeltaToEvent(delta, null);
        assertThat(event).isInstanceOf(AgentTextBlockDeltaEvent.class);
        assertThat(((AgentTextBlockDeltaEvent) event).getDelta()).isEqualTo("delta");
    }

    @Test
    void shouldParseThinkingDeltaToEvent() {
        AgentThinkingBlock thinkingBlock = AgentThinkingBlock.builder().thinking("think").build();
        AgentChatResponse delta = new AgentChatResponse(List.of(thinkingBlock), null);
        AgentEvent event = parser.parseDeltaToEvent(delta, null);
        assertThat(event).isInstanceOf(AgentThinkingBlockDeltaEvent.class);
        assertThat(((AgentThinkingBlockDeltaEvent) event).getDelta()).isEqualTo("think");
    }

    @Test
    void shouldParseToolCallDeltaToEvent() {
        AgentToolUseBlock toolUse = new AgentToolUseBlock("tool", "id1", Collections.emptyMap());
        AgentChatResponse delta = new AgentChatResponse(List.of(toolUse), null);
        AgentEvent event = parser.parseDeltaToEvent(delta, null);
        assertThat(event).isInstanceOf(AgentToolCallDeltaEvent.class);
    }

    @Test
    void shouldReturnNullWhenDeltaIsNull() {
        assertThat(parser.parseDeltaToEvent(null, null)).isNull();
    }

    @Test
    void shouldReturnNullWhenContentIsEmpty() {
        AgentChatResponse delta = new AgentChatResponse(Collections.emptyList(), null);
        assertThat(parser.parseDeltaToEvent(delta, null)).isNull();
    }

    @Test
    void shouldReturnNullWhenTextIsEmpty() {
        AgentTextBlock emptyText = AgentTextBlock.builder().text("").build();
        AgentChatResponse delta = new AgentChatResponse(List.of(emptyText), null);
        assertThat(parser.parseDeltaToEvent(delta, null)).isNull();
    }

    @Test
    void shouldParseMessageWithUsage() {
        AgentChatUsage usage = new AgentChatUsage(10, 20, 30);
        AgentTextBlock textBlock = AgentTextBlock.builder().text("hi").build();
        AgentChatResponse response = new AgentChatResponse(List.of(textBlock), usage);
        AgentMessage msg = parser.parseToMessage(response);
        assertThat(msg.getChatUsage()).isEqualTo(usage);
    }

    @Test
    void mergeResponses_正常合并多分片() {
        AgentTextBlock text1 = AgentTextBlock.builder().text("Hello").build();
        AgentTextBlock text2 = AgentTextBlock.builder().text(" World").build();
        AgentChatUsage usage1 = new AgentChatUsage(10, 5, 15);
        AgentChatUsage usage2 = new AgentChatUsage(20, 10, 30);
        AgentChatResponse resp1 = new AgentChatResponse(List.of(text1), usage1);
        AgentChatResponse resp2 = new AgentChatResponse(List.of(text2), usage2);

        AgentChatResponse merged = parser.mergeResponses(List.of(resp1, resp2));

        assertThat(merged.getContent()).hasSize(1);
        assertThat(merged.getContent().get(0)).isInstanceOf(AgentTextBlock.class);
        assertThat(((AgentTextBlock) merged.getContent().get(0)).getText()).isEqualTo("Hello World");
        assertThat(merged.getChatUsage()).isEqualTo(new AgentChatUsage(30, 15, 45));
    }

    @Test
    void mergeResponses_空列表返回空响应() {
        AgentChatResponse merged = parser.mergeResponses(Collections.emptyList());

        assertThat(merged.getContent()).isEmpty();
        assertThat(merged.getChatUsage()).isNull();
    }

    @Test
    void mergeResponses_合并文本块和工具调用块() {
        AgentTextBlock text1 = AgentTextBlock.builder().text("调用工具").build();
        AgentTextBlock text2 = AgentTextBlock.builder().text("完成").build();
        AgentToolUseBlock toolUse = new AgentToolUseBlock("search", "id1", Collections.emptyMap());
        AgentChatResponse resp1 = new AgentChatResponse(List.of(text1, toolUse), null);
        AgentChatResponse resp2 = new AgentChatResponse(List.of(text2), null);

        AgentChatResponse merged = parser.mergeResponses(List.of(resp1, resp2));

        // 文本块应被合并为一个，工具调用块保留
        assertThat(merged.getContent()).hasSize(2);
        assertThat(merged.getContent().get(0)).isInstanceOf(AgentTextBlock.class);
        assertThat(((AgentTextBlock) merged.getContent().get(0)).getText()).isEqualTo("调用工具完成");
        assertThat(merged.getContent().get(1)).isInstanceOf(AgentToolUseBlock.class);
        assertThat(((AgentToolUseBlock) merged.getContent().get(1)).getToolName()).isEqualTo("search");
    }

    @Test
    void mergeUsage_累加tokens() {
        AgentChatUsage usage1 = new AgentChatUsage(10, 20, 30);
        AgentChatUsage usage2 = new AgentChatUsage(5, 15, 20);
        AgentChatUsage usage3 = new AgentChatUsage(100, 200, 300);

        AgentChatUsage merged = parser.mergeUsage(List.of(usage1, usage2, usage3));

        assertThat(merged).isEqualTo(new AgentChatUsage(115, 235, 350));
    }

    @Test
    void mergeUsage_空列表返回零() {
        AgentChatUsage merged = parser.mergeUsage(Collections.emptyList());

        assertThat(merged).isEqualTo(new AgentChatUsage(0, 0, 0));
    }

    @Test
    void mergeTextBlocks_拼接文本() {
        AgentTextBlock block1 = AgentTextBlock.builder().text("流式").build();
        AgentTextBlock block2 = AgentTextBlock.builder().text("响应").build();
        AgentTextBlock block3 = AgentTextBlock.builder().text("聚合").build();

        List<AgentContentBlock> merged = parser.mergeTextBlocks(List.of(block1, block2, block3));

        assertThat(merged).hasSize(1);
        assertThat(merged.get(0)).isInstanceOf(AgentTextBlock.class);
        assertThat(((AgentTextBlock) merged.get(0)).getText()).isEqualTo("流式响应聚合");
    }

    @Test
    void mergeTextBlocks_空列表返回空() {
        List<AgentContentBlock> merged = parser.mergeTextBlocks(Collections.emptyList());

        assertThat(merged).isEmpty();
    }
}
