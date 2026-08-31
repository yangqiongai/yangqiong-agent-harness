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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import com.yangqiong.agent.harness.HarnessRuntimeBuilder;
import com.yangqiong.agent.harness.core.AgentRuntime;
import com.yangqiong.agent.harness.core.message.AgentMessage;
import com.yangqiong.agent.harness.core.message.AgentMessageRole;
import com.yangqiong.agent.harness.core.message.AgentTextBlock;
import com.yangqiong.agent.harness.core.model.AgentChatResponse;
import com.yangqiong.agent.harness.core.model.AgentModel;
import com.yangqiong.agent.harness.engine.AgentRuntimeContext;
import com.yangqiong.agent.harness.core.message.MessageFactory;
import com.yangqiong.agent.harness.subagent.orchestration.SubagentDeclaration;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import reactor.core.publisher.Flux;

/**
 * 子代理启用模式集成测试
 * <p>
 * 验证子代理主开关的智能启用语义：
 * 传入非空静态声明时自动启用静态模式（向后兼容）；
 * enableSubagents()且未传声明时走LLM动态生成模式；
 * 两者均未配置时默认关闭，不注入子代理能力。
 * </p>
 * @author yangqiong
 */
class SubagentEnableModeIntegrationTest {

    /**
     * 构建返回纯文本的模型桩
     * @return
     */
    private AgentModel textModel() {
        AgentModel model = mock(AgentModel.class);
        AgentChatResponse response = new AgentChatResponse(
                List.of(AgentTextBlock.builder().text("完成").build()), null);
        when(model.stream(any(), any(), any())).thenReturn(Flux.just(response));
        return model;
    }

    /**
     * 运行一次Agent调用并捕获模型收到的消息与工具Schema
     * @param model
     * @param builder
     * @return
     */
    private CapturedResult run(AgentModel model, HarnessRuntimeBuilder builder) {
        AgentRuntime runtime = builder
                .name("subagent-enable-mode-test")
                .model(model)
                .systemPrompt("基础系统提示词")
                .build();
        runtime.call(List.of(MessageFactory.createUserMessage("你好")),
                AgentRuntimeContext.empty()).block(Duration.ofSeconds(30));
        ArgumentCaptor<List<AgentMessage>> messagesCaptor = ArgumentCaptor.forClass(List.class);
        ArgumentCaptor<List<Map<String, Object>>> toolsCaptor = ArgumentCaptor.forClass(List.class);
        verify(model, atLeastOnce()).stream(messagesCaptor.capture(), toolsCaptor.capture(), any());
        return new CapturedResult(messagesCaptor.getValue(), toolsCaptor.getValue());
    }

    /**
     * 传了静态声明但未调用enableSubagents时自动启用静态模式（向后兼容）
     */
    @Test
    void 静态声明自动启用_注入子代理描述与auto_orchestrate话术() {
        SubagentDeclaration decl = SubagentDeclaration.builder()
                .name("researcher")
                .description("研究代理")
                .build();
        AgentModel model = textModel();
        CapturedResult result = run(model, new HarnessRuntimeBuilder()
                .subagentDeclarations(List.of(decl)));

        assertThat(result.systemPrompt()).contains("agent_spawn 工具委派以下子代理");
        assertThat(result.systemPrompt()).contains("- researcher: 研究代理");
        assertThat(result.systemPrompt()).contains("调用 auto_orchestrate 工具自动编排子代理协作");
        assertThat(result.toolsText()).contains("agent_spawn_researcher");
        assertThat(result.toolsText()).contains("auto_orchestrate");
    }

    /**
     * enableSubagents()且未传声明时走动态生成模式：仅注入auto_orchestrate，不注入静态子代理描述
     */
    @Test
    void enableSubagents无声明_走动态生成模式不注入静态描述() {
        AgentModel model = textModel();
        CapturedResult result = run(model, new HarnessRuntimeBuilder().enableSubagents());

        assertThat(result.systemPrompt()).contains("调用 auto_orchestrate 工具自动编排子代理协作");
        assertThat(result.systemPrompt()).doesNotContain("agent_spawn 工具委派以下子代理");
        assertThat(result.toolsText()).contains("auto_orchestrate");
    }

    /**
     * 默认关闭：既不传声明也不调用enableSubagents时不注入子代理能力
     */
    @Test
    void 默认关闭_不注入子代理能力() {
        AgentModel model = textModel();
        CapturedResult result = run(model, new HarnessRuntimeBuilder());

        assertThat(result.systemPrompt()).doesNotContain("agent_spawn");
        assertThat(result.systemPrompt()).doesNotContain("auto_orchestrate");
        assertThat(result.toolsText()).doesNotContain("auto_orchestrate");
    }

    /**
     * 捕获结果封装
     */
    private record CapturedResult(List<AgentMessage> messages, List<Map<String, Object>> tools) {

        String systemPrompt() {
            return messages.stream()
                    .filter(m -> m.getRole() == AgentMessageRole.SYSTEM)
                    .map(AgentMessage::getTextContent)
                    .findFirst().orElse("");
        }

        String toolsText() {
            return String.valueOf(tools);
        }
    }
}
