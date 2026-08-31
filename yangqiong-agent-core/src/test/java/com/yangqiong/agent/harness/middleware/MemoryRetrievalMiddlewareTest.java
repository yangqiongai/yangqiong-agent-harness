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
package com.yangqiong.agent.harness.middleware;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

import com.yangqiong.agent.harness.engine.AgentRuntimeContext;
import com.yangqiong.agent.harness.core.event.AgentEvent;
import com.yangqiong.agent.harness.core.memory.AgentLongTermMemory;
import com.yangqiong.agent.harness.core.message.AgentMessage;
import com.yangqiong.agent.harness.core.message.AgentMessageRole;
import com.yangqiong.agent.harness.core.message.AgentTextBlock;
import com.yangqiong.agent.harness.memory.InMemoryLongTermMemory;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;

/**
 * 长期记忆检索注入中间件测试
 * @author yangqiong
 */
class MemoryRetrievalMiddlewareTest {

    private AgentMessage userMessage(String text) {
        return AgentMessage.builder()
                .role(AgentMessageRole.USER)
                .content(Collections.singletonList(AgentTextBlock.builder().text(text).build()))
                .build();
    }

    private AgentMessage assistantMessage(String text) {
        return AgentMessage.builder()
                .role(AgentMessageRole.ASSISTANT)
                .content(Collections.singletonList(AgentTextBlock.builder().text(text).build()))
                .build();
    }

    private Function<List<AgentMessage>, Flux<AgentEvent>> capturingNext(AtomicReference<List<AgentMessage>> captured) {
        return input -> {
            captured.set(input);
            return Flux.empty();
        };
    }

    @Test
    void injectMemoryWhenLastMessageIsUser() {
        InMemoryLongTermMemory memory = new InMemoryLongTermMemory();
        memory.store("u1", "s1", "用户偏好Java开发", Map.of());

        MemoryRetrievalMiddleware middleware = new MemoryRetrievalMiddleware(memory, 3);
        AtomicReference<List<AgentMessage>> captured = new AtomicReference<>();
        AgentRuntimeContext ctx = AgentRuntimeContext.builder().userId("u1").sessionId("s1").build();

        middleware.onReasoning(ctx, List.of(userMessage("Java")), capturingNext(captured));

        List<AgentMessage> result = captured.get();
        assertThat(result).hasSize(2);
        assertThat(result.get(0).getRole()).isEqualTo(AgentMessageRole.SYSTEM);
        assertThat(result.get(0).getTextContent()).contains("相关长期记忆");
        assertThat(result.get(0).getTextContent()).contains("用户偏好Java开发");
    }

    @Test
    void notInjectWhenLastMessageIsAssistant() {
        InMemoryLongTermMemory memory = new InMemoryLongTermMemory();
        memory.store("u1", "s1", "记忆内容", Map.of());

        MemoryRetrievalMiddleware middleware = new MemoryRetrievalMiddleware(memory, 3);
        AtomicReference<List<AgentMessage>> captured = new AtomicReference<>();
        AgentRuntimeContext ctx = AgentRuntimeContext.builder().userId("u1").sessionId("s1").build();

        middleware.onReasoning(ctx, List.of(assistantMessage("Java")), capturingNext(captured));

        // 输入未变化，未被注入
        assertThat(captured.get()).hasSize(1);
    }

    @Test
    void notInjectWhenSearchReturnsEmpty() {
        InMemoryLongTermMemory memory = new InMemoryLongTermMemory();
        memory.store("u1", "s1", "完全无关的内容", Map.of());

        MemoryRetrievalMiddleware middleware = new MemoryRetrievalMiddleware(memory, 3);
        AtomicReference<List<AgentMessage>> captured = new AtomicReference<>();
        AgentRuntimeContext ctx = AgentRuntimeContext.builder().userId("u1").sessionId("s1").build();

        middleware.onReasoning(ctx, List.of(userMessage("Java")), capturingNext(captured));

        assertThat(captured.get()).hasSize(1);
    }

    @Test
    void notInjectWhenDisabled() {
        InMemoryLongTermMemory memory = new InMemoryLongTermMemory();
        memory.store("u1", "s1", "Java记忆", Map.of());

        MemoryRetrievalMiddleware middleware = new MemoryRetrievalMiddleware(memory, 3, false);
        AtomicReference<List<AgentMessage>> captured = new AtomicReference<>();
        AgentRuntimeContext ctx = AgentRuntimeContext.builder().userId("u1").sessionId("s1").build();

        middleware.onReasoning(ctx, List.of(userMessage("Java")), capturingNext(captured));

        assertThat(captured.get()).hasSize(1);
    }

    @Test
    void topKTruncatesResults() {
        InMemoryLongTermMemory memory = new InMemoryLongTermMemory();
        memory.store("u1", "s1", "记忆一关于Java", Map.of());
        memory.store("u1", "s1", "记忆二关于Java", Map.of());
        memory.store("u1", "s1", "记忆三关于Java", Map.of());

        MemoryRetrievalMiddleware middleware = new MemoryRetrievalMiddleware(memory, 2);
        AtomicReference<List<AgentMessage>> captured = new AtomicReference<>();
        AgentRuntimeContext ctx = AgentRuntimeContext.builder().userId("u1").sessionId("s1").build();

        middleware.onReasoning(ctx, List.of(userMessage("Java")), capturingNext(captured));

        String injectedText = captured.get().get(0).getTextContent();
        // 模板前缀 + 最多2条
        long markers = injectedText.lines().filter(l -> l.startsWith("[")).count();
        assertThat(markers).isEqualTo(2);
    }

    @Test
    void notInjectWhenMemoryNull() {
        MemoryRetrievalMiddleware middleware = new MemoryRetrievalMiddleware(null, 3);
        AtomicReference<List<AgentMessage>> captured = new AtomicReference<>();

        middleware.onReasoning(AgentRuntimeContext.empty(), List.of(userMessage("Java")), capturingNext(captured));

        assertThat(captured.get()).hasSize(1);
    }

    @Test
    void searchFailureDoesNotBlockReasoning() {
        // 抛异常的memory实现
        AgentLongTermMemory failing =
                new AgentLongTermMemory() {
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
        MemoryRetrievalMiddleware middleware = new MemoryRetrievalMiddleware(failing, 3);
        AtomicReference<List<AgentMessage>> captured = new AtomicReference<>();
        AgentRuntimeContext ctx = AgentRuntimeContext.builder().userId("u1").sessionId("s1").build();

        middleware.onReasoning(ctx, List.of(userMessage("Java")), capturingNext(captured));

        // 异常被吞掉，输入原样透传
        assertThat(captured.get()).hasSize(1);
    }

    @Test
    void notInjectWhenInputEmpty() {
        InMemoryLongTermMemory memory = new InMemoryLongTermMemory();
        memory.store("u1", "s1", "记忆", Map.of());

        MemoryRetrievalMiddleware middleware = new MemoryRetrievalMiddleware(memory, 3);
        List<AgentMessage> input = new ArrayList<>();
        AtomicReference<List<AgentMessage>> captured = new AtomicReference<>();

        middleware.onReasoning(AgentRuntimeContext.empty(), input, capturingNext(captured));

        assertThat(captured.get()).isEmpty();
    }
}
