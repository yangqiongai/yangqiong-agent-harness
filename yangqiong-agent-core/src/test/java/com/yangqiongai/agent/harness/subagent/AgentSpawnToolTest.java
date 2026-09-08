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
package com.yangqiongai.agent.harness.subagent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import com.yangqiongai.agent.harness.subagent.orchestration.SubagentDeclaration;
import com.yangqiongai.agent.harness.subagent.orchestration.SubagentResult;
import com.yangqiongai.agent.harness.engine.AgentRuntimeContext;
import com.yangqiongai.agent.harness.core.message.AgentToolResultBlock;
import com.yangqiongai.agent.harness.core.tool.AgentToolCallParam;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

/**
 * 子代理生成工具测试
 * @author yangqiong
 */
class AgentSpawnToolTest {

    @Test
    void shouldReturnToolNameAndDescription() {
        SubagentDeclaration decl = SubagentDeclaration.builder()
                .name("researcher")
                .description("研究代理")
                .build();
        AgentSpawnTool tool = new AgentSpawnTool(decl, null, null);
        assertThat(tool.getName()).isEqualTo("agent_spawn_researcher");
        assertThat(tool.getDescription()).contains("researcher");
        assertThat(tool.getDescription()).contains("研究代理");
    }

    @Test
    void shouldReturnParametersSchema() {
        SubagentDeclaration decl = SubagentDeclaration.builder()
                .name("worker")
                .description("工作代理")
                .build();
        AgentSpawnTool tool = new AgentSpawnTool(decl, null, null);
        java.util.Map<String, Object> params = tool.getParameters();
        assertThat(params).containsEntry("type", "object");
        assertThat(params).containsKey("properties");
        assertThat(params).containsKey("required");
    }

    @Test
    void shouldCallSpawnerAndReturnResult() {
        SubagentDeclaration decl = SubagentDeclaration.builder()
                .name("worker")
                .description("工作代理")
                .build();
        SubagentSpawner spawner = new SubagentSpawner(null, null, null, null);
        AgentSpawnTool tool = new AgentSpawnTool(decl, spawner, AgentRuntimeContext.empty());
        AgentToolCallParam param = new AgentToolCallParam(java.util.Map.of("input", "hi"));
        AgentToolResultBlock result = tool.callAsync(param).block();
        assertThat(result).isNotNull();
        assertThat(result.getTextContent()).contains("运行时工厂未注入");
    }

    @Test
    void shouldHandleNullParam() {
        SubagentDeclaration decl = SubagentDeclaration.builder()
                .name("worker")
                .description("工作代理")
                .build();
        SubagentSpawner spawner = new SubagentSpawner(null, null, null, null);
        AgentSpawnTool tool = new AgentSpawnTool(decl, spawner, AgentRuntimeContext.empty());
        AgentToolResultBlock result = tool.callAsync(null).block();
        assertThat(result).isNotNull();
    }

    @Test
    void shouldHandleEmptyInput() {
        SubagentDeclaration decl = SubagentDeclaration.builder()
                .name("worker")
                .description("工作代理")
                .build();
        SubagentSpawner spawner = new SubagentSpawner(null, null, null, null);
        AgentSpawnTool tool = new AgentSpawnTool(decl, spawner, AgentRuntimeContext.empty());
        AgentToolCallParam param = new AgentToolCallParam(Collections.emptyMap());
        AgentToolResultBlock result = tool.callAsync(param).block();
        assertThat(result).isNotNull();
    }

    @Test
    void callAsync_子代理返回结果_转换为ToolResultBlock() {
        SubagentDeclaration decl = SubagentDeclaration.builder()
                .name("worker")
                .description("工作代理")
                .build();
        SubagentSpawner spawner = mock(SubagentSpawner.class);
        when(spawner.spawn(any(), any(), any())).thenReturn(Mono.just(SubagentResult.success("worker", "子代理结果文本", 0)));
        AgentSpawnTool tool = new AgentSpawnTool(decl, spawner, AgentRuntimeContext.empty());
        AgentToolCallParam param = new AgentToolCallParam(Map.of("input", "任务内容"));
        AgentToolResultBlock result = tool.callAsync(param).block();
        assertThat(result).isNotNull();
        assertThat(result.getTextContent()).isEqualTo("子代理结果文本");
        assertThat(result.isError()).isFalse();
    }

    @Test
    void callAsync_空输入参数_返回错误结果() {
        SubagentDeclaration decl = SubagentDeclaration.builder()
                .name("worker")
                .description("工作代理")
                .build();
        SubagentSpawner spawner = mock(SubagentSpawner.class);
        when(spawner.spawn(any(), any(), any())).thenReturn(Mono.error(new RuntimeException("执行异常")));
        AgentSpawnTool tool = new AgentSpawnTool(decl, spawner, AgentRuntimeContext.empty());
        AgentToolCallParam param = new AgentToolCallParam(Collections.emptyMap());
        AgentToolResultBlock result = tool.callAsync(param).block();
        assertThat(result).isNotNull();
        assertThat(result.isError()).isTrue();
        assertThat(result.getTextContent()).contains("执行异常");
    }

    @Test
    void getParameters_包含task和description字段() {
        SubagentDeclaration decl = SubagentDeclaration.builder()
                .name("worker")
                .description("工作代理")
                .build();
        AgentSpawnTool tool = new AgentSpawnTool(decl, null, null);
        Map<String, Object> params = tool.getParameters();
        Map<String, Object> properties = (Map<String, Object>) params.get("properties");
        assertThat(properties).containsKey("input");
        Map<String, Object> inputProp = (Map<String, Object>) properties.get("input");
        assertThat(inputProp).containsKey("type");
        assertThat(inputProp).containsKey("description");
        assertThat(params.get("required")).isEqualTo(List.of("input"));
    }
}
