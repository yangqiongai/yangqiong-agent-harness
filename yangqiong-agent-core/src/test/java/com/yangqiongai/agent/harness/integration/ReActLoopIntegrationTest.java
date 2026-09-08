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
package com.yangqiongai.agent.harness.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.yangqiongai.agent.harness.engine.EngineContext;
import com.yangqiongai.agent.harness.engine.MiddlewareChain;
import com.yangqiongai.agent.harness.engine.ReActEngine;
import com.yangqiongai.agent.harness.core.message.MessageFactory;
import com.yangqiongai.agent.harness.middleware.CompactionMiddleware;
import com.yangqiongai.agent.harness.middleware.ToolResultEvictionMiddleware;
import com.yangqiongai.agent.harness.model.ModelCaller;
import com.yangqiongai.agent.harness.model.ModelResponseParser;
import com.yangqiongai.agent.harness.permission.PermissionEngine;
import com.yangqiongai.agent.harness.planmode.PlanModeMiddleware;
import com.yangqiongai.agent.harness.tool.HarnessToolkit;
import com.yangqiongai.agent.harness.tool.ToolExecutor;
import com.yangqiongai.agent.harness.engine.AgentRuntimeContext;
import com.yangqiongai.agent.harness.config.AgentCompactionConfig;
import com.yangqiongai.agent.harness.config.AgentPermissionContextState;
import com.yangqiongai.agent.harness.config.AgentPermissionMode;
import com.yangqiongai.agent.harness.config.AgentToolResultEvictionConfig;
import com.yangqiongai.agent.harness.core.event.AgentEvent;
import com.yangqiongai.agent.harness.core.event.AgentEventType;
import com.yangqiongai.agent.harness.core.event.AgentResultEvent;
import com.yangqiongai.agent.harness.core.event.AgentTextBlockDeltaEvent;
import com.yangqiongai.agent.harness.core.event.AgentThinkingBlockDeltaEvent;
import com.yangqiongai.agent.harness.core.message.AgentContentBlock;
import com.yangqiongai.agent.harness.core.message.AgentMessage;
import com.yangqiongai.agent.harness.core.message.AgentMessageRole;
import com.yangqiongai.agent.harness.core.message.AgentTextBlock;
import com.yangqiongai.agent.harness.core.message.AgentThinkingBlock;
import com.yangqiongai.agent.harness.core.message.AgentToolResultBlock;
import com.yangqiongai.agent.harness.core.message.AgentToolUseBlock;
import com.yangqiongai.agent.harness.core.middleware.AgentMiddleware;
import com.yangqiongai.agent.harness.core.model.AgentChatResponse;
import com.yangqiongai.agent.harness.core.tool.AgentTool;
import com.yangqiongai.agent.harness.core.tool.AgentToolCallParam;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

/**
 * ReAct循环集成测试
 * @author yangqiong
 */
class ReActLoopIntegrationTest {

    /**
     * LLM返回纯文本，单轮迭代返回结果
     */
    @Test
    void reactLoop_singleIteration_textResponse() {
        ModelCaller modelCaller = mock(ModelCaller.class);
        AgentTextBlock textBlock = AgentTextBlock.builder().text("hello").build();
        AgentChatResponse response = new AgentChatResponse(List.of(textBlock), null);
        when(modelCaller.stream(any(), any())).thenReturn(Flux.just(response));

        ToolExecutor toolExecutor = mock(ToolExecutor.class);
        MiddlewareChain chain = new MiddlewareChain(List.of());
        ModelResponseParser parser = new ModelResponseParser();

        ReActEngine engine = new ReActEngine(modelCaller, toolExecutor, chain, parser, null);
        EngineContext ctx = new EngineContext(null, null, AgentRuntimeContext.empty(), null, 10, null);

        AgentMessage input = MessageFactory.createUserMessage("hi");
        StepVerifier.create(engine.call(List.of(input), ctx))
                .assertNext(msg -> {
                    assertThat(msg.getRole()).isEqualTo(AgentMessageRole.ASSISTANT);
                    assertThat(((AgentTextBlock) msg.getContent().get(0)).getText()).isEqualTo("hello");
                })
                .verifyComplete();
    }

    /**
     * LLM先返回工具调用，执行工具后返回文本
     */
    @Test
    void reactLoop_multiIteration_toolCallThenText() {
        ModelCaller modelCaller = mock(ModelCaller.class);
        ToolExecutor toolExecutor = mock(ToolExecutor.class);

        AgentToolUseBlock toolUse = new AgentToolUseBlock("search", "id1", Map.of("q", "test"));
        AgentChatResponse firstResponse = new AgentChatResponse(List.of(toolUse), null);

        AgentTextBlock textBlock = AgentTextBlock.builder().text("final answer").build();
        AgentChatResponse secondResponse = new AgentChatResponse(List.of(textBlock), null);

        when(modelCaller.stream(any(), any()))
                .thenReturn(Flux.just(firstResponse))
                .thenReturn(Flux.just(secondResponse));

        AgentToolResultBlock toolResult = AgentToolResultBlock.of(List.of(
                AgentTextBlock.builder().text("result").build()));
        AgentMessage toolMsg = MessageFactory.createToolMessage(toolResult);
        when(toolExecutor.executeTools(any(), any(), any())).thenReturn(Mono.just(List.of(toolMsg)));

        MiddlewareChain chain = new MiddlewareChain(List.of());
        ModelResponseParser parser = new ModelResponseParser();

        ReActEngine engine = new ReActEngine(modelCaller, toolExecutor, chain, parser, null);
        EngineContext ctx = new EngineContext(null, null, AgentRuntimeContext.empty(), null, 10, null);

        AgentMessage input = MessageFactory.createUserMessage("search for test");
        StepVerifier.create(engine.call(List.of(input), ctx))
                .assertNext(msg -> {
                    assertThat(((AgentTextBlock) msg.getContent().get(0)).getText()).isEqualTo("final answer");
                })
                .verifyComplete();
    }

    /**
     * maxIters为1时达到上限后停止
     */
    @Test
    void reactLoop_maxItersReached() {
        ModelCaller modelCaller = mock(ModelCaller.class);
        ToolExecutor toolExecutor = mock(ToolExecutor.class);

        AgentToolUseBlock toolUse = new AgentToolUseBlock("search", "id1", Map.of());
        AgentChatResponse response = new AgentChatResponse(List.of(toolUse), null);
        when(modelCaller.stream(any(), any())).thenReturn(Flux.just(response));

        AgentToolResultBlock toolResult = AgentToolResultBlock.of(List.of(
                AgentTextBlock.builder().text("result").build()));
        AgentMessage toolMsg = MessageFactory.createToolMessage(toolResult);
        when(toolExecutor.executeTools(any(), any(), any())).thenReturn(Mono.just(List.of(toolMsg)));

        MiddlewareChain chain = new MiddlewareChain(List.of());
        ModelResponseParser parser = new ModelResponseParser();

        ReActEngine engine = new ReActEngine(modelCaller, toolExecutor, chain, parser, null);
        EngineContext ctx = new EngineContext(null, null, AgentRuntimeContext.empty(), null, 1, null);

        AgentMessage input = MessageFactory.createUserMessage("hi");
        StepVerifier.create(engine.call(List.of(input), ctx))
                .assertNext(msg -> assertThat(msg.getContent()).isNotNull())
                .verifyComplete();
    }

