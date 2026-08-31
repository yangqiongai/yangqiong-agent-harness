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

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.yangqiong.agent.harness.core.message.AgentContentBlock;
import com.yangqiong.agent.harness.core.message.AgentMessage;
import com.yangqiong.agent.harness.core.message.AgentMessageRole;
import com.yangqiong.agent.harness.core.message.AgentTextBlock;
import com.yangqiong.agent.harness.core.model.AgentChatResponse;

/**
 * 内存模型缓存测试
 * @author yangqiong
 */
class InMemoryModelCacheTest {

    @Test
    void shouldMissOnFirstQuery() {
        InMemoryModelCache cache = new InMemoryModelCache();
        List<AgentChatResponse> result = cache.get(
                List.of(createMessage("hello")), null, null);
        assertThat(result).isNull();
    }

    @Test
    void shouldHitAfterPut() {
        InMemoryModelCache cache = new InMemoryModelCache();
        List<AgentMessage> messages = List.of(createMessage("hello"));
        List<AgentChatResponse> responses = List.of(createResponse("world"));
        cache.put(messages, null, null, responses);
        List<AgentChatResponse> cached = cache.get(messages, null, null);
        assertThat(cached).isNotNull();
        assertThat(cached).hasSize(1);
    }

    @Test
    void shouldClearCache() {
        InMemoryModelCache cache = new InMemoryModelCache();
        cache.put(List.of(createMessage("test")), null, null, List.of(createResponse("resp")));
        assertThat(cache.size()).isEqualTo(1);
        cache.clear();
        assertThat(cache.size()).isZero();
    }

    @Test
    void shouldReturnCopyNotReference() {
        InMemoryModelCache cache = new InMemoryModelCache();
        List<AgentMessage> messages = List.of(createMessage("hello"));
        cache.put(messages, null, null, List.of(createResponse("world")));
        List<AgentChatResponse> first = cache.get(messages, null, null);
        List<AgentChatResponse> second = cache.get(messages, null, null);
        assertThat(first).isNotSameAs(second);
    }

    @Test
    void shouldDifferentiateByMessages() {
        InMemoryModelCache cache = new InMemoryModelCache();
        cache.put(List.of(createMessage("hello")), null, null, List.of(createResponse("world")));
        List<AgentChatResponse> result = cache.get(
                List.of(createMessage("different")), null, null);
        assertThat(result).isNull();
    }

    @Test
    void shouldDifferentiateByToolSchemas() {
        InMemoryModelCache cache = new InMemoryModelCache();
        List<AgentMessage> messages = List.of(createMessage("hello"));
        cache.put(messages, List.of(Map.of("name", "tool1")), null,
                List.of(createResponse("with-tool1")));
        List<AgentChatResponse> result = cache.get(messages, List.of(Map.of("name", "tool2")), null);
        assertThat(result).isNull();
    }

    private AgentMessage createMessage(String text) {
        return AgentMessage.builder()
                .role(AgentMessageRole.USER)
                .content(List.of(AgentTextBlock.builder().text(text).build()))
                .build();
    }

    private AgentChatResponse createResponse(String text) {
        List<AgentContentBlock> blocks = List.of(AgentTextBlock.builder().text(text).build());
        return new AgentChatResponse(blocks, null);
    }
}
