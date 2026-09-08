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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import com.yangqiongai.agent.harness.config.AgentPermissionDecision;
import com.yangqiongai.agent.harness.permission.ToolPolicyGate;

/**
 * 本地Shell命令内容分级审批门
 * <p>
 * 针对local_shell先做词法解析再做确定性分级：剥离引号与转义、展开变量与
 * $IFS、递归解析$()与反引号命令替换、按 ; &amp;&amp; | 等切分简单命令段，
 * 之后按命令词分级：只读命令放行、危险命令拒绝、无法归类的命令转人工确认；
 * 展开后含未知变量或替换输出时收敛到人工确认，防止rm$IFS、变量拼接、
 * 引号拆词等混淆绕过。其它工具委托注入的策略门按既有规则判定。
 * </p>
 * @author yangqiong
 */
public class LocalShellPolicyGate extends ToolPolicyGate {

    /**
     * 本地Shell工具名称
     */
    private static final String SHELL_TOOL_NAME = "local_shell";

    /**
     * 未知变量展开占位符，出现在命令词位置即无法归类
     */
    private static final char UNKNOWN_MARK = '\u0000';

    /**
     * 命令替换输出占位符，替换输出文本不可预知
     */
    private static final char SUBST_MARK = '\u0001';

    /**
     * 无条件只读安全命令，命中直接放行
     */
    private static final Set<String> SAFE_COMMANDS = Set.of(
            "dir", "ls", "pwd", "cd", "echo", "type", "cat", "findstr", "find", "more",
            "where", "which", "ver", "hostname", "ipconfig", "netstat", "tasklist",
            "systeminfo", "whoami", "vol", "tree", "fc", "comp", "sort", "help",
            "cls", "clear", "path", "set",
            "reg", "git", "java", "javac", "mvn", "node", "npm", "python", "python3",
            "date", "time");

    /**
     * 条件只读命令的版本查询参数
     */
    private static final Set<String> VERSION_FLAGS = Set.of("--version", "-v", "-V");

    /**
     * 可写文件系统的find参数，命中则降级人工确认
     */
    private static final Set<String> FIND_WRITE_FLAGS = Set.of(
            "-delete", "-exec", "-execdir", "-ok", "-okdir");

    /**
     * 只读git子命令
     */
    private static final Set<String> SAFE_GIT_SUBCOMMANDS = Set.of(
            "status", "log", "diff", "branch", "remote", "tag", "show", "config",
            "ls-files", "help", "rev-parse", "--version", "version");

    /**
     * 危险命令词，作为命令词出现即拒绝
     */
    private static final Set<String> DANGEROUS_COMMANDS = Set.of(
            "del", "erase", "rd", "rmdir", "deltree", "format", "diskpart", "fsutil",
            "bcdedit", "shutdown", "restart", "reboot", "powercfg", "regedt32",
            "taskkill", "wmic", "takeown", "icacls", "cacls", "certutil", "bitsadmin",
            "schtasks", "telnet", "sftp", "scp", "ftp", "nc", "ncat", "smbclient",
            "clip", "curl", "wget", "powershell", "pwsh", "iex", "cleanmgr",
            "vssadmin", "netsh", "mklink",
            "set-executionpolicy", "start-process", "new-object", "invoke-expression",
            "invoke-command", "invoke-webrequest", "downloadstring", "downloadfile");

    /**
     * net子命令危险词
     */
    private static final Set<String> NET_DANGEROUS_SUBCOMMANDS = Set.of(
            "user", "localgroup", "group");

    /**
     * reg子命令危险词
     */
    private static final Set<String> REG_DANGEROUS_SUBCOMMANDS = Set.of(
            "delete", "add", "import");

    /**
     * sc子命令危险词
     */
    private static final Set<String> SC_DANGEROUS_SUBCOMMANDS = Set.of(
            "delete", "stop", "config", "create");

    /**
     * 危险git子命令
     */
    private static final Set<String> GIT_DANGEROUS_SUBCOMMANDS = Set.of(
            "push", "reset", "clean", "rm");

