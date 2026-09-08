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
package com.yangqiongai.agent.harness.server;

import java.util.List;
import java.util.Map;

import com.yangqiongai.agent.harness.core.event.AgentEvent;
import com.yangqiongai.agent.harness.core.event.AgentEventType;
import com.yangqiongai.agent.harness.core.event.RequireUserConfirmEvent;
import com.yangqiongai.agent.harness.core.message.AgentToolUseBlock;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.Test;
import org.springframework.http.codec.ServerSentEvent;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Agent事件SSE映射测试
 * @author yangqiong
 */
class AgentEventSseMapperTest {

    /**
     * 事件载荷
     */
    private static final ObjectMapper MAPPER = new ObjectMapper();

    /**
     * SSE映射器
     */
    private final AgentEventSseMapper mapper = new AgentEventSseMapper();

    @Test
    void genericEventSerializedWithType() throws Exception {
        AgentEvent event = AgentEvent.of(AgentEventType.TEXT_BLOCK_DELTA, "hello");

        ServerSentEvent<String> sse = mapper.toSse(event);
        Map<String, Object> data = MAPPER.readValue(sse.data(), Map.class);

        assertThat(sse.event()).isEqualTo(AgentEventType.TEXT_BLOCK_DELTA.name());
        assertThat(data).containsEntry("type", AgentEventType.TEXT_BLOCK_DELTA.name())
                .containsEntry("payload", "hello");
    }

    @Test
    void requireUserConfirmFlattensPendingToolCalls() throws Exception {
        List<AgentToolUseBlock> pending = List.of(
                new AgentToolUseBlock("local_shell", "call-1", Map.of("command", "echo hi")));
        RequireUserConfirmEvent event = new RequireUserConfirmEvent(pending);

        ServerSentEvent<String> sse = mapper.toSse(event);
        Map<String, Object> data = MAPPER.readValue(sse.data(), Map.class);

        assertThat(data).containsEntry("type", AgentEventType.REQUIRE_USER_CONFIRM.name());
        List<?> confirmations = (List<?>) data.get("pendingConfirmations");
        assertThat(confirmations).hasSize(1);
        @SuppressWarnings("unchecked")
        Map<String, Object> confirmation = (Map<String, Object>) confirmations.get(0);
        assertThat(confirmation).containsEntry("toolCallId", "call-1")
                .containsEntry("toolName", "local_shell");
    }

    @Test
    void errorEventCarriesMessage() throws Exception {
        ServerSentEvent<String> sse = mapper.errorSse(new IllegalStateException("模型不可用"));
        Map<String, Object> data = MAPPER.readValue(sse.data(), Map.class);

        assertThat(sse.event()).isEqualTo(AgentEventType.ERROR.name());
        assertThat(data).containsEntry("message", "模型不可用");
    }

    @Test
    void toSseByStepVerifierEmitsOnlyOneElement() {
        Flux<ServerSentEvent<String>> flux = Flux.just(
                AgentEvent.of(AgentEventType.AGENT_START, Map.of("run", "1")))
                .map(mapper::toSse);

        StepVerifier.create(flux)
                .assertNext(sse -> assertThat(sse.event()).isEqualTo("AGENT_START"))
                .expectComplete()
                .verify();
    }
}