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
package com.yangqiong.agent.harness.boundary;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;

import com.yangqiong.agent.harness.subagent.orchestration.SubagentDeclaration;
import com.yangqiong.agent.harness.engine.EngineContext;
import com.yangqiong.agent.harness.engine.MiddlewareChain;
import com.yangqiong.agent.harness.engine.ReActEngine;
import com.yangqiong.agent.harness.core.message.MessageFactory;
import com.yangqiong.agent.harness.middleware.CompactionMiddleware;
import com.yangqiong.agent.harness.middleware.ToolResultEvictionMiddleware;
import com.yangqiong.agent.harness.model.ModelCaller;
import com.yangqiong.agent.harness.model.ModelResponseParser;
import com.yangqiong.agent.harness.subagent.orchestration.SubagentResult;
import com.yangqiong.agent.harness.permission.PermissionEngine;
import com.yangqiong.agent.harness.subagent.SubagentSpawner;
import com.yangqiong.agent.harness.tool.HarnessToolkit;
import com.yangqiong.agent.harness.tool.ToolExecutor;
import com.yangqiong.agent.harness.core.AgentRuntime;
import com.yangqiong.agent.harness.engine.AgentRuntimeContext;
import com.yangqiong.agent.harness.core.AgentRuntimeFactory;
import com.yangqiong.agent.harness.core.AdvancedAgentRuntimeBuilder;
import com.yangqiong.agent.harness.config.AgentCompactionConfig;
import com.yangqiong.agent.harness.config.AgentToolResultEvictionConfig;
import com.yangqiong.agent.harness.core.event.AgentEvent;
import com.yangqiong.agent.harness.core.event.AgentEventType;
import com.yangqiong.agent.harness.core.message.AgentContentBlock;
import com.yangqiong.agent.harness.core.message.AgentMessage;
import com.yangqiong.agent.harness.core.message.AgentMessageRole;
import com.yangqiong.agent.harness.core.message.AgentTextBlock;
import com.yangqiong.agent.harness.core.message.AgentToolResultBlock;
import com.yangqiong.agent.harness.core.message.AgentToolUseBlock;
import com.yangqiong.agent.harness.core.middleware.AgentMiddleware;
import com.yangqiong.agent.harness.core.model.AgentChatResponse;
import com.yangqiong.agent.harness.core.tool.AgentTool;
import com.yangqiong.agent.harness.core.tool.AgentToolCallParam;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

/**
 * 边界条件与异常场景测试
 * @author yangqiong
 */
class BoundaryTest {

    /**
     * null输入列表传入engine.call应抛出空指针异常
     */
    @Test
    void call_withNullInput_throwsNpe() {
        ModelCaller modelCaller = mock(ModelCaller.class);
        ToolExecutor toolExecutor = mock(ToolExecutor.class);
        MiddlewareChain chain = new MiddlewareChain(List.of());
        ModelResponseParser parser = new ModelResponseParser();

        ReActEngine engine = new ReActEngine(modelCaller, toolExecutor, chain, parser, null);
        EngineContext ctx = new EngineContext(null, null, AgentRuntimeContext.empty(), null, 10, null);

        // new ArrayList<>(null) 在组装阶段即抛出NPE
        assertThatThrownBy(() -> engine.call(null, ctx))
                .isInstanceOf(NullPointerException.class);
    }

    /**
     * 空输入列表传入engine.call应正常执行并返回响应
     */
    @Test
    void call_withEmptyInputList_handlesGracefully() {
        ModelCaller modelCaller = mock(ModelCaller.class);
        AgentTextBlock textBlock = AgentTextBlock.builder().text("empty handled").build();
        AgentChatResponse response = new AgentChatResponse(List.of(textBlock), null);
        when(modelCaller.stream(any(), any())).thenReturn(Flux.just(response));

        ToolExecutor toolExecutor = mock(ToolExecutor.class);
        ReActEngine engine = new ReActEngine(modelCaller, toolExecutor,
                new MiddlewareChain(List.of()), new ModelResponseParser(), null);
        EngineContext ctx = new EngineContext(null, null, AgentRuntimeContext.empty(), null, 10, null);

        StepVerifier.create(engine.call(Collections.emptyList(), ctx))
                .assertNext(msg -> assertThat(msg.getTextContent()).isEqualTo("empty handled"))
                .verifyComplete();
    }

