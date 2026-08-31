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
package com.yangqiong.agent.harness.orchestration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.yangqiong.agent.harness.subagent.orchestration.HandoffRegistry;
import com.yangqiong.agent.harness.subagent.orchestration.HandoffTarget;
import com.yangqiong.agent.harness.subagent.orchestration.HandoffTool;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import com.yangqiong.agent.harness.core.AgentRuntime;
import com.yangqiong.agent.harness.core.message.AgentMessage;
import com.yangqiong.agent.harness.core.message.AgentMessageRole;
import com.yangqiong.agent.harness.core.message.AgentTextBlock;
import com.yangqiong.agent.harness.core.tool.AgentToolCallParam;
import com.yangqiong.agent.harness.engine.AgentRuntimeContext;

/**
 * Handoff移交工具测试
 * @author yangqiong
 */
class HandoffToolTest {

    @Test
    void shouldReturnCorrectToolMetadata() {
        HandoffTool tool = new HandoffTool(new HandoffRegistry());
        assertThat(tool.getName()).isEqualTo("handoff");
        assertThat(tool.getDescription()).isNotBlank();
        Map<String, Object> params = tool.getParameters();
        assertThat(params.get("type")).isEqualTo("object");
        assertThat(params.get("required")).asList().containsExactly("target");
        assertThat(params.get("properties")).isNotNull();
    }

    @Test
    void shouldHandoffAndReturnTargetTextWithContext() {
        AgentRuntime runtime = mock(AgentRuntime.class);
        when(runtime.getName()).thenReturn("coder");
        List<AgentMessage> captured = new ArrayList<>();
        when(runtime.call(anyList(), any(AgentRuntimeContext.class))).thenAnswer(invocation -> {
            captured.addAll(invocation.getArgument(0));
            return Mono.just(AgentMessage.builder()
                    .role(AgentMessageRole.ASSISTANT)
                    .content(List.of(AgentTextBlock.builder().text("目标Agent处理完成").build()))
                    .build());
        });
        HandoffRegistry registry = new HandoffRegistry();
        registry.register(new HandoffTarget(runtime, "编码助手", null));
        HandoffTool tool = new HandoffTool(registry);

        AgentToolCallParam param = new AgentToolCallParam(Map.of(
                "target", "coder",
                "reason", "请处理编码任务"));
        StepVerifier.create(tool.callAsync(param))
                .assertNext(block -> {
                    assertThat(block.isError()).isFalse();
                    assertThat(block.getTextContent()).isEqualTo("目标Agent处理完成");
                })
                .verifyComplete();
        // 验证传入的上下文包含reason
        assertThat(captured).isNotEmpty();
        assertThat(captured.get(0).getTextContent()).contains("请处理编码任务");
    }

    @Test
    void shouldReturnErrorForUnknownTarget() {
        HandoffTool tool = new HandoffTool(new HandoffRegistry());
        AgentToolCallParam param = new AgentToolCallParam(Map.of("target", "unknown"));
        StepVerifier.create(tool.callAsync(param))
                .assertNext(block -> {
                    assertThat(block.isError()).isTrue();
                    assertThat(block.getTextContent()).contains("未知handoff目标");
                })
                .verifyComplete();
    }

    @Test
    void shouldReturnErrorWhenTargetMissing() {
        HandoffTool tool = new HandoffTool(new HandoffRegistry());
        StepVerifier.create(tool.callAsync(new AgentToolCallParam(Map.of())))
                .assertNext(block -> assertThat(block.isError()).isTrue())
                .verifyComplete();
    }

    @Test
    void shouldApplyFilterWhenBuildingInput() {
        AgentRuntime runtime = mock(AgentRuntime.class);
        when(runtime.getName()).thenReturn("coder");
        List<AgentMessage> captured = new ArrayList<>();
        when(runtime.call(anyList(), any(AgentRuntimeContext.class))).thenAnswer(invocation -> {
            captured.addAll(invocation.getArgument(0));
            return Mono.just(AgentMessage.builder()
                    .role(AgentMessageRole.ASSISTANT)
                    .content(List.of(AgentTextBlock.builder().text("完成").build()))
                    .build());
        });
        HandoffRegistry registry = new HandoffRegistry();
        registry.register(new HandoffTarget(runtime, "编码助手", null));
        HandoffTool tool = new HandoffTool(registry);

        List<AgentMessage> history = List.of(
                userMessage("第一条用户消息"),
                assistantMessage("助手回复"),
                userMessage("最后一条用户消息")
        );
        AgentToolCallParam param = new AgentToolCallParam(Map.of(
                "target", "coder",
                "context", history,
                "reason", "移交原因说明"));
        StepVerifier.create(tool.callAsync(param))
                .expectNextCount(1)
                .verifyComplete();
        // filter生效：仅保留context + 最后一条USER消息
        assertThat(captured).hasSize(2);
        assertThat(captured.get(0).getTextContent()).contains("移交原因说明");
        assertThat(captured.get(1).getTextContent()).isEqualTo("最后一条用户消息");
    }