    /**
     * 可执行内嵌命令的启动器，参数中出现危险命令词即拒绝
     */
    private static final Set<String> LAUNCHERS = Set.of(
            "sudo", "doas", "su", "xargs", "cmd", "command", "bash", "sh", "dash",
            "zsh", "ksh", "start", "call", "env", "nohup", "timeout", "busybox");

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
     * 词法解析后按命令内容分级：危险拒绝、只读放行、其余转人工确认
     * @param toolInput
     * @return
     */
    private AgentPermissionDecision classify(Map<String, Object> toolInput) {
        String command = extractCommand(toolInput);
        if (command == null || command.isBlank()) {
            return AgentPermissionDecision.ASK;
        }
        LexResult lex = new LexResult();
        new CommandParser(command.toLowerCase(Locale.ROOT), lex).parse();
        AgentPermissionDecision merged = null;
        for (Segment segment : lex.segments) {
            AgentPermissionDecision decision = classifySegment(segment.tokens, segment.hasRedirect);
            if (decision != null) {
                merged = merge(merged, decision);
            }
        }
        // 命令替换内部的子命令同样纳入分级，防止echo $(rm -rf /)借道执行
        for (AgentPermissionDecision nested : lex.nestedDecisions) {
            merged = merge(merged, nested);
        }
        return merged != null ? merged : AgentPermissionDecision.ASK;
    }

    /**
     * 合并分段决策：拒绝优先，其次人工确认，最后放行
     * @param left
     * @param right
     * @return
     */
    private AgentPermissionDecision merge(AgentPermissionDecision left, AgentPermissionDecision right) {
        if (left == AgentPermissionDecision.DENY || right == AgentPermissionDecision.DENY) {
            return AgentPermissionDecision.DENY;
        }
        if (left == AgentPermissionDecision.ASK || right == AgentPermissionDecision.ASK) {
            return AgentPermissionDecision.ASK;
        }
        return AgentPermissionDecision.ALLOW;
    }

    /**
     * 按词法分段分级：危险拒绝、只读放行、其余转人工确认，纯赋值段不参与决策
     * @param tokens
     * @param hasRedirect
     * @return
     */
    private static AgentPermissionDecision classifySegment(List<String> tokens, boolean hasRedirect) {
        List<String> words = new ArrayList<>(tokens);
        // 段首赋值（含PATH=x cmd环境前缀风格）仅注册变量，不作为命令词参与分级
        while (!words.isEmpty() && isAssignmentToken(words.get(0))) {
            words.remove(0);
        }
        if (words.isEmpty()) {
            return null;
        }
        String command = words.get(0);
        if (command.indexOf(UNKNOWN_MARK) >= 0 || command.indexOf(SUBST_MARK) >= 0) {
            return AgentPermissionDecision.ASK;
        }
        if (isDangerous(words)) {
            return AgentPermissionDecision.DENY;
        }
        if (LAUNCHERS.contains(command)) {
            return hasDangerousArg(words) ? AgentPermissionDecision.DENY : AgentPermissionDecision.ASK;
        }
        if (!isSafe(words)) {
            return AgentPermissionDecision.ASK;
        }
        // 重定向写文件使只读语义失效，降级人工确认
        return hasRedirect ? AgentPermissionDecision.ASK : AgentPermissionDecision.ALLOW;
    }

    /**
     * 判断词法分段是否命中危险命令
     * @param words
     * @return
     */
    private static boolean isDangerous(List<String> words) {
        String command = words.get(0);
        List<String> args = words.subList(1, words.size());
        if (DANGEROUS_COMMANDS.contains(command)) {
            return !"cipher".equals(command) || args.stream().anyMatch(arg -> arg.startsWith("/w"));
        }
        return switch (command) {
            case "rm" -> args.stream().anyMatch(LocalShellPolicyGate::isForceFlag);
            case "net" -> !args.isEmpty() && NET_DANGEROUS_SUBCOMMANDS.contains(args.get(0));
            case "reg" -> !args.isEmpty() && REG_DANGEROUS_SUBCOMMANDS.contains(args.get(0));
            case "sc" -> !args.isEmpty() && SC_DANGEROUS_SUBCOMMANDS.contains(args.get(0));
            case "git" -> isDangerousGit(args);
            default -> false;
        };
    }

    /**
     * 判断git参数是否命中危险子命令
     * @param args
     * @return
     */
    private static boolean isDangerousGit(List<String> args) {
        if (args.isEmpty()) {
            return false;
        }
        String sub = args.get(0);
        if (GIT_DANGEROUS_SUBCOMMANDS.contains(sub)) {
            return true;
        }
        return "remote".equals(sub) && args.size() >= 2
                && Set.of("add", "set-url").contains(args.get(1));
    }