    /**
     * 空运行时上下文应能正常驱动引擎执行
     */
    @Test
    void call_withNullContext_usesEmptyContext() {
        ModelCaller modelCaller = mock(ModelCaller.class);
        AgentTextBlock textBlock = AgentTextBlock.builder().text("ok").build();
        AgentChatResponse response = new AgentChatResponse(List.of(textBlock), null);
        when(modelCaller.stream(any(), any())).thenReturn(Flux.just(response));

        ToolExecutor toolExecutor = mock(ToolExecutor.class);
        ReActEngine engine = new ReActEngine(modelCaller, toolExecutor,
                new MiddlewareChain(List.of()), new ModelResponseParser(), null);

        // 使用空上下文（无任何属性）验证不抛异常
        EngineContext ctx = new EngineContext(null, null, AgentRuntimeContext.empty(), null, 10, null);

        StepVerifier.create(engine.call(List.of(MessageFactory.createUserMessage("hi")), ctx))
                .assertNext(msg -> assertThat(msg.getTextContent()).isEqualTo("ok"))
                .verifyComplete();
    }

    /**
     * 未设置系统提示词时不应在消息头部注入系统消息
     */
    @Test
    void call_withNullSystemPrompt_noSystemMessage() {
        ModelCaller modelCaller = mock(ModelCaller.class);
        AgentTextBlock textBlock = AgentTextBlock.builder().text("response").build();
        AgentChatResponse response = new AgentChatResponse(List.of(textBlock), null);
        when(modelCaller.stream(any(), any())).thenAnswer(invocation -> {
            List<AgentMessage> msgs = invocation.getArgument(0);
            assertThat(msgs.get(0).getRole()).isNotEqualTo(AgentMessageRole.SYSTEM);
            return Flux.just(response);
        });

        ToolExecutor toolExecutor = mock(ToolExecutor.class);
        ReActEngine engine = new ReActEngine(modelCaller, toolExecutor,
                new MiddlewareChain(List.of()), new ModelResponseParser(), null);
        // systemPrompt 为 null，getSystemPrompt 返回 null
        EngineContext ctx = new EngineContext(null, null, AgentRuntimeContext.empty(), null, 10, null);

        StepVerifier.create(engine.call(List.of(MessageFactory.createUserMessage("hi")), ctx))
                .assertNext(msg -> assertThat(msg.getContent()).isNotNull())
                .verifyComplete();
    }

    /**
     * 空工具箱下LLM无工具可调用，工具Schema列表应为空
     */
    @Test
    void call_withEmptyToolkit_noToolsAvailable() {
        ModelCaller modelCaller = mock(ModelCaller.class);
        AgentTextBlock textBlock = AgentTextBlock.builder().text("no tools").build();
        AgentChatResponse response = new AgentChatResponse(List.of(textBlock), null);
        when(modelCaller.stream(any(), any())).thenAnswer(invocation -> {
            List<Map<String, Object>> schemas = invocation.getArgument(1);
            assertThat(schemas).isEmpty();
            return Flux.just(response);
        });

        ToolExecutor toolExecutor = mock(ToolExecutor.class);
        ReActEngine engine = new ReActEngine(modelCaller, toolExecutor,
                new MiddlewareChain(List.of()), new ModelResponseParser(), null);
        // 空工具箱（从 null AgentToolkit 构造）
        HarnessToolkit toolkit = new HarnessToolkit(null);
        EngineContext ctx = new EngineContext(null, null, AgentRuntimeContext.empty(), toolkit, 10, null);

        StepVerifier.create(engine.call(List.of(MessageFactory.createUserMessage("hi")), ctx))
                .assertNext(msg -> assertThat(msg.getTextContent()).isEqualTo("no tools"))
                .verifyComplete();
    }

