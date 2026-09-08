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
package com.yangqiongai.agent.harness.message;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Collections;

import com.yangqiongai.agent.harness.core.message.*;
import org.junit.jupiter.api.Test;

/**
 * 消息工厂测试
 * @author yangqiong
 */
class MessageFactoryTest {

    @Test
    void shouldCreateUserMessage() {
        AgentMessage msg = MessageFactory.createUserMessage("hello");
        assertThat(msg.getRole()).isEqualTo(AgentMessageRole.USER);
        assertThat(msg.getContent()).hasSize(1);
        assertThat(((AgentTextBlock) msg.getContent().get(0)).getText()).isEqualTo("hello");
    }

    @Test
    void shouldCreateSystemMessage() {
        AgentMessage msg = MessageFactory.createSystemMessage("system prompt");
        assertThat(msg.getRole()).isEqualTo(AgentMessageRole.SYSTEM);
        assertThat(((AgentTextBlock) msg.getContent().get(0)).getText()).isEqualTo("system prompt");
    }

    @Test
    void shouldCreateAssistantMessage() {
        AgentTextBlock textBlock = AgentTextBlock.builder().text("reply").build();
        AgentChatUsage usage = new AgentChatUsage(5, 10, 15);
        AgentMessage msg = MessageFactory.createAssistantMessage(Collections.singletonList(textBlock), usage);
        assertThat(msg.getRole()).isEqualTo(AgentMessageRole.ASSISTANT);
        assertThat(msg.getChatUsage()).isEqualTo(usage);
    }

    @Test
    void shouldCreateToolMessage() {
        AgentToolResultBlock result = AgentToolResultBlock.error("err");
        AgentMessage msg = MessageFactory.createToolMessage(result);
        assertThat(msg.getRole()).isEqualTo(AgentMessageRole.TOOL);
        assertThat(msg.getContent().get(0)).isInstanceOf(AgentToolResultBlock.class);
    }

    @Test
    void shouldCreateUserMessageWithEmptyText() {
        AgentMessage msg = MessageFactory.createUserMessage("");
        assertThat(msg.getRole()).isEqualTo(AgentMessageRole.USER);
        assertThat(((AgentTextBlock) msg.getContent().get(0)).getText()).isEmpty();
    }

    @Test
    void shouldCreateAssistantMessageWithNullUsage() {
        AgentTextBlock textBlock = AgentTextBlock.builder().text("hi").build();
        AgentMessage msg = MessageFactory.createAssistantMessage(Collections.singletonList(textBlock), null);
        assertThat(msg.getRole()).isEqualTo(AgentMessageRole.ASSISTANT);
        assertThat(msg.getChatUsage()).isNull();
    }
}
