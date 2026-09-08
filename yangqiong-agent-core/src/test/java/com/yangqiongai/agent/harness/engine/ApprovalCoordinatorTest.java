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
package com.yangqiongai.agent.harness.engine;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.Set;

import com.yangqiongai.agent.harness.config.AgentPermissionDecision;
import com.yangqiongai.agent.harness.core.event.ConfirmResult;
import com.yangqiongai.agent.harness.core.message.AgentToolUseBlock;
import com.yangqiongai.agent.harness.permission.PermissionEngine;
import com.yangqiongai.agent.harness.permission.ToolPolicyGate;
import org.junit.jupiter.api.Test;

/**
 * 审批协调器测试
 * @author yangqiong
 */
class ApprovalCoordinatorTest {

    @Test
    void shouldTriageThreeWaysViaPolicyGate() {
        ToolPolicyGate gate = new ToolPolicyGate(
                new PermissionEngine(null, Set.of("dangerous_tool")),
                Set.of("safe_tool", "dangerous_tool"), Set.of("banned_tool"), false, null);
        ApprovalCoordinator coordinator = new ApprovalCoordinator(gate, null);

        ApprovalCoordinator.TriageResult result = coordinator.triage(List.of(
                new AgentToolUseBlock("safe_tool", "call-1", Map.of()),
                new AgentToolUseBlock("dangerous_tool", "call-2", Map.of()),
                new AgentToolUseBlock("banned_tool", "call-3", Map.of()),
                new AgentToolUseBlock("unknown_tool", "call-4", Map.of())),
                AgentRuntimeContext.empty(), "run-1");

        assertThat(result.getAllowedCalls()).extracting(AgentToolUseBlock::getToolName)
                .containsExactly("safe_tool");
        assertThat(result.getAskCalls()).extracting(AgentToolUseBlock::getToolName)
                .containsExactly("dangerous_tool");
        assertThat(result.getDeniedMessages()).hasSize(2);
    }

    @Test
    void shouldFallbackToPermissionEngineWhenGateAbsent() {
        PermissionEngine engine = new PermissionEngine(null, Set.of("review_tool"));
        ApprovalCoordinator coordinator = new ApprovalCoordinator(null, engine);

        assertThat(coordinator.isEnabled()).isTrue();
        assertThat(coordinator.evaluate("review_tool", "call-1", AgentRuntimeContext.empty(), null))
                .isEqualTo(AgentPermissionDecision.ASK);
        assertThat(coordinator.evaluate("other_tool", "call-2", AgentRuntimeContext.empty(), null))
                .isEqualTo(AgentPermissionDecision.ALLOW);
    }

    @Test
    void shouldAllowAllWhenDisabled() {
        ApprovalCoordinator coordinator = new ApprovalCoordinator(null, null);

        assertThat(coordinator.isEnabled()).isFalse();
        assertThat(coordinator.evaluate("any_tool", "call-1", AgentRuntimeContext.empty(), null))
                .isEqualTo(AgentPermissionDecision.ALLOW);
    }

    @Test
    void shouldMatchConfirmByToolCallIdPrecisely() {
        ApprovalCoordinator coordinator = new ApprovalCoordinator(null, null);
        AgentToolUseBlock callA = new AgentToolUseBlock("send_email", "call-a", Map.of());
        AgentToolUseBlock callB = new AgentToolUseBlock("send_email", "call-b", Map.of());

        ConfirmResult matched = coordinator.matchConfirm(callB, List.of(
                ConfirmResult.approveCall("call-a", "send_email"),
                ConfirmResult.denyCall("call-b", "send_email", "频率过高")));

        assertThat(matched).isNotNull();
        assertThat(matched.getToolCallId()).isEqualTo("call-b");
        assertThat(matched.isApproved()).isFalse();

        ConfirmResult matchedA = coordinator.matchConfirm(callA, List.of(
                ConfirmResult.approveCall("call-a", "send_email")));
        assertThat(matchedA).isNotNull();
        assertThat(matchedA.isApproved()).isTrue();
    }

    @Test
    void shouldFallbackToToolNameWhenConfirmIdAbsent() {
        ApprovalCoordinator coordinator = new ApprovalCoordinator(null, null);
        AgentToolUseBlock call = new AgentToolUseBlock("send_email", "call-x", Map.of());

        ConfirmResult matched = coordinator.matchConfirm(call,
                List.of(ConfirmResult.approve("send_email")));

        assertThat(matched).isNotNull();
        assertThat(matched.isApproved()).isTrue();
    }

    @Test
    void shouldReturnNullWhenNoConfirmMatched() {
        ApprovalCoordinator coordinator = new ApprovalCoordinator(null, null);
        AgentToolUseBlock call = new AgentToolUseBlock("send_email", "call-x", Map.of());

        assertThat(coordinator.matchConfirm(call, null)).isNull();
        assertThat(coordinator.matchConfirm(call, List.of())).isNull();
        assertThat(coordinator.matchConfirm(call,
                List.of(ConfirmResult.approve("other_tool")))).isNull();
    }
}