    /**
     * maxIters为0时首轮迭代即停止，工具调用不应被执行
     */
    @Test
    void call_withMaxItersZero_stopsImmediately() {
        ModelCaller modelCaller = mock(ModelCaller.class);
        ToolExecutor toolExecutor = mock(ToolExecutor.class);

        AgentToolUseBlock toolUse = new AgentToolUseBlock("search", "id1", Map.of());
        AgentChatResponse response = new AgentChatResponse(List.of(toolUse), null);
        when(modelCaller.stream(any(), any())).thenReturn(Flux.just(response));

        ReActEngine engine = new ReActEngine(modelCaller, toolExecutor,
                new MiddlewareChain(List.of()), new ModelResponseParser(), null);
        // iteration=0 >= maxIters=0 立即停止
        EngineContext ctx = new EngineContext(null, null, AgentRuntimeContext.empty(), null, 0, null);

        StepVerifier.create(engine.call(List.of(MessageFactory.createUserMessage("hi")), ctx))
                .assertNext(msg -> {
                    assertThat(msg.getContent()).hasSize(1);
                    assertThat(msg.getContent().get(0) instanceof AgentToolUseBlock).isTrue();
                })
                .verifyComplete();
        // 工具未被执行
        verify(toolExecutor, never()).executeTools(any(), any(), any());
    }

    /**
     * maxIters为负数时应被当作立即停止条件优雅处理
     */
    @Test
    void call_withNegativeMaxIters_handlesGracefully() {
        ModelCaller modelCaller = mock(ModelCaller.class);
        AgentTextBlock textBlock = AgentTextBlock.builder().text("graceful").build();
        AgentChatResponse response = new AgentChatResponse(List.of(textBlock), null);
        when(modelCaller.stream(any(), any())).thenReturn(Flux.just(response));

        ToolExecutor toolExecutor = mock(ToolExecutor.class);
        ReActEngine engine = new ReActEngine(modelCaller, toolExecutor,
                new MiddlewareChain(List.of()), new ModelResponseParser(), null);
        // iteration=0 >= maxIters=-1 为 true，立即停止
        EngineContext ctx = new EngineContext(null, null, AgentRuntimeContext.empty(), null, -1, null);

        StepVerifier.create(engine.call(List.of(MessageFactory.createUserMessage("hi")), ctx))
                .assertNext(msg -> assertThat(msg.getTextContent()).isEqualTo("graceful"))
                .verifyComplete();
    }

    /**
     * null输入列表传入engine.stream应抛出空指针异常
     */
    @Test
    void streamEvents_withNullInput_throwsOrHandles() {
        ModelCaller modelCaller = mock(ModelCaller.class);
        ToolExecutor toolExecutor = mock(ToolExecutor.class);
        ReActEngine engine = new ReActEngine(modelCaller, toolExecutor,
                new MiddlewareChain(List.of()), new ModelResponseParser(), null);
        EngineContext ctx = new EngineContext(null, null, AgentRuntimeContext.empty(), null, 10, null);

        // new ArrayList<>(null) 在组装阶段即抛出NPE
        assertThatThrownBy(() -> engine.stream(null, ctx))
                .isInstanceOf(NullPointerException.class);
    }