    /**
     * 注册中间件，验证onSystemPrompt钩子触发
     */
    @Test
    void reactLoop_withMiddleware_endToEnd() {
        final List<String> traces = new ArrayList<>();
        AgentMiddleware trackingMiddleware = new AgentMiddleware() {
            @Override
            public String onSystemPrompt(String systemPrompt, AgentRuntimeContext context) {
                traces.add("onSystemPrompt");
                return systemPrompt;
            }

            @Override
            public AgentMessage onMessage(AgentMessage message, AgentRuntimeContext context) {
                traces.add("onMessage:" + message.getRole());
                return message;
            }
        };

        ModelCaller modelCaller = mock(ModelCaller.class);
        AgentTextBlock textBlock = AgentTextBlock.builder().text("response").build();
        AgentChatResponse response = new AgentChatResponse(List.of(textBlock), null);
        when(modelCaller.stream(any(), any())).thenReturn(Flux.just(response));

        ToolExecutor toolExecutor = mock(ToolExecutor.class);
        MiddlewareChain chain = new MiddlewareChain(List.of(trackingMiddleware));
        ModelResponseParser parser = new ModelResponseParser();

        ReActEngine engine = new ReActEngine(modelCaller, toolExecutor, chain, parser, null);
        AgentRuntimeContext runtimeCtx = AgentRuntimeContext.empty();
        EngineContext ctx = new EngineContext(null, "you are an agent", runtimeCtx, null, 10, null);

        AgentMessage input = MessageFactory.createUserMessage("hi");
        StepVerifier.create(engine.call(List.of(input), ctx))
                .assertNext(msg -> assertThat(msg.getContent()).isNotNull())
                .verifyComplete();

        assertThat(traces).contains("onSystemPrompt");
    }

    /**
     * 设置系统提示词，验证LLM收到系统消息
     */
    @Test
    void reactLoop_withSystemPrompt_injected() {
        ModelCaller modelCaller = mock(ModelCaller.class);
        AgentTextBlock textBlock = AgentTextBlock.builder().text("response").build();
        AgentChatResponse response = new AgentChatResponse(List.of(textBlock), null);

        when(modelCaller.stream(any(), any())).thenAnswer(invocation -> {
            List<AgentMessage> msgs = invocation.getArgument(0);
            assertThat(msgs.get(0).getRole()).isEqualTo(AgentMessageRole.SYSTEM);
            return Flux.just(response);
        });

        ToolExecutor toolExecutor = mock(ToolExecutor.class);
        MiddlewareChain chain = new MiddlewareChain(List.of());
        ModelResponseParser parser = new ModelResponseParser();

        ReActEngine engine = new ReActEngine(modelCaller, toolExecutor, chain, parser, null);
        AgentRuntimeContext runtimeCtx = AgentRuntimeContext.empty();
        EngineContext ctx = new EngineContext(null, "you are an agent", runtimeCtx, null, 10, null);

        AgentMessage input = MessageFactory.createUserMessage("hi");
        StepVerifier.create(engine.call(List.of(input), ctx))
                .assertNext(msg -> assertThat(msg.getContent()).isNotNull())
                .verifyComplete();
    }

    /**
     * 有工具箱时工具Schema传给模型
     */
    @Test
    void reactLoop_withToolkit_toolAvailable() {
        AgentTool tool = new TestTool("search", "search tool", Map.of("q", Map.of("type", "string")));
        HarnessToolkit toolkit = new HarnessToolkit(null);
        toolkit.addTool(tool);

        ModelCaller modelCaller = mock(ModelCaller.class);
        AgentTextBlock textBlock = AgentTextBlock.builder().text("response").build();
        AgentChatResponse response = new AgentChatResponse(List.of(textBlock), null);

        when(modelCaller.stream(any(), any())).thenAnswer(invocation -> {
            List<Map<String, Object>> schemas = invocation.getArgument(1);
            assertThat(schemas).isNotEmpty();
            boolean found = false;
            for (Map<String, Object> schema : schemas) {
                Object functionObj = schema.get("function");
                if (functionObj instanceof Map) {
                    Map<?, ?> function = (Map<?, ?>) functionObj;
                    if ("search".equals(function.get("name"))) {
                        found = true;
                    }
                }
            }
            assertThat(found).isTrue();
            return Flux.just(response);
        });

        ToolExecutor toolExecutor = mock(ToolExecutor.class);
        MiddlewareChain chain = new MiddlewareChain(List.of());
        ModelResponseParser parser = new ModelResponseParser();

        ReActEngine engine = new ReActEngine(modelCaller, toolExecutor, chain, parser, null);
        EngineContext ctx = new EngineContext(null, null, AgentRuntimeContext.empty(), toolkit, 10, null);

        AgentMessage input = MessageFactory.createUserMessage("hi");
        StepVerifier.create(engine.call(List.of(input), ctx))
                .assertNext(msg -> assertThat(msg.getContent()).isNotNull())
                .verifyComplete();
    }

    /**
     * 流式事件顺序：增量、结果、完成
     */
    @Test
    void reactLoop_streamEvents_eventOrder() {
        ModelCaller modelCaller = mock(ModelCaller.class);
        AgentTextBlock textBlock = AgentTextBlock.builder().text("hello").build();
        AgentChatResponse response = new AgentChatResponse(List.of(textBlock), null);
        when(modelCaller.stream(any(), any())).thenReturn(Flux.just(response));

        ToolExecutor toolExecutor = mock(ToolExecutor.class);
        MiddlewareChain chain = new MiddlewareChain(List.of());
        ModelResponseParser parser = new ModelResponseParser();

        ReActEngine engine = new ReActEngine(modelCaller, toolExecutor, chain, parser, null);
        EngineContext ctx = new EngineContext(null, null, AgentRuntimeContext.empty(), null, 10, null);

        AgentMessage input = MessageFactory.createUserMessage("hi");
        StepVerifier.create(engine.stream(List.of(input), ctx)
                .filter(e -> e.getType() != AgentEventType.AGENT_START
                        && e.getType() != AgentEventType.AGENT_END
                        && e.getType() != AgentEventType.MODEL_CALL_START
                        && e.getType() != AgentEventType.MODEL_CALL_END
                        && e.getType() != AgentEventType.TOOL_CALL_START
                        && e.getType() != AgentEventType.TOOL_CALL_END))
                .expectNextMatches(e -> e.getType() == AgentEventType.TEXT_BLOCK_DELTA)
                .expectNextMatches(e -> e.getType() == AgentEventType.AGENT_RESULT)
                .expectNextMatches(e -> e.getType() == AgentEventType.COMPLETED)
                .thenCancel()
                .verify();
    }

    /**
     * 模型错误时发射ERROR事件
     */
    @Test
    void reactLoop_withError_recoversOrAborts() {
        ModelCaller modelCaller = mock(ModelCaller.class);
        when(modelCaller.stream(any(), any())).thenReturn(Flux.error(new RuntimeException("model error")));

        ToolExecutor toolExecutor = mock(ToolExecutor.class);
        MiddlewareChain chain = new MiddlewareChain(List.of());
        ModelResponseParser parser = new ModelResponseParser();

        ReActEngine engine = new ReActEngine(modelCaller, toolExecutor, chain, parser, null);
        EngineContext ctx = new EngineContext(null, null, AgentRuntimeContext.empty(), null, 10, null);

        AgentMessage input = MessageFactory.createUserMessage("hi");
        StepVerifier.create(engine.stream(List.of(input), ctx)
                .filter(e -> e.getType() != AgentEventType.AGENT_START
                        && e.getType() != AgentEventType.AGENT_END
                        && e.getType() != AgentEventType.MODEL_CALL_START
                        && e.getType() != AgentEventType.MODEL_CALL_END
                        && e.getType() != AgentEventType.TOOL_CALL_START
                        && e.getType() != AgentEventType.TOOL_CALL_END))
                .expectNextMatches(e -> e.getType() == AgentEventType.ERROR)
                .expectNextMatches(e -> e.getType() == AgentEventType.COMPLETED)
                .thenCancel()
                .verify();
    }

