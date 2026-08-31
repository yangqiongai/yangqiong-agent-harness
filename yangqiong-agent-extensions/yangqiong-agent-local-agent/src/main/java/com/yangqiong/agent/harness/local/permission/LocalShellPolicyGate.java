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

import java.util.List;
import java.util.Locale;
import java.util.Map;

import com.yangqiong.agent.harness.config.AgentPermissionDecision;
import com.yangqiong.agent.harness.permission.ToolPolicyGate;

/**
 * 本地Shell命令内容分级审批门
 * <p>
 * 针对 local_shell 按命令内容做确定性分级：只读命令放行、危险命令拒绝、
 * 无法归类的命令转人工确认；其它工具委托注入的策略门按既有规则判定。
 * </p>
 * @author yangqiong
 */
public class LocalShellPolicyGate extends ToolPolicyGate {

    /**
     * 本地Shell工具名称
     */
    private static final String SHELL_TOOL_NAME = "local_shell";

    /**
     * 只读安全命令前缀，命中直接放行
     */
    private static final List<String> SAFE_PREFIXES = List.of(
            "dir", "ls", "pwd", "cd", "echo", "type", "cat", "findstr", "find", "more",
            "where", "which", "ver", "date /t", "time /t", "hostname", "ipconfig", "netstat",
            "tasklist", "systeminfo", "whoami", "set ", "path", "vol", "tree", "fc", "comp",
            "sort", "help", "cls", "clear",
            "git status", "git log", "git diff", "git branch", "git remote", "git tag",
            "git show", "git stash list", "git rev-parse", "git config", "git ls-files",
            "git help", "git --version", "git version",
            "java -version", "javac -version", "mvn -v", "node -v", "npm -v",
            "python --version", "python -v", "python3 --version", "reg query");

    /**
     * 危险命令片段，命中直接拒绝
     */
    private static final List<String> DANGEROUS_MARKERS = List.of(
            "rm -rf", "rm -f", "rmdir ", "rd /s", "rd /q", "del /s", "del /f", "del /q",
            " erase ", "deltree", "format ", "format:", "diskpart", "fsutil", "bcdedit",
            "shutdown ", "restart", "reboot", "powercfg",
            "reg delete", "reg add", "reg import", "regedt32",
            "sc delete", "sc stop", "sc config", "sc create",
            "taskkill", "wmic", "net user", "net localgroup", "net group",
            "takeown", "icacls", "cacls",
            "certutil", "bitsadmin", "schtasks", "telnet", "sftp", "scp",
            "ftp ", "nc ", "ncat", "clip", "curl", "wget", "smbclient",
            "powershell", "pwsh", "iex ", "invoke-expression", "invoke-webrequest",
            "invoke-command", "new-object", "start-process", "set-executionpolicy",
            "downloadstring", "downloadfile",
            "git push", "git reset", "git clean", "git rm", "git remote add", "git remote set-url",
            "del ", "erase ", "rd ", "cleanmgr", "cipher /w", "vssadmin", "netsh", "mklink");

    /**
     * 非Shell工具的委托策略门
     */
    private final ToolPolicyGate delegate;

    /**
     * 构造，未指定委托门时按默认放行语义处理非Shell工具
     * @param delegate
     */
    public LocalShellPolicyGate(ToolPolicyGate delegate) {
        super(null, null, null, false, null);
        this.delegate = delegate != null ? delegate : new ToolPolicyGate(null, null, null, false, null);
    }

    /**
     * 分级评估本地Shell命令，其它工具委托上层策略门
     * @param toolName
     * @param toolCallId
     * @param scopeId
     * @param runId
     * @param toolInput
     * @return
     */
    @Override
    public AgentPermissionDecision evaluate(String toolName, String toolCallId,
                                             String scopeId, String runId,
                                             Map<String, Object> toolInput) {
        if (SHELL_TOOL_NAME.equals(toolName)) {
            AgentPermissionDecision decision = classify(toolInput);
            audit(toolName, toolCallId, scopeId, runId, decision);
            return decision;
        }
        return delegate.evaluate(toolName, toolCallId, scopeId, runId, toolInput);
    }

    /**
     * 按命令内容分级：危险拒绝、只读放行、其余转人工确认
     * @param toolInput
     * @return
     */
    private AgentPermissionDecision classify(Map<String, Object> toolInput) {
        String command = extractCommand(toolInput);
        if (command == null || command.isBlank()) {
            return AgentPermissionDecision.ASK;
        }
        String normalized = normalize(command);
        if (containsDangerous(normalized)) {
            return AgentPermissionDecision.DENY;
        }
        if (isSafeReadOnly(normalized)) {
            return AgentPermissionDecision.ALLOW;
        }
        return AgentPermissionDecision.ASK;
    }

    /**
     * 提取命令入参
     * @param toolInput
     * @return
     */
    private String extractCommand(Map<String, Object> toolInput) {
        if (toolInput == null) {
            return null;
        }
        Object value = toolInput.get("command");
        return value == null ? null : value.toString();
    }

    /**
     * 统一命令格式：小写并压缩空白
     * @param command
     * @return
     */
    private String normalize(String command) {
        return command.trim().toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
    }

    /**
     * 判断命令是否命中危险片段
     * @param normalized
     * @return
     */
    private boolean containsDangerous(String normalized) {
        for (String marker : DANGEROUS_MARKERS) {
            if (normalized.contains(marker)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 判断命令是否为只读安全命令
     * @param normalized
     * @return
     */
    private boolean isSafeReadOnly(String normalized) {
        for (String prefix : SAFE_PREFIXES) {
            if (normalized.equals(prefix) || normalized.startsWith(prefix + " ")) {
                return true;
            }
        }
        return false;
    }
}
