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
package com.yangqiongai.agent.harness.orchestration;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import com.yangqiongai.agent.harness.subagent.orchestration.HandoffContextFilter;
import org.junit.jupiter.api.Test;

import com.yangqiongai.agent.harness.core.message.AgentMessage;
import com.yangqiongai.agent.harness.core.message.AgentMessageRole;
import com.yangqiongai.agent.harness.core.message.AgentTextBlock;

/**
 * Handoff上下文过滤器测试
 * @author yangqiong
 */
class HandoffContextFilterTest {

    private final HandoffContextFilter filter = new HandoffContextFilter.DefaultHandoffContextFilter();

    @Test
    void shouldKeepContextAndLastUserMessage() {
        List<AgentMessage> history = List.of(
                userMessage("第一条用户消息"),
                assistantMessage("助手回复"),
                userMessage("第二条用户消息"),
                assistantMessage("助手回复2"),
                userMessage("最后一条用户消息")
        );
        List<AgentMessage> result = filter.filter(history, "移交上下文说明");
        assertThat(result).hasSize(2);
        // 第一条应为context
        assertThat(result.get(0).getTextContent()).isEqualTo("移交上下文说明");
        assertThat(result.get(0).getRole()).isEqualTo(AgentMessageRole.USER);
        // 第二条应为最后一条USER消息
        assertThat(result.get(1).getTextContent()).isEqualTo("最后一条用户消息");
        assertThat(result.get(1).getRole()).isEqualTo(AgentMessageRole.USER);
    }

    @Test
    void shouldKeepOnlyContextWhenNoUserInHistory() {
        List<AgentMessage> history = List.of(
                assistantMessage("只有助手回复"),
                assistantMessage("没有用户消息")
        );
        List<AgentMessage> result = filter.filter(history, "移交上下文");
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getTextContent()).isEqualTo("移交上下文");
    }

    @Test
    void shouldKeepOnlyLastUserWhenContextIsEmpty() {
        List<AgentMessage> history = List.of(
                userMessage("第一条用户消息"),
                assistantMessage("助手回复"),
                userMessage("最后一条用户消息")
        );
        List<AgentMessage> result = filter.filter(history, null);
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getTextContent()).isEqualTo("最后一条用户消息");
    }

    @Test
    void shouldReturnEmptyWhenAllEmpty() {
        List<AgentMessage> result = filter.filter(null, null);
        assertThat(result).isEmpty();
    }

    @Test
    void shouldKeepOnlyContextWhenContextIsBlankAndNoUser() {
        List<AgentMessage> history = List.of(
                assistantMessage("助手回复")
        );
        List<AgentMessage> result = filter.filter(history, "   ");
        assertThat(result).isEmpty();
    }

    private static AgentMessage userMessage(String text) {
        return AgentMessage.builder()
                .role(AgentMessageRole.USER)
                .content(List.of(AgentTextBlock.builder().text(text).build()))
                .build();
    }

    private static AgentMessage assistantMessage(String text) {
        return AgentMessage.builder()
                .role(AgentMessageRole.ASSISTANT)
                .content(List.of(AgentTextBlock.builder().text(text).build()))
                .build();
    }
}