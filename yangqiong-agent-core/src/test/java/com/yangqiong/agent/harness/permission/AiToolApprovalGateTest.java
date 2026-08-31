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
package com.yangqiong.agent.harness.permission;

import java.util.List;
import java.util.Map;
import java.util.Set;

import com.yangqiong.agent.harness.config.AgentPermissionDecision;
import com.yangqiong.agent.harness.core.message.AgentTextBlock;
import com.yangqiong.agent.harness.core.model.AgentChatResponse;
import com.yangqiong.agent.harness.core.model.AgentGenerateOptions;
import com.yangqiong.agent.harness.core.model.AgentModel;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * AI自动审批策略门测试
 * @author yangqiong
 */
class AiToolApprovalGateTest {

    /**
     * 按固定文本回复的审批模型桩
     */
    private static class StubJudgeModel implements AgentModel {

        private final String reply;

        private String lastPrompt;

        StubJudgeModel(String reply) {
            this.reply = reply;
        }

        @Override
        public AgentChatResponse generate(List<com.yangqiong.agent.harness.core.message.AgentMessage> messages,
                                          List<Map<String, Object>> tools, AgentGenerateOptions options) {
            this.lastPrompt = messages.get(0).getTextContent();
            return new AgentChatResponse(List.of(AgentTextBlock.builder().text(reply).build()), null);
        }

        @Override
        public reactor.core.publisher.Flux<AgentChatResponse> stream(
                List<com.yangqiong.agent.harness.core.message.AgentMessage> messages,
                List<Map<String, Object>> tools, AgentGenerateOptions options) {
            return reactor.core.publisher.Flux.just(generate(messages, tools, options));
        }
    }

    /**
     * 审批模型回复ALLOW时放行
     */
    @Test
    void shouldAllowWhenModelRepliesAllow() {
        StubJudgeModel model = new StubJudgeModel("ALLOW");
        AiToolApprovalGate gate = new AiToolApprovalGate(model);
        AgentPermissionDecision decision = gate.evaluate("file_write", "c1", "s", "r",
                Map.of("path", "a.txt"));
        assertThat(decision).isEqualTo(AgentPermissionDecision.ALLOW);
        assertThat(model.lastPrompt).contains("file_write").contains("a.txt");
    }

    /**
     * 审批模型回复DENY时拒绝
     */
    @Test
    void shouldDenyWhenModelRepliesDeny() {
        AiToolApprovalGate gate = new AiToolApprovalGate(new StubJudgeModel("deny"));
        assertThat(gate.evaluate("local_shell", "c1", "s", "r", Map.of("command", "rm -rf /")))
                .isEqualTo(AgentPermissionDecision.DENY);
    }

    /**
     * 静态黑名单命中时直接拒绝且不调用模型
     */
    @Test
    void shouldDenyWithoutModelWhenToolInDeniedList() {
        StubJudgeModel model = new StubJudgeModel("ALLOW");
        AiToolApprovalGate gate = new AiToolApprovalGate(model, null, Set.of("drop_table"), null, true, null);
        assertThat(gate.evaluate("drop_table", "c1", "s", "r", null))
                .isEqualTo(AgentPermissionDecision.DENY);
        assertThat(model.lastPrompt).isNull();
    }

    /**
     * 静态白名单命中时直接放行且不调用模型
     */
    @Test
    void shouldAllowWithoutModelWhenToolInAllowedList() {
        StubJudgeModel model = new StubJudgeModel("DENY");
        AiToolApprovalGate gate = new AiToolApprovalGate(model, Set.of("read_file"), null, null, true, null);
        assertThat(gate.evaluate("read_file", "c1", "s", "r", null))
                .isEqualTo(AgentPermissionDecision.ALLOW);
        assertThat(model.lastPrompt).isNull();
    }

    /**
     * 强制人工审批集合命中时直接转ASK且不调用模型
     */
    @Test
    void shouldAskWithoutModelWhenToolInAlwaysAskSet() {
        StubJudgeModel model = new StubJudgeModel("ALLOW");
        AiToolApprovalGate gate = new AiToolApprovalGate(model, null, null, Set.of("local_shell"), true, null);
        assertThat(gate.evaluate("local_shell", "c1", "s", "r", null))
                .isEqualTo(AgentPermissionDecision.ASK);
        assertThat(model.lastPrompt).isNull();
    }

    /**
     * 模型输出不可解析时按回退策略处理：默认回退人工ASK
     */
    @Test
    void shouldFallbackToAskWhenOutputUnparsable() {
        AiToolApprovalGate gate = new AiToolApprovalGate(new StubJudgeModel("今天天气不错"));
        assertThat(gate.evaluate("file_edit", "c1", "s", "r", null))
                .isEqualTo(AgentPermissionDecision.ASK);
    }

    /**
     * 模型输出不可解析且配置回退拒绝时直接DENY
     */
    @Test
    void shouldFallbackToDenyWhenConfigured() {
        AiToolApprovalGate gate = new AiToolApprovalGate(new StubJudgeModel("无法判断"),
                null, null, null, false, null);
        assertThat(gate.evaluate("file_edit", "c1", "s", "r", null))
                .isEqualTo(AgentPermissionDecision.DENY);
    }

    /**
     * 模型抛出异常时按回退策略处理
     */
    @Test
    void shouldFallbackWhenModelThrows() {
        AgentModel brokenModel = new AgentModel() {
            @Override
            public AgentChatResponse generate(List<com.yangqiong.agent.harness.core.message.AgentMessage> messages,
                                              List<Map<String, Object>> tools, AgentGenerateOptions options) {
                throw new IllegalStateException("模型不可用");
            }

            @Override
            public reactor.core.publisher.Flux<AgentChatResponse> stream(
                    List<com.yangqiong.agent.harness.core.message.AgentMessage> messages,
                    List<Map<String, Object>> tools, AgentGenerateOptions options) {
                return reactor.core.publisher.Flux.error(new IllegalStateException("模型不可用"));
            }
        };
        AiToolApprovalGate gate = new AiToolApprovalGate(brokenModel);
        assertThat(gate.evaluate("file_write", "c1", "s", "r", null))
                .isEqualTo(AgentPermissionDecision.ASK);
    }

    /**
     * 附加策略描述应拼入判定提示词
     */
    @Test
    void shouldIncludeGuidanceInPrompt() {
        StubJudgeModel model = new StubJudgeModel("ALLOW");
        AiToolApprovalGate gate = new AiToolApprovalGate(model, null, null, null, true, "禁止删除任何文件");
        gate.evaluate("file_edit", "c1", "s", "r", null);
        assertThat(model.lastPrompt).contains("禁止删除任何文件");
    }
}