    /**
     * 动态注册工具可用
     */
    @Test
    void reactLoop_withDynamicTools_toolAvailable() {
        AgentTool tool = new TestTool("dynamic_search", "dynamic search tool", Map.of());

        ModelCaller modelCaller = mock(ModelCaller.class);
        AgentTextBlock textBlock = AgentTextBlock.builder().text("response").build();
        AgentChatResponse response = new AgentChatResponse(List.of(textBlock), null);

        when(modelCaller.stream(any(), any())).thenAnswer(invocation -> {
            List<Map<String, Object>> schemas = invocation.getArgument(1);
            assertThat(schemas).isNotEmpty();
            boolean found = false;
            for (Map<String, Object> schema : schemas) {
                Object functionObj = schema.get("function");
                if (functionObj instanceof Map) {
                    Map<?, ?> function = (Map<?, ?>) functionObj;
                    if ("dynamic_search".equals(function.get("name"))) {
                        found = true;
                    }
                }
            }
            assertThat(found).isTrue();
            return Flux.just(response);
        });

        ToolExecutor toolExecutor = mock(ToolExecutor.class);
        MiddlewareChain chain = new MiddlewareChain(List.of());
        ModelResponseParser parser = new ModelResponseParser();

        ReActEngine engine = new ReActEngine(modelCaller, toolExecutor, chain, parser, null);
        EngineContext ctx = new EngineContext(null, null, AgentRuntimeContext.empty(), null, 10, null);
        ctx.registerDynamicTools(List.of(tool));

        AgentMessage input = MessageFactory.createUserMessage("hi");
        StepVerifier.create(engine.call(List.of(input), ctx))
                .assertNext(msg -> assertThat(msg.getContent()).isNotNull())
                .verifyComplete();
    }

    /**
     * 子代理委派，mock toolExecutor返回子代理结果
     */
    @Test
    void reactLoop_subagentSpawn_childAgentExecutes() {
        ModelCaller modelCaller = mock(ModelCaller.class);
        ToolExecutor toolExecutor = mock(ToolExecutor.class);

        AgentToolUseBlock toolUse = new AgentToolUseBlock("spawn_subagent", "id1",
                Map.of("task", "summarize"));
        AgentChatResponse firstResponse = new AgentChatResponse(List.of(toolUse), null);

        AgentTextBlock textBlock = AgentTextBlock.builder().text("child agent completed").build();
        AgentChatResponse secondResponse = new AgentChatResponse(List.of(textBlock), null);

        when(modelCaller.stream(any(), any()))
                .thenReturn(Flux.just(firstResponse))
                .thenReturn(Flux.just(secondResponse));

        AgentToolResultBlock childResult = AgentToolResultBlock.of(List.of(
                AgentTextBlock.builder().text("child agent result: task done").build()));
        AgentMessage toolMsg = MessageFactory.createToolMessage(childResult);
        when(toolExecutor.executeTools(any(), any(), any())).thenReturn(Mono.just(List.of(toolMsg)));

        MiddlewareChain chain = new MiddlewareChain(List.of());
        ModelResponseParser parser = new ModelResponseParser();

        ReActEngine engine = new ReActEngine(modelCaller, toolExecutor, chain, parser, null);
        EngineContext ctx = new EngineContext(null, null, AgentRuntimeContext.empty(), null, 10, null);

        AgentMessage input = MessageFactory.createUserMessage("delegate to child");
        StepVerifier.create(engine.call(List.of(input), ctx))
                .assertNext(msg -> {
                    assertThat(((AgentTextBlock) msg.getContent().get(0)).getText())
                            .isEqualTo("child agent completed");
                })
                .verifyComplete();
    }

    /**
     * 计划模式，mock toolExecutor返回计划结果
     */
    @Test
    void reactLoop_planMode_planCreatedAndExecuted() {
        ModelCaller modelCaller = mock(ModelCaller.class);
        ToolExecutor toolExecutor = mock(ToolExecutor.class);

        AgentToolUseBlock toolUse = new AgentToolUseBlock("plan_write", "id1",
                Map.of("plan", "step1,step2"));
        AgentChatResponse firstResponse = new AgentChatResponse(List.of(toolUse), null);

        AgentTextBlock textBlock = AgentTextBlock.builder().text("plan executed").build();
        AgentChatResponse secondResponse = new AgentChatResponse(List.of(textBlock), null);

        when(modelCaller.stream(any(), any()))
                .thenReturn(Flux.just(firstResponse))
                .thenReturn(Flux.just(secondResponse));

        AgentToolResultBlock planResult = AgentToolResultBlock.of(List.of(
                AgentTextBlock.builder().text("plan created: step1,step2").build()));
        AgentMessage toolMsg = MessageFactory.createToolMessage(planResult);
        when(toolExecutor.executeTools(any(), any(), any())).thenReturn(Mono.just(List.of(toolMsg)));

        PlanModeMiddleware planMiddleware = new PlanModeMiddleware();
        MiddlewareChain chain = new MiddlewareChain(List.of(planMiddleware));
        ModelResponseParser parser = new ModelResponseParser();

        ReActEngine engine = new ReActEngine(modelCaller, toolExecutor, chain, parser, null);
        AgentRuntimeContext runtimeCtx = AgentRuntimeContext.empty();
        EngineContext ctx = new EngineContext(null, "you are a planning agent", runtimeCtx, null, 10, null);

        AgentMessage input = MessageFactory.createUserMessage("create a plan");
        StepVerifier.create(engine.call(List.of(input), ctx))
                .assertNext(msg -> {
                    assertThat(((AgentTextBlock) msg.getContent().get(0)).getText())
                            .isEqualTo("plan executed");
                })
                .verifyComplete();

        Object planTools = runtimeCtx.get(PlanModeMiddleware.ATTR_PLAN_MODE_TOOLS);
        assertThat(planTools).isNotNull();
    }

    /**
     * 上下文压缩，验证compactionConfig构建不报错
     */
    @Test
    void reactLoop_compaction_historyCompressed() {
        AgentCompactionConfig config = AgentCompactionConfig.builder()
                .triggerMessages(5)
                .keepMessages(2)
                .build();
        assertThat(config.getTriggerMessages()).isEqualTo(5);
        assertThat(config.getKeepMessages()).isEqualTo(2);

        CompactionMiddleware middleware = new CompactionMiddleware(config);
        assertThat(middleware).isNotNull();

        List<AgentMessage> messages = new ArrayList<>();
        messages.add(MessageFactory.createSystemMessage("system"));
        for (int i = 0; i < 10; i++) {
            messages.add(MessageFactory.createUserMessage("msg" + i));
        }
        int originalSize = messages.size();

        Flux<AgentEvent> resultFlux = middleware.onReasoning(
                AgentRuntimeContext.empty(),
                messages,
                compacted -> {
                    assertThat(compacted.size()).isLessThan(originalSize);
                    return Flux.empty();
                });
        StepVerifier.create(resultFlux).verifyComplete();
    }

    /**
     * 权限控制，mock toolExecutor返回权限拒绝结果
     */
    @Test
    void reactLoop_permission_toolBlocked() {
        ModelCaller modelCaller = mock(ModelCaller.class);
        ToolExecutor toolExecutor = mock(ToolExecutor.class);

        AgentToolUseBlock toolUse = new AgentToolUseBlock("delete_file", "id1", Map.of());
        AgentChatResponse firstResponse = new AgentChatResponse(List.of(toolUse), null);

        AgentTextBlock textBlock = AgentTextBlock.builder().text("permission denied handled").build();
        AgentChatResponse secondResponse = new AgentChatResponse(List.of(textBlock), null);

        when(modelCaller.stream(any(), any()))
                .thenReturn(Flux.just(firstResponse))
                .thenReturn(Flux.just(secondResponse));

        AgentToolResultBlock deniedResult = AgentToolResultBlock.error("权限拒绝: delete_file");
        AgentMessage toolMsg = MessageFactory.createToolMessage(deniedResult);
        when(toolExecutor.executeTools(any(), any(), any())).thenReturn(Mono.just(List.of(toolMsg)));

        MiddlewareChain chain = new MiddlewareChain(List.of());
        ModelResponseParser parser = new ModelResponseParser();

        ReActEngine engine = new ReActEngine(modelCaller, toolExecutor, chain, parser, null);
        EngineContext ctx = new EngineContext(null, null, AgentRuntimeContext.empty(), null, 10, null);

        AgentMessage input = MessageFactory.createUserMessage("delete a file");
        StepVerifier.create(engine.call(List.of(input), ctx))
                .assertNext(msg -> {
                    assertThat(((AgentTextBlock) msg.getContent().get(0)).getText())
                            .isEqualTo("permission denied handled");
                })
                .verifyComplete();
    }

