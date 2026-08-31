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

import java.time.Duration;
import java.util.List;
import java.util.Map;

import com.yangqiong.agent.harness.engine.AgentRuntimeContext;
import com.yangqiong.agent.harness.engine.EngineContext;
import com.yangqiong.agent.harness.core.HarnessAgentRuntimeBuilder;
import com.yangqiong.agent.harness.core.message.AgentMessage;
import com.yangqiong.agent.harness.core.message.AgentTextBlock;
import com.yangqiong.agent.harness.core.message.AgentToolResultBlock;
import com.yangqiong.agent.harness.core.message.AgentToolUseBlock;
import com.yangqiong.agent.harness.core.tool.AgentTool;
import com.yangqiong.agent.harness.core.tool.AgentToolCallParam;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

/**
 * 工具执行器增强测试（超时控制与结果大小限制）
 * @author yangqiong
 */
class ToolExecutorEnhancementTest {

    /**
     * 构建带超时与结果大小限制的引擎上下文
     * @param toolCallTimeout
     * @param maxToolResultChars
     * @return
     */
    private EngineContext buildContext(Duration toolCallTimeout, int maxToolResultChars) {
        return new EngineContext(null, null, AgentRuntimeContext.empty(), null, 10, null,
                null, null, 1,
                HarnessAgentRuntimeBuilder.ToolFailureStrategy.SKIP, 0,
                0, null, toolCallTimeout, maxToolResultChars, 0);
    }

    @Test
    void shouldTimeoutWhenToolHangs() {
        HarnessToolkit toolkit = new HarnessToolkit(null);
        toolkit.addTool(new AgentTool() {
            @Override
            public String getName() { return "hang"; }
            @Override
            public String getDescription() { return "卡住工具"; }
            @Override
            public Map<String, Object> getParameters() {
                return Map.of("type", "object", "properties", Map.of());
            }
            @Override
            public Mono<AgentToolResultBlock> callAsync(AgentToolCallParam param) {
                return Mono.never();
            }
        });
        ToolExecutor executor = new ToolExecutor(toolkit);
        EngineContext ctx = buildContext(Duration.ofMillis(100), 0);

        AgentToolUseBlock toolUse = new AgentToolUseBlock("hang", "id1", Map.of());
        AgentMessage result = executor.executeTool(toolUse, AgentRuntimeContext.empty(), ctx).block();

        AgentToolResultBlock block = (AgentToolResultBlock) result.getContent().get(0);
        assertThat(block.isError()).isTrue();
    }

    @Test
    void shouldNotTimeoutWhenToolCompletesFast() {
        HarnessToolkit toolkit = new HarnessToolkit(null);
        toolkit.addTool(new StubResultTool("fast", "done"));
        ToolExecutor executor = new ToolExecutor(toolkit);
        EngineContext ctx = buildContext(Duration.ofSeconds(5), 0);

        AgentToolUseBlock toolUse = new AgentToolUseBlock("fast", "id1", Map.of());
        AgentMessage result = executor.executeTool(toolUse, AgentRuntimeContext.empty(), ctx).block();

        AgentToolResultBlock block = (AgentToolResultBlock) result.getContent().get(0);
        assertThat(block.isError()).isFalse();
        assertThat(block.getTextContent()).isEqualTo("done");
    }

    @Test
    void shouldTruncateLargeResult() {
        String largeText = repeat('a', 50000);
        HarnessToolkit toolkit = new HarnessToolkit(null);
        toolkit.addTool(new StubResultTool("big", largeText));
        ToolExecutor executor = new ToolExecutor(toolkit);
        EngineContext ctx = buildContext(null, 100);

        AgentToolUseBlock toolUse = new AgentToolUseBlock("big", "id1", Map.of());
        AgentMessage result = executor.executeTool(toolUse, AgentRuntimeContext.empty(), ctx).block();

        AgentToolResultBlock block = (AgentToolResultBlock) result.getContent().get(0);
        String text = block.getTextContent();
        assertThat(text).contains("结果已截断");
        assertThat(text).contains("50000");
        // 截断后文本应远小于原始长度
        assertThat(text.length()).isLessThan(largeText.length());
    }

    @Test
    void shouldNotTruncateWhenLimitZero() {
        String text = repeat('b', 200);
        HarnessToolkit toolkit = new HarnessToolkit(null);
        toolkit.addTool(new StubResultTool("mid", text));
        ToolExecutor executor = new ToolExecutor(toolkit);
        EngineContext ctx = buildContext(null, 0);

        AgentToolUseBlock toolUse = new AgentToolUseBlock("mid", "id1", Map.of());
        AgentMessage result = executor.executeTool(toolUse, AgentRuntimeContext.empty(), ctx).block();

        AgentToolResultBlock block = (AgentToolResultBlock) result.getContent().get(0);
        assertThat(block.getTextContent()).isEqualTo(text);
    }

    private static String repeat(char c, int count) {
        char[] arr = new char[count];
        java.util.Arrays.fill(arr, c);
        return new String(arr);
    }

    /**
     * 返回固定文本结果的桩工具
     */
    private static class StubResultTool implements AgentTool {
        private final String name;
        private final String resultText;

        StubResultTool(String name, String resultText) {
            this.name = name;
            this.resultText = resultText;
        }

        @Override
        public String getName() { return name; }
        @Override
        public String getDescription() { return "结果工具"; }
        @Override
        public Map<String, Object> getParameters() {
            return Map.of("type", "object", "properties", Map.of());
        }
        @Override
        public Mono<AgentToolResultBlock> callAsync(AgentToolCallParam param) {
            return Mono.just(AgentToolResultBlock.of(List.of(
                    AgentTextBlock.builder().text(resultText).build())));
        }
    }
}