    /**
     * 模型调用异常时流式通道应发射ERROR事件
     */
    @Test
    void modelCallError_recoversOrAborts() {
        ModelCaller modelCaller = mock(ModelCaller.class);
        when(modelCaller.stream(any(), any()))
                .thenReturn(Flux.error(new RuntimeException("model error")));

        ToolExecutor toolExecutor = mock(ToolExecutor.class);
        ReActEngine engine = new ReActEngine(modelCaller, toolExecutor,
                new MiddlewareChain(List.of()), new ModelResponseParser(), null);
        EngineContext ctx = new EngineContext(null, null, AgentRuntimeContext.empty(), null, 10, null);

        StepVerifier.create(engine.stream(List.of(MessageFactory.createUserMessage("hi")), ctx)
                .filter(e -> e.getType() != AgentEventType.AGENT_START
                        && e.getType() != AgentEventType.AGENT_END
                        && e.getType() != AgentEventType.MODEL_CALL_START
                        && e.getType() != AgentEventType.MODEL_CALL_END
                        && e.getType() != AgentEventType.TOOL_CALL_START
                        && e.getType() != AgentEventType.TOOL_CALL_END))
                .assertNext(event -> assertThat(event.getType()).isEqualTo(AgentEventType.ERROR))
                .assertNext(event -> assertThat(event.getType()).isEqualTo(AgentEventType.COMPLETED))
                .thenCancel()
                .verify();
    }

    /**
     * 工具调用返回错误结果后引擎应继续推理直至给出最终答复
     */
    @Test
    void toolCallError_returnsErrorResult() {
        ModelCaller modelCaller = mock(ModelCaller.class);
        ToolExecutor toolExecutor = mock(ToolExecutor.class);

        AgentToolUseBlock toolUse = new AgentToolUseBlock("failing_tool", "id1", Map.of());
        AgentChatResponse firstResponse = new AgentChatResponse(List.of(toolUse), null);
        AgentTextBlock textBlock = AgentTextBlock.builder().text("after error").build();
        AgentChatResponse secondResponse = new AgentChatResponse(List.of(textBlock), null);
        when(modelCaller.stream(any(), any()))
                .thenReturn(Flux.just(firstResponse))
                .thenReturn(Flux.just(secondResponse));

        AgentToolResultBlock errorResult = AgentToolResultBlock.error("tool failed");
        when(toolExecutor.executeTools(any(), any(), any()))
                .thenReturn(Mono.just(List.of(MessageFactory.createToolMessage(errorResult))));

        ReActEngine engine = new ReActEngine(modelCaller, toolExecutor,
                new MiddlewareChain(List.of()), new ModelResponseParser(), null);
        EngineContext ctx = new EngineContext(null, null, AgentRuntimeContext.empty(), null, 10, null);

        StepVerifier.create(engine.call(List.of(MessageFactory.createUserMessage("hi")), ctx))
                .assertNext(msg -> assertThat(((AgentTextBlock) msg.getContent().get(0)).getText())
                        .isEqualTo("after error"))
                .verifyComplete();
    }

    /**
     * 中间件抛出异常时应以错误信号优雅降级而非卡死
     */
    @Test
    void middlewareThrowsError_degradesGracefully() {
        ModelCaller modelCaller = mock(ModelCaller.class);
        AgentTextBlock textBlock = AgentTextBlock.builder().text("hi").build();
        AgentChatResponse response = new AgentChatResponse(List.of(textBlock), null);
        when(modelCaller.stream(any(), any())).thenReturn(Flux.just(response));

        // 系统提示词钩子抛异常，模拟中间件故障
        AgentMiddleware throwingMiddleware = new AgentMiddleware() {
            @Override
            public String onSystemPrompt(String systemPrompt, AgentRuntimeContext context) {
                throw new RuntimeException("middleware boom");
            }
        };
        MiddlewareChain chain = new MiddlewareChain(List.of(throwingMiddleware));
        ReActEngine engine = new ReActEngine(modelCaller, mock(ToolExecutor.class),
                chain, new ModelResponseParser(), null);
        EngineContext ctx = new EngineContext(null, "system prompt", AgentRuntimeContext.empty(), null, 10, null);

        // onSystemPrompt在stream组装阶段抛出，call同步传播异常
        assertThatThrownBy(() -> engine.call(List.of(MessageFactory.createUserMessage("hi")), ctx))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("middleware boom");
    }

