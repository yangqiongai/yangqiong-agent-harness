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
package com.yangqiongai.agent.harness.local.permission;

import java.util.Map;

import org.junit.jupiter.api.Test;

import com.yangqiongai.agent.harness.config.AgentPermissionDecision;

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
     * 词法解析消除混淆绕过：$IFS、变量拼接、引号拆词、转义符展开后必须命中危险命令
     */
    @Test
    void shouldDenyObfuscatedDangerousCommands() {
        // $IFS展开为分隔符
        assertThat(evaluate("rm$IFS -rf /")).isEqualTo(AgentPermissionDecision.DENY);
        assertThat(evaluate("rm${IFS}-rf /")).isEqualTo(AgentPermissionDecision.DENY);
        // 变量赋值后拼接展开
        assertThat(evaluate("p=rm; $p -rf /")).isEqualTo(AgentPermissionDecision.DENY);
        assertThat(evaluate("p=r; q=m; $p$q -rf /")).isEqualTo(AgentPermissionDecision.DENY);
        assertThat(evaluate("set p=rm&& %p% -rf /")).isEqualTo(AgentPermissionDecision.DENY);
        // 引号与空引号拆词
        assertThat(evaluate("r''m -rf /")).isEqualTo(AgentPermissionDecision.DENY);
        assertThat(evaluate("\"rm\" -rf /")).isEqualTo(AgentPermissionDecision.DENY);
        assertThat(evaluate("'r'm -f /etc/passwd")).isEqualTo(AgentPermissionDecision.DENY);
        // cmd转义符
        assertThat(evaluate("d^el /s /q C:\\temp")).isEqualTo(AgentPermissionDecision.DENY);
        // 反斜杠转义拆词
        assertThat(evaluate("r\\m -rf /")).isEqualTo(AgentPermissionDecision.DENY);
        // 命令替换内部递归分级
        assertThat(evaluate("echo `rm -rf /`")).isEqualTo(AgentPermissionDecision.DENY);
        assertThat(evaluate("echo $(rm -rf /)")).isEqualTo(AgentPermissionDecision.DENY);
        // 启动器内嵌危险命令
        assertThat(evaluate("bash -c \"rm -rf /\"")).isEqualTo(AgentPermissionDecision.DENY);
        assertThat(evaluate("sudo rm -rf /")).isEqualTo(AgentPermissionDecision.DENY);
        assertThat(evaluate("ls | xargs rm -rf")).isEqualTo(AgentPermissionDecision.DENY);
        assertThat(evaluate("cmd /c del /s /q C:\\temp")).isEqualTo(AgentPermissionDecision.DENY);
        // rm标志变体
        assertThat(evaluate("rm -r -f /")).isEqualTo(AgentPermissionDecision.DENY);
        assertThat(evaluate("rm --recursive /")).isEqualTo(AgentPermissionDecision.DENY);
    }

    /**
     * 未知变量与替换输出无法归类时转人工确认，不猜测放行
     */
    @Test
    void shouldAskWhenUnknownVariableInCommandPosition() {
        assertThat(evaluate("$CMD -rf /")).isEqualTo(AgentPermissionDecision.ASK);
        assertThat(evaluate("${UNSET} /")).isEqualTo(AgentPermissionDecision.ASK);
        assertThat(evaluate("p=$Q; $p -rf /")).isEqualTo(AgentPermissionDecision.ASK);
        assertThat(evaluate("%CMD% /s")).isEqualTo(AgentPermissionDecision.ASK);
        assertThat(evaluate("$((rm -rf /))")).isEqualTo(AgentPermissionDecision.ASK);
    }

    /**
     * 安全命令携带变量参数或管道替换仍放行，重定向写文件与find删除参数降级人工确认
     */
    @Test
    void shouldAllowSafeCommandsWithExpansionsButDowngradeWrites() {
        assertThat(evaluate("echo $HOME")).isEqualTo(AgentPermissionDecision.ALLOW);
        assertThat(evaluate("echo $(git status)")).isEqualTo(AgentPermissionDecision.ALLOW);
        assertThat(evaluate("cat$IFS/etc/hosts")).isEqualTo(AgentPermissionDecision.ALLOW);
        // 重定向写文件使只读语义失效
        assertThat(evaluate("cat a.txt > b.txt")).isEqualTo(AgentPermissionDecision.ASK);
        assertThat(evaluate("echo hello >> out.log")).isEqualTo(AgentPermissionDecision.ASK);
        // 重定向到空设备仍放行
        assertThat(evaluate("dir > nul 2>&1")).isEqualTo(AgentPermissionDecision.ALLOW);
        assertThat(evaluate("ls > /dev/null")).isEqualTo(AgentPermissionDecision.ALLOW);
        // find的删除与执行参数
        assertThat(evaluate("find . -name *.tmp")).isEqualTo(AgentPermissionDecision.ALLOW);
        assertThat(evaluate("find . -name *.tmp -delete")).isEqualTo(AgentPermissionDecision.ASK);
        // date改时间降级
        assertThat(evaluate("date")).isEqualTo(AgentPermissionDecision.ALLOW);
        assertThat(evaluate("date -s 2026-01-01")).isEqualTo(AgentPermissionDecision.ASK);
    }

    /**
     * 纯赋值命令与未知变量处于命令词位置时转人工确认，参数位置不改变只读语义
     */
    @Test
    void shouldAskForPureAssignmentAndUnknownTokens() {
        assertThat(evaluate("p=rm")).isEqualTo(AgentPermissionDecision.ASK);
        assertThat(evaluate("r${UNSET}m -rf /")).isEqualTo(AgentPermissionDecision.ASK);
        assertThat(evaluate("echo r${UNSET}m")).isEqualTo(AgentPermissionDecision.ALLOW);
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
