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

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.yangqiongai.agent.harness.engine.MiddlewareChain;
import com.yangqiongai.agent.harness.permission.PermissionEngine;
import com.yangqiongai.agent.harness.engine.AgentRuntimeContext;
import com.yangqiongai.agent.harness.config.AgentPermissionContextState;
import com.yangqiongai.agent.harness.config.AgentPermissionMode;
import com.yangqiongai.agent.harness.core.message.AgentMessage;
import com.yangqiongai.agent.harness.core.message.AgentMessageRole;
import com.yangqiongai.agent.harness.core.message.AgentTextBlock;
import com.yangqiongai.agent.harness.core.message.AgentToolResultBlock;
import com.yangqiongai.agent.harness.core.message.AgentToolUseBlock;
import com.yangqiongai.agent.harness.core.middleware.AgentMiddleware;
import com.yangqiongai.agent.harness.core.tool.AgentTool;
import com.yangqiongai.agent.harness.core.tool.AgentToolCallParam;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

/**
 * 工具执行器测试
 * @author yangqiong
 */
class ToolExecutorTest {

    @Test
    void shouldExecuteToolSuccessfully() {
        HarnessToolkit toolkit = new HarnessToolkit(null);
        toolkit.addTool(new StubTool("echo", "回显工具"));
        ToolExecutor executor = new ToolExecutor(toolkit);
        AgentToolUseBlock toolUse = new AgentToolUseBlock("echo", "id1", Map.of("text", "hi"));
        AgentMessage result = executor.executeTool(toolUse, AgentRuntimeContext.empty(), null).block();
        assertThat(result.getRole()).isEqualTo(AgentMessageRole.TOOL);
        assertThat(result.getContent()).hasSize(1);
    }

    @Test
    void shouldReturnErrorWhenToolNotFound() {
        HarnessToolkit toolkit = new HarnessToolkit(null);
        ToolExecutor executor = new ToolExecutor(toolkit);
        AgentToolUseBlock toolUse = new AgentToolUseBlock("not_exists", "id1", Map.of());
        AgentMessage result = executor.executeTool(toolUse, AgentRuntimeContext.empty(), null).block();
        AgentToolResultBlock block = (AgentToolResultBlock) result.getContent().get(0);
        assertThat(block.isError()).isTrue();
        assertThat(block.getTextContent()).contains("工具不存在");
    }

    @Test
    void shouldDenyWhenPermissionRefuses() {
        HarnessToolkit toolkit = new HarnessToolkit(null);
        toolkit.addTool(new StubTool("echo", "回显工具"));
        AgentPermissionContextState state = AgentPermissionContextState.builder()
                .mode(AgentPermissionMode.EXPLORE)
                .build();
        PermissionEngine permission = new PermissionEngine(state);
        ToolExecutor executor = new ToolExecutor(toolkit, null, permission);
        AgentToolUseBlock toolUse = new AgentToolUseBlock("echo", "id1", Map.of());
        AgentMessage result = executor.executeTool(toolUse, AgentRuntimeContext.empty(), null).block();
        AgentToolResultBlock block = (AgentToolResultBlock) result.getContent().get(0);
        assertThat(block.isError()).isTrue();
        assertThat(block.getTextContent()).contains("权限拒绝");
    }

    @Test
    void shouldApplyMiddlewareOnToolCall() {
        HarnessToolkit toolkit = new HarnessToolkit(null);
        toolkit.addTool(new StubTool("echo", "回显工具"));
        AgentMiddleware mw = new AgentMiddleware() {
            @Override
            public Map<String, Object> onToolCall(String toolName, Map<String, Object> input, AgentRuntimeContext context) {
                java.util.HashMap<String, Object> modified = new java.util.HashMap<>(input);
                modified.put("modified", true);
                return modified;
            }
        };
        MiddlewareChain chain = new MiddlewareChain(List.of(mw));
        ToolExecutor executor = new ToolExecutor(toolkit, chain, null);
        AgentToolUseBlock toolUse = new AgentToolUseBlock("echo", "id1", Map.of("text", "hi"));
        AgentMessage result = executor.executeTool(toolUse, AgentRuntimeContext.empty(), null).block();
        assertThat(result).isNotNull();
    }

