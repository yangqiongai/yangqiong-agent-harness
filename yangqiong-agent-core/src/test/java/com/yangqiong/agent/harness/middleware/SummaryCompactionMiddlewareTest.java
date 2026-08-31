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
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

import com.yangqiong.agent.harness.config.AgentCompactionConfig;
import com.yangqiong.agent.harness.engine.AgentRuntimeContext;
import com.yangqiong.agent.harness.core.event.AgentEvent;
import com.yangqiong.agent.harness.core.memory.SessionSummary;
import com.yangqiong.agent.harness.core.message.AgentContentBlock;
import com.yangqiong.agent.harness.core.message.AgentMessage;
import com.yangqiong.agent.harness.core.message.AgentMessageRole;
import com.yangqiong.agent.harness.core.message.AgentTextBlock;
import com.yangqiong.agent.harness.core.model.AgentChatResponse;
import com.yangqiong.agent.harness.core.model.AgentModel;
import com.yangqiong.agent.harness.core.model.AgentGenerateOptions;
import com.yangqiong.agent.harness.memory.InMemorySessionMemory;
import com.yangqiong.agent.harness.model.ModelCaller;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * 基于LLM摘要的上下文压缩中间件测试
 * @author yangqiong
 */
class SummaryCompactionMiddlewareTest {

    private AgentMessage systemMessage(String text) {
        return AgentMessage.builder()
                .role(AgentMessageRole.SYSTEM)
                .content(Collections.singletonList(AgentTextBlock.builder().text(text).build()))
                .build();
    }

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

    /**
     * 计数桩模型，每次生成返回固定摘要文本
     */
    private AgentModel countingModel(AtomicInteger counter) {
        return new AgentModel() {
            @Override
            public AgentChatResponse generate(List<AgentMessage> messages, List<Map<String, Object>> tools,
                                                AgentGenerateOptions options) {
                counter.incrementAndGet();
                List<AgentContentBlock> content = Collections.singletonList(
                        AgentTextBlock.builder().text("合成摘要").build());
                return new AgentChatResponse(content, null);
            }

            @Override
            public Flux<AgentChatResponse> stream(List<AgentMessage> messages, List<Map<String, Object>> tools,
                                                    AgentGenerateOptions options) {
                return Flux.just(generate(messages, tools, options));
            }
        };
    }

    private Function<List<AgentMessage>, Flux<AgentEvent>> capturingNext(AtomicReference<List<AgentMessage>> captured) {
        return input -> {
            captured.set(input);
            return Flux.empty();
        };
    }

    private List<AgentMessage> buildConversation() {
        List<AgentMessage> messages = new ArrayList<>();
        messages.add(systemMessage("系统提示"));
        messages.add(userMessage("问题1"));
        messages.add(assistantMessage("回答1"));
        messages.add(userMessage("问题2"));
        messages.add(assistantMessage("回答2"));
        messages.add(userMessage("问题3"));
        return messages;
    }

    @Test
    void firstTriggerSummarizesMiddleAndSavesSummary() {
        AtomicInteger generateCount = new AtomicInteger(0);
        ModelCaller modelCaller = new ModelCaller(countingModel(generateCount));
        InMemorySessionMemory sessionMemory = new InMemorySessionMemory();
        AgentCompactionConfig config = AgentCompactionConfig.builder()
                .triggerMessages(4).keepMessages(2).build();
        SummaryCompactionMiddleware middleware = new SummaryCompactionMiddleware(config, modelCaller, sessionMemory);

        AtomicReference<List<AgentMessage>> captured = new AtomicReference<>();
        AgentRuntimeContext ctx = AgentRuntimeContext.builder().sessionId("s1").build();
        List<AgentMessage> input = buildConversation();

        middleware.onReasoning(ctx, input, capturingNext(captured)).blockLast();

        assertThat(generateCount.get()).isEqualTo(1);
        SessionSummary saved = sessionMemory.loadSummary("s1");
        assertThat(saved).isNotNull();
        assertThat(saved.getSummarizedMessageCount()).isEqualTo(4);
        // 压缩结果含系统头、摘要消息与尾部保留
        List<AgentMessage> result = captured.get();
        assertThat(result.get(0).getRole()).isEqualTo(AgentMessageRole.SYSTEM);
        assertThat(result.get(1).getTextContent()).contains("历史对话摘要");
    }

    @Test
    void secondTriggerWithNoNewMessagesSkipsLlmCall() {
        AtomicInteger generateCount = new AtomicInteger(0);
        ModelCaller modelCaller = new ModelCaller(countingModel(generateCount));
        InMemorySessionMemory sessionMemory = new InMemorySessionMemory();
        AgentCompactionConfig config = AgentCompactionConfig.builder()
                .triggerMessages(4).keepMessages(2).build();
        SummaryCompactionMiddleware middleware = new SummaryCompactionMiddleware(config, modelCaller, sessionMemory);

        AtomicReference<List<AgentMessage>> captured = new AtomicReference<>();
        AgentRuntimeContext ctx = AgentRuntimeContext.builder().sessionId("s1").build();
        List<AgentMessage> input = buildConversation();

        // 第一次触发：摘要并保存
        middleware.onReasoning(ctx, input, capturingNext(captured)).blockLast();
        assertThat(generateCount.get()).isEqualTo(1);

        // 第二次相同消息：无新消息，复用旧摘要，跳过LLM
        middleware.onReasoning(ctx, input, capturingNext(captured)).blockLast();
        assertThat(generateCount.get()).isEqualTo(1);

        List<AgentMessage> result = captured.get();
        assertThat(result.get(1).getTextContent()).contains("合成摘要");
    }