    /**
     * 并发隔离，两个独立context并发执行
     */
    @Test
    void reactLoop_concurrent_contextIsolation() {
        ModelCaller modelCaller1 = mock(ModelCaller.class);
        AgentTextBlock textBlock1 = AgentTextBlock.builder().text("result1").build();
        AgentChatResponse response1 = new AgentChatResponse(List.of(textBlock1), null);
        when(modelCaller1.stream(any(), any())).thenReturn(Flux.just(response1));

        ModelCaller modelCaller2 = mock(ModelCaller.class);
        AgentTextBlock textBlock2 = AgentTextBlock.builder().text("result2").build();
        AgentChatResponse response2 = new AgentChatResponse(List.of(textBlock2), null);
        when(modelCaller2.stream(any(), any())).thenReturn(Flux.just(response2));

        ToolExecutor toolExecutor = mock(ToolExecutor.class);
        ModelResponseParser parser = new ModelResponseParser();

        ReActEngine engine1 = new ReActEngine(modelCaller1, toolExecutor,
                new MiddlewareChain(List.of()), parser, null);
        ReActEngine engine2 = new ReActEngine(modelCaller2, toolExecutor,
                new MiddlewareChain(List.of()), parser, null);

        EngineContext ctx1 = new EngineContext(null, null, AgentRuntimeContext.empty(), null, 10, null);
        EngineContext ctx2 = new EngineContext(null, null, AgentRuntimeContext.empty(), null, 10, null);

        AgentMessage input1 = MessageFactory.createUserMessage("query1");
        AgentMessage input2 = MessageFactory.createUserMessage("query2");

        Mono<AgentMessage> call1 = engine1.call(List.of(input1), ctx1);
        Mono<AgentMessage> call2 = engine2.call(List.of(input2), ctx2);

        StepVerifier.create(Mono.zip(call1, call2))
                .assertNext(tuple -> {
                    AgentMessage msg1 = tuple.getT1();
                    AgentMessage msg2 = tuple.getT2();
                    assertThat(((AgentTextBlock) msg1.getContent().get(0)).getText()).isEqualTo("result1");
                    assertThat(((AgentTextBlock) msg2.getContent().get(0)).getText()).isEqualTo("result2");
                })
                .verifyComplete();
    }

    /**
     * 流式工具调用增量事件
     */
    @Test
    void reactLoop_toolCallDelta_emittedInStream() {
        ModelCaller modelCaller = mock(ModelCaller.class);
        AgentToolUseBlock toolUse = new AgentToolUseBlock("search", "id1", Map.of("q", "test"));
        AgentChatResponse response = new AgentChatResponse(List.of(toolUse), null);
        when(modelCaller.stream(any(), any())).thenReturn(Flux.just(response));

        ToolExecutor toolExecutor = mock(ToolExecutor.class);
        MiddlewareChain chain = new MiddlewareChain(List.of());
        ModelResponseParser parser = new ModelResponseParser();

        ReActEngine engine = new ReActEngine(modelCaller, toolExecutor, chain, parser, null);
        EngineContext ctx = new EngineContext(null, null, AgentRuntimeContext.empty(), null, 0, null);

        AgentMessage input = MessageFactory.createUserMessage("hi");
        StepVerifier.create(engine.stream(List.of(input), ctx)
                .filter(e -> e.getType() != AgentEventType.AGENT_START
                        && e.getType() != AgentEventType.AGENT_END
                        && e.getType() != AgentEventType.MODEL_CALL_START
                        && e.getType() != AgentEventType.MODEL_CALL_END
                        && e.getType() != AgentEventType.TOOL_CALL_START
                        && e.getType() != AgentEventType.TOOL_CALL_END))
                .expectNextMatches(e -> e.getType() == AgentEventType.TOOL_CALL_DELTA)
                .expectNextMatches(e -> e.getType() == AgentEventType.AGENT_RESULT)
                .expectNextMatches(e -> e.getType() == AgentEventType.COMPLETED)
                .thenCancel()
                .verify();
    }

    /**
     * 单轮响应无工具调用立即完成，工具执行器不被调用
     */
    @Test
    void reactLoop_singleResponseNoToolCall_completesImmediately() {
        ModelCaller modelCaller = mock(ModelCaller.class);
        AgentTextBlock textBlock = AgentTextBlock.builder().text("done").build();
        AgentChatResponse response = new AgentChatResponse(List.of(textBlock), null);
        when(modelCaller.stream(any(), any())).thenReturn(Flux.just(response));

        ToolExecutor toolExecutor = mock(ToolExecutor.class);
        MiddlewareChain chain = new MiddlewareChain(List.of());
        ModelResponseParser parser = new ModelResponseParser();

        ReActEngine engine = new ReActEngine(modelCaller, toolExecutor, chain, parser, null);
        EngineContext ctx = new EngineContext(null, null, AgentRuntimeContext.empty(), null, 10, null);

        AgentMessage input = MessageFactory.createUserMessage("hi");
        StepVerifier.create(engine.call(List.of(input), ctx))
                .assertNext(msg -> {
                    assertThat(msg.getRole()).isEqualTo(AgentMessageRole.ASSISTANT);
                    assertThat(((AgentTextBlock) msg.getContent().get(0)).getText()).isEqualTo("done");
                })
                .verifyComplete();
        verify(toolExecutor, never()).executeTools(any(), any(), any());
    }

    /**
     * 多个工具调用并行执行
     */
    @Test
    void reactLoop_multipleToolCalls_executesInParallel() {
        ModelCaller modelCaller = mock(ModelCaller.class);
        ToolExecutor toolExecutor = mock(ToolExecutor.class);

        AgentToolUseBlock toolUse1 = new AgentToolUseBlock("search", "id1", Map.of("q", "test1"));
        AgentToolUseBlock toolUse2 = new AgentToolUseBlock("search", "id2", Map.of("q", "test2"));
        AgentChatResponse firstResponse = new AgentChatResponse(List.of(toolUse1, toolUse2), null);

        AgentTextBlock textBlock = AgentTextBlock.builder().text("merged result").build();
        AgentChatResponse secondResponse = new AgentChatResponse(List.of(textBlock), null);

        when(modelCaller.stream(any(), any()))
                .thenReturn(Flux.just(firstResponse))
                .thenReturn(Flux.just(secondResponse));

        AgentToolResultBlock toolResult1 = AgentToolResultBlock.of(List.of(
                AgentTextBlock.builder().text("result1").build()));
        AgentToolResultBlock toolResult2 = AgentToolResultBlock.of(List.of(
                AgentTextBlock.builder().text("result2").build()));
        AgentMessage toolMsg1 = MessageFactory.createToolMessage(toolResult1);
        AgentMessage toolMsg2 = MessageFactory.createToolMessage(toolResult2);
        when(toolExecutor.executeTools(any(), any(), any()))
                .thenReturn(Mono.just(List.of(toolMsg1, toolMsg2)));

        MiddlewareChain chain = new MiddlewareChain(List.of());
        ModelResponseParser parser = new ModelResponseParser();

        ReActEngine engine = new ReActEngine(modelCaller, toolExecutor, chain, parser, null);
        EngineContext ctx = new EngineContext(null, null, AgentRuntimeContext.empty(), null, 10, null);

        AgentMessage input = MessageFactory.createUserMessage("search multiple");
        StepVerifier.create(engine.call(List.of(input), ctx))
                .assertNext(msg -> {
                    assertThat(((AgentTextBlock) msg.getContent().get(0)).getText())
                            .isEqualTo("merged result");
                })
                .verifyComplete();

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<AgentToolUseBlock>> captor = ArgumentCaptor.forClass(List.class);
        verify(toolExecutor).executeTools(captor.capture(), any(), any());
        assertThat(captor.getValue()).hasSize(2);
    }

