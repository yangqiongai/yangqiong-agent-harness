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

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import com.yangqiong.agent.harness.config.AgentApprovalMode;
import com.yangqiong.agent.harness.core.AgentRuntime;
import com.yangqiong.agent.harness.core.event.AgentEvent;
import com.yangqiong.agent.harness.core.event.AgentEventType;
import com.yangqiong.agent.harness.core.message.AgentTextBlock;
import com.yangqiong.agent.harness.core.message.AgentToolResultBlock;
import com.yangqiong.agent.harness.core.message.AgentToolUseBlock;
import com.yangqiong.agent.harness.core.model.AgentChatResponse;
import com.yangqiong.agent.harness.core.model.AgentGenerateOptions;
import com.yangqiong.agent.harness.core.model.AgentModel;
import com.yangqiong.agent.harness.core.tool.AgentTool;
import com.yangqiong.agent.harness.core.tool.AgentToolCallParam;
import com.yangqiong.agent.harness.core.tool.ToolRiskLevel;
import com.yangqiong.agent.harness.engine.AgentRuntimeContext;
import com.yangqiong.agent.harness.core.message.MessageFactory;
import com.yangqiong.agent.harness.tool.HarnessToolkit;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 统一审批模式构建器接线测试
 * @author yangqiong
 */
class ApprovalModeWiringTest {

    /**
     * 主模型桩：首次调用返回破坏性工具调用，其后返回纯文本结束
     */
    private static class ToolCallingStubModel implements AgentModel {

        private final AtomicInteger calls = new AtomicInteger();

        @Override
        public AgentChatResponse generate(List<com.yangqiong.agent.harness.core.message.AgentMessage> messages,
                                          List<Map<String, Object>> tools, AgentGenerateOptions options) {
            if (calls.incrementAndGet() == 1) {
                return new AgentChatResponse(List.of(new AgentToolUseBlock("delete_file", "call-1", Map.of())), null);
            }
            return new AgentChatResponse(List.of(AgentTextBlock.builder().text("done").build()), null);
        }

        @Override
        public Flux<AgentChatResponse> stream(List<com.yangqiong.agent.harness.core.message.AgentMessage> messages,
                                              List<Map<String, Object>> tools, AgentGenerateOptions options) {
            return Flux.just(generate(messages, tools, options));
        }
    }

    /**
     * 审批模型桩：固定回复ALLOW或DENY
     */
    private static class FixedReplyJudgeModel implements AgentModel {

        private final String reply;

        FixedReplyJudgeModel(String reply) {
            this.reply = reply;
        }

        @Override
        public AgentChatResponse generate(List<com.yangqiong.agent.harness.core.message.AgentMessage> messages,
                                          List<Map<String, Object>> tools, AgentGenerateOptions options) {
            return new AgentChatResponse(List.of(AgentTextBlock.builder().text(reply).build()), null);
        }

        @Override
        public Flux<AgentChatResponse> stream(List<com.yangqiong.agent.harness.core.message.AgentMessage> messages,
                                              List<Map<String, Object>> tools, AgentGenerateOptions options) {
            return Flux.just(generate(messages, tools, options));
        }
    }

    /**
     * 破坏性测试工具：记录执行次数
     */
    private static class DeleteFileTool implements AgentTool {

        private final AtomicInteger executions = new AtomicInteger();

        @Override
        public String getName() {
            return "delete_file";
        }

        @Override
        public String getDescription() {
            return "删除文件";
        }

        @Override
        public Map<String, Object> getParameters() {
            return Map.of("type", "object", "properties", Map.of());
        }

        @Override
        public Mono<AgentToolResultBlock> callAsync(AgentToolCallParam param) {
            executions.incrementAndGet();
            return Mono.just(AgentToolResultBlock.of(List.of(
                    AgentTextBlock.builder().text("deleted").build())));
        }

        @Override
        public ToolRiskLevel getRiskLevel() {
            return ToolRiskLevel.HIGH;
        }
    }

    /**
     * MANUAL模式下破坏性工具应暂停等待人工确认且不执行
     */
    @Test
    void manualModeShouldPauseOnDestructiveTool() {
        DeleteFileTool tool = new DeleteFileTool();
        AgentRuntime runtime = new HarnessRuntimeBuilder()
                .name("approval-test")
                .model(new ToolCallingStubModel())
                .toolkit(toolkitOf(tool))
                .approvalMode(AgentApprovalMode.MANUAL)
                .build();

        List<AgentEvent> events = runtime.stream(
                List.of(MessageFactory.createUserMessage("删除文件")),
                AgentRuntimeContext.empty()).collectList().block();

        assertThat(events).isNotNull();
        assertThat(events.stream().anyMatch(e -> e.getType() == AgentEventType.REQUIRE_USER_CONFIRM)).isTrue();
        assertThat(tool.executions.get()).isZero();
    }