    @Test
    void secondTriggerWithNewMessagesOnlySummarizesNewPortion() {
        AtomicInteger generateCount = new AtomicInteger(0);
        ModelCaller modelCaller = new ModelCaller(countingModel(generateCount));
        InMemorySessionMemory sessionMemory = new InMemorySessionMemory();
        AgentCompactionConfig config = AgentCompactionConfig.builder()
                .triggerMessages(4).keepMessages(2).build();
        SummaryCompactionMiddleware middleware = new SummaryCompactionMiddleware(config, modelCaller, sessionMemory);

        AtomicReference<List<AgentMessage>> captured = new AtomicReference<>();
        AgentRuntimeContext ctx = AgentRuntimeContext.builder().sessionId("s1").build();
        List<AgentMessage> input = buildConversation();

        middleware.onReasoning(ctx, input, capturingNext(captured)).blockLast();
        assertThat(generateCount.get()).isEqualTo(1);

        // 追加新消息，再次触发：仅摘要新增区间
        List<AgentMessage> grown = new ArrayList<>(input);
        grown.add(assistantMessage("回答3"));
        grown.add(userMessage("问题4"));
        middleware.onReasoning(ctx, grown, capturingNext(captured)).blockLast();

        assertThat(generateCount.get()).isEqualTo(2);
        SessionSummary saved = sessionMemory.loadSummary("s1");
        assertThat(saved.getSummarizedMessageCount()).isEqualTo(6);
    }

    @Test
    void nullSessionIdDegradesToFullSummaryWithoutCaching() {
        AtomicInteger generateCount = new AtomicInteger(0);
        ModelCaller modelCaller = new ModelCaller(countingModel(generateCount));
        InMemorySessionMemory sessionMemory = new InMemorySessionMemory();
        AgentCompactionConfig config = AgentCompactionConfig.builder()
                .triggerMessages(4).keepMessages(2).build();
        SummaryCompactionMiddleware middleware = new SummaryCompactionMiddleware(config, modelCaller, sessionMemory);

        AtomicReference<List<AgentMessage>> captured = new AtomicReference<>();
        AgentRuntimeContext ctx = AgentRuntimeContext.builder().build();
        List<AgentMessage> input = buildConversation();

        middleware.onReasoning(ctx, input, capturingNext(captured)).blockLast();
        assertThat(generateCount.get()).isEqualTo(1);

        // sessionId为空时不缓存，无摘要被保存
        assertThat(sessionMemory.loadSummary("s1")).isNull();
    }

    @Test
    void noCompactionWhenBelowTrigger() {
        AtomicInteger generateCount = new AtomicInteger(0);
        ModelCaller modelCaller = new ModelCaller(countingModel(generateCount));
        AgentCompactionConfig config = AgentCompactionConfig.builder()
                .triggerMessages(10).keepMessages(2).build();
        SummaryCompactionMiddleware middleware = new SummaryCompactionMiddleware(config, modelCaller, null);

        AtomicReference<List<AgentMessage>> captured = new AtomicReference<>();
        List<AgentMessage> input = buildConversation();

        middleware.onReasoning(AgentRuntimeContext.empty(), input, capturingNext(captured)).blockLast();

        assertThat(generateCount.get()).isZero();
        // 输入原样透传
        assertThat(captured.get()).hasSameSizeAs(input);
    }

    @Test
    void nullSessionMemoryDegradesToFullSummary() {
        AtomicInteger generateCount = new AtomicInteger(0);
        ModelCaller modelCaller = new ModelCaller(countingModel(generateCount));
        AgentCompactionConfig config = AgentCompactionConfig.builder()
                .triggerMessages(4).keepMessages(2).build();
        // 两参构造器，sessionMemory=null
        SummaryCompactionMiddleware middleware = new SummaryCompactionMiddleware(config, modelCaller);

        AtomicReference<List<AgentMessage>> captured = new AtomicReference<>();
        AgentRuntimeContext ctx = AgentRuntimeContext.builder().sessionId("s1").build();
        List<AgentMessage> input = buildConversation();

        middleware.onReasoning(ctx, input, capturingNext(captured)).blockLast();

        assertThat(generateCount.get()).isEqualTo(1);
        List<AgentMessage> result = captured.get();
        assertThat(result.get(1).getTextContent()).contains("历史对话摘要");
    }

    @Test
    void summaryErrorFallsBackToOriginalMessages() {
        AgentModel failingModel = new AgentModel() {
            @Override
            public AgentChatResponse generate(List<AgentMessage> messages, List<Map<String, Object>> tools,
                                                AgentGenerateOptions options) {
                throw new RuntimeException("摘要失败");
            }

            @Override
            public Flux<AgentChatResponse> stream(List<AgentMessage> messages, List<Map<String, Object>> tools,
                                                    AgentGenerateOptions options) {
                return Flux.error(new RuntimeException("摘要失败"));
            }
        };
        ModelCaller modelCaller = new ModelCaller(failingModel);
        AgentCompactionConfig config = AgentCompactionConfig.builder()
                .triggerMessages(4).keepMessages(2).build();
        SummaryCompactionMiddleware middleware = new SummaryCompactionMiddleware(config, modelCaller, null);

        AtomicReference<List<AgentMessage>> captured = new AtomicReference<>();
        List<AgentMessage> input = buildConversation();

        middleware.onReasoning(AgentRuntimeContext.empty(), input, capturingNext(captured)).blockLast();

        // 异常时回退原始消息
        assertThat(captured.get()).isEqualTo(input);
    }
}
