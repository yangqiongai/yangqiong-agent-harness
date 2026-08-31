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
package com.yangqiong.agent.harness.serialization;

import java.util.List;
import java.util.Map;

import com.yangqiong.agent.harness.core.message.AgentChatUsage;
import com.yangqiong.agent.harness.core.message.AgentContentBlock;
import com.yangqiong.agent.harness.core.message.AgentImageBlock;
import com.yangqiong.agent.harness.core.message.AgentMessage;
import com.yangqiong.agent.harness.core.message.AgentMessageRole;
import com.yangqiong.agent.harness.core.message.AgentTextBlock;
import com.yangqiong.agent.harness.core.message.AgentThinkingBlock;
import com.yangqiong.agent.harness.core.message.AgentToolResultBlock;
import com.yangqiong.agent.harness.core.message.AgentToolUseBlock;
import com.yangqiong.agent.harness.durable.AgentCheckpoint;
import com.yangqiong.agent.harness.durable.serialization.AgentCheckpointSerializer;
import com.yangqiong.agent.harness.durable.serialization.AgentContentBlockSerializer;
import com.yangqiong.agent.harness.durable.serialization.AgentMessageSerializer;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 序列化基础设施 JSON 往返一致
 * @author yangqiong
 */
public class SerializationRoundTripTest {

    /**
     * 全部内容块类型往返一致且保留多态类型信息
     */
    @Test
    void contentBlock_全部类型往返一致() {
        List<AgentContentBlock> blocks = List.of(
                AgentTextBlock.builder().text("你好，世界").build(),
                AgentThinkingBlock.builder().thinking("让我想想").build(),
                AgentImageBlock.builder().url("https://example.com/a.png").mediaType("image/png").build(),
                AgentImageBlock.builder().base64Data("aGVsbG8=").mediaType("image/png").build(),
                new AgentToolUseBlock("search", "call-1", Map.of("query", "分布式", "page", 2)),
                AgentToolResultBlock.of("call-1", List.of(AgentTextBlock.builder().text("搜索结果").build())),
                AgentToolResultBlock.error("call-2", "调用失败"),
                AgentToolResultBlock.clarification("请确认您的意图?"));

        String json = AgentContentBlockSerializer.listToJson(blocks);
        assertThat(json).contains("\"type\":\"textBlock\"")
                .contains("\"type\":\"thinkingBlock\"")
                .contains("\"type\":\"imageBlock\"")
                .contains("\"type\":\"toolUseBlock\"")
                .contains("\"type\":\"toolResultBlock\"");

        List<AgentContentBlock> restored = AgentContentBlockSerializer.listFromJson(json);
        assertThat(restored).hasSize(blocks.size());
        for (int i = 0; i < blocks.size(); i++) {
            assertThat(restored.get(i)).isEqualTo(blocks.get(i));
        }
    }

    /**
     * 文本块中文往返无损且不含未知字段
     */
    @Test
    void textBlock_中文往返无损() {
        AgentTextBlock block = AgentTextBlock.builder().text("中文内容：序列化、持久化、分布式").build();
        String json = AgentContentBlockSerializer.toJson(block);
        AgentContentBlock restored = AgentContentBlockSerializer.fromJson(json);
        assertThat(restored).isEqualTo(block);
    }

    /**
     * 消息往返一致，包含全部内容块与Token用量
     */
    @Test
    void message_完整消息往返一致() {
        AgentMessage message = AgentMessage.builder()
                .name("assistant")
                .role(AgentMessageRole.ASSISTANT)
                .chatUsage(new AgentChatUsage(10, 20, 30))
                .latency(123L)
                .content(List.of(
                        AgentThinkingBlock.builder().thinking("分析中").build(),
                        new AgentToolUseBlock("fetch", "call-9", Map.of("url", "https://example.com")),
                        AgentTextBlock.builder().text("结果如下").build()))
                .build();

        String json = AgentMessageSerializer.toJson(message);
        AgentMessage restored = AgentMessageSerializer.fromJson(json);

        assertThat(restored).isEqualTo(message);
        assertThat(restored.getChatUsage()).isEqualTo(new AgentChatUsage(10, 20, 30));
        assertThat(restored.getLatency()).isEqualTo(123L);
        assertThat(restored.getContent()).hasSize(3);
        assertThat(restored.getTextContent()).isEqualTo("结果如下");
    }