    @Test
    void shouldApplyMiddlewareOnToolResult() {
        HarnessToolkit toolkit = new HarnessToolkit(null);
        toolkit.addTool(new StubTool("echo", "回显工具"));
        AgentMiddleware mw = new AgentMiddleware() {
            @Override
            public AgentToolResultBlock onToolResult(String toolName, AgentToolResultBlock result, AgentRuntimeContext context) {
                return AgentToolResultBlock.error("modified");
            }
        };
        MiddlewareChain chain = new MiddlewareChain(List.of(mw));
        ToolExecutor executor = new ToolExecutor(toolkit, chain, null);
        AgentToolUseBlock toolUse = new AgentToolUseBlock("echo", "id1", Map.of("text", "hi"));
        AgentMessage result = executor.executeTool(toolUse, AgentRuntimeContext.empty(), null).block();
        AgentToolResultBlock block = (AgentToolResultBlock) result.getContent().get(0);
        assertThat(block.isError()).isTrue();
        assertThat(block.getTextContent()).isEqualTo("modified");
    }

    @Test
    void shouldHandleToolCallException() {
        HarnessToolkit toolkit = new HarnessToolkit(null);
        toolkit.addTool(new AgentTool() {
            @Override
            public String getName() { return "boom"; }
            @Override
            public String getDescription() { return "爆炸工具"; }
            @Override
            public Map<String, Object> getParameters() { return Map.of(); }
            @Override
            public Mono<AgentToolResultBlock> callAsync(AgentToolCallParam param) {
                return Mono.error(new RuntimeException("boom!"));
            }
        });
        ToolExecutor executor = new ToolExecutor(toolkit);
        AgentToolUseBlock toolUse = new AgentToolUseBlock("boom", "id1", Map.of());
        AgentMessage result = executor.executeTool(toolUse, AgentRuntimeContext.empty(), null).block();
        AgentToolResultBlock block = (AgentToolResultBlock) result.getContent().get(0);
        assertThat(block.isError()).isTrue();
        assertThat(block.getTextContent()).contains("boom!");
    }

    @Test
    void shouldExecuteMultipleToolsInBatch() {
        HarnessToolkit toolkit = new HarnessToolkit(null);
        toolkit.addTool(new StubTool("t1", "工具1"));
        toolkit.addTool(new StubTool("t2", "工具2"));
        ToolExecutor executor = new ToolExecutor(toolkit);
        List<AgentToolUseBlock> calls = List.of(
                new AgentToolUseBlock("t1", "id1", Map.of()),
                new AgentToolUseBlock("t2", "id2", Map.of()));
        List<AgentMessage> results = executor.executeTools(calls, AgentRuntimeContext.empty(), null).block();
        assertThat(results).hasSize(2);
    }

    @Test
    void shouldReturnEmptyListWhenNoToolCalls() {
        HarnessToolkit toolkit = new HarnessToolkit(null);
        ToolExecutor executor = new ToolExecutor(toolkit);
        List<AgentMessage> results = executor.executeTools(Collections.emptyList(), AgentRuntimeContext.empty(), null).block();
        assertThat(results).isEmpty();
    }

    @Test
    void shouldHandleNullToolCalls() {
        HarnessToolkit toolkit = new HarnessToolkit(null);
        ToolExecutor executor = new ToolExecutor(toolkit);
        List<AgentMessage> results = executor.executeTools(null, AgentRuntimeContext.empty(), null).block();
        assertThat(results).isEmpty();
    }

