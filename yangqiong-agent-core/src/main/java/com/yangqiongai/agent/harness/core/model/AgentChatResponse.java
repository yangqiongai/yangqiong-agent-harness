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
package com.yangqiongai.agent.harness.core.model;

import java.util.List;
import java.util.Objects;

import com.yangqiongai.agent.harness.core.message.AgentChatUsage;
import com.yangqiongai.agent.harness.core.message.AgentContentBlock;

/**
 * Agent对话响应
 * @author yangqiong
 */
public final class AgentChatResponse {

    /**
     * 内容块列表
     */
    private final List<AgentContentBlock> content;

    /**
     * Token用量统计
     */
    private final AgentChatUsage chatUsage;

    public AgentChatResponse(List<AgentContentBlock> content, AgentChatUsage chatUsage) {
        this.content = content != null ? List.copyOf(content) : List.of();
        this.chatUsage = chatUsage;
    }

    /**
     * 获取内容块列表
     * @return
     */
    public List<AgentContentBlock> getContent() {
        return content;
    }

    /**
     * 获取Token用量统计
     * @return
     */
    public AgentChatUsage getChatUsage() {
        return chatUsage;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        AgentChatResponse that = (AgentChatResponse) o;
        return Objects.equals(content, that.content)
                && Objects.equals(chatUsage, that.chatUsage);
    }

    @Override
    public int hashCode() {
        return Objects.hash(content, chatUsage);
    }

    @Override
    public String toString() {
        return "AgentChatResponse{contentSize=" + (content != null ? content.size() : 0)
                + ", chatUsage=" + chatUsage + "}";
    }
}
