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
package com.yangqiongai.agent.harness.rag;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

import com.yangqiongai.agent.harness.core.event.AgentEvent;
import com.yangqiongai.agent.harness.core.message.AgentMessage;
import com.yangqiongai.agent.harness.core.message.AgentMessageRole;
import com.yangqiongai.agent.harness.core.message.AgentTextBlock;
import com.yangqiongai.agent.harness.engine.AgentRuntimeContext;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;

/**
 * RAG检索注入中间件测试
 * @author yangqiong
 */
class RagRetrievalMiddlewareTest {

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

    private RetrieverRegistry registryWithHits() {
        RetrieverRegistry registry = new RetrieverRegistry();
        registry.register("wiki", (query, topK, filters) -> List.of(
                new RetrievedChunk("Java是面向对象的编程语言", 0.95, "wiki", Map.of())));
        return registry;
    }

    @Test
    void injectWhenLastMessageIsUserAndEnabled() {
        RagRetrievalMiddleware middleware =
                new RagRetrievalMiddleware(registryWithHits(), "wiki", 3, true);
        AtomicReference<List<AgentMessage>> captured = new AtomicReference<>();

        middleware.onReasoning(AgentRuntimeContext.empty(),
                List.of(userMessage("Java")), capturingNext(captured));

        List<AgentMessage> result = captured.get();
        assertThat(result).hasSize(2);
        assertThat(result.get(0).getRole()).isEqualTo(AgentMessageRole.SYSTEM);
        assertThat(result.get(0).getTextContent()).contains("相关检索资料");
        assertThat(result.get(0).getTextContent()).contains("<untrusted_content>");
        assertThat(result.get(0).getTextContent()).contains("Java是面向对象的编程语言");
    }

    @Test
    void notInjectWhenLastMessageIsAssistant() {
        RagRetrievalMiddleware middleware =
                new RagRetrievalMiddleware(registryWithHits(), "wiki", 3, true);
        AtomicReference<List<AgentMessage>> captured = new AtomicReference<>();

        middleware.onReasoning(AgentRuntimeContext.empty(),
                List.of(assistantMessage("Java")), capturingNext(captured));

        assertThat(captured.get()).hasSize(1);
    }

    @Test
    void notInjectWhenDisabledByDefault() {
        RagRetrievalMiddleware middleware = new RagRetrievalMiddleware(registryWithHits(), "wiki", 3);
        AtomicReference<List<AgentMessage>> captured = new AtomicReference<>();

        middleware.onReasoning(AgentRuntimeContext.empty(),
                List.of(userMessage("Java")), capturingNext(captured));

        assertThat(captured.get()).hasSize(1);
    }

    @Test
    void notInjectWhenNoHits() {
        RetrieverRegistry registry = new RetrieverRegistry();
        registry.register("wiki", (query, topK, filters) -> List.of());
        RagRetrievalMiddleware middleware = new RagRetrievalMiddleware(registry, "wiki", 3, true);
        AtomicReference<List<AgentMessage>> captured = new AtomicReference<>();

        middleware.onReasoning(AgentRuntimeContext.empty(),
                List.of(userMessage("Java")), capturingNext(captured));

        assertThat(captured.get()).hasSize(1);
    }

    @Test
    void searchFailurePassesThrough() {
        RetrieverRegistry registry = new RetrieverRegistry();
        registry.register("wiki", (query, topK, filters) -> {
            throw new IllegalStateException("检索失败");
        });
        RagRetrievalMiddleware middleware = new RagRetrievalMiddleware(registry, "wiki", 3, true);
        AtomicReference<List<AgentMessage>> captured = new AtomicReference<>();

        middleware.onReasoning(AgentRuntimeContext.empty(),
                List.of(userMessage("Java")), capturingNext(captured));

        assertThat(captured.get()).hasSize(1);
    }

    @Test
    void notInjectWhenSourceUnknown() {
        RagRetrievalMiddleware middleware = new RagRetrievalMiddleware(registryWithHits(), "unknown", 3, true);
        AtomicReference<List<AgentMessage>> captured = new AtomicReference<>();

        middleware.onReasoning(AgentRuntimeContext.empty(),
                List.of(userMessage("Java")), capturingNext(captured));

        assertThat(captured.get()).hasSize(1);
    }

    @Test
    void notInjectWhenInputEmpty() {
        RagRetrievalMiddleware middleware =
                new RagRetrievalMiddleware(registryWithHits(), "wiki", 3, true);
        List<AgentMessage> input = new ArrayList<>();
        AtomicReference<List<AgentMessage>> captured = new AtomicReference<>();

        middleware.onReasoning(AgentRuntimeContext.empty(), input, capturingNext(captured));

        assertThat(captured.get()).isEmpty();
    }
}