    @Test
    void shouldFindToolFromEngineContextFirst() {
        HarnessToolkit toolkit = new HarnessToolkit(null);
        toolkit.addTool(new StubTool("shared", "静态工具"));
        ToolExecutor executor = new ToolExecutor(toolkit);
        AgentToolUseBlock toolUse = new AgentToolUseBlock("shared", "id1", Map.of());
        AgentMessage result = executor.executeTool(toolUse, AgentRuntimeContext.empty(), null).block();
        assertThat(result).isNotNull();
    }

    @Test
    void shouldBlockNonWhitelistedTool() {
        HarnessToolkit toolkit = new HarnessToolkit(null);
        toolkit.addTool(new StubTool("echo", "回显工具"));
        toolkit.addTool(new StubTool("delete", "删除工具"));
        ToolExecutor executor = new ToolExecutor(toolkit, null, null,
                Set.of("echo"), null);
        AgentToolUseBlock toolUse = new AgentToolUseBlock("delete", "id1", Map.of());
        AgentMessage result = executor.executeTool(toolUse, AgentRuntimeContext.empty(), null).block();
        AgentToolResultBlock block = (AgentToolResultBlock) result.getContent().get(0);
        assertThat(block.isError()).isTrue();
        assertThat(block.getTextContent()).contains("不在白名单");
    }

    @Test
    void shouldAllowWhitelistedTool() {
        HarnessToolkit toolkit = new HarnessToolkit(null);
        toolkit.addTool(new StubTool("echo", "回显工具"));
        ToolExecutor executor = new ToolExecutor(toolkit, null, null,
                Set.of("echo"), null);
        AgentToolUseBlock toolUse = new AgentToolUseBlock("echo", "id1", Map.of("text", "hi"));
        AgentMessage result = executor.executeTool(toolUse, AgentRuntimeContext.empty(), null).block();
        assertThat(result.getRole()).isEqualTo(AgentMessageRole.TOOL);
        assertThat(result.getContent()).hasSize(1);
    }

    @Test
    void shouldBlockBlacklistedTool() {
        HarnessToolkit toolkit = new HarnessToolkit(null);
        toolkit.addTool(new StubTool("echo", "回显工具"));
        toolkit.addTool(new StubTool("danger", "危险工具"));
        ToolExecutor executor = new ToolExecutor(toolkit, null, null,
                null, Set.of("danger"));
        AgentToolUseBlock toolUse = new AgentToolUseBlock("danger", "id1", Map.of());
        AgentMessage result = executor.executeTool(toolUse, AgentRuntimeContext.empty(), null).block();
        AgentToolResultBlock block = (AgentToolResultBlock) result.getContent().get(0);
        assertThat(block.isError()).isTrue();
        assertThat(block.getTextContent()).contains("黑名单");
    }

    @Test
    void shouldAllowNonBlacklistedTool() {
        HarnessToolkit toolkit = new HarnessToolkit(null);
        toolkit.addTool(new StubTool("echo", "回显工具"));
        ToolExecutor executor = new ToolExecutor(toolkit, null, null,
                null, Set.of("danger"));
        AgentToolUseBlock toolUse = new AgentToolUseBlock("echo", "id1", Map.of("text", "hi"));
        AgentMessage result = executor.executeTool(toolUse, AgentRuntimeContext.empty(), null).block();
        assertThat(result.getRole()).isEqualTo(AgentMessageRole.TOOL);
        assertThat(result.getContent()).hasSize(1);
    }

