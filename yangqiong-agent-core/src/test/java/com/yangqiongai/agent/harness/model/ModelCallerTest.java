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
package com.yangqiongai.agent.harness.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;

import com.yangqiongai.agent.harness.core.message.AgentTextBlock;
import com.yangqiongai.agent.harness.core.model.AgentChatResponse;
import com.yangqiongai.agent.harness.core.model.AgentGenerateOptions;
import com.yangqiongai.agent.harness.core.model.AgentModel;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

/**
 * 模型调用器测试
 * @author yangqiong
 */
class ModelCallerTest {

    @Test
    void shouldCallModelSync() {
        AgentModel model = Mockito.mock(AgentModel.class);
        AgentTextBlock textBlock = AgentTextBlock.builder().text("hi").build();
        AgentChatResponse response = new AgentChatResponse(List.of(textBlock), null);
        when(model.generate(any(), any(), any())).thenReturn(response);

        ModelCaller caller = new ModelCaller(model);
        StepVerifier.create(caller.call(List.of(), List.of()))
                .assertNext(resp -> assertThat(resp.getContent()).hasSize(1))
                .verifyComplete();
    }

    @Test
    void shouldCallModelStream() {
        AgentModel model = Mockito.mock(AgentModel.class);
        AgentTextBlock textBlock = AgentTextBlock.builder().text("hi").build();
        AgentChatResponse response = new AgentChatResponse(List.of(textBlock), null);
        when(model.stream(any(), any(), any())).thenReturn(Flux.just(response));

        ModelCaller caller = new ModelCaller(model);
        StepVerifier.create(caller.stream(List.of(), List.of()))
                .assertNext(resp -> assertThat(resp.getContent()).hasSize(1))
                .verifyComplete();
    }

    @Test
    void shouldPassToolSchemasToModel() {
        AgentModel model = Mockito.mock(AgentModel.class);
        AgentChatResponse response = new AgentChatResponse(List.of(), null);
        when(model.generate(any(), any(), any())).thenAnswer(invocation -> {
            List<Map<String, Object>> tools = invocation.getArgument(1);
            assertThat(tools).hasSize(1);
            return response;
        });

        ModelCaller caller = new ModelCaller(model);
        Map<String, Object> schema = Map.of("type", "function");
        caller.call(List.of(), List.of(schema)).block();
        Mockito.verify(model).generate(any(), any(), any());
    }

    @Test
    void shouldPassEmptyListWhenToolsIsNull() {
        AgentModel model = Mockito.mock(AgentModel.class);
        AgentChatResponse response = new AgentChatResponse(List.of(), null);
        when(model.generate(any(), any(), any())).thenAnswer(invocation -> {
            List<Map<String, Object>> tools = invocation.getArgument(1);
            assertThat(tools).isEmpty();
            return response;
        });

        ModelCaller caller = new ModelCaller(model);
        caller.call(List.of(), null).block();
    }

    @Test
    void shouldPassGenerateOptionsToModel() {
        AgentModel model = Mockito.mock(AgentModel.class);
        AgentGenerateOptions options = AgentGenerateOptions.builder().temperature(0.5).build();
        AgentChatResponse response = new AgentChatResponse(List.of(), null);
        when(model.generate(any(), any(), any())).thenAnswer(invocation -> {
            AgentGenerateOptions opts = invocation.getArgument(2);
            assertThat(opts).isEqualTo(options);
            return response;
        });

        ModelCaller caller = new ModelCaller(model);
        caller.setGenerateOptions(options);
        caller.call(List.of(), List.of()).block();
    }

    @Test
    void shouldPropagateErrorInCall() {
        AgentModel model = Mockito.mock(AgentModel.class);
        when(model.generate(any(), any(), any())).thenThrow(new RuntimeException("model error"));

        ModelCaller caller = new ModelCaller(model);
        StepVerifier.create(caller.call(List.of(), List.of()))
                .expectError(RuntimeException.class)
                .verify();
    }

    @Test
    void shouldPropagateErrorInStream() {
        AgentModel model = Mockito.mock(AgentModel.class);
        when(model.stream(any(), any(), any())).thenReturn(Flux.error(new RuntimeException("stream error")));

        ModelCaller caller = new ModelCaller(model);
        StepVerifier.create(caller.stream(List.of(), List.of()))
                .expectError(RuntimeException.class)
                .verify();
    }

    @Test
    void shouldHandleEmptyStreamResponse() {
        AgentModel model = Mockito.mock(AgentModel.class);
        when(model.stream(any(), any(), any())).thenReturn(Flux.empty());

        ModelCaller caller = new ModelCaller(model);
        StepVerifier.create(caller.stream(List.of(), List.of()))
                .verifyComplete();
    }
}
