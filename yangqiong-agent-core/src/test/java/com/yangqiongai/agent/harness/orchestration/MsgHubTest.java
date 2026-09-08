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
package com.yangqiongai.agent.harness.orchestration;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import com.yangqiongai.agent.harness.subagent.orchestration.MsgHub;
import org.junit.jupiter.api.Test;

import com.yangqiongai.agent.harness.core.AgentRuntime;
import com.yangqiongai.agent.harness.engine.AgentRuntimeContext;
import com.yangqiongai.agent.harness.core.event.AgentEvent;
import com.yangqiongai.agent.harness.core.message.AgentMessage;
import com.yangqiongai.agent.harness.core.message.AgentMessageRole;
import com.yangqiongai.agent.harness.core.message.AgentTextBlock;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * 消息中枢测试
 * @author yangqiong
 */
class MsgHubTest {

    @Test
    void shouldRegisterParticipant() {
        MsgHub hub = new MsgHub();
        hub.register(new StubRuntime("agent-1"));
        assertThat(hub.getParticipantCount()).isEqualTo(1);
        assertThat(hub.getParticipantNames()).contains("agent-1");
    }

    @Test
    void shouldUnregisterParticipant() {
        MsgHub hub = new MsgHub();
        hub.register(new StubRuntime("agent-1"));
        hub.unregister("agent-1");
        assertThat(hub.getParticipantCount()).isZero();
    }

    @Test
    void shouldBroadcastAndCollectResponses() {
        MsgHub hub = new MsgHub();
        hub.register(new StubRuntime("agent-1"));
        hub.register(new StubRuntime("agent-2"));
        Map<String, String> responses = hub.broadcast("hello", AgentRuntimeContext.empty()).block();
        assertThat(responses).hasSize(2);
        assertThat(responses.get("agent-1")).isEqualTo("RESPONSE:agent-1");
        assertThat(responses.get("agent-2")).isEqualTo("RESPONSE:agent-2");
    }

    @Test
    void shouldRecordBroadcastHistory() {
        MsgHub hub = new MsgHub();
        hub.register(new StubRuntime("agent-1"));
        hub.broadcast("first message", AgentRuntimeContext.empty()).block();
        assertThat(hub.getBroadcastHistory()).hasSize(1);
    }

    @Test
    void shouldCapBroadcastHistorySize() {
        MsgHub hub = new MsgHub();
        hub.register(new StubRuntime("agent-1"));
        for (int i = 0; i <= MsgHub.MAX_HISTORY; i++) {
            hub.broadcast("message-" + i, AgentRuntimeContext.empty()).block();
        }
        assertThat(hub.getBroadcastHistory()).hasSize(MsgHub.MAX_HISTORY);
    }

    @Test
    void shouldRunRoundRobinDiscussion() {
        MsgHub hub = new MsgHub();
        hub.register(new StubRuntime("agent-1"));
        hub.register(new StubRuntime("agent-2"));
        List<AgentMessage> discussion = hub.roundRobin("topic", AgentRuntimeContext.empty(), 2).block();
        // 轮询2轮，2个Agent各发言2次 = 4条回复
        assertThat(discussion).isNotEmpty();
    }

    @Test
    void shouldNotDuplicateSpeakerPrefixWhenParticipantPrefixesItsOwnOutput() {
        RecordingRuntime agent1 = new RecordingRuntime("agent-1", "【agent-1】技术侧完全可行");
        RecordingRuntime agent2 = new RecordingRuntime("agent-2", "【agent-2】产品侧需要看板");
        MsgHub hub = new MsgHub();
        hub.register(agent1);
        hub.register(agent2);
        List<AgentMessage> discussion = hub.roundRobin("topic", AgentRuntimeContext.empty(), 2).block();
        // 轮询2轮，2个Agent各发言2次 = 4条回复
        assertThat(discussion).hasSize(5);
        // 参与者输出已自带发言人前缀时，不应再被重复标注
        for (String input : agent1.getReceivedInputs()) {
            assertThat(input).doesNotContain("【agent-1】【agent-1】");
            assertThat(input).doesNotContain("【agent-2】【agent-2】");
        }
        for (String input : agent2.getReceivedInputs()) {
            assertThat(input).doesNotContain("【agent-1】【agent-1】");
            assertThat(input).doesNotContain("【agent-2】【agent-2】");
        }
    }

