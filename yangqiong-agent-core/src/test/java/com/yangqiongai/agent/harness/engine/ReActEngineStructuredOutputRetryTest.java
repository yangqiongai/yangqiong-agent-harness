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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.yangqiongai.agent.harness.config.AgentJsonSchema;
import com.yangqiongai.agent.harness.config.AgentResponseFormat;
import com.yangqiongai.agent.harness.core.model.AgentGenerateOptions;
import com.yangqiongai.agent.harness.core.model.AgentChatResponse;
import com.yangqiongai.agent.harness.core.message.AgentMessage;
import com.yangqiongai.agent.harness.core.message.AgentTextBlock;
import com.yangqiongai.agent.harness.core.message.MessageFactory;
import com.yangqiongai.agent.harness.model.ModelCaller;
import com.yangqiongai.agent.harness.model.ModelResponseParser;
import com.yangqiongai.agent.harness.model.StructuredOutputRetryPolicy;
import com.yangqiongai.agent.harness.tool.ToolExecutor;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

/**
 * 结构化输出重试测试
 * @author yangqiong
 */
class ReActEngineStructuredOutputRetryTest {

    /**
     * 创建JSON Schema响应格式：{type: "object", properties: {name: {type: "string"}}, required: ["name"]}
     * @return
     */
    private static AgentResponseFormat createNameSchema() {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        Map<String, Object> properties = new LinkedHashMap<>();
        Map<String, Object> nameProp = new LinkedHashMap<>();
        nameProp.put("type", "string");
        properties.put("name", nameProp);
        schema.put("properties", properties);
        schema.put("required", List.of("name"));
        return AgentResponseFormat.jsonSchema(AgentJsonSchema.builder()
                .name("test_schema")
                .schema(schema)
                .build());
    }

    @Test
    void shouldRetryOnInvalidJsonAndSucceedOnSecondAttempt() {
        ModelCaller modelCaller = mock(ModelCaller.class);
        // 首次返回非法JSON（缺少name字段），第二次返回合法JSON
        AgentTextBlock invalidBlock = AgentTextBlock.builder().text("{\"foo\": \"bar\"}").build();
        AgentChatResponse invalidResponse = new AgentChatResponse(List.of(invalidBlock), null);
        AgentTextBlock validBlock = AgentTextBlock.builder().text("{\"name\": \"test\"}").build();
        AgentChatResponse validResponse = new AgentChatResponse(List.of(validBlock), null);
        when(modelCaller.stream(any(), any()))
                .thenReturn(Flux.just(invalidResponse))
                .thenReturn(Flux.just(validResponse));

        ToolExecutor toolExecutor = mock(ToolExecutor.class);
        MiddlewareChain chain = new MiddlewareChain(List.of());
        ModelResponseParser parser = new ModelResponseParser();

        ReActEngine engine = new ReActEngine(modelCaller, toolExecutor, chain, parser, null);
        engine.structuredOutputRetryPolicy(new StructuredOutputRetryPolicy(2, null));
        AgentRuntimeContext runtimeCtx = AgentRuntimeContext.empty();
        AgentGenerateOptions options = AgentGenerateOptions.builder()
                .responseFormat(createNameSchema())
                .build();
        EngineContext ctx = new EngineContext(null, null, runtimeCtx, null, 10, options);

        AgentMessage input = MessageFactory.createUserMessage("生成一个名称");
        // 第二次调用应返回合法JSON，最终AGENT_RESULT
        StepVerifier.create(engine.call(List.of(input), ctx))
                .assertNext(msg -> {
                    assertThat(msg.getTextContent()).contains("name", "test");
                })
                .verifyComplete();
    }

    @Test
    void shouldEmitErrorWhenRetriesExhausted() {
        ModelCaller modelCaller = mock(ModelCaller.class);
        // 所有响应都是非法JSON
        AgentTextBlock invalidBlock = AgentTextBlock.builder().text("{\"foo\": \"bar\"}").build();
        AgentChatResponse invalidResponse = new AgentChatResponse(List.of(invalidBlock), null);
        // 默认重试2次 + 首次尝试 = 3次调用
        when(modelCaller.stream(any(), any()))
                .thenReturn(Flux.just(invalidResponse))
                .thenReturn(Flux.just(invalidResponse))
                .thenReturn(Flux.just(invalidResponse));

        ToolExecutor toolExecutor = mock(ToolExecutor.class);
        MiddlewareChain chain = new MiddlewareChain(List.of());
        ModelResponseParser parser = new ModelResponseParser();

        ReActEngine engine = new ReActEngine(modelCaller, toolExecutor, chain, parser, null);
        engine.structuredOutputRetryPolicy(new StructuredOutputRetryPolicy(2, null));
        AgentRuntimeContext runtimeCtx = AgentRuntimeContext.empty();
        AgentGenerateOptions options = AgentGenerateOptions.builder()
                .responseFormat(createNameSchema())
                .build();
        EngineContext ctx = new EngineContext(null, null, runtimeCtx, null, 10, options);

        AgentMessage input = MessageFactory.createUserMessage("生成一个名称");
        // 重试耗尽，call返回ERROR异常
        StepVerifier.create(engine.call(List.of(input), ctx))
                .expectErrorMatches(error -> error instanceof AbstractAgentLoop.StructuredOutputException
                        && error.getMessage().contains("校验失败"))
                .verify();
    }

    @Test
    void shouldNotRetryWhenNoResponseFormatConfigured() {
        ModelCaller modelCaller = mock(ModelCaller.class);
        // 未配置json_schema时，即使输出非JSON也不触发校验与重试
        AgentTextBlock textBlock = AgentTextBlock.builder().text("hello world").build();
        AgentChatResponse response = new AgentChatResponse(List.of(textBlock), null);
        when(modelCaller.stream(any(), any())).thenReturn(Flux.just(response));

        ToolExecutor toolExecutor = mock(ToolExecutor.class);
        MiddlewareChain chain = new MiddlewareChain(List.of());
        ModelResponseParser parser = new ModelResponseParser();

        ReActEngine engine = new ReActEngine(modelCaller, toolExecutor, chain, parser, null);
        AgentRuntimeContext runtimeCtx = AgentRuntimeContext.empty();
        // 不配置responseFormat，引擎不校验结构化输出
        EngineContext ctx = new EngineContext(null, null, runtimeCtx, null, 10, null);

        AgentMessage input = MessageFactory.createUserMessage("hi");
        // 直接返回文本结果，无重试、无ERROR
        StepVerifier.create(engine.call(List.of(input), ctx))
                .assertNext(msg -> assertThat(msg.getTextContent()).isEqualTo("hello world"))
                .verifyComplete();
    }
}