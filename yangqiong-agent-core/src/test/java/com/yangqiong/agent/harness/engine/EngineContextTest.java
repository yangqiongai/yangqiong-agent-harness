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
package com.yangqiong.agent.harness.engine;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import com.yangqiong.agent.harness.core.message.AgentToolResultBlock;
import com.yangqiong.agent.harness.tool.HarnessToolkit;
import com.yangqiong.agent.harness.core.message.AgentMessage;
import com.yangqiong.agent.harness.core.message.AgentMessageRole;
import com.yangqiong.agent.harness.core.message.AgentTextBlock;
import com.yangqiong.agent.harness.core.model.AgentGenerateOptions;
import com.yangqiong.agent.harness.core.tool.AgentTool;
import com.yangqiong.agent.harness.core.tool.AgentToolCallParam;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

/**
 * 引擎上下文测试
 * @author yangqiong
 */
class EngineContextTest {

    @Test
    void shouldRegisterDynamicTools() {
        EngineContext ctx = new EngineContext(null, null, AgentRuntimeContext.empty(), null, 10, null);
        AgentTool tool = new StubTool("dyn_tool");
        ctx.registerDynamicTools(List.of(tool));
        assertThat(ctx.findTool("dyn_tool")).isNotNull();
    }

    @Test
    void shouldReturnNullWhenToolNotFound() {
        EngineContext ctx = new EngineContext(null, null, AgentRuntimeContext.empty(), null, 10, null);
        assertThat(ctx.findTool("not_exists")).isNull();
    }

    @Test
    void shouldFindStaticToolFirst() {
        HarnessToolkit toolkit = new HarnessToolkit(null);
        toolkit.addTool(new StubTool("shared"));
        EngineContext ctx = new EngineContext(null, null, AgentRuntimeContext.empty(), toolkit, 10, null);
        ctx.registerDynamicTools(List.of(new StubTool("shared")));
        AgentTool tool = ctx.findTool("shared");
        assertThat(tool).isNotNull();
        assertThat(tool.getName()).isEqualTo("shared");
    }

    @Test
    void shouldMergeAllToolSchemas() {
        HarnessToolkit toolkit = new HarnessToolkit(null);
        toolkit.addTool(new StubTool("static_tool"));
        EngineContext ctx = new EngineContext(null, null, AgentRuntimeContext.empty(), toolkit, 10, null);
        ctx.registerDynamicTools(List.of(new StubTool("dyn_tool")));
        List<Map<String, Object>> schemas = ctx.getAllToolSchemas();
        assertThat(schemas).hasSize(2);
    }

    @Test
    void shouldFindPlanModeToolsFromContext() {
        AgentRuntimeContext runtimeCtx = AgentRuntimeContext.empty();
        AgentTool planTool = new StubTool("plan_enter");
        runtimeCtx.getAttributes().put("_planModeTools", List.of(planTool));
        EngineContext ctx = new EngineContext(null, null, runtimeCtx, null, 10, null);
        assertThat(ctx.findTool("plan_enter")).isNotNull();
    }

    @Test
    void shouldIncludePlanModeToolsInSchemas() {
        AgentRuntimeContext runtimeCtx = AgentRuntimeContext.empty();
        AgentTool planTool = new StubTool("plan_enter");
        runtimeCtx.getAttributes().put("_planModeTools", List.of(planTool));
        EngineContext ctx = new EngineContext(null, null, runtimeCtx, null, 10, null);
        List<Map<String, Object>> schemas = ctx.getAllToolSchemas();
        assertThat(schemas).hasSize(1);
    }

    @Test
    void shouldManageMessageHistory() {
        EngineContext ctx = new EngineContext(null, null, AgentRuntimeContext.empty(), null, 10, null);
        AgentMessage msg = AgentMessage.builder()
                .role(AgentMessageRole.USER)
                .content(Collections.singletonList(AgentTextBlock.builder().text("hi").build()))
                .build();
        ctx.addMessage(msg);
        assertThat(ctx.getMessages()).hasSize(1);
    }

    @Test
    void shouldNotAddNullMessage() {
        EngineContext ctx = new EngineContext(null, null, AgentRuntimeContext.empty(), null, 10, null);
        ctx.addMessage(null);
        assertThat(ctx.getMessages()).isEmpty();
    }

    @Test
    void shouldReturnMessageCopy() {
        EngineContext ctx = new EngineContext(null, null, AgentRuntimeContext.empty(), null, 10, null);
        AgentMessage msg = AgentMessage.builder()
                .role(AgentMessageRole.USER)
                .content(Collections.singletonList(AgentTextBlock.builder().text("hi").build()))
                .build();
        ctx.addMessage(msg);
        List<AgentMessage> list1 = ctx.getMessages();
        List<AgentMessage> list2 = ctx.getMessages();
        assertThat(list1).isNotSameAs(list2);
        assertThat(list1).isEqualTo(list2);
    }