    @Test
    void shouldHandleEmptyHubRoundRobin() {
        MsgHub hub = new MsgHub();
        List<AgentMessage> result = hub.roundRobin("topic", AgentRuntimeContext.empty(), 2).block();
        assertThat(result).isEmpty();
    }

    @Test
    void shouldClearHistory() {
        MsgHub hub = new MsgHub();
        hub.register(new StubRuntime("agent-1"));
        hub.broadcast("test", AgentRuntimeContext.empty()).block();
        hub.clearHistory();
        assertThat(hub.getBroadcastHistory()).isEmpty();
    }

    @Test
    void shouldHandleBroadcastError() {
        MsgHub hub = new MsgHub();
        hub.register(new StubRuntime("agent-1"));
        hub.register(new ErrorRuntime("agent-2"));
        Map<String, String> responses = hub.broadcast("hello", AgentRuntimeContext.empty()).block();
        assertThat(responses).hasSize(2);
        assertThat(responses.get("agent-1")).isEqualTo("RESPONSE:agent-1");
        assertThat(responses.get("agent-2")).contains("回复失败");
    }

    /**
     * 桩运行时
     */
    private static class StubRuntime implements AgentRuntime {
        private final String name;

        StubRuntime(String name) {
            this.name = name;
        }

        @Override
        public Mono<AgentMessage> call(List<AgentMessage> inputs, AgentRuntimeContext context) {
            return Mono.just(AgentMessage.builder()
                    .role(AgentMessageRole.ASSISTANT)
                    .content(Collections.singletonList(AgentTextBlock.builder()
                            .text("RESPONSE:" + name).build()))
                    .build());
        }

        @Override
        public Flux<AgentEvent> stream(List<AgentMessage> inputs, AgentRuntimeContext context) {
            return Flux.empty();
        }

        @Override
        public String getName() {
            return name;
        }
    }

    /**
     * 报错运行时
     */
    private static class ErrorRuntime implements AgentRuntime {
        private final String name;

        ErrorRuntime(String name) {
            this.name = name;
        }

        @Override
        public Mono<AgentMessage> call(List<AgentMessage> inputs, AgentRuntimeContext context) {
            return Mono.error(new RuntimeException("模拟失败"));
        }

        @Override
        public Flux<AgentEvent> stream(List<AgentMessage> inputs, AgentRuntimeContext context) {
            return Flux.empty();
        }

        @Override
        public String getName() {
            return name;
        }
    }

    /**
     * 记录输入内容的桩运行时
     */
    private static class RecordingRuntime implements AgentRuntime {
        private final String name;

        private final String responseText;

        private final List<String> receivedInputs = new ArrayList<>();

        RecordingRuntime(String name, String responseText) {
            this.name = name;
            this.responseText = responseText;
        }

        /**
         * 获取该运行时收到的输入文本
         * @return
         */
        List<String> getReceivedInputs() {
            return receivedInputs;
        }

        @Override
        public Mono<AgentMessage> call(List<AgentMessage> inputs, AgentRuntimeContext context) {
            inputs.forEach(msg -> receivedInputs.add(msg.getTextContent()));
            return Mono.just(AgentMessage.builder()
                    .role(AgentMessageRole.ASSISTANT)
                    .content(Collections.singletonList(AgentTextBlock.builder().text(responseText).build()))
                    .build());
        }

        @Override
        public Flux<AgentEvent> stream(List<AgentMessage> inputs, AgentRuntimeContext context) {
            return Flux.empty();
        }

        @Override
        public String getName() {
            return name;
        }
    }
}
