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
package com.yangqiong.agent.harness.tool;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import com.yangqiong.agent.harness.core.message.AgentToolResultBlock;
import com.yangqiong.agent.harness.core.tool.AgentTool;
import com.yangqiong.agent.harness.core.tool.AgentToolCallParam;
import org.junit.jupiter.api.Test;

import reactor.core.publisher.Mono;

/**
 * 工具箱测试
 * @author yangqiong
 */
class HarnessToolkitTest {

    @Test
    void shouldAddTool() {
        HarnessToolkit toolkit = new HarnessToolkit(null);
        toolkit.addTool(new StubTool("tool1"));
        assertThat(toolkit.getTools()).hasSize(1);
        assertThat(toolkit.find("tool1")).isNotNull();
    }

    @Test
    void shouldAddMultipleTools() {
        HarnessToolkit toolkit = new HarnessToolkit(null);
        toolkit.addTool(new StubTool("tool1"));
        toolkit.addTool(new StubTool("tool2"));
        assertThat(toolkit.getTools()).hasSize(2);
    }

    @Test
    void shouldBeEmptyWhenNoTools() {
        HarnessToolkit toolkit = new HarnessToolkit(null);
        assertThat(toolkit.isEmpty()).isTrue();
    }

    @Test
    void shouldNotBeEmptyWhenHasTools() {
        HarnessToolkit toolkit = new HarnessToolkit(null);
        toolkit.addTool(new StubTool("tool1"));
        assertThat(toolkit.isEmpty()).isFalse();
    }

    @Test
    void shouldFindToolByName() {
        HarnessToolkit toolkit = new HarnessToolkit(null);
        toolkit.addTool(new StubTool("search"));
        AgentTool found = toolkit.find("search");
        assertThat(found).isNotNull();
        assertThat(found.getName()).isEqualTo("search");
    }

    @Test
    void shouldReturnNullWhenToolNotFound() {
        HarnessToolkit toolkit = new HarnessToolkit(null);
        assertThat(toolkit.find("nonexistent")).isNull();
    }

    @Test
    void shouldReturnToolSchemas() {
        HarnessToolkit toolkit = new HarnessToolkit(null);
        toolkit.addTool(new StubTool("tool1"));
        assertThat(toolkit.getToolSchemas()).hasSize(1);
        Map<String, Object> schema = toolkit.getToolSchemas().get(0);
        assertThat(schema).containsKey("function");
    }

    @Test
    void shouldReturnEmptySchemasWhenNoTools() {
        HarnessToolkit toolkit = new HarnessToolkit(null);
        assertThat(toolkit.getToolSchemas()).isEmpty();
    }

    @Test
    void shouldNotAddNullTool() {
        HarnessToolkit toolkit = new HarnessToolkit(null);
        toolkit.addTool(null);
        assertThat(toolkit.isEmpty()).isTrue();
    }

    @Test
    void shouldOverwriteDuplicateName() {
        HarnessToolkit toolkit = new HarnessToolkit(null);
        toolkit.addTool(new StubTool("dup"));
        toolkit.addTool(new StubTool("dup"));
        assertThat(toolkit.getTools()).hasSize(1);
    }

    /**
     * 桩工具
     */
    private static class StubTool implements AgentTool {
        private final String name;

        StubTool(String name) {
            this.name = name;
        }

        @Override
        public String getName() { return name; }

        @Override
        public String getDescription() { return "stub tool"; }

        @Override
        public Map<String, Object> getParameters() { return Map.of("type", "object", "properties", Map.of()); }

        @Override
        public Mono<AgentToolResultBlock> callAsync(AgentToolCallParam param) {
            return Mono.empty();
        }
    }
}