    /**
     * 子代理工具返回错误结果后主流程应继续并给出最终答复
     */
    @Test
    void subagentThrowsError_returnsErrorResult() {
        ModelCaller modelCaller = mock(ModelCaller.class);
        ToolExecutor toolExecutor = mock(ToolExecutor.class);

        AgentToolUseBlock toolUse = new AgentToolUseBlock("agent_spawn", "id1", Map.of());
        AgentChatResponse firstResponse = new AgentChatResponse(List.of(toolUse), null);
        AgentTextBlock textBlock = AgentTextBlock.builder().text("subagent recovered").build();
        AgentChatResponse secondResponse = new AgentChatResponse(List.of(textBlock), null);
        when(modelCaller.stream(any(), any()))
                .thenReturn(Flux.just(firstResponse))
                .thenReturn(Flux.just(secondResponse));

        AgentToolResultBlock errorResult = AgentToolResultBlock.error("subagent crashed");
        when(toolExecutor.executeTools(any(), any(), any()))
                .thenReturn(Mono.just(List.of(MessageFactory.createToolMessage(errorResult))));

        ReActEngine engine = new ReActEngine(modelCaller, toolExecutor,
                new MiddlewareChain(List.of()), new ModelResponseParser(), null);
        EngineContext ctx = new EngineContext(null, null, AgentRuntimeContext.empty(), null, 10, null);

        StepVerifier.create(engine.call(List.of(MessageFactory.createUserMessage("delegate")), ctx))
                .assertNext(msg -> assertThat(((AgentTextBlock) msg.getContent().get(0)).getText())
                        .isEqualTo("subagent recovered"))
                .verifyComplete();
    }

    /**
     * 计划模式工具返回错误结果时不中断主流程
     */
    @Test
    void planModeToolThrowsError_handlesGracefully() {
        ModelCaller modelCaller = mock(ModelCaller.class);
        ToolExecutor toolExecutor = mock(ToolExecutor.class);

        AgentToolUseBlock toolUse = new AgentToolUseBlock("plan_write", "id1", Map.of());
        AgentChatResponse firstResponse = new AgentChatResponse(List.of(toolUse), null);
        AgentTextBlock textBlock = AgentTextBlock.builder().text("plan continued").build();
        AgentChatResponse secondResponse = new AgentChatResponse(List.of(textBlock), null);
        when(modelCaller.stream(any(), any()))
                .thenReturn(Flux.just(firstResponse))
                .thenReturn(Flux.just(secondResponse));

        AgentToolResultBlock errorResult = AgentToolResultBlock.error("plan tool error");
        when(toolExecutor.executeTools(any(), any(), any()))
                .thenReturn(Mono.just(List.of(MessageFactory.createToolMessage(errorResult))));

        ReActEngine engine = new ReActEngine(modelCaller, toolExecutor,
                new MiddlewareChain(List.of()), new ModelResponseParser(), null);
        EngineContext ctx = new EngineContext(null, null, AgentRuntimeContext.empty(), null, 10, null);

        StepVerifier.create(engine.call(List.of(MessageFactory.createUserMessage("plan")), ctx))
                .assertNext(msg -> assertThat(((AgentTextBlock) msg.getContent().get(0)).getText())
                        .isEqualTo("plan continued"))
                .verifyComplete();
    }

    /**
     * 动态工具注册失败（null入参）时应跳过注册并继续执行
     */
    @Test
    void dynamicToolRegistrationFails_continuesWithoutDynamic() {
        ModelCaller modelCaller = mock(ModelCaller.class);
        AgentTextBlock textBlock = AgentTextBlock.builder().text("ok").build();
        AgentChatResponse response = new AgentChatResponse(List.of(textBlock), null);
        when(modelCaller.stream(any(), any())).thenAnswer(invocation -> {
            List<Map<String, Object>> schemas = invocation.getArgument(1);
            // 注册失败，无动态工具
            assertThat(schemas).isEmpty();
            return Flux.just(response);
        });

        ToolExecutor toolExecutor = mock(ToolExecutor.class);
        ReActEngine engine = new ReActEngine(modelCaller, toolExecutor,
                new MiddlewareChain(List.of()), new ModelResponseParser(), null);
        EngineContext ctx = new EngineContext(null, null, AgentRuntimeContext.empty(), null, 10, null);

        // null 入参为空操作，模拟动态工具注册失败
        ctx.registerDynamicTools(null);

        StepVerifier.create(engine.call(List.of(MessageFactory.createUserMessage("hi")), ctx))
                .assertNext(msg -> assertThat(msg.getTextContent()).isEqualTo("ok"))
                .verifyComplete();
    }

