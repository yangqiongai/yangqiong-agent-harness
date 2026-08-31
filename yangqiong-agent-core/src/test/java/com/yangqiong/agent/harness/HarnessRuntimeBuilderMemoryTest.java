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
package com.yangqiong.agent.harness;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import com.yangqiong.agent.harness.core.AgentRuntime;
import com.yangqiong.agent.harness.core.model.AgentChatResponse;
import com.yangqiong.agent.harness.core.model.AgentGenerateOptions;
import com.yangqiong.agent.harness.core.model.AgentModel;
import com.yangqiong.agent.harness.core.message.AgentContentBlock;
import com.yangqiong.agent.harness.core.message.AgentMessage;
import com.yangqiong.agent.harness.core.message.AgentTextBlock;
import com.yangqiong.agent.harness.memory.InMemoryLongTermMemory;
import com.yangqiong.agent.harness.memory.InMemorySessionMemory;
import com.yangqiong.agent.harness.tool.HarnessToolkit;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;

/**
 * 运行时构建器记忆子系统装配测试
 * @author yangqiong
 */
class HarnessRuntimeBuilderMemoryTest {

    private AgentModel stubModel() {
        return new AgentModel() {
            @Override
            public AgentChatResponse generate(List<AgentMessage> messages, List<Map<String, Object>> tools,
                                                AgentGenerateOptions options) {
                List<AgentContentBlock> content = Collections.singletonList(AgentTextBlock.builder().text("ok").build());
                return new AgentChatResponse(content, null);
            }

            @Override
            public Flux<AgentChatResponse> stream(List<AgentMessage> messages, List<Map<String, Object>> tools,
                                                    AgentGenerateOptions options) {
                return Flux.just(generate(messages, tools, options));
            }
        };
    }

    private HarnessToolkit toolkitOf(AgentRuntime runtime) {
        return ((HarnessAgentRuntime) runtime).getEngineConfig().getToolkit();
    }

    @Test
    void longTermMemoryRegistersThreeMemoryTools() {
        AgentRuntime runtime = new HarnessRuntimeBuilder()
                .name("agent")
                .model(stubModel())
                .longTermMemory(new InMemoryLongTermMemory())
                .build();

        HarnessToolkit toolkit = toolkitOf(runtime);
        assertThat(toolkit.find("memory_search")).isNotNull();
        assertThat(toolkit.find("memory_store")).isNotNull();
        assertThat(toolkit.find("memory_delete")).isNotNull();
    }

    @Test
    void disableMemoryToolsSkipsToolRegistration() {
        AgentRuntime runtime = new HarnessRuntimeBuilder()
                .name("agent")
                .model(stubModel())
                .longTermMemory(new InMemoryLongTermMemory())
                .memoryToolsEnabled(false)
                .build();

        HarnessToolkit toolkit = toolkitOf(runtime);
        assertThat(toolkit.find("memory_search")).isNull();
        assertThat(toolkit.find("memory_store")).isNull();
        assertThat(toolkit.find("memory_delete")).isNull();
    }

    @Test
    void noLongTermMemoryRegistersNoMemoryToolsByDefault() {
        AgentRuntime runtime = new HarnessRuntimeBuilder()
                .name("agent")
                .model(stubModel())
                .build();

        HarnessToolkit toolkit = toolkitOf(runtime);
        assertThat(toolkit.find("memory_search")).isNull();
    }

    @Test
    void enableSummaryCompactionBuildsWithoutError() {
        AgentRuntime runtime = new HarnessRuntimeBuilder()
                .name("agent")
                .model(stubModel())
                .summaryCompactionEnabled(true)
                .sessionMemory(new InMemorySessionMemory())
                .build();

        assertThat(runtime).isNotNull();
        assertThat(runtime.getName()).isEqualTo("agent");
    }

    @Test
    void longTermMemoryWithHooksDisabledStillRegistersTools() {
        // 禁用钩子只影响中间件链，不影响工具注册
        AgentRuntime runtime = new HarnessRuntimeBuilder()
                .name("agent")
                .model(stubModel())
                .longTermMemory(new InMemoryLongTermMemory())
                .memoryHooksEnabled(false)
                .build();

        HarnessToolkit toolkit = toolkitOf(runtime);
        assertThat(toolkit.find("memory_search")).isNotNull();
    }

    @Test
    void disableMemoryRetrievalDoesNotAffectTools() {
        AgentRuntime runtime = new HarnessRuntimeBuilder()
                .name("agent")
                .model(stubModel())
                .longTermMemory(new InMemoryLongTermMemory())
                .memoryRetrievalEnabled(false)
                .build();

        HarnessToolkit toolkit = toolkitOf(runtime);
        assertThat(toolkit.find("memory_search")).isNotNull();
    }
}