    /**
     * 工具结果追加到对话历史
     */
    @Test
    void reactLoop_toolResultAppendedToConversation() {
        ModelCaller modelCaller = mock(ModelCaller.class);
        ToolExecutor toolExecutor = mock(ToolExecutor.class);

        AgentToolUseBlock toolUse = new AgentToolUseBlock("search", "id1", Map.of("q", "test"));
        AgentChatResponse firstResponse = new AgentChatResponse(List.of(toolUse), null);

        AgentTextBlock textBlock = AgentTextBlock.builder().text("final").build();
        AgentChatResponse secondResponse = new AgentChatResponse(List.of(textBlock), null);

        when(modelCaller.stream(any(), any()))
                .thenReturn(Flux.just(firstResponse))
                .thenAnswer(invocation -> {
                    List<AgentMessage> msgs = invocation.getArgument(0);
                    boolean hasToolResult = msgs.stream()
                            .anyMatch(m -> m.getRole() == AgentMessageRole.TOOL);
                    assertThat(hasToolResult).isTrue();
                    return Flux.just(secondResponse);
                });

        AgentToolResultBlock toolResult = AgentToolResultBlock.of(List.of(
                AgentTextBlock.builder().text("tool output").build()));
        AgentMessage toolMsg = MessageFactory.createToolMessage(toolResult);
        when(toolExecutor.executeTools(any(), any(), any())).thenReturn(Mono.just(List.of(toolMsg)));

        MiddlewareChain chain = new MiddlewareChain(List.of());
        ModelResponseParser parser = new ModelResponseParser();

        ReActEngine engine = new ReActEngine(modelCaller, toolExecutor, chain, parser, null);
        EngineContext ctx = new EngineContext(null, null, AgentRuntimeContext.empty(), null, 10, null);

        AgentMessage input = MessageFactory.createUserMessage("search");
        StepVerifier.create(engine.call(List.of(input), ctx))
                .assertNext(msg -> {
                    assertThat(((AgentTextBlock) msg.getContent().get(0)).getText()).isEqualTo("final");
                })
                .verifyComplete();
    }

    /**
     * 达到最大迭代次数停止迭代
     */
    @Test
    void reactLoop_maxItersReached_stopsIteration() {
        ModelCaller modelCaller = mock(ModelCaller.class);
        ToolExecutor toolExecutor = mock(ToolExecutor.class);

        AgentToolUseBlock toolUse = new AgentToolUseBlock("search", "id1", Map.of());
        AgentChatResponse response = new AgentChatResponse(List.of(toolUse), null);
        when(modelCaller.stream(any(), any())).thenReturn(Flux.just(response));

        AgentToolResultBlock toolResult = AgentToolResultBlock.of(List.of(
                AgentTextBlock.builder().text("result").build()));
        AgentMessage toolMsg = MessageFactory.createToolMessage(toolResult);
        when(toolExecutor.executeTools(any(), any(), any())).thenReturn(Mono.just(List.of(toolMsg)));

        MiddlewareChain chain = new MiddlewareChain(List.of());
        ModelResponseParser parser = new ModelResponseParser();

        ReActEngine engine = new ReActEngine(modelCaller, toolExecutor, chain, parser, null);
        EngineContext ctx = new EngineContext(null, null, AgentRuntimeContext.empty(), null, 2, null);

        AgentMessage input = MessageFactory.createUserMessage("hi");
        StepVerifier.create(engine.call(List.of(input), ctx))
                .assertNext(msg -> {
                    assertThat(msg.getRole()).isEqualTo(AgentMessageRole.ASSISTANT);
                    assertThat(msg.getContent().get(0)).isInstanceOf(AgentToolUseBlock.class);
                })
                .verifyComplete();
        verify(toolExecutor, times(2)).executeTools(any(), any(), any());
    }

    /**
     * 空内容响应完成迭代
     */
    @Test
    void reactLoop_emptyToolCallList_completes() {
        ModelCaller modelCaller = mock(ModelCaller.class);
        AgentChatResponse response = new AgentChatResponse(List.of(), null);
        when(modelCaller.stream(any(), any())).thenReturn(Flux.just(response));

        ToolExecutor toolExecutor = mock(ToolExecutor.class);
        MiddlewareChain chain = new MiddlewareChain(List.of());
        ModelResponseParser parser = new ModelResponseParser();

        ReActEngine engine = new ReActEngine(modelCaller, toolExecutor, chain, parser, null);
        EngineContext ctx = new EngineContext(null, null, AgentRuntimeContext.empty(), null, 10, null);

        AgentMessage input = MessageFactory.createUserMessage("hi");
        StepVerifier.create(engine.call(List.of(input), ctx))
                .assertNext(msg -> {
                    assertThat(msg.getRole()).isEqualTo(AgentMessageRole.ASSISTANT);
                    assertThat(msg.getContent()).isEmpty();
                })
                .verifyComplete();
        verify(toolExecutor, never()).executeTools(any(), any(), any());
    }

    /**
     * 系统提示词注入到消息首部
     */
    @Test
    void reactLoop_systemPromptInjection_prependedToMessages() {
        ModelCaller modelCaller = mock(ModelCaller.class);
        AgentTextBlock textBlock = AgentTextBlock.builder().text("response").build();
        AgentChatResponse response = new AgentChatResponse(List.of(textBlock), null);

        when(modelCaller.stream(any(), any())).thenAnswer(invocation -> {
            List<AgentMessage> msgs = invocation.getArgument(0);
            assertThat(msgs.get(0).getRole()).isEqualTo(AgentMessageRole.SYSTEM);
            assertThat(((AgentTextBlock) msgs.get(0).getContent().get(0)).getText())
                    .isEqualTo("you are an agent");
            return Flux.just(response);
        });

        ToolExecutor toolExecutor = mock(ToolExecutor.class);
        MiddlewareChain chain = new MiddlewareChain(List.of());
        ModelResponseParser parser = new ModelResponseParser();

        ReActEngine engine = new ReActEngine(modelCaller, toolExecutor, chain, parser, null);
        AgentRuntimeContext runtimeCtx = AgentRuntimeContext.empty();
        EngineContext ctx = new EngineContext(null, "you are an agent", runtimeCtx, null, 10, null);

        AgentMessage input = MessageFactory.createUserMessage("hi");
        StepVerifier.create(engine.call(List.of(input), ctx))
                .assertNext(msg -> assertThat(msg.getContent()).isNotNull())
                .verifyComplete();
    }

    /**
     * 中间件 onSystemPrompt 钩子触发并可修改提示词
     */
    @Test
    void reactLoop_middlewareOnSystemPrompt_triggered() {
        AgentMiddleware modifyingMiddleware = new AgentMiddleware() {
            @Override
            public String onSystemPrompt(String systemPrompt, AgentRuntimeContext context) {
                return systemPrompt + " - enhanced";
            }
        };

        ModelCaller modelCaller = mock(ModelCaller.class);
        AgentTextBlock textBlock = AgentTextBlock.builder().text("response").build();
        AgentChatResponse response = new AgentChatResponse(List.of(textBlock), null);

        when(modelCaller.stream(any(), any())).thenAnswer(invocation -> {
            List<AgentMessage> msgs = invocation.getArgument(0);
            assertThat(msgs.get(0).getRole()).isEqualTo(AgentMessageRole.SYSTEM);
            assertThat(((AgentTextBlock) msgs.get(0).getContent().get(0)).getText())
                    .isEqualTo("you are an agent - enhanced");
            return Flux.just(response);
        });

        ToolExecutor toolExecutor = mock(ToolExecutor.class);
        MiddlewareChain chain = new MiddlewareChain(List.of(modifyingMiddleware));
        ModelResponseParser parser = new ModelResponseParser();

        ReActEngine engine = new ReActEngine(modelCaller, toolExecutor, chain, parser, null);
        AgentRuntimeContext runtimeCtx = AgentRuntimeContext.empty();
        EngineContext ctx = new EngineContext(null, "you are an agent", runtimeCtx, null, 10, null);

        AgentMessage input = MessageFactory.createUserMessage("hi");
        StepVerifier.create(engine.call(List.of(input), ctx))
                .assertNext(msg -> assertThat(msg.getContent()).isNotNull())
                .verifyComplete();
    }

