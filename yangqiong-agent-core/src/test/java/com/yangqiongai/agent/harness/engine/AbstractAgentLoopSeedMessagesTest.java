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
import java.util.Map;

import com.yangqiongai.agent.harness.core.message.MessageFactory;
import com.yangqiongai.agent.harness.core.message.AgentMessage;
import com.yangqiongai.agent.harness.core.message.AgentMessageRole;
import com.yangqiongai.agent.harness.core.message.AgentTextBlock;
import com.yangqiongai.agent.harness.model.ModelCaller;
import com.yangqiongai.agent.harness.model.ModelResponseParser;
import com.yangqiongai.agent.harness.tool.ToolExecutor;
import com.yangqiongai.agent.harness.core.model.AgentChatResponse;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

/**
 * 种子上下文并入测试
 * @author yangqiong
 */
class AbstractAgentLoopSeedMessagesTest {

    @Test
    void shouldMergeUserAndAssistantSeedsBeforeInput() {
        List<AgentMessage> captured = runWithSeeds(
                Map.of("role", "user", "content", "历史用户消息"),
                Map.of("role", "assistant", "content", "历史助手回复"));

        assertThat(captured).hasSize(3);
        assertThat(captured.get(0).getRole()).isEqualTo(AgentMessageRole.USER);
        assertThat(captured.get(0).getTextContent()).isEqualTo("历史用户消息");
        assertThat(captured.get(1).getRole()).isEqualTo(AgentMessageRole.ASSISTANT);
        assertThat(captured.get(1).getTextContent()).isEqualTo("历史助手回复");
        assertThat(captured.get(2).getRole()).isEqualTo(AgentMessageRole.USER);
        assertThat(captured.get(2).getTextContent()).isEqualTo("本次输入");
    }

    @Test
    void shouldSkipSystemSeedEntries() {
        List<AgentMessage> captured = runWithSeeds(
                Map.of("role", "system", "content", "种子系统提示"));

        assertThat(captured).hasSize(1);
        assertThat(captured.get(0).getTextContent()).isEqualTo("本次输入");
    }

    @Test
    void shouldDowngradeToolSeedToUserText() {
        List<AgentMessage> captured = runWithSeeds(
                Map.of("role", "tool", "content", "查询结果42"));

        assertThat(captured).hasSize(2);
        assertThat(captured.get(0).getRole()).isEqualTo(AgentMessageRole.USER);
        assertThat(captured.get(0).getTextContent()).isEqualTo("[工具结果]查询结果42");
    }

    @Test
    void shouldSupportUppercaseRoleAndBlockListContent() {
        List<AgentMessage> captured = runWithSeeds(
                Map.of("role", "USER", "content", List.of(
                        Map.of("text", "块一"), Map.of("text", "块二"))));

        assertThat(captured).hasSize(2);
        assertThat(captured.get(0).getTextContent()).isEqualTo("块一块二");
    }

    @Test
    void shouldSkipBrokenSeedEntryWithoutBreakingLoop() {
        Map<String, Object> broken = Mockito.mock(Map.class);
        Mockito.when(broken.get(Mockito.anyString()))
                .thenThrow(new IllegalStateException("boom"));

        List<AgentMessage> captured = runWithSeeds(
                broken,
                Map.of("role", "user", "content", "正常种子"));

        assertThat(captured).hasSize(2);
        assertThat(captured.get(0).getTextContent()).isEqualTo("正常种子");
        assertThat(captured.get(1).getTextContent()).isEqualTo("本次输入");
    }

    @Test
    void shouldRunNormallyWithoutSeeds() {
        ModelCaller modelCaller = Mockito.mock(ModelCaller.class);
        AgentChatResponse response = new AgentChatResponse(List.of(
                AgentTextBlock.builder().text("done").build()), null);
        Mockito.when(modelCaller.stream(Mockito.any(), Mockito.any()))
                .thenAnswer(invocation -> {
                    @SuppressWarnings("unchecked")
                    List<AgentMessage> messages = (List<AgentMessage>) invocation.getArgument(0);
                    assertThat(messages).hasSize(1);
                    return Flux.just(response);
                });
        ReActEngine engine = new ReActEngine(modelCaller, Mockito.mock(ToolExecutor.class),
                new MiddlewareChain(List.of()), new ModelResponseParser(), null);
        EngineContext ctx = new EngineContext(null, null, AgentRuntimeContext.empty(), null, 10, null);

        StepVerifier.create(engine.call(List.of(MessageFactory.createUserMessage("本次输入")), ctx))
                .assertNext(msg -> assertThat(msg.getTextContent()).isEqualTo("done"))
                .verifyComplete();
    }

    /**
     * 携带种子执行一轮引擎调用并捕获注入模型的完整消息列表
     * @param seeds 种子消息映射列表
     * @return 注入模型的消息列表
     */
    @SuppressWarnings("unchecked")
    private List<AgentMessage> runWithSeeds(Map<String, Object>... seeds) {
        List<AgentMessage> captured = new java.util.concurrent.CopyOnWriteArrayList<>();
        ModelCaller modelCaller = Mockito.mock(ModelCaller.class);
        AgentChatResponse response = new AgentChatResponse(List.of(
                AgentTextBlock.builder().text("done").build()), null);
        Mockito.when(modelCaller.stream(Mockito.any(), Mockito.any()))
                .thenAnswer(invocation -> {
                    captured.addAll((List<AgentMessage>) invocation.getArgument(0));
                    return Flux.just(response);
                });
        ReActEngine engine = new ReActEngine(modelCaller, Mockito.mock(ToolExecutor.class),
                new MiddlewareChain(List.of()), new ModelResponseParser(), null);
        AgentRuntimeContext runtime = AgentRuntimeContext.empty();
        runtime.put(AbstractAgentLoop.ATTR_SEED_MESSAGES, List.of(seeds));
        EngineContext ctx = new EngineContext(null, null, runtime, null, 10, null);

        StepVerifier.create(engine.call(List.of(MessageFactory.createUserMessage("本次输入")), ctx))
                .assertNext(msg -> assertThat(msg.getTextContent()).isEqualTo("done"))
                .verifyComplete();
        return captured;
    }
}
