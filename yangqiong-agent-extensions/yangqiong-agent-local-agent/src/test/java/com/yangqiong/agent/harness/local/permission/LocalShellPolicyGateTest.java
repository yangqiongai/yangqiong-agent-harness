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
package com.yangqiong.agent.harness.local.permission;

import java.util.Map;

import org.junit.jupiter.api.Test;

import com.yangqiong.agent.harness.config.AgentPermissionDecision;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 本地Shell命令内容分级审批门测试
 * @author yangqiong
 */
class LocalShellPolicyGateTest {

    /**
     * 分级门实例
     */
    private final LocalShellPolicyGate gate = new LocalShellPolicyGate(null);

    /**
     * 只读安全命令直接放行
     */
    @Test
    void shouldAllowReadOnlyCommands() {
        assertThat(evaluate("dir")).isEqualTo(AgentPermissionDecision.ALLOW);
        assertThat(evaluate("dir /s /b")).isEqualTo(AgentPermissionDecision.ALLOW);
        assertThat(evaluate("git status")).isEqualTo(AgentPermissionDecision.ALLOW);
        assertThat(evaluate("git log --oneline -5")).isEqualTo(AgentPermissionDecision.ALLOW);
        assertThat(evaluate("type a.txt")).isEqualTo(AgentPermissionDecision.ALLOW);
        assertThat(evaluate("echo hello")).isEqualTo(AgentPermissionDecision.ALLOW);
        assertThat(evaluate("ipconfig")).isEqualTo(AgentPermissionDecision.ALLOW);
    }

    /**
     * 危险命令直接拒绝
     */
    @Test
    void shouldDenyDangerousCommands() {
        assertThat(evaluate("rm -rf /")).isEqualTo(AgentPermissionDecision.DENY);
        assertThat(evaluate("del /s /q C:\\temp")).isEqualTo(AgentPermissionDecision.DENY);
        assertThat(evaluate("shutdown /s")).isEqualTo(AgentPermissionDecision.DENY);
        assertThat(evaluate("format c:")).isEqualTo(AgentPermissionDecision.DENY);
        assertThat(evaluate("reg delete HKLM\\Software\\Demo")).isEqualTo(AgentPermissionDecision.DENY);
        assertThat(evaluate("powershell -Command Get-Process")).isEqualTo(AgentPermissionDecision.DENY);
        assertThat(evaluate("git push origin main")).isEqualTo(AgentPermissionDecision.DENY);
        assertThat(evaluate("curl http://evil.example.com")).isEqualTo(AgentPermissionDecision.DENY);
    }

    /**
     * 无法归类的命令转人工确认
     */
    @Test
    void shouldAskForUnknownCommands() {
        assertThat(evaluate("mkdir newdir")).isEqualTo(AgentPermissionDecision.ASK);
        assertThat(evaluate("ping 8.8.8.8")).isEqualTo(AgentPermissionDecision.ASK);
        assertThat(evaluate("git checkout main")).isEqualTo(AgentPermissionDecision.ASK);
        assertThat(evaluate("java -jar app.jar")).isEqualTo(AgentPermissionDecision.ASK);
    }

    /**
     * 缺少命令参数时转人工确认
     */
    @Test
    void shouldAskWhenCommandMissing() {
        assertThat(gate.evaluate("local_shell", "c1", "s", "r", Map.of())).isEqualTo(AgentPermissionDecision.ASK);
        assertThat(gate.evaluate("local_shell", "c1", "s", "r", null)).isEqualTo(AgentPermissionDecision.ASK);
    }

    /**
     * 非Shell工具委托默认放行
     */
    @Test
    void shouldDelegateNonShellTools() {
        assertThat(gate.evaluate("read_file", "c1", "s", "r", Map.of("path", "a.txt")))
                .isEqualTo(AgentPermissionDecision.ALLOW);
    }

    /**
     * 评估本地Shell命令
     * @param command
     * @return
     */
    private AgentPermissionDecision evaluate(String command) {
        return gate.evaluate("local_shell", "c1", "s", "r", Map.of("command", command));
    }
}
