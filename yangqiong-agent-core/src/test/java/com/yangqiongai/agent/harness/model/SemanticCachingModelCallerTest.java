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
package com.yangqiongai.agent.harness.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import com.yangqiongai.agent.harness.core.message.AgentContentBlock;
import com.yangqiongai.agent.harness.core.message.AgentMessage;
import com.yangqiongai.agent.harness.core.message.AgentMessageRole;
import com.yangqiongai.agent.harness.core.message.AgentTextBlock;
import com.yangqiongai.agent.harness.core.model.AgentChatResponse;
import com.yangqiongai.agent.harness.core.model.AgentGenerateOptions;
import com.yangqiongai.agent.harness.core.model.AgentModel;
import com.yangqiongai.agent.harness.model.embedding.HashingEmbeddingModel;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;

/**
 * 语义缓存模型调用器测试
 * @author yangqiong
 */
class SemanticCachingModelCallerTest {

    private static final List<Map<String, Object>> NO_TOOLS = Collections.emptyList();

    /**
     * 构建用户消息列表
     * @param text
     * @return
     */
    private List<AgentMessage> userMessage(String text) {
        return Collections.singletonList(
                AgentMessage.builder().role(AgentMessageRole.USER)
                        .content(Collections.singletonList(
                                AgentTextBlock.builder().text(text).build()))
                        .build());
    }

    /**
     * 从响应中提取文本
     * @param response
     * @return
     */
    private String getText(AgentChatResponse response) {
        if (response == null || response.getContent() == null || response.getContent().isEmpty()) {
            return "";
        }
        AgentContentBlock block = response.getContent().get(0);
        return block instanceof AgentTextBlock textBlock ? textBlock.getText() : "";
    }

    @Test
    void hitReturnsCachedResponse() {
        AtomicInteger calls = new AtomicInteger(0);
        AgentModel delegate = new CountingModel(calls, "今天天气很好");
        SemanticCachingModelCaller caller =
                new SemanticCachingModelCaller(delegate, new HashingEmbeddingModel(), 0.5d);

        AgentChatResponse first = caller.generate(userMessage("今天北京的天气怎么样"), NO_TOOLS, null);
        assertThat(calls.get()).isEqualTo(1);
        assertThat(getText(first)).isEqualTo("今天天气很好");

        AgentChatResponse second = caller.generate(userMessage("今天北京的天气如何"), NO_TOOLS, null);
        // 语义相近命中缓存，不调用真实模型
        assertThat(calls.get()).isEqualTo(1);
        assertThat(getText(second)).isEqualTo("今天天气很好");
    }

    @Test
    void missCallsModelOnce() {
        AtomicInteger calls = new AtomicInteger(0);
        AgentModel delegate = new CountingModel(calls, "天气不错");
        SemanticCachingModelCaller caller =
                new SemanticCachingModelCaller(delegate, new HashingEmbeddingModel(), 0.99d);

        AgentChatResponse first = caller.generate(userMessage("今天天气怎么样"), NO_TOOLS, null);
        assertThat(calls.get()).isEqualTo(1);

        AgentChatResponse second = caller.generate(userMessage("今天天气如何"), NO_TOOLS, null);
        // 阈值过高，无法命中，再次调用真实模型
        assertThat(calls.get()).isEqualTo(2);
    }

    @Test
    void belowThresholdNoHit() {
        AtomicInteger calls = new AtomicInteger(0);
        AgentModel delegate = new CountingModel(calls, "蛋糕制作方法");
        SemanticCachingModelCaller caller =
                new SemanticCachingModelCaller(delegate, new HashingEmbeddingModel(), 0.6d);

        caller.generate(userMessage("今天天气怎么样"), NO_TOOLS, null);
        assertThat(calls.get()).isEqualTo(1);

        // 完全不相关的查询，余弦相似度低于阈值，应调用真实模型
        caller.generate(userMessage("如何制作美味的蛋糕"), NO_TOOLS, null);
        assertThat(calls.get()).isEqualTo(2);
    }

    /**
     * 计数模型，每次调用计数并返回固定响应
     */
    private static class CountingModel implements AgentModel {

        private final AtomicInteger calls;
        private final String responseText;

        CountingModel(AtomicInteger calls, String responseText) {
            this.calls = calls;
            this.responseText = responseText;
        }

        @Override
        public AgentChatResponse generate(List<AgentMessage> messages, List<Map<String, Object>> tools,
                                          AgentGenerateOptions options) {
            calls.incrementAndGet();
            return new AgentChatResponse(Collections.singletonList(
                    AgentTextBlock.builder().text(responseText).build()), null);
        }

        @Override
        public Flux<AgentChatResponse> stream(List<AgentMessage> messages, List<Map<String, Object>> tools,
                                              AgentGenerateOptions options) {
            calls.incrementAndGet();
            return Flux.just(new AgentChatResponse(Collections.singletonList(
                    AgentTextBlock.builder().text(responseText).build()), null));
        }
    }
}