    /**
     * 流式输出文本增量事件
     */
    @Test
    void streamEvents_emitsTextBlockDelta() {
        ModelCaller modelCaller = mock(ModelCaller.class);
        AgentTextBlock textBlock = AgentTextBlock.builder().text("hello").build();
        AgentChatResponse response = new AgentChatResponse(List.of(textBlock), null);
        when(modelCaller.stream(any(), any())).thenReturn(Flux.just(response));

        ToolExecutor toolExecutor = mock(ToolExecutor.class);
        MiddlewareChain chain = new MiddlewareChain(List.of());
        ModelResponseParser parser = new ModelResponseParser();

        ReActEngine engine = new ReActEngine(modelCaller, toolExecutor, chain, parser, null);
        EngineContext ctx = new EngineContext(null, null, AgentRuntimeContext.empty(), null, 10, null);

        AgentMessage input = MessageFactory.createUserMessage("hi");
        StepVerifier.create(engine.stream(List.of(input), ctx)
                .filter(e -> e.getType() != AgentEventType.AGENT_START
                        && e.getType() != AgentEventType.AGENT_END
                        && e.getType() != AgentEventType.MODEL_CALL_START
                        && e.getType() != AgentEventType.MODEL_CALL_END
                        && e.getType() != AgentEventType.TOOL_CALL_START
                        && e.getType() != AgentEventType.TOOL_CALL_END))
                .assertNext(e -> {
                    assertThat(e.getType()).isEqualTo(AgentEventType.TEXT_BLOCK_DELTA);
                    assertThat(((AgentTextBlockDeltaEvent) e).getDelta()).isEqualTo("hello");
                })
                .thenCancel()
                .verify();
    }

    /**
     * 流式输出思考增量事件
     */
    @Test
    void streamEvents_emitsThinkingBlockDelta() {
        ModelCaller modelCaller = mock(ModelCaller.class);
        AgentThinkingBlock thinkingBlock = AgentThinkingBlock.builder().thinking("analyzing").build();
        AgentChatResponse response = new AgentChatResponse(List.of(thinkingBlock), null);
        when(modelCaller.stream(any(), any())).thenReturn(Flux.just(response));

        ToolExecutor toolExecutor = mock(ToolExecutor.class);
        MiddlewareChain chain = new MiddlewareChain(List.of());
        ModelResponseParser parser = new ModelResponseParser();

        ReActEngine engine = new ReActEngine(modelCaller, toolExecutor, chain, parser, null);
        EngineContext ctx = new EngineContext(null, null, AgentRuntimeContext.empty(), null, 10, null);

        AgentMessage input = MessageFactory.createUserMessage("hi");
        StepVerifier.create(engine.stream(List.of(input), ctx)
                .filter(e -> e.getType() != AgentEventType.AGENT_START
                        && e.getType() != AgentEventType.AGENT_END
                        && e.getType() != AgentEventType.MODEL_CALL_START
                        && e.getType() != AgentEventType.MODEL_CALL_END
                        && e.getType() != AgentEventType.TOOL_CALL_START
                        && e.getType() != AgentEventType.TOOL_CALL_END))
                .assertNext(e -> {
                    assertThat(e.getType()).isEqualTo(AgentEventType.THINKING_BLOCK_DELTA);
                    assertThat(((AgentThinkingBlockDeltaEvent) e).getDelta()).isEqualTo("analyzing");
                })
                .thenCancel()
                .verify();
    }

    /**
     * 完成时输出结果事件
     */
    @Test
    void streamEvents_emitsAgentResultOnCompletion() {
        ModelCaller modelCaller = mock(ModelCaller.class);
        AgentTextBlock textBlock = AgentTextBlock.builder().text("final answer").build();
        AgentChatResponse response = new AgentChatResponse(List.of(textBlock), null);
        when(modelCaller.stream(any(), any())).thenReturn(Flux.just(response));

        ToolExecutor toolExecutor = mock(ToolExecutor.class);
        MiddlewareChain chain = new MiddlewareChain(List.of());
        ModelResponseParser parser = new ModelResponseParser();

        ReActEngine engine = new ReActEngine(modelCaller, toolExecutor, chain, parser, null);
        EngineContext ctx = new EngineContext(null, null, AgentRuntimeContext.empty(), null, 10, null);

        AgentMessage input = MessageFactory.createUserMessage("hi");
        StepVerifier.create(engine.stream(List.of(input), ctx)
                .filter(e -> e.getType() != AgentEventType.AGENT_START
                        && e.getType() != AgentEventType.AGENT_END
                        && e.getType() != AgentEventType.MODEL_CALL_START
                        && e.getType() != AgentEventType.MODEL_CALL_END
                        && e.getType() != AgentEventType.TOOL_CALL_START
                        && e.getType() != AgentEventType.TOOL_CALL_END))
                .expectNextMatches(e -> e.getType() == AgentEventType.TEXT_BLOCK_DELTA)
                .assertNext(e -> {
                    assertThat(e.getType()).isEqualTo(AgentEventType.AGENT_RESULT);
                    assertThat(e).isInstanceOf(AgentResultEvent.class);
                    AgentMessage result = ((AgentResultEvent) e).getResult();
                    assertThat(((AgentTextBlock) result.getContent().get(0)).getText())
                            .isEqualTo("final answer");
                })
                .thenCancel()
                .verify();
    }

    /**
     * 结果后输出完成事件
     */
    @Test
    void streamEvents_emitsCompletedAfterResult() {
        ModelCaller modelCaller = mock(ModelCaller.class);
        AgentTextBlock textBlock = AgentTextBlock.builder().text("done").build();
        AgentChatResponse response = new AgentChatResponse(List.of(textBlock), null);
        when(modelCaller.stream(any(), any())).thenReturn(Flux.just(response));

        ToolExecutor toolExecutor = mock(ToolExecutor.class);
        MiddlewareChain chain = new MiddlewareChain(List.of());
        ModelResponseParser parser = new ModelResponseParser();

        ReActEngine engine = new ReActEngine(modelCaller, toolExecutor, chain, parser, null);
        EngineContext ctx = new EngineContext(null, null, AgentRuntimeContext.empty(), null, 10, null);

        AgentMessage input = MessageFactory.createUserMessage("hi");
        StepVerifier.create(engine.stream(List.of(input), ctx)
                .filter(e -> e.getType() != AgentEventType.AGENT_START
                        && e.getType() != AgentEventType.AGENT_END
                        && e.getType() != AgentEventType.MODEL_CALL_START
                        && e.getType() != AgentEventType.MODEL_CALL_END
                        && e.getType() != AgentEventType.TOOL_CALL_START
                        && e.getType() != AgentEventType.TOOL_CALL_END))
                .expectNextMatches(e -> e.getType() == AgentEventType.TEXT_BLOCK_DELTA)
                .expectNextMatches(e -> e.getType() == AgentEventType.AGENT_RESULT)
                .assertNext(e -> assertThat(e.getType()).isEqualTo(AgentEventType.COMPLETED))
                .thenCancel()
                .verify();
    }

    /**
     * 模型失败时输出错误事件
     */
    @Test
    void streamEvents_emitsErrorOnModelFailure() {
        ModelCaller modelCaller = mock(ModelCaller.class);
        when(modelCaller.stream(any(), any()))
                .thenReturn(Flux.error(new RuntimeException("model failure")));

        ToolExecutor toolExecutor = mock(ToolExecutor.class);
        MiddlewareChain chain = new MiddlewareChain(List.of());
        ModelResponseParser parser = new ModelResponseParser();

        ReActEngine engine = new ReActEngine(modelCaller, toolExecutor, chain, parser, null);
        EngineContext ctx = new EngineContext(null, null, AgentRuntimeContext.empty(), null, 10, null);

        AgentMessage input = MessageFactory.createUserMessage("hi");
        StepVerifier.create(engine.stream(List.of(input), ctx)
                .filter(e -> e.getType() != AgentEventType.AGENT_START
                        && e.getType() != AgentEventType.AGENT_END
                        && e.getType() != AgentEventType.MODEL_CALL_START
                        && e.getType() != AgentEventType.MODEL_CALL_END
                        && e.getType() != AgentEventType.TOOL_CALL_START
                        && e.getType() != AgentEventType.TOOL_CALL_END))
                .assertNext(e -> {
                    assertThat(e.getType()).isEqualTo(AgentEventType.ERROR);
                    assertThat(e.getPayload()).isInstanceOf(RuntimeException.class);
                    assertThat(((Throwable) e.getPayload()).getMessage()).isEqualTo("model failure");
                })
                .expectNextMatches(e -> e.getType() == AgentEventType.COMPLETED)
                .thenCancel()
                .verify();
    }

