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
package com.yangqiongai.agent.harness.subagent;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Collections;

import com.yangqiongai.agent.harness.subagent.orchestration.AutoOrchestrateTool;
import com.yangqiongai.agent.harness.subagent.orchestration.SubagentDeclaration;
import com.yangqiongai.agent.harness.tool.HarnessToolkit;
import com.yangqiongai.agent.harness.engine.AgentRuntimeContext;
import org.junit.jupiter.api.Test;

/**
 * 子代理中间件测试
 * @author yangqiong
 */
class SubagentsMiddlewareTest {

    @Test
    void shouldInjectSubagentDescription() {
        SubagentDeclaration decl = SubagentDeclaration.builder()
                .name("researcher")
                .description("研究代理")
                .build();
        HarnessToolkit toolkit = new HarnessToolkit(null);
        SubagentsMiddleware middleware = new SubagentsMiddleware(
                Collections.singletonList(decl), null, toolkit);

        String result = middleware.onSystemPrompt("base", AgentRuntimeContext.empty());
        assertThat(result).contains("base");
        assertThat(result).contains("researcher");
        assertThat(result).contains("研究代理");
        assertThat(result).contains("agent_spawn");
    }

    @Test
    void shouldRegisterToolsToToolkit() {
        SubagentDeclaration decl = SubagentDeclaration.builder()
                .name("researcher")
                .description("研究代理")
                .build();
        HarnessToolkit toolkit = new HarnessToolkit(null);
        SubagentsMiddleware middleware = new SubagentsMiddleware(
                Collections.singletonList(decl), null, toolkit);

        middleware.onSystemPrompt("base", AgentRuntimeContext.empty());
        assertThat(toolkit.getTools()).hasSize(1);
        assertThat(toolkit.getTools().get(0).getName()).isEqualTo("agent_spawn_researcher");
    }

    @Test
    void shouldNotRegisterTwice() {
        SubagentDeclaration decl = SubagentDeclaration.builder()
                .name("researcher")
                .description("研究代理")
                .build();
        HarnessToolkit toolkit = new HarnessToolkit(null);
        SubagentsMiddleware middleware = new SubagentsMiddleware(
                Collections.singletonList(decl), null, toolkit);

        middleware.onSystemPrompt("base", AgentRuntimeContext.empty());
        middleware.onSystemPrompt("base2", AgentRuntimeContext.empty());
        assertThat(toolkit.getTools()).hasSize(1);
    }

    @Test
    void shouldRegisterMultipleDeclarations() {
        SubagentDeclaration decl1 = SubagentDeclaration.builder()
                .name("r1")
                .description("研究1")
                .build();
        SubagentDeclaration decl2 = SubagentDeclaration.builder()
                .name("r2")
                .description("研究2")
                .build();
        HarnessToolkit toolkit = new HarnessToolkit(null);
        SubagentsMiddleware middleware = new SubagentsMiddleware(
                java.util.List.of(decl1, decl2), null, toolkit);

        middleware.onSystemPrompt("base", AgentRuntimeContext.empty());
        assertThat(toolkit.getTools()).hasSize(2);
    }

    @Test
    void shouldHandleEmptyDeclarations() {
        HarnessToolkit toolkit = new HarnessToolkit(null);
        SubagentsMiddleware middleware = new SubagentsMiddleware(
                Collections.emptyList(), null, toolkit);
        String result = middleware.onSystemPrompt("base", AgentRuntimeContext.empty());
        assertThat(result).contains("base");
    }

    @Test
    void onSystemPrompt_注入子代理描述到提示词() {
        SubagentDeclaration decl = SubagentDeclaration.builder()
                .name("researcher")
                .description("研究代理")
                .build();
        HarnessToolkit toolkit = new HarnessToolkit(null);
        SubagentsMiddleware middleware = new SubagentsMiddleware(
                Collections.singletonList(decl), null, toolkit);
        String result = middleware.onSystemPrompt("base prompt", AgentRuntimeContext.empty());
        assertThat(result).contains("base prompt");
        assertThat(result).contains("你可以通过调用 agent_spawn 工具委派以下子代理执行子任务");
        assertThat(result).contains("- researcher: 研究代理");
    }

    @Test
    void onSystemPrompt_注册agent_spawn动态工具() {
        SubagentDeclaration decl = SubagentDeclaration.builder()
                .name("analyst")
                .description("分析代理")
                .build();
        HarnessToolkit toolkit = new HarnessToolkit(null);
        SubagentsMiddleware middleware = new SubagentsMiddleware(
                Collections.singletonList(decl), null, toolkit);
        middleware.onSystemPrompt("base", AgentRuntimeContext.empty());
        assertThat(toolkit.getTools()).hasSize(1);
        assertThat(toolkit.getTools().get(0).getName()).isEqualTo("agent_spawn_analyst");
        assertThat(toolkit.getTools().get(0)).isInstanceOf(AgentSpawnTool.class);
    }

    @Test
    void onSystemPrompt_空声明列表不注入() {
        HarnessToolkit toolkit = new HarnessToolkit(null);
        SubagentsMiddleware middleware = new SubagentsMiddleware(
                Collections.emptyList(), null, toolkit);
        String result = middleware.onSystemPrompt("base", AgentRuntimeContext.empty());
        assertThat(toolkit.isEmpty()).isTrue();
        assertThat(result).doesNotContain("- ");
    }

    @Test
    void onSystemPrompt_注册auto_orchestrate时自动注入使用话术() {
        SubagentDeclaration decl = SubagentDeclaration.builder()
                .name("researcher")
                .description("研究代理")
                .build();
        HarnessToolkit toolkit = new HarnessToolkit(null);
        AutoOrchestrateTool autoTool = new AutoOrchestrateTool(null,
                Collections.singletonList(decl), 3, true);
        SubagentsMiddleware middleware = new SubagentsMiddleware(
                Collections.singletonList(decl), null, toolkit, autoTool);

        String result = middleware.onSystemPrompt("base", AgentRuntimeContext.empty());
        assertThat(result).contains("调用 auto_orchestrate 工具自动编排子代理协作");
        assertThat(toolkit.getTools()).extracting(t -> t.getName()).contains("auto_orchestrate");
    }

    @Test
    void onSystemPrompt_未注册auto_orchestrate时不注入话术() {
        SubagentDeclaration decl = SubagentDeclaration.builder()
                .name("researcher")
                .description("研究代理")
                .build();
        HarnessToolkit toolkit = new HarnessToolkit(null);
        SubagentsMiddleware middleware = new SubagentsMiddleware(
                Collections.singletonList(decl), null, toolkit);

        String result = middleware.onSystemPrompt("base", AgentRuntimeContext.empty());
        assertThat(result).doesNotContain("auto_orchestrate");
        assertThat(toolkit.getTools()).extracting(t -> t.getName()).doesNotContain("auto_orchestrate");
    }
}