    @Test
    void shouldRejectInvalidToolInputAndReturnError() {
        HarnessToolkit toolkit = new HarnessToolkit(null);
        toolkit.addTool(new AgentTool() {
            @Override
            public String getName() { return "search"; }
            @Override
            public String getDescription() { return "搜索工具"; }
            @Override
            public Map<String, Object> getParameters() {
                return Map.of(
                        "type", "object",
                        "required", List.of("query"),
                        "properties", Map.of(
                                "query", Map.of("type", "string", "minLength", 1)));
            }
            @Override
            public Mono<AgentToolResultBlock> callAsync(AgentToolCallParam param) {
                return Mono.just(AgentToolResultBlock.of(List.of(
                        AgentTextBlock.builder().text("ok").build())));
            }
        });
        ToolExecutor executor = new ToolExecutor(toolkit);
        AgentToolUseBlock toolUse = new AgentToolUseBlock("search", "id1", Map.of());
        AgentMessage result = executor.executeTool(toolUse, AgentRuntimeContext.empty(), null).block();
        AgentToolResultBlock block = (AgentToolResultBlock) result.getContent().get(0);
        assertThat(block.isError()).isTrue();
        assertThat(block.getTextContent()).contains("参数校验失败");
        assertThat(block.getTextContent()).contains("缺少必填参数: query");
    }

    @Test
    void shouldRejectWrongTypeInputAndReturnError() {
        HarnessToolkit toolkit = new HarnessToolkit(null);
        toolkit.addTool(new AgentTool() {
            @Override
            public String getName() { return "calc"; }
            @Override
            public String getDescription() { return "计算工具"; }
            @Override
            public Map<String, Object> getParameters() {
                return Map.of(
                        "type", "object",
                        "properties", Map.of(
                                "num", Map.of("type", "integer")));
            }
            @Override
            public Mono<AgentToolResultBlock> callAsync(AgentToolCallParam param) {
                return Mono.just(AgentToolResultBlock.of(List.of(
                        AgentTextBlock.builder().text("ok").build())));
            }
        });
        ToolExecutor executor = new ToolExecutor(toolkit);
        AgentToolUseBlock toolUse = new AgentToolUseBlock("calc", "id1", Map.of("num", "不是数字"));
        AgentMessage result = executor.executeTool(toolUse, AgentRuntimeContext.empty(), null).block();
        AgentToolResultBlock block = (AgentToolResultBlock) result.getContent().get(0);
        assertThat(block.isError()).isTrue();
        assertThat(block.getTextContent()).contains("参数校验失败");
        assertThat(block.getTextContent()).contains("类型错误");
    }

    @Test
    void shouldExecuteWhenInputIsValidAgainstSchema() {
        HarnessToolkit toolkit = new HarnessToolkit(null);
        toolkit.addTool(new AgentTool() {
            @Override
            public String getName() { return "search"; }
            @Override
            public String getDescription() { return "搜索工具"; }
            @Override
            public Map<String, Object> getParameters() {
                return Map.of(
                        "type", "object",
                        "required", List.of("query"),
                        "properties", Map.of(
                                "query", Map.of("type", "string", "minLength", 1),
                                "limit", Map.of("type", "integer")));
            }
            @Override
            public Mono<AgentToolResultBlock> callAsync(AgentToolCallParam param) {
                return Mono.just(AgentToolResultBlock.of(List.of(
                        AgentTextBlock.builder().text("搜索结果").build())));
            }
        });
        ToolExecutor executor = new ToolExecutor(toolkit);
        AgentToolUseBlock toolUse = new AgentToolUseBlock("search", "id1",
                Map.of("query", "测试", "limit", 10));
        AgentMessage result = executor.executeTool(toolUse, AgentRuntimeContext.empty(), null).block();
        AgentToolResultBlock block = (AgentToolResultBlock) result.getContent().get(0);
        assertThat(block.isError()).isFalse();
        assertThat(block.getTextContent()).isEqualTo("搜索结果");
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
        public Map<String, Object> getParameters() {
            return Map.of("type", "object", "properties", Map.of());
        }

        @Override
        public Mono<AgentToolResultBlock> callAsync(AgentToolCallParam param) {
            return Mono.just(AgentToolResultBlock.of(List.of(
                    AgentTextBlock.builder().text("result").build())));
        }
    }
}
