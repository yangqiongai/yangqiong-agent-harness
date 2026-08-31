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
package com.yangqiong.agent.harness.subagent;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Collections;
import java.util.List;

import com.yangqiong.agent.harness.subagent.orchestration.SubagentDeclaration;
import com.yangqiong.agent.harness.tool.HarnessToolkit;
import com.yangqiong.agent.harness.engine.AgentRuntimeContext;
import org.junit.jupiter.api.Test;

/**
 * 子代理工具注册器测试
 * @author yangqiong
 */
class SubagentToolRegistrarTest {

    @Test
    void shouldRegisterNothingWhenToolkitNull() {
        SubagentDeclaration decl = SubagentDeclaration.builder()
                .name("worker")
                .description("工作")
                .build();
        SubagentToolRegistrar.register(null, Collections.singletonList(decl), null, null);
    }

    @Test
    void shouldRegisterNothingWhenDeclarationsNull() {
        HarnessToolkit toolkit = new HarnessToolkit(null);
        SubagentToolRegistrar.register(toolkit, null, null, null);
        assertThat(toolkit.isEmpty()).isTrue();
    }

    @Test
    void shouldRegisterNothingWhenDeclarationsEmpty() {
        HarnessToolkit toolkit = new HarnessToolkit(null);
        SubagentToolRegistrar.register(toolkit, Collections.emptyList(), null, null);
        assertThat(toolkit.isEmpty()).isTrue();
    }

    @Test
    void shouldRegisterAllDeclarations() {
        SubagentDeclaration decl1 = SubagentDeclaration.builder().name("a").description("A").build();
        SubagentDeclaration decl2 = SubagentDeclaration.builder().name("b").description("B").build();
        HarnessToolkit toolkit = new HarnessToolkit(null);
        SubagentToolRegistrar.register(toolkit, List.of(decl1, decl2), null, AgentRuntimeContext.empty());
        assertThat(toolkit.getTools()).hasSize(2);
    }

    @Test
    void shouldSkipNullDeclaration() {
        SubagentDeclaration decl = SubagentDeclaration.builder().name("a").description("A").build();
        HarnessToolkit toolkit = new HarnessToolkit(null);
        SubagentToolRegistrar.register(toolkit, java.util.Arrays.asList(null, decl), null, null);
        assertThat(toolkit.getTools()).hasSize(1);
    }

    @Test
    void shouldSkipNullNameDeclaration() {
        SubagentDeclaration decl = SubagentDeclaration.builder().description("A").build();
        HarnessToolkit toolkit = new HarnessToolkit(null);
        SubagentToolRegistrar.register(toolkit, List.of(decl), null, null);
        assertThat(toolkit.isEmpty()).isTrue();
    }

    @Test
    void register_多个声明注册多个agent_spawn工具() {
        SubagentDeclaration decl1 = SubagentDeclaration.builder().name("alpha").description("A").build();
        SubagentDeclaration decl2 = SubagentDeclaration.builder().name("beta").description("B").build();
        SubagentDeclaration decl3 = SubagentDeclaration.builder().name("gamma").description("G").build();
        HarnessToolkit toolkit = new HarnessToolkit(null);
        SubagentToolRegistrar.register(toolkit, List.of(decl1, decl2, decl3), null, AgentRuntimeContext.empty());
        assertThat(toolkit.getTools()).hasSize(3);
        assertThat(toolkit.getTools()).allSatisfy(tool ->
                assertThat(tool.getName()).startsWith("agent_spawn_"));
    }

    @Test
    void register_空声明列表返回空列表() {
        HarnessToolkit toolkit = new HarnessToolkit(null);
        SubagentToolRegistrar.register(toolkit, Collections.emptyList(), null, null);
        assertThat(toolkit.getTools()).isEmpty();
        assertThat(toolkit.isEmpty()).isTrue();
    }

    @Test
    void register_注册的工具name包含声明名称() {
        SubagentDeclaration decl = SubagentDeclaration.builder().name("analyst").description("分析").build();
        HarnessToolkit toolkit = new HarnessToolkit(null);
        SubagentToolRegistrar.register(toolkit, List.of(decl), null, null);
        assertThat(toolkit.getTools()).hasSize(1);
        assertThat(toolkit.getTools().get(0).getName()).contains("analyst");
    }
}
