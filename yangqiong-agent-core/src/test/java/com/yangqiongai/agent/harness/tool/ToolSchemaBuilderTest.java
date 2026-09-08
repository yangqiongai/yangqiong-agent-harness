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

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import com.yangqiongai.agent.harness.core.message.AgentToolResultBlock;
import com.yangqiongai.agent.harness.core.tool.AgentTool;
import com.yangqiongai.agent.harness.core.tool.AgentToolCallParam;
import org.junit.jupiter.api.Test;

import reactor.core.publisher.Mono;

/**
 * 工具Schema构建器测试
 * @author yangqiong
 */
class ToolSchemaBuilderTest {

    @Test
    void shouldBuildSchemaFromTool() {
        AgentTool tool = new StubTool("my_tool", "desc");
        Map<String, Object> schema = ToolSchemaBuilder.build(tool);
        assertThat(schema).containsKey("type");
        assertThat(schema.get("type")).isEqualTo("function");
        assertThat(schema).containsKey("function");
        @SuppressWarnings("unchecked")
        Map<String, Object> function = (Map<String, Object>) schema.get("function");
        assertThat(function.get("name")).isEqualTo("my_tool");
        assertThat(function.get("description")).isEqualTo("desc");
        assertThat(function).containsKey("parameters");
    }

    @Test
    void shouldReturnEmptyParametersWhenNull() {
        AgentTool tool = new StubTool("tool", "desc");
        Map<String, Object> schema = ToolSchemaBuilder.build(tool);
        @SuppressWarnings("unchecked")
        Map<String, Object> function = (Map<String, Object>) schema.get("function");
        assertThat(function.get("parameters")).isNotNull();
    }

    @Test
    void shouldBuildAllTools() {
        List<AgentTool> tools = List.of(
                new StubTool("tool1", "desc1"),
                new StubTool("tool2", "desc2")
        );
        List<Map<String, Object>> schemas = ToolSchemaBuilder.buildAll(tools);
        assertThat(schemas).hasSize(2);
    }

    @Test
    void shouldReturnEmptyListForNullTools() {
        List<Map<String, Object>> schemas = ToolSchemaBuilder.buildAll(null);
        assertThat(schemas).isEmpty();
    }

    @Test
    void shouldReturnEmptyListForEmptyTools() {
        List<Map<String, Object>> schemas = ToolSchemaBuilder.buildAll(List.of());
        assertThat(schemas).isEmpty();
    }

    @Test
    void shouldSkipNullToolsInList() {
        List<AgentTool> tools = Arrays.asList(new StubTool("t1", "d1"), null);
        List<Map<String, Object>> schemas = ToolSchemaBuilder.buildAll(tools);
        assertThat(schemas).hasSize(1);
    }

    /**
     * 桩工具
     */
    private static class StubTool implements AgentTool {
        private final String name;
        private final String desc;

        StubTool(String name, String desc) {
            this.name = name;
            this.desc = desc;
        }

        @Override
        public String getName() { return name; }

        @Override
        public String getDescription() { return desc; }

        @Override
        public Map<String, Object> getParameters() { return Map.of("type", "object", "properties", Map.of()); }

        @Override
        public Mono<AgentToolResultBlock> callAsync(AgentToolCallParam param) {
            return Mono.empty();
        }
    }
}