    @Test
    void shouldReturnNullSystemPromptWhenNotProvided() {
        EngineContext ctx = new EngineContext(null, null, AgentRuntimeContext.empty(), null, 10, null);
        assertThat(ctx.getSystemPrompt()).isNull();
    }

    @Test
    void shouldReturnMaxIters() {
        EngineContext ctx = new EngineContext(null, null, AgentRuntimeContext.empty(), null, 15, null);
        assertThat(ctx.getMaxIters()).isEqualTo(15);
    }

    @Test
    void shouldHandleCastErrorInPlanTools() {
        AgentRuntimeContext runtimeCtx = AgentRuntimeContext.empty();
        runtimeCtx.getAttributes().put("_planModeTools", "not a list");
        EngineContext ctx = new EngineContext(null, null, runtimeCtx, null, 10, null);
        assertThat(ctx.findTool("plan_enter")).isNull();
    }

    @Test
    void shouldExposeAgentNameFromConstructor() {
        EngineContext ctx = new EngineContext("myAgent", null, AgentRuntimeContext.empty(), null, 10, null);
        assertThat(ctx.getAgentName()).isEqualTo("myAgent");
    }

    @Test
    void shouldExposeSystemPromptFromConstructor() {
        EngineContext ctx = new EngineContext(null, "you are an agent",
                AgentRuntimeContext.empty(), null, 10, null);
        assertThat(ctx.getSystemPrompt()).isEqualTo("you are an agent");
    }

    @Test
    void shouldExposeGenerateOptionsFromConstructor() {
        AgentGenerateOptions options = AgentGenerateOptions.builder().temperature(0.5).build();
        EngineContext ctx = new EngineContext(null, null, AgentRuntimeContext.empty(), null, 10, options);
        assertThat(ctx.getGenerateOptions()).isSameAs(options);
    }

    @Test
    void shouldIncrementIterAndReturnValue() {
        EngineContext ctx = new EngineContext(null, null, AgentRuntimeContext.empty(), null, 10, null);
        assertThat(ctx.getCurrentIter()).isEqualTo(0);
        assertThat(ctx.incrementIter()).isEqualTo(1);
        assertThat(ctx.getCurrentIter()).isEqualTo(1);
        assertThat(ctx.incrementIter()).isEqualTo(2);
        assertThat(ctx.getCurrentIter()).isEqualTo(2);
    }

    @Test
    void shouldReachMaxItersWhenCurrentAtOrAboveMax() {
        EngineContext ctx = new EngineContext(null, null, AgentRuntimeContext.empty(), null, 3, null);
        assertThat(ctx.reachedMaxIters()).isFalse();
        ctx.incrementIter();
        ctx.incrementIter();
        assertThat(ctx.reachedMaxIters()).isFalse();
        ctx.incrementIter();
        assertThat(ctx.reachedMaxIters()).isTrue();
    }

    @Test
    void shouldReachMaxItersImmediatelyWhenMaxIsZero() {
        EngineContext ctx = new EngineContext(null, null, AgentRuntimeContext.empty(), null, 0, null);
        assertThat(ctx.reachedMaxIters()).isTrue();
    }

    @Test
    void shouldExposeConversationAndKeepMessagesAlias() {
        EngineContext ctx = new EngineContext(null, null, AgentRuntimeContext.empty(), null, 10, null);
        AgentMessage msg = AgentMessage.builder()
                .role(AgentMessageRole.USER)
                .content(Collections.singletonList(AgentTextBlock.builder().text("hi").build()))
                .build();
        ctx.addMessage(msg);
        assertThat(ctx.getConversation()).hasSize(1);
        assertThat(ctx.getMessages()).hasSize(1);
        assertThat(ctx.getConversation()).isEqualTo(ctx.getMessages());
        assertThat(ctx.getConversation()).isNotSameAs(ctx.getMessages());
    }

    /**
     * 桩工具
     */
    private static class StubTool implements AgentTool {
        private final String name;

        StubTool(String name) {
            this.name = name;
        }

        @Override
        public String getName() { return name; }

        @Override
        public String getDescription() { return "stub"; }

        @Override
        public Map<String, Object> getParameters() { return Map.of(); }

        @Override
        public Mono<AgentToolResultBlock> callAsync(AgentToolCallParam param) {
            return Mono.empty();
        }
    }
}
