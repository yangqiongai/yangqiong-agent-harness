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
package com.yangqiongai.agent.harness.engine;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import com.yangqiongai.agent.harness.core.message.AgentMessage;
import com.yangqiongai.agent.harness.core.message.AgentMessageRole;
import com.yangqiongai.agent.harness.core.message.AgentTextBlock;
import com.yangqiongai.agent.harness.core.message.AgentToolResultBlock;
import com.yangqiongai.agent.harness.core.message.MessageFactory;
import com.yangqiongai.agent.harness.core.trace.ContextSnapshotListener;
import com.yangqiongai.agent.harness.model.ContextMessage;
import com.yangqiongai.agent.harness.model.ContextSnapshot;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

/**
 * 上下文快照组装测试
 * @author yangqiong
 */
class ContextSnapshotAssemblerTest {

    @Test
    void shouldAnnotateSourcesByRoleAndPosition() {
        AgentMessage memory = MessageFactory.createSystemMessage("相关记忆片段");
        AgentMessage systemPrompt = MessageFactory.createSystemMessage("你是助手");
        AgentMessage user = MessageFactory.createUserMessage("你好");
        AgentMessage assistant = MessageFactory.createAssistantMessage(
                List.of(AgentTextBlock.builder().text("回复").build()), null);
        AgentMessage tool = MessageFactory.createToolMessage(
                AgentToolResultBlock.of(List.of(AgentTextBlock.builder().text("工具输出").build())));

        List<ContextMessage> messages = ContextSnapshotAssembler.convert(
                List.of(memory, systemPrompt, user, assistant, tool), 65536);

        assertThat(messages).hasSize(5);
        assertThat(messages.get(0).getSource()).isEqualTo("memory_inject");
        assertThat(messages.get(1).getSource()).isEqualTo("system_prompt");
        assertThat(messages.get(2).getSource()).isEqualTo("history");
        assertThat(messages.get(3).getSource()).isEqualTo("history");
        assertThat(messages.get(4).getSource()).isEqualTo("tool_result");
        assertThat(messages.get(0).isTruncated()).isFalse();
    }

    @Test
    void shouldMarkSingleLeadingSystemAsPrompt() {
        List<ContextMessage> messages = ContextSnapshotAssembler.convert(
                List.of(MessageFactory.createSystemMessage("系统提示"), MessageFactory.createUserMessage("hi")), 65536);

        assertThat(messages.get(0).getSource()).isEqualTo("system_prompt");
        assertThat(messages.get(1).getSource()).isEqualTo("history");
    }

    @Test
    void shouldTruncateOversizedContent() {
        String oversized = "字".repeat(70000);
        List<ContextMessage> messages = ContextSnapshotAssembler.convert(
                List.of(MessageFactory.createUserMessage(oversized)), 65536);

        ContextMessage truncated = messages.get(0);
        assertThat(truncated.getContent().length()).isEqualTo(65536);
        assertThat(truncated.isTruncated()).isTrue();
    }

    @Test
    void shouldKeepShortContentIntact() {
        List<ContextMessage> messages = ContextSnapshotAssembler.convert(
                List.of(MessageFactory.createUserMessage("正常长度")), 65536);

        assertThat(messages.get(0).getContent()).isEqualTo("正常长度");
        assertThat(messages.get(0).isTruncated()).isFalse();
    }

    @Test
    void shouldBuildSnapshotFromContext() {
        AgentRuntimeContext runtime = AgentRuntimeContext.empty();
        runtime.put("taskId", "task-1");
        runtime.put("agentCode", "writer");
        runtime.put("harness.traceId", "trace-9");
        EngineContext ctx = new EngineContext(null, null, runtime, null, 10, null);

        ContextSnapshot snapshot = ContextSnapshotAssembler.build(ctx,
                List.of(MessageFactory.createUserMessage("hi")), "qwen-max", 3, 65536);

        assertThat(snapshot.getTaskId()).isEqualTo("task-1");
        assertThat(snapshot.getTraceId()).isEqualTo("trace-9");
        assertThat(snapshot.getAgentCode()).isEqualTo("writer");
        assertThat(snapshot.getModelCode()).isEqualTo("qwen-max");
        assertThat(snapshot.getCallSeq()).isEqualTo(3);
        assertThat(snapshot.getMessages()).hasSize(1);
    }

    @Test
    void shouldFillMissingAttributesAsNull() {
        EngineContext ctx = new EngineContext(null, null, AgentRuntimeContext.empty(), null, 10, null);

        ContextSnapshot snapshot = ContextSnapshotAssembler.build(ctx,
                List.of(MessageFactory.createUserMessage("hi")), "qwen-max", 1, 65536);

        assertThat(snapshot.getTaskId()).isNull();
        assertThat(snapshot.getTraceId()).isNull();
        assertThat(snapshot.getAgentCode()).isNull();
    }

    @Test
    void shouldNotThrowOnNullContent() {
        AgentMessage message = Mockito.mock(AgentMessage.class);
        Mockito.when(message.getRole()).thenReturn(AgentMessageRole.USER);
        Mockito.when(message.getTextContent()).thenReturn(null);

        List<ContextMessage> messages = ContextSnapshotAssembler.convert(List.of(message), 65536);

        assertThat(messages.get(0).getContent()).isEmpty();
        assertThat(messages.get(0).getSource()).isEqualTo("history");
    }
}