    /**
     * 工具失败时输出错误事件
     */
    @Test
    void streamEvents_emitsErrorOnToolFailure() {
        ModelCaller modelCaller = mock(ModelCaller.class);
        ToolExecutor toolExecutor = mock(ToolExecutor.class);

        AgentToolUseBlock toolUse = new AgentToolUseBlock("search", "id1", Map.of());
        AgentChatResponse response = new AgentChatResponse(List.of(toolUse), null);
        when(modelCaller.stream(any(), any())).thenReturn(Flux.just(response));

        when(toolExecutor.executeTools(any(), any(), any()))
                .thenReturn(Mono.error(new RuntimeException("tool execution failed")));

        MiddlewareChain chain = new MiddlewareChain(List.of());
        ModelResponseParser parser = new ModelResponseParser();

        ReActEngine engine = new ReActEngine(modelCaller, toolExecutor, chain, parser, null);
        EngineContext ctx = new EngineContext(null, null, AgentRuntimeContext.empty(), null, 10, null);

        AgentMessage input = MessageFactory.createUserMessage("hi");
        StepVerifier.create(engine.stream(List.of(input), ctx)
                .filter(e -> e.getType() != AgentEventType.AGENT_START
                        && e.getType() != AgentEventType.AGENT_END
                        && e.getType() != AgentEventType.MODEL_CALL_START
                        && e.getType() != AgentEventType.MODEL_CALL_END
                        && e.getType() != AgentEventType.TOOL_CALL_START
                        && e.getType() != AgentEventType.TOOL_CALL_END))
                .expectNextMatches(e -> e.getType() == AgentEventType.TOOL_CALL_DELTA)
                .assertNext(e -> {
                    assertThat(e.getType()).isEqualTo(AgentEventType.ERROR);
                    assertThat(e.getPayload()).isInstanceOf(RuntimeException.class);
                    assertThat(((Throwable) e.getPayload()).getMessage()).isEqualTo("tool execution failed");
                })
                .expectNextMatches(e -> e.getType() == AgentEventType.COMPLETED)
                .thenCancel()
                .verify();
    }

    /**
     * call 返回最终 AgentMessage
     */
    @Test
    void call_returnsFinalAgentMessage() {
        ModelCaller modelCaller = mock(ModelCaller.class);
        ToolExecutor toolExecutor = mock(ToolExecutor.class);

        AgentToolUseBlock toolUse = new AgentToolUseBlock("search", "id1", Map.of("q", "test"));
        AgentChatResponse firstResponse = new AgentChatResponse(List.of(toolUse), null);

        AgentTextBlock textBlock = AgentTextBlock.builder().text("the answer").build();
        AgentChatResponse secondResponse = new AgentChatResponse(List.of(textBlock), null);

        when(modelCaller.stream(any(), any()))
                .thenReturn(Flux.just(firstResponse))
                .thenReturn(Flux.just(secondResponse));

        AgentToolResultBlock toolResult = AgentToolResultBlock.of(List.of(
                AgentTextBlock.builder().text("result").build()));
        AgentMessage toolMsg = MessageFactory.createToolMessage(toolResult);
        when(toolExecutor.executeTools(any(), any(), any())).thenReturn(Mono.just(List.of(toolMsg)));

        MiddlewareChain chain = new MiddlewareChain(List.of());
        ModelResponseParser parser = new ModelResponseParser();

        ReActEngine engine = new ReActEngine(modelCaller, toolExecutor, chain, parser, null);
        EngineContext ctx = new EngineContext(null, null, AgentRuntimeContext.empty(), null, 10, null);

        AgentMessage input = MessageFactory.createUserMessage("search");
        AgentMessage result = engine.call(List.of(input), ctx).block();
        assertThat(result).isNotNull();
        assertThat(result.getRole()).isEqualTo(AgentMessageRole.ASSISTANT);
        assertThat(((AgentTextBlock) result.getContent().get(0)).getText()).isEqualTo("the answer");
    }

    /**
     * call 失败时传播错误
     */
    @Test
    void call_propagatesErrorOnFailure() {
        ModelCaller modelCaller = mock(ModelCaller.class);
        when(modelCaller.stream(any(), any()))
                .thenReturn(Flux.error(new RuntimeException("fatal error")));

        ToolExecutor toolExecutor = mock(ToolExecutor.class);
        MiddlewareChain chain = new MiddlewareChain(List.of());
        ModelResponseParser parser = new ModelResponseParser();

        ReActEngine engine = new ReActEngine(modelCaller, toolExecutor, chain, parser, null);
        EngineContext ctx = new EngineContext(null, null, AgentRuntimeContext.empty(), null, 10, null);

        AgentMessage input = MessageFactory.createUserMessage("hi");
        StepVerifier.create(engine.call(List.of(input), ctx))
                .expectErrorMatches(e -> e instanceof RuntimeException
                        && "fatal error".equals(e.getMessage()))
                .verify();
    }

    /**
     * 配置CompactionMiddleware，消息超过阈值时触发压缩
     */
    @Test
    void reactLoop_withCompactionMiddleware_compactsHistory() {
        AgentCompactionConfig config = AgentCompactionConfig.builder()
                .triggerMessages(5)
                .keepMessages(2)
                .build();
        CompactionMiddleware middleware = new CompactionMiddleware(config);
        MiddlewareChain chain = new MiddlewareChain(List.of(middleware));

        List<AgentMessage> messages = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            messages.add(MessageFactory.createUserMessage("msg" + i));
        }
        int originalSize = messages.size();

        final List<AgentMessage> passedToNext = new ArrayList<>();
        Flux<AgentEvent> result = chain.applyOnReasoning(
                AgentRuntimeContext.empty(),
                messages,
                compacted -> {
                    passedToNext.addAll(compacted);
                    return Flux.empty();
                });

