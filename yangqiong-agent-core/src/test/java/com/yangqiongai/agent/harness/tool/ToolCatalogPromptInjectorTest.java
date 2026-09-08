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

import java.util.Map;
import java.util.Set;

import com.yangqiongai.agent.harness.core.message.AgentToolResultBlock;
import com.yangqiongai.agent.harness.core.tool.AgentTool;
import com.yangqiongai.agent.harness.core.tool.AgentToolCallParam;
import com.yangqiongai.agent.harness.engine.AgentRuntimeContext;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

/**
 * 工具目录提示词注入器测试
 * @author yangqiong
 */
class ToolCatalogPromptInjectorTest {

    @Test
    void shouldAppendCatalogToSystemPrompt() {
        HarnessToolkit toolkit = new HarnessToolkit(null);
        toolkit.addTool(new CatalogStubTool("query_holiday", "查询指定年份的节假日列表。返回日期与名称。"));
        toolkit.addTool(new CatalogStubTool("query_database", "执行SQL查询。"));
        ToolCatalogPromptInjector injector = new ToolCatalogPromptInjector(toolkit, Set.of());

        String result = injector.onSystemPrompt("你是助手", AgentRuntimeContext.empty());
        assertThat(result)
                .startsWith("你是助手")
                .contains("## 可用工具目录")
                .contains("load_tool")
                .contains("- query_holiday: 查询指定年份的节假日列表")
                .contains("- query_database: 执行SQL查询");
        // 目录只取首句，不携带后续描述
        assertThat(result).doesNotContain("返回日期与名称");
    }

    @Test
    void shouldExcludeDeniedAndAlwaysOnAndMetaTools() {
        HarnessToolkit toolkit = new HarnessToolkit(null);
        toolkit.addTool(new CatalogStubTool("visible_tool", "可见工具。"));
        toolkit.addTool(new CatalogStubTool("denied_tool", "黑名单工具。"));
        toolkit.addTool(new CatalogStubTool("resident_tool", "常驻工具。"));
        toolkit.addTool(new LoadToolTool(toolkit, null));
        ToolCatalogPromptInjector injector = new ToolCatalogPromptInjector(
                toolkit, Set.of("denied_tool", "resident_tool", "load_tool"));

        String result = injector.onSystemPrompt("p", AgentRuntimeContext.empty());
        assertThat(result).contains("visible_tool");
        assertThat(result).doesNotContain("- denied_tool:")
                .doesNotContain("- resident_tool:")
                .doesNotContain("- load_tool:");
    }

    @Test
    void shouldReturnOriginalPromptWhenNoDeferredTools() {
        HarnessToolkit toolkit = new HarnessToolkit(null);
        toolkit.addTool(new CatalogStubTool("resident_tool", "常驻工具。"));
        ToolCatalogPromptInjector injector = new ToolCatalogPromptInjector(toolkit, Set.of("resident_tool"));

        String result = injector.onSystemPrompt("原始提示词", AgentRuntimeContext.empty());
        assertThat(result).isEqualTo("原始提示词");
    }

    @Test
    void shouldHandleNullPromptAndEmptyToolkit() {
        ToolCatalogPromptInjector injector = new ToolCatalogPromptInjector(null, Set.of());
        assertThat(injector.onSystemPrompt(null, AgentRuntimeContext.empty())).isNull();

        ToolCatalogPromptInjector emptyInjector = new ToolCatalogPromptInjector(new HarnessToolkit(null), Set.of());
        assertThat(emptyInjector.onSystemPrompt("p", AgentRuntimeContext.empty())).isEqualTo("p");
    }

    @Test
    void shouldTruncateLongDescription() {
        HarnessToolkit toolkit = new HarnessToolkit(null);
        String longDescription = "工具描述内容".repeat(20);
        toolkit.addTool(new CatalogStubTool("long_desc_tool", longDescription));
        ToolCatalogPromptInjector injector = new ToolCatalogPromptInjector(toolkit, Set.of());

        String result = injector.onSystemPrompt(null, AgentRuntimeContext.empty());
        assertThat(result).contains("…");
        assertThat(result).doesNotContain(longDescription);
    }

    /**
     * 目录桩工具
     */
    private static class CatalogStubTool implements AgentTool {
        private final String name;
        private final String description;

        CatalogStubTool(String name, String description) {
            this.name = name;
            this.description = description;
        }

        @Override
        public String getName() {
            return name;
        }

        @Override
        public String getDescription() {
            return description;
        }

        @Override
        public Map<String, Object> getParameters() {
            return Map.of();
        }

        @Override
        public Mono<AgentToolResultBlock> callAsync(AgentToolCallParam param) {
            return Mono.empty();
        }
    }
}
