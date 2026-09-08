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
package com.yangqiongai.agent.harness.tool;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashMap;
import java.util.Map;

import com.yangqiongai.agent.harness.core.message.AgentToolResultBlock;
import com.yangqiongai.agent.harness.core.tool.AgentTool;
import com.yangqiongai.agent.harness.core.tool.AgentToolCallParam;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

/**
 * 启用工具元工具测试
 * @author yangqiong
 */
class LoadToolToolTest {

    @Test
    void shouldExposeMetaToolMetadata() {
        LoadToolTool tool = new LoadToolTool(null, null);
        assertThat(tool.getName()).isEqualTo("load_tool");
        assertThat(tool.getDescription()).isNotBlank();
        Map<String, Object> params = tool.getParameters();
        assertThat(params.get("type")).isEqualTo("object");
        assertThat(params.get("required")).isEqualTo(java.util.List.of("tool_name"));
    }

    @Test
    void shouldActivateToolAndReturnSchemaText() {
        HarnessToolkit toolkit = new HarnessToolkit(null);
        toolkit.addTool(new DeferredStubTool("query_holiday"));
        ToolLoadingState state = new ToolLoadingState(true, java.util.Set.of());
        LoadToolTool tool = new LoadToolTool(toolkit, state);

        StepVerifier.create(tool.callAsync(param("tool_name", "query_holiday")))
                .assertNext(block -> {
                    assertThat(block.isError()).isFalse();
                    String text = block.getTextContent();
                    assertThat(text).contains("query_holiday").contains("已启用").contains("\"type\":\"function\"");
                })
                .verifyComplete();
        assertThat(state.isActivated("query_holiday")).isTrue();
    }

    @Test
    void shouldBeIdempotentOnRepeatedActivation() {
        HarnessToolkit toolkit = new HarnessToolkit(null);
        toolkit.addTool(new DeferredStubTool("dup_tool"));
        ToolLoadingState state = new ToolLoadingState(true, java.util.Set.of());
        LoadToolTool tool = new LoadToolTool(toolkit, state);

        tool.callAsync(param("tool_name", "dup_tool")).block();
        StepVerifier.create(tool.callAsync(param("tool_name", "dup_tool")))
                .assertNext(block -> {
                    assertThat(block.isError()).isFalse();
                    assertThat(block.getTextContent()).contains("dup_tool");
                })
                .verifyComplete();
    }

    @Test
    void shouldRejectUnknownTool() {
        HarnessToolkit toolkit = new HarnessToolkit(null);
        LoadToolTool tool = new LoadToolTool(toolkit, new ToolLoadingState(true, java.util.Set.of()));

        StepVerifier.create(tool.callAsync(param("tool_name", "not_exists")))
                .assertNext(block -> {
                    assertThat(block.isError()).isTrue();
                    assertThat(block.getTextContent()).contains("工具未找到");
                })
                .verifyComplete();
    }

    @Test
    void shouldRejectAlwaysOnTool() {
        HarnessToolkit toolkit = new HarnessToolkit(null);
        toolkit.addTool(new DeferredStubTool("resident_tool"));
        ToolLoadingState state = new ToolLoadingState(true, java.util.Set.of("resident_tool"));
        LoadToolTool tool = new LoadToolTool(toolkit, state);

        StepVerifier.create(tool.callAsync(param("tool_name", "resident_tool")))
                .assertNext(block -> {
                    assertThat(block.isError()).isTrue();
                    assertThat(block.getTextContent()).contains("已常驻");
                })
                .verifyComplete();
    }

    @Test
    void shouldReturnErrorWhenParamMissing() {
        LoadToolTool tool = new LoadToolTool(null, null);

        StepVerifier.create(tool.callAsync(new AgentToolCallParam(Map.of())))
                .assertNext(block -> {
                    assertThat(block.isError()).isTrue();
                    assertThat(block.getTextContent()).contains("tool_name");
                })
                .verifyComplete();
        StepVerifier.create(tool.callAsync(param("tool_name", " ")))
                .assertNext(block -> assertThat(block.isError()).isTrue())
                .verifyComplete();
        StepVerifier.create(tool.callAsync(null))
                .assertNext(block -> assertThat(block.isError()).isTrue())
                .verifyComplete();
    }

    @Test
    void shouldTolerateNullLoadingState() {
        HarnessToolkit toolkit = new HarnessToolkit(null);
        toolkit.addTool(new DeferredStubTool("any_tool"));
        LoadToolTool tool = new LoadToolTool(toolkit, null);

        StepVerifier.create(tool.callAsync(param("tool_name", "any_tool")))
                .assertNext(block -> assertThat(block.isError()).isFalse())
                .verifyComplete();
    }

    private AgentToolCallParam param(String key, String value) {
        Map<String, Object> input = new HashMap<>();
        input.put(key, value);
        return new AgentToolCallParam(input);
    }

    /**
     * 延迟池桩工具
     */
    private static class DeferredStubTool implements AgentTool {
        private final String name;

        DeferredStubTool(String name) {
            this.name = name;
        }

        @Override
        public String getName() {
            return name;
        }

        @Override
        public String getDescription() {
            return "延迟池桩工具，用于按需启用。";
        }

        @Override
        public Map<String, Object> getParameters() {
            Map<String, Object> schema = new HashMap<>();
            schema.put("type", "object");
            return schema;
        }

        @Override
        public Mono<AgentToolResultBlock> callAsync(AgentToolCallParam param) {
            return Mono.empty();
        }
    }
}
