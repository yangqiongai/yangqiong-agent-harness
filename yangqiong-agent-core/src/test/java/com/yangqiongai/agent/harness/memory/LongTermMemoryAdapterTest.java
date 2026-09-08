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
package com.yangqiongai.agent.harness.memory;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import com.yangqiongai.agent.harness.engine.AgentRuntimeContext;
import com.yangqiongai.agent.harness.core.memory.AgentLongTermMemory;
import com.yangqiongai.agent.harness.core.message.AgentMessage;
import com.yangqiongai.agent.harness.core.message.AgentMessageRole;
import com.yangqiongai.agent.harness.core.message.AgentTextBlock;
import com.yangqiongai.agent.harness.core.tool.AgentTool;
import com.yangqiongai.agent.harness.core.tool.AgentToolCallParam;
import org.junit.jupiter.api.Test;

import reactor.core.publisher.Mono;

/**
 * 长期记忆适配器测试
 * @author yangqiong
 */
class LongTermMemoryAdapterTest {

    @Test
    void shouldReturnEmptyToolsWhenMemoryNull() {
        LongTermMemoryAdapter adapter = new LongTermMemoryAdapter(null);
        assertThat(adapter.getMemoryTools()).isEmpty();
    }

    @Test
    void shouldReturnThreeToolsWhenMemoryProvided() {
        AgentLongTermMemory memory = new InMemoryLongTermMemory();
        LongTermMemoryAdapter adapter = new LongTermMemoryAdapter(memory);
        List<AgentTool> tools = adapter.getMemoryTools();
        assertThat(tools).hasSize(3);
        assertThat(tools.get(0).getName()).isEqualTo("memory_search");
        assertThat(tools.get(1).getName()).isEqualTo("memory_store");
        assertThat(tools.get(2).getName()).isEqualTo("memory_delete");
    }

    @Test
    void shouldReturnEmptyToolsWhenToolsDisabled() {
        AgentLongTermMemory memory = new InMemoryLongTermMemory();
        LongTermMemoryAdapter adapter = new LongTermMemoryAdapter(memory, true, false);
        assertThat(adapter.getMemoryTools()).isEmpty();
    }

    @Test
    void shouldAutoStoreAssistantMessage() {
        InMemoryLongTermMemory memory = new InMemoryLongTermMemory();
        LongTermMemoryAdapter adapter = new LongTermMemoryAdapter(memory, true, false);
        AgentRuntimeContext ctx = AgentRuntimeContext.builder()
                .userId("u1")
                .sessionId("s1")
                .build();
        AgentMessage msg = AgentMessage.builder()
                .role(AgentMessageRole.ASSISTANT)
                .content(Collections.singletonList(AgentTextBlock.builder().text("hello").build()))
                .build();
        adapter.onMessage(msg, ctx);
        assertThat(memory.storeCount.get()).isEqualTo(1);
    }

    @Test
    void shouldNotStoreUserMessage() {
        InMemoryLongTermMemory memory = new InMemoryLongTermMemory();
        LongTermMemoryAdapter adapter = new LongTermMemoryAdapter(memory, true, false);
        AgentMessage msg = AgentMessage.builder()
                .role(AgentMessageRole.USER)
                .content(Collections.singletonList(AgentTextBlock.builder().text("hello").build()))
                .build();
        adapter.onMessage(msg, AgentRuntimeContext.empty());
        assertThat(memory.storeCount.get()).isZero();
    }

    @Test
    void shouldNotStoreWhenHooksDisabled() {
        InMemoryLongTermMemory memory = new InMemoryLongTermMemory();
        LongTermMemoryAdapter adapter = new LongTermMemoryAdapter(memory, false, true);
        AgentMessage msg = AgentMessage.builder()
                .role(AgentMessageRole.ASSISTANT)
                .content(Collections.singletonList(AgentTextBlock.builder().text("hello").build()))
                .build();
        adapter.onMessage(msg, AgentRuntimeContext.empty());
        assertThat(memory.storeCount.get()).isZero();
    }

    @Test
    void shouldNotStoreEmptyMessage() {
        InMemoryLongTermMemory memory = new InMemoryLongTermMemory();
        LongTermMemoryAdapter adapter = new LongTermMemoryAdapter(memory);
        AgentMessage msg = AgentMessage.builder()
                .role(AgentMessageRole.ASSISTANT)
                .content(Collections.emptyList())
                .build();
        adapter.onMessage(msg, AgentRuntimeContext.empty());
        assertThat(memory.storeCount.get()).isZero();
    }

    @Test
    void shouldHandleStoreException() {
        AgentLongTermMemory memory = new AgentLongTermMemory() {
            @Override
            public void store(String userId, String sessionId, String content, Map<String, Object> metadata) {
                throw new RuntimeException("store error");
            }
            @Override
            public List<String> search(String userId, String query, int limit) {
                return Collections.emptyList();
            }
            @Override
            public void delete(String memoryId) {
            }
        };
        LongTermMemoryAdapter adapter = new LongTermMemoryAdapter(memory);
        AgentMessage msg = AgentMessage.builder()
                .role(AgentMessageRole.ASSISTANT)
                .content(Collections.singletonList(AgentTextBlock.builder().text("hello").build()))
                .build();
        AgentMessage result = adapter.onMessage(msg, AgentRuntimeContext.empty());
        assertThat(result).isSameAs(msg);
    }