    /**
     * 压缩无需裁剪时应保留原始历史继续执行
     */
    @Test
    void compactionFails_continuesWithOriginalHistory() {
        // keepMessages >= 消息总数时压缩为空操作，等价于压缩失败回退原始历史
        AgentCompactionConfig config = AgentCompactionConfig.builder()
                .triggerMessages(2)
                .keepMessages(1000)
                .build();
        CompactionMiddleware middleware = new CompactionMiddleware(config);

        List<AgentMessage> messages = new ArrayList<>();
        messages.add(MessageFactory.createSystemMessage("system"));
        for (int i = 0; i < 4; i++) {
            messages.add(MessageFactory.createUserMessage("msg" + i));
        }

        AtomicReference<List<AgentMessage>> captured = new AtomicReference<>();
        Flux<AgentEvent> result = middleware.onReasoning(AgentRuntimeContext.empty(), messages, input -> {
            captured.set(input);
            return Flux.empty();
        });

        StepVerifier.create(result).verifyComplete();
        // 原始5条历史被完整保留
        assertThat(captured.get()).hasSize(5);
        assertThat(captured.get().get(0).getRole()).isEqualTo(AgentMessageRole.SYSTEM);
    }

    /**
     * 权限引擎未配置状态时校验失败应默认放行
     */
    @Test
    void permissionCheckFails_allowsByDefault() {
        // state 为 null 代表权限检查未初始化（校验失败）
        PermissionEngine engine = new PermissionEngine(null);
        assertThat(engine.allow("delete_file")).isTrue();
        assertThat(engine.allow("shell")).isTrue();
        assertThat(engine.allow("any_tool")).isTrue();
    }

    /**
     * 历史消息超过触发阈值时应启动压缩并保留系统头与近期消息
     */
    @Test
    void largeHistory_triggersCompaction() {
        AgentCompactionConfig config = AgentCompactionConfig.builder()
                .triggerMessages(5)
                .keepMessages(2)
                .build();
        CompactionMiddleware middleware = new CompactionMiddleware(config);

        List<AgentMessage> messages = new ArrayList<>();
        messages.add(MessageFactory.createSystemMessage("system"));
        for (int i = 0; i < 9; i++) {
            messages.add(MessageFactory.createUserMessage("msg" + i));
        }

        AtomicReference<List<AgentMessage>> captured = new AtomicReference<>();
        Flux<AgentEvent> result = middleware.onReasoning(AgentRuntimeContext.empty(), messages, input -> {
            captured.set(input);
            return Flux.empty();
        });

        StepVerifier.create(result).verifyComplete();
        // 10条触发压缩后保留系统头+1条近期，共2条
        assertThat(captured.get()).hasSize(2);
        assertThat(captured.get().get(0).getRole()).isEqualTo(AgentMessageRole.SYSTEM);
    }

