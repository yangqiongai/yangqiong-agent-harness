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

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;

import com.yangqiong.agent.harness.config.AgentPermissionDecision;
import org.junit.jupiter.api.Test;

/**
 * 工具权限统一策略门测试
 * @author yangqiong
 */
class ToolPolicyGateTest {

    @Test
    void shouldDenyBlacklistedToolWithHighestPriority() {
        ToolPolicyGate gate = new ToolPolicyGate(
                new PermissionEngine(null), Set.of("a", "b"), Set.of("b"), false, null);

        assertThat(gate.evaluate("b", "call-1", "scope-1", "run-1"))
                .isEqualTo(AgentPermissionDecision.DENY);
    }

    @Test
    void shouldDenyToolOutsideWhitelist() {
        ToolPolicyGate gate = new ToolPolicyGate(
                null, Set.of("allowed_tool"), null, false, null);

        assertThat(gate.evaluate("allowed_tool", "call-1", "scope-1", "run-1"))
                .isEqualTo(AgentPermissionDecision.ALLOW);
        assertThat(gate.evaluate("not_in_list", "call-2", "scope-1", "run-1"))
                .isEqualTo(AgentPermissionDecision.DENY);
    }

    @Test
    void shouldDelegateToPermissionEngineForWhitelistedTool() {
        ToolPolicyGate gate = new ToolPolicyGate(
                new PermissionEngine(null, Set.of("review_tool")),
                Set.of("review_tool", "safe_tool"), null, false, null);

        assertThat(gate.evaluate("review_tool", "call-1", "scope-1", "run-1"))
                .isEqualTo(AgentPermissionDecision.ASK);
        assertThat(gate.evaluate("safe_tool", "call-2", "scope-1", "run-1"))
                .isEqualTo(AgentPermissionDecision.ALLOW);
    }

    @Test
    void shouldApplyDefaultDenyWhenNoEngineConfigured() {
        ToolPolicyGate denyByDefault = new ToolPolicyGate(null, null, null, true, null);
        ToolPolicyGate allowByDefault = new ToolPolicyGate(null, null, null, false, null);

        assertThat(denyByDefault.evaluate("any_tool", "call-1", "scope-1", "run-1"))
                .isEqualTo(AgentPermissionDecision.DENY);
        assertThat(allowByDefault.evaluate("any_tool", "call-2", "scope-1", "run-1"))
                .isEqualTo(AgentPermissionDecision.ALLOW);
    }

    @Test
    void shouldDenyNullToolName() {
        ToolPolicyGate gate = new ToolPolicyGate(null, null, null, false, null);

        assertThat(gate.evaluate(null, "call-1", "scope-1", "run-1"))
                .isEqualTo(AgentPermissionDecision.DENY);
    }

    @Test
    void shouldWriteAuditRecordForEveryDecision() {
        AuditSink.InMemory auditSink = new AuditSink.InMemory();
        ToolPolicyGate gate = new ToolPolicyGate(
                null, Set.of("safe_tool"), Set.of("banned_tool"), false, auditSink);

        gate.evaluate("safe_tool", "call-1", "scope-1", "run-1");
        gate.evaluate("banned_tool", "call-2", "scope-1", "run-1");

        assertThat(auditSink.getRecords()).hasSize(2);
        assertThat(auditSink.getRecords().get(0).getDecision()).isEqualTo("ALLOW");
        assertThat(auditSink.getRecords().get(0).getToolCallId()).isEqualTo("call-1");
        assertThat(auditSink.getRecords().get(1).getDecision()).isEqualTo("DENY");
        assertThat(auditSink.getRecords().get(1).getAction()).isEqualTo("TOOL_PERMISSION_DECISION");
    }

    @Test
    void shouldNotFailWhenAuditSinkThrows() {
        ToolPolicyGate gate = new ToolPolicyGate(null, null, null, false, record -> {
            throw new IllegalStateException("审计出口故障");
        });

        assertThat(gate.evaluate("any_tool", "call-1", "scope-1", "run-1"))
                .isEqualTo(AgentPermissionDecision.ALLOW);
    }
}