    @Test
    void shouldExecuteSearchTool() {
        InMemoryLongTermMemory memory = new InMemoryLongTermMemory();
        memory.items.add("记忆1");
        LongTermMemoryAdapter adapter = new LongTermMemoryAdapter(memory);
        AgentTool searchTool = adapter.getMemoryTools().get(0);
        AgentToolCallParam param = new AgentToolCallParam(Map.of("query", "test", "limit", 5));
        Mono.from(searchTool.callAsync(param)).block();
        assertThat(memory.searchCount.get()).isEqualTo(1);
    }

    @Test
    void shouldExecuteStoreTool() {
        InMemoryLongTermMemory memory = new InMemoryLongTermMemory();
        LongTermMemoryAdapter adapter = new LongTermMemoryAdapter(memory);
        AgentTool storeTool = adapter.getMemoryTools().get(1);
        AgentToolCallParam param = new AgentToolCallParam(Map.of("content", "new memory"));
        Mono.from(storeTool.callAsync(param)).block();
        assertThat(memory.storeCount.get()).isEqualTo(1);
    }

    @Test
    void shouldExecuteDeleteTool() {
        InMemoryLongTermMemory memory = new InMemoryLongTermMemory();
        LongTermMemoryAdapter adapter = new LongTermMemoryAdapter(memory);
        AgentTool deleteTool = adapter.getMemoryTools().get(2);
        AgentToolCallParam param = new AgentToolCallParam(Map.of("memoryId", "m1"));
        Mono.from(deleteTool.callAsync(param)).block();
        assertThat(memory.deleteCount.get()).isEqualTo(1);
    }

    @Test
    void shouldHandleNullParamInSearchTool() {
        InMemoryLongTermMemory memory = new InMemoryLongTermMemory();
        LongTermMemoryAdapter adapter = new LongTermMemoryAdapter(memory);
        AgentTool searchTool = adapter.getMemoryTools().get(0);
        Mono.from(searchTool.callAsync(null)).block();
        assertThat(memory.searchCount.get()).isEqualTo(1);
    }

    @Test
    void record_正常存储记忆() {
        InMemoryLongTermMemory memory = new InMemoryLongTermMemory();
        LongTermMemoryAdapter adapter = new LongTermMemoryAdapter(memory);
        Map<String, Object> metadata = Map.of("key", "value");
        Void result = adapter.record("记忆内容", metadata).block();
        assertThat(result).isNull();
        assertThat(memory.storeCount.get()).isEqualTo(1);
        assertThat(memory.items).contains("记忆内容");
    }

    @Test
    void record_空内容不存储() {
        InMemoryLongTermMemory memory = new InMemoryLongTermMemory();
        LongTermMemoryAdapter adapter = new LongTermMemoryAdapter(memory);
        Void result = adapter.record("", Map.of()).block();
        assertThat(result).isNull();
        assertThat(memory.storeCount.get()).isZero();
    }

    @Test
    void record_异常时返回empty() {
        AgentLongTermMemory memory = new AgentLongTermMemory() {
            @Override
            public void store(String userId, String sessionId, String content, Map<String, Object> metadata) {
                throw new RuntimeException("store error");
            }
            @Override
            public List<String> search(String userId, String query, int limit) {
                return Collections.emptyList();
            }
            @Override
            public void delete(String memoryId) {
            }
        };
        LongTermMemoryAdapter adapter = new LongTermMemoryAdapter(memory);
        Void result = adapter.record("记忆内容", null).block();
        assertThat(result).isNull();
    }

    @Test
    void search_正常检索返回结果() {
        InMemoryLongTermMemory memory = new InMemoryLongTermMemory();
        memory.items.add("记忆1");
        memory.items.add("记忆2");
        LongTermMemoryAdapter adapter = new LongTermMemoryAdapter(memory);
        List<String> results = adapter.search("关键词", 5).block();
        assertThat(results).isNotEmpty();
        assertThat(memory.searchCount.get()).isEqualTo(1);
    }

    @Test
    void search_空查询返回空列表() {
        InMemoryLongTermMemory memory = new InMemoryLongTermMemory();
        LongTermMemoryAdapter adapter = new LongTermMemoryAdapter(memory);
        List<String> results = adapter.search("", 5).block();
        assertThat(results).isEmpty();
        assertThat(memory.searchCount.get()).isZero();
    }

    @Test
    void search_异常时返回空列表() {
        AgentLongTermMemory memory = new AgentLongTermMemory() {
            @Override
            public void store(String userId, String sessionId, String content, Map<String, Object> metadata) {
            }
            @Override
            public List<String> search(String userId, String query, int limit) {
                throw new RuntimeException("search error");
            }
            @Override
            public void delete(String memoryId) {
            }
        };
        LongTermMemoryAdapter adapter = new LongTermMemoryAdapter(memory);
        List<String> results = adapter.search("关键词", 5).block();
        assertThat(results).isEmpty();
    }

    /**
     * 内存长期记忆实现
     */
    private static class InMemoryLongTermMemory implements AgentLongTermMemory {
        final AtomicInteger storeCount = new AtomicInteger(0);
        final AtomicInteger searchCount = new AtomicInteger(0);
        final AtomicInteger deleteCount = new AtomicInteger(0);
        final java.util.List<String> items = new java.util.ArrayList<>();

        @Override
        public void store(String userId, String sessionId, String content, Map<String, Object> metadata) {
            storeCount.incrementAndGet();
            if (content != null) {
                items.add(content);
            }
        }

        @Override
        public List<String> search(String userId, String query, int limit) {
            searchCount.incrementAndGet();
            return items.stream().limit(limit).collect(java.util.stream.Collectors.toList());
        }

        @Override
        public void delete(String memoryId) {
            deleteCount.incrementAndGet();
        }
    }
}