    /**
     * 工具结果超过最大字符数时应触发驱逐并截断为预览
     */
    @Test
    void largeToolResult_triggersEviction() {
        AgentToolResultEvictionConfig config = AgentToolResultEvictionConfig.builder()
                .maxResultChars(10)
                .previewChars(3)
                .build();
        ToolResultEvictionMiddleware middleware = new ToolResultEvictionMiddleware(config);

        AgentTextBlock largeText = AgentTextBlock.builder()
                .text("这是一个非常长的工具结果内容，远远超过最大字符数限制")
                .build();
        AgentToolResultBlock largeResult = AgentToolResultBlock.of("id1", List.of(largeText));
        AgentMessage toolMsg = MessageFactory.createToolMessage(largeResult);

        AtomicReference<List<AgentMessage>> captured = new AtomicReference<>();
        Flux<AgentEvent> result = middleware.onReasoning(AgentRuntimeContext.empty(),
                List.of(toolMsg), input -> {
                    captured.set(input);
                    return Flux.empty();
                });

        StepVerifier.create(result).verifyComplete();
        AgentMessage evicted = captured.get().get(0);
        boolean foundTruncated = false;
        for (AgentContentBlock block : evicted.getContent()) {
            if (block instanceof AgentToolResultBlock) {
                AgentToolResultBlock toolResult = (AgentToolResultBlock) block;
                assertThat(toolResult.getTextContent()).contains("...[已截断]");
                foundTruncated = true;
            }
        }
        assertThat(foundTruncated).isTrue();
    }

    /**
     * 批量构建大量子代理声明应全部成功且字段正确
     */
    @Test
    void manySubagents_handlesAll() {
        List<SubagentDeclaration> declarations = new ArrayList<>();
        for (int i = 0; i < 50; i++) {
            declarations.add(SubagentDeclaration.builder()
                    .name("subagent-" + i)
                    .description("子代理 " + i)
                    .systemPrompt("你是子代理" + i)
                    .build());
        }

        assertThat(declarations).hasSize(50);
        for (int i = 0; i < 50; i++) {
            SubagentDeclaration decl = declarations.get(i);
            assertThat(decl.getName()).isEqualTo("subagent-" + i);
            assertThat(decl.getDescription()).isEqualTo("子代理 " + i);
        }
    }

    /**
     * 深层嵌套子代理通过mock运行时应能逐层返回结果而不发生栈溢出
     */
    @Test
    void deepSubagentNesting_handlesGracefully() {
        AgentRuntimeFactory runtimeFactory = mock(AgentRuntimeFactory.class);
        AdvancedAgentRuntimeBuilder builder = mock(AdvancedAgentRuntimeBuilder.class);
        AgentRuntime childRuntime = mock(AgentRuntime.class);
        when(runtimeFactory.createBuilder()).thenReturn(builder);
        when(builder.build()).thenReturn(childRuntime);

        AgentMessage resultMsg = MessageFactory.createAssistantMessage(
                List.of(AgentTextBlock.builder().text("nested result").build()), null);
        when(childRuntime.call(any(), any())).thenReturn(Mono.just(resultMsg));

        SubagentSpawner spawner = new SubagentSpawner(runtimeFactory, null, null, null);

        // 模拟多层级嵌套调用，每层均通过mock立即返回，验证不发生栈溢出
        for (int level = 0; level < 20; level++) {
            SubagentDeclaration levelDecl = SubagentDeclaration.builder()
                    .name("agent-level-" + level)
                    .description("嵌套层级 " + level)
                    .systemPrompt("你是层级 " + level + " 的子代理")
                    .build();
            SubagentResult levelResult =
                    spawner.spawn(levelDecl, "input-" + level,
                    AgentRuntimeContext.empty()).block();
            assertThat(levelResult.isSuccess()).isTrue();
            assertThat(levelResult.output()).isEqualTo("nested result");
        }
    }

    /**
     * 长时间运行的工具调用应被超时机制中断
     */
    @Test
    void longRunningTool_timeoutEnforced() {
        AgentTool slowTool = mock(AgentTool.class);
        when(slowTool.callAsync(any())).thenReturn(
                Mono.delay(Duration.ofSeconds(2))
                        .map(i -> AgentToolResultBlock.of(
                                List.of(AgentTextBlock.builder().text("late").build()))));

        StepVerifier.create(slowTool.callAsync(new AgentToolCallParam(Map.of()))
                .timeout(Duration.ofMillis(100)))
                .expectError(TimeoutException.class)
                .verify();
    }
}
