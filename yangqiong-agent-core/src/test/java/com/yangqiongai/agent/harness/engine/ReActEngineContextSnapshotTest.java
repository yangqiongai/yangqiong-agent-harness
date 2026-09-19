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
import java.util.concurrent.CopyOnWriteArrayList;

import com.yangqiongai.agent.harness.core.message.MessageFactory;
import com.yangqiongai.agent.harness.core.trace.ContextSnapshotListener;
import com.yangqiongai.agent.harness.model.ContextSnapshot;
import com.yangqiongai.agent.harness.model.ModelCaller;
import com.yangqiongai.agent.harness.model.ModelResponseParser;
import com.yangqiongai.agent.harness.tool.ToolExecutor;
import com.yangqiongai.agent.harness.core.message.AgentTextBlock;
import com.yangqiongai.agent.harness.core.message.AgentMessageRole;
import com.yangqiongai.agent.harness.core.model.AgentChatResponse;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

/**
 * 上下文快照采集钩子测试
 * @author yangqiong
 */
class ReActEngineContextSnapshotTest {

    @Test
    void shouldFireSnapshotWithCallSeqIncrement() {
        List<ContextSnapshot> snapshots = new CopyOnWriteArrayList<>();
        EngineFixture fixture = new EngineFixture(snapshots::add);

        fixture.run("hi");
        fixture.run("hi again");

        assertThat(snapshots).hasSize(2);
        assertThat(snapshots.get(0).getCallSeq()).isEqualTo(1);
        assertThat(snapshots.get(1).getCallSeq()).isEqualTo(2);
        ContextSnapshot first = snapshots.get(0);
        assertThat(first.getTaskId()).isEqualTo("task-1");
        assertThat(first.getAgentCode()).isEqualTo("writer");
        assertThat(first.getTraceId()).isEqualTo("trace-9");
        assertThat(first.getMessages()).isNotEmpty();
        assertThat(first.getMessages().get(0).getSource()).isEqualTo("history");
    }

    @Test
    void shouldTruncateOversizedContent() {
        List<ContextSnapshot> snapshots = new CopyOnWriteArrayList<>();
        EngineFixture fixture = new EngineFixture(snapshots::add);

        fixture.run("长".repeat(70000));

        assertThat(snapshots).hasSize(1);
        assertThat(snapshots.get(0).getMessages().stream()
                .anyMatch(m -> m.isTruncated()
                        && m.getContent().length() == AbstractAgentLoop.CONTEXT_MESSAGE_MAX_CHARS))
                .isTrue();
    }

    @Test
    void shouldSwallowListenerException() {
        EngineFixture fixture = new EngineFixture(snapshot -> {
            throw new IllegalStateException("listener boom");
        });

        fixture.run("hi");

        assertThat(fixture.getLastSeq()).isEqualTo(1);
    }

    @Test
    void shouldSkipWhenListenerAbsent() {
        List<ContextSnapshot> snapshots = new CopyOnWriteArrayList<>();
        EngineFixture fixture = new EngineFixture(null);

        fixture.run("hi");

        assertThat(snapshots).isEmpty();
    }

    /**
     * 引擎夹具：单轮文本响应 + 可注入监听器的引擎上下文
     */
    private static final class EngineFixture {

        /**
         * 引擎上下文（跨调用复用以验证调用序号递增）
         */
        private final EngineContext ctx;

        /**
         * ReAct引擎
         */
        private final ReActEngine engine;

        /**
         * 构造夹具
         * @param listener 快照监听器（可空）
         */
        private EngineFixture(ContextSnapshotListener listener) {
            AgentRuntimeContext runtime = AgentRuntimeContext.empty();
            runtime.put("taskId", "task-1");
            runtime.put("agentCode", "writer");
            runtime.put("harness.traceId", "trace-9");

            ModelCaller modelCaller = Mockito.mock(ModelCaller.class);
            AgentChatResponse response = new AgentChatResponse(List.of(
                    AgentTextBlock.builder().text("done").build()), null);
            Mockito.when(modelCaller.stream(Mockito.any(), Mockito.any())).thenReturn(Flux.just(response));

            this.engine = new ReActEngine(modelCaller, Mockito.mock(ToolExecutor.class),
                    new MiddlewareChain(List.of()), new ModelResponseParser(), null);
            this.ctx = new EngineContext(null, null, runtime, null, 10, null);
            if (listener != null) {
                ctx.setContextSnapshotListener(listener);
            }
        }

        /**
         * 执行一轮引擎调用
         * @param inputText 用户输入
         */
        private void run(String inputText) {
            StepVerifier.create(engine.call(List.of(MessageFactory.createUserMessage(inputText)), ctx))
                    .assertNext(msg -> assertThat(msg.getRole()).isEqualTo(AgentMessageRole.ASSISTANT))
                    .verifyComplete();
        }

        /**
         * 读取当前模型调用序号
         * @return
         */
        private int getLastSeq() {
            return ctx.nextModelCallSeq() - 1;
        }
    }
}