    @Test
    void shouldReturnErrorWhenTargetExecutionFails() {
        AgentRuntime runtime = mock(AgentRuntime.class);
        when(runtime.getName()).thenReturn("coder");
        when(runtime.call(anyList(), any(AgentRuntimeContext.class)))
                .thenReturn(Mono.error(new RuntimeException("目标执行失败")));
        HandoffRegistry registry = new HandoffRegistry();
        registry.register(new HandoffTarget(runtime, "编码助手", null));
        HandoffTool tool = new HandoffTool(registry);

        AgentToolCallParam param = new AgentToolCallParam(Map.of("target", "coder"));
        StepVerifier.create(tool.callAsync(param))
                .assertNext(block -> {
                    assertThat(block.isError()).isTrue();
                    assertThat(block.getTextContent()).contains("目标执行失败");
                })
                .verifyComplete();
    }

    @Test
    void shouldPropagateScopeAndUserFromParam() {
        AgentRuntime runtime = mock(AgentRuntime.class);
        when(runtime.getName()).thenReturn("coder");
        List<AgentRuntimeContext> capturedCtx = new ArrayList<>();
        when(runtime.call(anyList(), any(AgentRuntimeContext.class))).thenAnswer(invocation -> {
            capturedCtx.add(invocation.getArgument(1));
            return Mono.just(AgentMessage.builder()
                    .role(AgentMessageRole.ASSISTANT)
                    .content(List.of(AgentTextBlock.builder().text("完成").build()))
                    .build());
        });
        HandoffRegistry registry = new HandoffRegistry();
        registry.register(new HandoffTarget(runtime, "编码助手", null));
        HandoffTool tool = new HandoffTool(registry);

        AgentToolCallParam param = new AgentToolCallParam(Map.of("target", "coder"), "scope-1", "user-1");
        StepVerifier.create(tool.callAsync(param))
                .expectNextCount(1)
                .verifyComplete();
        assertThat(capturedCtx).hasSize(1);
        assertThat(capturedCtx.get(0).getScopeId()).isEqualTo("scope-1");
        assertThat(capturedCtx.get(0).getUserId()).isEqualTo("user-1");
    }

    @Test
    void shouldPropagateSessionFromParentContext() {
        AgentRuntime runtime = mock(AgentRuntime.class);
        when(runtime.getName()).thenReturn("coder");
        List<AgentRuntimeContext> capturedCtx = new ArrayList<>();
        when(runtime.call(anyList(), any(AgentRuntimeContext.class))).thenAnswer(invocation -> {
            capturedCtx.add(invocation.getArgument(1));
            return Mono.just(AgentMessage.builder()
                    .role(AgentMessageRole.ASSISTANT)
                    .content(List.of(AgentTextBlock.builder().text("完成").build()))
                    .build());
        });
        HandoffRegistry registry = new HandoffRegistry();
        registry.register(new HandoffTarget(runtime, "编码助手", null));
        HandoffTool tool = new HandoffTool(registry, AgentRuntimeContext.builder()
                .scopeId("parent-scope")
                .sessionId("session-1")
                .userId("parent-user")
                .build());

        AgentToolCallParam param = new AgentToolCallParam(Map.of("target", "coder"));
        StepVerifier.create(tool.callAsync(param))
                .expectNextCount(1)
                .verifyComplete();
        assertThat(capturedCtx).hasSize(1);
        AgentRuntimeContext merged = capturedCtx.get(0);
        // 参数缺失时回退到父级上下文
        assertThat(merged.getScopeId()).isEqualTo("parent-scope");
        assertThat(merged.getSessionId()).isEqualTo("session-1");
        assertThat(merged.getUserId()).isEqualTo("parent-user");
    }

    private static AgentMessage userMessage(String text) {
        return AgentMessage.builder()
                .role(AgentMessageRole.USER)
                .content(List.of(AgentTextBlock.builder().text(text).build()))
                .build();
    }

    private static AgentMessage assistantMessage(String text) {
        return AgentMessage.builder()
                .role(AgentMessageRole.ASSISTANT)
                .content(List.of(AgentTextBlock.builder().text(text).build()))
                .build();
    }
}