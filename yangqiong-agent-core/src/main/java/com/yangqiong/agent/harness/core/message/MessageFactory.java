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
package com.yangqiong.agent.harness.core.message;

import java.util.Collections;
import java.util.List;

/**
 * 消息工厂
 * @author yangqiong
 */
public final class MessageFactory {

    private MessageFactory() {
    }

    /**
     * 创建用户消息
     * @param text
     * @return
     */
    public static AgentMessage createUserMessage(String text) {
        AgentTextBlock textBlock = AgentTextBlock.builder().text(text).build();
        return AgentMessage.builder()
                .role(AgentMessageRole.USER)
                .content(Collections.singletonList(textBlock))
                .build();
    }

    /**
     * 创建系统消息
     * @param text
     * @return
     */
    public static AgentMessage createSystemMessage(String text) {
        AgentTextBlock textBlock = AgentTextBlock.builder().text(text).build();
        return AgentMessage.builder()
                .role(AgentMessageRole.SYSTEM)
                .content(Collections.singletonList(textBlock))
                .build();
    }

    /**
     * 创建助手消息
     * @param content
     * @param usage
     * @return
     */
    public static AgentMessage createAssistantMessage(List<AgentContentBlock> content, AgentChatUsage usage) {
        return AgentMessage.builder()
                .role(AgentMessageRole.ASSISTANT)
                .content(content)
                .chatUsage(usage)
                .build();
    }

    /**
     * 创建工具结果消息
     * @param result
     * @return
     */
    public static AgentMessage createToolMessage(AgentToolResultBlock result) {
        return AgentMessage.builder()
                .role(AgentMessageRole.TOOL)
                .content(Collections.singletonList(result))
                .build();
    }
}