    /**
     * AUTO模式下审批模型回复ALLOW时工具直接执行
     */
    @Test
    void autoModeShouldExecuteWhenJudgeAllows() {
        DeleteFileTool tool = new DeleteFileTool();
        AgentRuntime runtime = new HarnessRuntimeBuilder()
                .name("approval-test")
                .model(new ToolCallingStubModel())
                .toolkit(toolkitOf(tool))
                .approvalMode(AgentApprovalMode.AUTO)
                .approvalJudgeModel(new FixedReplyJudgeModel("ALLOW"))
                .build();

        List<AgentEvent> events = runtime.stream(
                List.of(MessageFactory.createUserMessage("删除文件")),
                AgentRuntimeContext.empty()).collectList().block();

        assertThat(events).isNotNull();
        assertThat(events.stream().anyMatch(e -> e.getType() == AgentEventType.REQUIRE_USER_CONFIRM)).isFalse();
        assertThat(tool.executions.get()).isEqualTo(1);
    }

    /**
     * AUTO模式下审批模型回复DENY时工具被拒绝且不执行
     */
    @Test
    void autoModeShouldDenyWhenJudgeDenies() {
        DeleteFileTool tool = new DeleteFileTool();
        AgentRuntime runtime = new HarnessRuntimeBuilder()
                .name("approval-test")
                .model(new ToolCallingStubModel())
                .toolkit(toolkitOf(tool))
                .approvalMode(AgentApprovalMode.AUTO)
                .approvalJudgeModel(new FixedReplyJudgeModel("DENY"))
                .build();

        List<AgentEvent> events = runtime.stream(
                List.of(MessageFactory.createUserMessage("删除文件")),
                AgentRuntimeContext.empty()).collectList().block();

        assertThat(events).isNotNull();
        assertThat(events.stream().anyMatch(e -> e.getType() == AgentEventType.REQUIRE_USER_CONFIRM)).isFalse();
        assertThat(tool.executions.get()).isZero();
    }

    /**
     * FULL_ACCESS模式下破坏性工具跳过审批直接执行
     */
    @Test
    void fullAccessModeShouldExecuteWithoutApproval() {
        DeleteFileTool tool = new DeleteFileTool();
        AgentRuntime runtime = new HarnessRuntimeBuilder()
                .name("approval-test")
                .model(new ToolCallingStubModel())
                .toolkit(toolkitOf(tool))
                .approvalMode(AgentApprovalMode.FULL_ACCESS)
                .build();

        List<AgentEvent> events = runtime.stream(
                List.of(MessageFactory.createUserMessage("删除文件")),
                AgentRuntimeContext.empty()).collectList().block();

        assertThat(events).isNotNull();
        assertThat(events.stream().anyMatch(e -> e.getType() == AgentEventType.REQUIRE_USER_CONFIRM)).isFalse();
        assertThat(tool.executions.get()).isEqualTo(1);
    }

    /**
     * AUTO模式未设置审批模型时构建应报错
     */
    @Test
    void autoModeShouldFailWithoutJudgeModel() {
        try {
            new HarnessRuntimeBuilder()
                    .name("approval-test")
                    .model(new ToolCallingStubModel())
                    .approvalMode(AgentApprovalMode.AUTO)
                    .build();
            assertThat(false).as("AUTO模式缺少审批模型应抛异常").isTrue();
        } catch (IllegalStateException e) {
            assertThat(e.getMessage()).contains("approvalJudgeModel");
        }
    }

    /**
     * CUSTOM模式沿用requireApproval既有语义：指定工具暂停等人工确认
     */
    @Test
    void customModeShouldHonorRequireApproval() {
        DeleteFileTool tool = new DeleteFileTool();
        AgentRuntime runtime = new HarnessRuntimeBuilder()
                .name("approval-test")
                .model(new ToolCallingStubModel())
                .toolkit(toolkitOf(tool))
                .approvalMode(AgentApprovalMode.CUSTOM)
                .requireApproval(java.util.Set.of("delete_file"))
                .build();

        List<AgentEvent> events = runtime.stream(
                List.of(MessageFactory.createUserMessage("删除文件")),
                AgentRuntimeContext.empty()).collectList().block();

        assertThat(events).isNotNull();
        assertThat(events.stream().anyMatch(e -> e.getType() == AgentEventType.REQUIRE_USER_CONFIRM)).isTrue();
        assertThat(tool.executions.get()).isZero();
    }

    /**
     * 组装仅含单个工具的工具箱
     * @param tool
     * @return
     */
    private static HarnessToolkit toolkitOf(AgentTool tool) {
        HarnessToolkit toolkit = new HarnessToolkit(null);
        toolkit.addTool(tool);
        return toolkit;
    }
}