    /**
     * 判断启动器参数中是否内嵌危险命令，含引号包裹的整串命令文本
     * @param words
     * @return
     */
    private static boolean hasDangerousArg(List<String> words) {
        for (String arg : words.subList(1, words.size())) {
            if (DANGEROUS_COMMANDS.contains(arg) || "rm".equals(arg)) {
                return true;
            }
            if (arg.indexOf(' ') > 0 && containsDangerousCommandWord(arg)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 重新词法解析字符串参数，判断其内嵌子命令是否危险
     * @param value
     * @return
     */
    private static boolean containsDangerousCommandWord(String value) {
        LexResult lex = new LexResult();
        new CommandParser(value, lex).parse();
        for (Segment segment : lex.segments) {
            List<String> tokens = new ArrayList<>(segment.tokens);
            while (!tokens.isEmpty() && isAssignmentToken(tokens.get(0))) {
                tokens.remove(0);
            }
            if (tokens.isEmpty()) {
                continue;
            }
            String command = tokens.get(0);
            if (command.indexOf(UNKNOWN_MARK) >= 0 || command.indexOf(SUBST_MARK) >= 0) {
                continue;
            }
            if (isDangerous(tokens) || (LAUNCHERS.contains(command) && hasDangerousArg(tokens))) {
                return true;
            }
        }
        return lex.nestedDecisions.contains(AgentPermissionDecision.DENY);
    }

    /**
     * 判断词法分段是否为只读安全命令
     * @param words
     * @return
     */
    private static boolean isSafe(List<String> words) {
        String command = words.get(0);
        if (!SAFE_COMMANDS.contains(command)) {
            return false;
        }
        List<String> args = words.subList(1, words.size());
        return switch (command) {
            case "reg" -> !args.isEmpty() && "query".equals(args.get(0));
            case "git" -> isSafeGit(args);
            case "java", "javac" -> !args.isEmpty() && "-version".equals(args.get(0));
            case "mvn" -> !args.isEmpty() && VERSION_FLAGS.contains(args.get(0));
            case "node", "npm" -> !args.isEmpty() && "-v".equals(args.get(0));
            case "python", "python3" -> !args.isEmpty() && VERSION_FLAGS.contains(args.get(0));
            case "date", "time" -> args.stream().noneMatch(
                    arg -> "-s".equals(arg) || "--set".equals(arg) || "/set".equals(arg));
            case "find" -> args.stream().noneMatch(FIND_WRITE_FLAGS::contains);
            default -> true;
        };
    }

    /**
     * 判断git参数是否为只读子命令
     * @param args
     * @return
     */
    private static boolean isSafeGit(List<String> args) {
        if (args.isEmpty()) {
            return false;
        }
        String sub = args.get(0);
        if (SAFE_GIT_SUBCOMMANDS.contains(sub)) {
            return true;
        }
        return "stash".equals(sub) && args.size() >= 2 && "list".equals(args.get(1));
    }

    /**
     * 判断rm参数是否携带递归或强制删除标志
     * @param flag
     * @return
     */
    private static boolean isForceFlag(String flag) {
        return "--recursive".equals(flag) || "--force".equals(flag)
                || (flag.startsWith("-") && flag.matches("-[a-z]*[rf][a-z]*"));
    }

    /**
     * 判断token是否为变量赋值形式
     * @param token
     * @return
     */
    private static boolean isAssignmentToken(String token) {
        int eq = token.indexOf('=');
        if (eq <= 0) {
            return false;
        }
        if (token.charAt(0) == UNKNOWN_MARK || token.charAt(0) == SUBST_MARK) {
            return false;
        }
        for (int i = 0; i < eq; i++) {
            char c = token.charAt(i);
            if (!(c >= 'a' && c <= 'z' || c >= '0' && c <= '9' || c == '_')) {
                return false;
            }
        }
        return true;
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
     * 词法解析结果
     */
    private static final class LexResult {

        /**
         * 按分隔符切分的简单命令段
         */
        final List<Segment> segments = new ArrayList<>();

        /**
         * 段内与命令替换解析出的变量赋值
         */
        final Map<String, String> variables = new HashMap<>();

        /**
         * 命令替换内部子命令的分级决策
         */
        final List<AgentPermissionDecision> nestedDecisions = new ArrayList<>();
    }

    /**
     * 单个简单命令段
     */
    private static final class Segment {

        /**
         * 段内词法单元序列
         */
        final List<String> tokens = new ArrayList<>();

        /**
         * 段内是否存在文件重定向
         */
        boolean hasRedirect;
    }

    /**
     * Shell命令词法解析器
     * <p>
     * 单趟扫描完成：引号与反斜杠转义剥离、$VAR/${VAR}/%VAR%与$IFS展开、
     * $(...)与反引号命令替换递归分级、按;|&与括号切分简单命令段、
     * 段首赋值与set赋值注册变量、识别文件重定向。
     * </p>
     */
    private static final class CommandParser {

        /**
         * 重定向到空设备的目标词，不视为文件写入
         */
        private static final Set<String> NULL_TARGETS = Set.of("nul", "/dev/null", "null");

        private final String command;
        private final LexResult out;

        /**
         * 当前段
         */
        private Segment segment = new Segment();

        /**
         * 当前token缓冲
         */
        private final StringBuilder buffer = new StringBuilder();

        /**
         * 扫描位置
         */
        private int position;

        /**
         * 上一条已flush的token，用于识别set赋值
         */
        private String prevToken = "";

        private boolean inSingleQuote;
        private boolean inDoubleQuote;
        private boolean expectRedirectTarget;

        CommandParser(String command, LexResult out) {
            this.command = command;
            this.out = out;
        }

        /**
         * 执行单趟词法扫描
         */
        void parse() {
            int length = command.length();
            while (position < length) {
                char c = command.charAt(position);
                if (inSingleQuote) {
                    if (c == '\'') {
                        inSingleQuote = false;
                    } else {
                        buffer.append(c);
                    }
                    position++;
                } else if (inDoubleQuote) {
                    parseDoubleQuote(c);
                } else {
                    parseNormal(c);
                }
            }
            flushToken();
            endSegment();
        }

        /**
         * 解析双引号内字符：引号关闭、转义、变量与命令替换仍生效
         * @param c
         */
        private void parseDoubleQuote(char c) {
            if (c == '"') {
                inDoubleQuote = false;
                position++;
            } else if (c == '`') {
                readBacktick();
            } else if (c == '$') {
                readDollar();
            } else if (c == '\\' && position + 1 < command.length()) {
                buffer.append(command.charAt(position + 1));
                position += 2;
            } else {
                buffer.append(c);
                position++;
            }
        }

        /**
         * 解析非引号字符：转义、变量、替换、分隔符与重定向
         * @param c
         */
        private void parseNormal(char c) {
            switch (c) {
                case '\'' -> {
                    inSingleQuote = true;
                    position++;
                }
                case '"' -> {
                    inDoubleQuote = true;
                    position++;
                }
                case '\\' -> {
                    if (position + 1 < command.length()) {
                        buffer.append(command.charAt(position + 1));
                        position += 2;
                    } else {
                        position++;
                    }
                }
                case '^' -> {
                    // cmd转义符：跳过并保留下一字符
                    if (position + 1 < command.length()) {
                        buffer.append(command.charAt(position + 1));
                        position += 2;
                    } else {
                        position++;
                    }
                }
                case ' ', '\t' -> {
                    flushToken();
                    position++;
                }
                case '`' -> readBacktick();
                case '$' -> readDollar();
                case '%' -> readPercent();
                case ';', '\n', '\r', '|', '&', '(', ')' -> {
                    flushToken();
                    endSegment();
                    position++;
                }
                case '>' -> {
                    // 2>&1描述符复制不写文件
                    if (peekAt(1) == '&' && isDigit(peekAt(2))) {
                        flushToken();
                        position += 3;
                    } else {
                        flushToken();
                        markRedirect();
                        position++;
                    }
                }
                case '<' -> {
                    flushToken();
                    markRedirect();
                    position++;
                }
                default -> {
                    buffer.append(c);
                    position++;
                }
            }
        }

        /**
         * 解析$开头：变量展开与命令替换
         */
        private void readDollar() {
            char next = peekAt(1);
            if (next == '(') {
                readDollarParenthesis();
            } else if (next == '{') {
                int close = command.indexOf('}', position + 2);
                if (close < 0) {
                    buffer.append(UNKNOWN_MARK);
                    position = command.length();
                    return;
                }
                expandVariable(command.substring(position + 2, close));
                position = close + 1;
            } else {
                int end = position + 1;
                while (end < command.length() && isVarChar(command.charAt(end))) {
                    end++;
                }
                if (end == position + 1) {
                    buffer.append('$');
                    position++;
                    return;
                }
                expandVariable(command.substring(position + 1, end));
                position = end;
            }
        }

        /**
         * 解析$(...)命令替换，含$((算术展开
         */
        private void readDollarParenthesis() {
            if (peekAt(2) == '(') {
                // 算术展开结果不可预知
                buffer.append(UNKNOWN_MARK);
                int close = command.indexOf("))", position + 3);
                position = close >= 0 ? close + 2 : command.length();
                return;
            }
            int start = position + 2;
            int depth = 0;
            int i = start;
            while (i < command.length()) {
                char ch = command.charAt(i);
                if (ch == '(') {
                    depth++;
                } else if (ch == ')') {
                    if (depth == 0) {
                        break;
                    }
                    depth--;
                }
                i++;
            }
            classifySubstitution(command.substring(start, Math.min(i, command.length())));
            buffer.append(SUBST_MARK);
            position = Math.min(i + 1, command.length());
        }

        /**
         * 解析反引号命令替换
         */
        private void readBacktick() {
            int close = command.indexOf('`', position + 1);
            int end = close >= 0 ? close : command.length();
            classifySubstitution(command.substring(position + 1, end));
            buffer.append(SUBST_MARK);
            position = Math.min(end + 1, command.length());
        }

        /**
         * 解析%VAR%形式的cmd变量展开
         */
        private void readPercent() {
            int close = command.indexOf('%', position + 1);
            if (close < 0) {
                buffer.append('%');
                position++;
                return;
            }
            String name = command.substring(position + 1, close);
            if (name.isEmpty()) {
                buffer.append('%');
                position++;
                return;
            }
            expandVariable(name);
            position = close + 1;
        }

        /**
         * 展开变量：IFS视为分隔符，已赋值变量按值内联展开，未知变量打占位标记
         * @param name
         */
        private void expandVariable(String name) {
            if ("ifs".equals(name)) {
                flushToken();
                return;
            }
            String value = out.variables.get(name);
            if (value == null) {
                buffer.append(UNKNOWN_MARK);
                return;
            }
            for (int i = 0; i < value.length(); i++) {
                char c = value.charAt(i);
                if (c == ' ' || c == '\t') {
                    flushToken();
                } else {
                    buffer.append(c);
                }
            }
        }

        /**
         * 递归分级命令替换内部的子命令
         * @param inner
         */
        private void classifySubstitution(String inner) {
            if (inner == null || inner.isBlank()) {
                return;
            }
            LexResult nested = new LexResult();
            new CommandParser(inner, nested).parse();
            for (Segment sub : nested.segments) {
                AgentPermissionDecision decision = classifySegment(sub.tokens, sub.hasRedirect);
                if (decision != null) {
                    out.nestedDecisions.add(decision);
                }
            }
            // 多层嵌套替换递归收敛
            out.nestedDecisions.addAll(nested.nestedDecisions);
        }

        /**
         * 结束当前token：识别段首赋值与set赋值并注册变量
         */
        private void flushToken() {
            if (buffer.isEmpty()) {
                return;
            }
            String value = buffer.toString();
            if (isAssignmentToken(value) && (segment.tokens.isEmpty() || "set".equals(prevToken))) {
                int eq = value.indexOf('=');
                out.variables.put(value.substring(0, eq), value.substring(eq + 1));
            }
            if (expectRedirectTarget) {
                expectRedirectTarget = false;
                // 重定向到空设备不视为文件写入
                if (NULL_TARGETS.contains(value)) {
                    segment.hasRedirect = false;
                }
            }
            segment.tokens.add(value);
            prevToken = value;
            buffer.setLength(0);
        }

        /**
         * 结束当前段并重置段状态，空段跳过
         */
        private void endSegment() {
            if (!segment.tokens.isEmpty()) {
                out.segments.add(segment);
                prevToken = "";
            }
            segment = new Segment();
            expectRedirectTarget = false;
        }

        /**
         * 标记重定向并等待校验目标
         */
        private void markRedirect() {
            segment.hasRedirect = true;
            expectRedirectTarget = true;
        }

        private char peekAt(int offset) {
            int index = position + offset;
            return index >= 0 && index < command.length() ? command.charAt(index) : '\uFFFF';
        }

        private boolean isDigit(char c) {
            return c >= '0' && c <= '9';
        }

        private boolean isVarChar(char c) {
            return c >= 'a' && c <= 'z' || c >= '0' && c <= '9' || c == '_';
        }
    }
}