        StepVerifier.create(result).verifyComplete();
        assertThat(passedToNext.size()).isLessThan(originalSize);
        assertThat(passedToNext.size()).isEqualTo(2);
    }

    /**
     * 配置ToolResultEvictionMiddleware，清理历史工具结果
     */
    @Test
    void reactLoop_withToolResultEvictionMiddleware_evictsOldResults() {
        AgentToolResultEvictionConfig config = AgentToolResultEvictionConfig.builder()
                .maxResultChars(10)
                .previewChars(5)
                .build();
        ToolResultEvictionMiddleware middleware = new ToolResultEvictionMiddleware(config);
        MiddlewareChain chain = new MiddlewareChain(List.of(middleware));

        String largeContent = "abcdefghij0123456789";
        AgentToolResultBlock largeResult = AgentToolResultBlock.of(List.of(
                AgentTextBlock.builder().text(largeContent).build()));
        AgentMessage toolMsg = MessageFactory.createToolMessage(largeResult);

        final List<AgentMessage> passedToNext = new ArrayList<>();
        Flux<AgentEvent> result = chain.applyOnReasoning(
                AgentRuntimeContext.empty(),
                List.of(toolMsg),
                evicted -> {
                    passedToNext.addAll(evicted);
                    return Flux.empty();
                });

        StepVerifier.create(result).verifyComplete();
        assertThat(passedToNext).hasSize(1);
        AgentContentBlock block = passedToNext.get(0).getContent().get(0);
        assertThat(block).isInstanceOf(AgentToolResultBlock.class);
        String evictedText = ((AgentToolResultBlock) block).getTextContent();
        assertThat(evictedText.length()).isLessThan(largeContent.length());
        assertThat(evictedText).contains("已截断");
    }

    /**
     * 配置权限引擎，阻止破坏性工具调用
     */
    @Test
    void reactLoop_withPermissionEngine_blocksDisallowedTool() {
        AgentPermissionContextState state = AgentPermissionContextState.builder()
                .mode(AgentPermissionMode.EXPLORE)
                .build();
        PermissionEngine permissionEngine = new PermissionEngine(state);
        assertThat(permissionEngine.allow("delete_file")).isFalse();

        ModelCaller modelCaller = mock(ModelCaller.class);
        ToolExecutor toolExecutor = mock(ToolExecutor.class);

        AgentToolUseBlock toolUse = new AgentToolUseBlock("delete_file", "id1", Map.of());
        AgentChatResponse firstResponse = new AgentChatResponse(List.of(toolUse), null);

        AgentTextBlock textBlock = AgentTextBlock.builder().text("tool blocked").build();
        AgentChatResponse secondResponse = new AgentChatResponse(List.of(textBlock), null);

        when(modelCaller.stream(any(), any()))
                .thenReturn(Flux.just(firstResponse))
                .thenReturn(Flux.just(secondResponse));

        when(toolExecutor.executeTools(any(), any(), any())).thenAnswer(invocation -> {
            List<AgentToolUseBlock> calls = invocation.getArgument(0);
            List<AgentMessage> results = new ArrayList<>();
            for (AgentToolUseBlock tc : calls) {
                if (!permissionEngine.allow(tc.getToolName())) {
                    results.add(MessageFactory.createToolMessage(
                            AgentToolResultBlock.error("权限拒绝: " + tc.getToolName())));
                }
            }
            return Mono.just(results);
        });

        MiddlewareChain chain = new MiddlewareChain(List.of());
        ModelResponseParser parser = new ModelResponseParser();

        ReActEngine engine = new ReActEngine(modelCaller, toolExecutor, chain, parser, null);
        EngineContext ctx = new EngineContext(null, null, AgentRuntimeContext.empty(), null, 10, null);

        AgentMessage input = MessageFactory.createUserMessage("delete a file");
        StepVerifier.create(engine.call(List.of(input), ctx))
                .assertNext(msg -> {
                    assertThat(((AgentTextBlock) msg.getContent().get(0)).getText())
                            .isEqualTo("tool blocked");
                })
                .verifyComplete();
    }

    /**
     * 配置权限引擎，允许只读工具调用
     */
    @Test
    void reactLoop_withPermissionEngine_allowsReadOnlyTool() {
        AgentPermissionContextState state = AgentPermissionContextState.builder()
                .mode(AgentPermissionMode.EXPLORE)
                .build();
        PermissionEngine permissionEngine = new PermissionEngine(state);
        assertThat(permissionEngine.allow("read_file")).isTrue();

        ModelCaller modelCaller = mock(ModelCaller.class);
        ToolExecutor toolExecutor = mock(ToolExecutor.class);

        AgentToolUseBlock toolUse = new AgentToolUseBlock("read_file", "id1", Map.of());
        AgentChatResponse firstResponse = new AgentChatResponse(List.of(toolUse), null);

        AgentTextBlock textBlock = AgentTextBlock.builder().text("file content read").build();
        AgentChatResponse secondResponse = new AgentChatResponse(List.of(textBlock), null);

        when(modelCaller.stream(any(), any()))
                .thenReturn(Flux.just(firstResponse))
                .thenReturn(Flux.just(secondResponse));

        when(toolExecutor.executeTools(any(), any(), any())).thenAnswer(invocation -> {
            List<AgentToolUseBlock> calls = invocation.getArgument(0);
            List<AgentMessage> results = new ArrayList<>();
            for (AgentToolUseBlock tc : calls) {
                if (permissionEngine.allow(tc.getToolName())) {
                    results.add(MessageFactory.createToolMessage(AgentToolResultBlock.of(List.of(
                            AgentTextBlock.builder().text("file content").build()))));
                }
            }
            return Mono.just(results);
        });

        MiddlewareChain chain = new MiddlewareChain(List.of());
        ModelResponseParser parser = new ModelResponseParser();

        ReActEngine engine = new ReActEngine(modelCaller, toolExecutor, chain, parser, null);
        EngineContext ctx = new EngineContext(null, null, AgentRuntimeContext.empty(), null, 10, null);

        AgentMessage input = MessageFactory.createUserMessage("read a file");
        StepVerifier.create(engine.call(List.of(input), ctx))
                .assertNext(msg -> {
                    assertThat(((AgentTextBlock) msg.getContent().get(0)).getText())
                            .isEqualTo("file content read");
                })
                .verifyComplete();
        verify(toolExecutor, times(1)).executeTools(any(), any(), any());
    }

    /**
     * 验证中间件onReasoning钩子触发
     */
    @Test
    void streamEvents_withMiddlewareChain_appliesOnReasoning() {
        final List<String> traces = new ArrayList<>();
        final List<AgentMessage> captured = new ArrayList<>();
        AgentMiddleware trackingMiddleware = new AgentMiddleware() {
            @Override
            public Flux<AgentEvent> onReasoning(AgentRuntimeContext context, List<AgentMessage> input,
                                                 Function<List<AgentMessage>, Flux<AgentEvent>> next) {
                traces.add("onReasoning");
                captured.addAll(input);
                return next.apply(input);
            }
        };

        MiddlewareChain chain = new MiddlewareChain(List.of(trackingMiddleware));
        AgentMessage msg = MessageFactory.createUserMessage("hi");

        Flux<AgentEvent> result = chain.applyOnReasoning(
                AgentRuntimeContext.empty(),
                List.of(msg),
                messages -> {
                    traces.add("next");
                    return Flux.empty();
                });

        StepVerifier.create(result).verifyComplete();
        assertThat(traces).containsExactly("onReasoning", "next");
        assertThat(captured).hasSize(1);
        assertThat(captured.get(0)).isEqualTo(msg);
    }

    /**
     * 验证中间件onActing钩子触发
     */
    @Test
    void streamEvents_withMiddlewareChain_appliesOnActing() {
        final List<String> traces = new ArrayList<>();
        final List<AgentToolUseBlock> captured = new ArrayList<>();
        AgentMiddleware trackingMiddleware = new AgentMiddleware() {
            @Override
            public Flux<AgentEvent> onActing(AgentRuntimeContext context, List<AgentToolUseBlock> toolCalls,
                                               Function<List<AgentToolUseBlock>, Flux<AgentEvent>> next) {
                traces.add("onActing");
                captured.addAll(toolCalls);
                return next.apply(toolCalls);
            }
        };

        MiddlewareChain chain = new MiddlewareChain(List.of(trackingMiddleware));
        AgentToolUseBlock toolCall = new AgentToolUseBlock("search", "id1", Map.of("q", "test"));

        Flux<AgentEvent> result = chain.applyOnActing(
                AgentRuntimeContext.empty(),
                List.of(toolCall),
                calls -> {
                    traces.add("next");
                    return Flux.empty();
                });

        StepVerifier.create(result).verifyComplete();
        assertThat(traces).containsExactly("onActing", "next");
        assertThat(captured).hasSize(1);
        assertThat(captured.get(0)).isEqualTo(toolCall);
    }

    /**
     * 测试工具
     * @author yangqiong
     */
    private static class TestTool implements AgentTool {

        /**
         * 工具名称
         */
        private final String name;

        /**
         * 工具描述
         */
        private final String description;

        /**
         * 工具参数
         */
        private final Map<String, Object> parameters;

        TestTool(String name, String description, Map<String, Object> parameters) {
            this.name = name;
            this.description = description;
            this.parameters = parameters;
        }

        @Override
        public String getName() {
            return name;
        }

        @Override
        public String getDescription() {
            return description;
        }

        @Override
        public Map<String, Object> getParameters() {
            return parameters;
        }

        @Override
        public Mono<AgentToolResultBlock> callAsync(AgentToolCallParam param) {
            AgentTextBlock textBlock = AgentTextBlock.builder().text("tool result").build();
            return Mono.just(AgentToolResultBlock.of(List.of(textBlock)));
        }
    }
}