    /**
     * 空内容块列表与空消息列表往返后保持空
     */
    @Test
    void message_空列表往返保持空() {
        List<AgentMessage> messages = List.of(
                AgentMessage.builder().role(AgentMessageRole.USER)
                        .content(List.of()).build(),
                AgentMessage.builder().role(AgentMessageRole.ASSISTANT)
                        .content(List.of()).chatUsage(null).build());

        String json = AgentMessageSerializer.listToJson(messages);
        List<AgentMessage> restored = AgentMessageSerializer.listFromJson(json);

        assertThat(restored).hasSize(2);
        assertThat(restored.get(0).getContent()).isEmpty();
        assertThat(restored.get(1).getContent()).isEmpty();
        assertThat(restored.get(1).getChatUsage()).isNull();
        assertThat(AgentMessageSerializer.listFromJson("[]")).isEmpty();
    }

    /**
     * 未知字段被忽略，不破坏既有结构反序列化
     */
    @Test
    void message_未知字段被忽略() {
        String json = "{\"name\":\"user\",\"role\":\"USER\",\"content\":[],"
                + "\"unknownField\":\"x\",\"content2\":[1,2,3]}";
        AgentMessage restored = AgentMessageSerializer.fromJson(json);
        assertThat(restored.getRole()).isEqualTo(AgentMessageRole.USER);
        assertThat(restored.getContent()).isEmpty();
    }

    /**
     * 检查点往返一致，包含消息、待执行工具与已完成工具ID
     */
    @Test
    void checkpoint_完整检查点往返一致() {
        AgentCheckpoint checkpoint = new AgentCheckpoint(
                "run-100", "tenant-a", "session-1", 5,
                List.of(AgentMessage.builder().role(AgentMessageRole.USER)
                        .content(List.of(AgentTextBlock.builder().text("继续执行").build())).build()),
                List.of(new AgentToolUseBlock("file", "call-7", Map.of("path", "/tmp/a.txt"))),
                List.of("call-1", "call-2"),
                1700000000000L, 3L);

        String json = AgentCheckpointSerializer.toJson(checkpoint);
        AgentCheckpoint restored = AgentCheckpointSerializer.fromJson(json);

        assertThat(restored.getRunId()).isEqualTo("run-100");
        assertThat(restored.getScopeId()).isEqualTo("tenant-a");
        assertThat(restored.getSessionId()).isEqualTo("session-1");
        assertThat(restored.getIteration()).isEqualTo(5);
        assertThat(restored.getTimestamp()).isEqualTo(1700000000000L);
        assertThat(restored.getVersion()).isEqualTo(3L);
        assertThat(restored.getMessages()).hasSize(1);
        assertThat(restored.getMessages().get(0).getContent().get(0)).isEqualTo(
                AgentTextBlock.builder().text("继续执行").build());
        assertThat(restored.getPendingToolCalls()).containsExactly(
                new AgentToolUseBlock("file", "call-7", Map.of("path", "/tmp/a.txt")));
        assertThat(restored.getCompletedToolUseIds()).containsExactly("call-1", "call-2");
    }

    /**
     * 检查点空字段往返后保持空列表
     */
    @Test
    void checkpoint_空字段往返保持空() {
        AgentCheckpoint checkpoint = new AgentCheckpoint(
                "run-200", "tenant-b", "session-2", 0, null, null, null, 100L, 1L);

        AgentCheckpoint restored = AgentCheckpointSerializer.fromJson(AgentCheckpointSerializer.toJson(checkpoint));

        assertThat(restored.getMessages()).isEmpty();
        assertThat(restored.getPendingToolCalls()).isEmpty();
        assertThat(restored.getCompletedToolUseIds()).isEmpty();
    }
}
