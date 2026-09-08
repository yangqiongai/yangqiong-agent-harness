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
package com.yangqiongai.agent.harness.local.tool;

import com.yangqiongai.agent.harness.core.message.AgentTextBlock;
import com.yangqiongai.agent.harness.core.message.AgentToolResultBlock;
import com.yangqiongai.agent.harness.core.tool.AgentTool;
import com.yangqiongai.agent.harness.core.tool.AgentToolCallParam;
import com.yangqiongai.agent.harness.core.tool.ToolRiskLevel;
import reactor.core.publisher.Mono;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * 本地Shell工具
 * <p>
 * 在默认工作目录执行Shell命令（Windows使用cmd.exe /c，其它系统使用sh -c），
 * 合并捕获stdout与stderr并按上限截断，结果包含退出码，超时强制终止进程及其子进程。
 * 注意：本工具运行在宿主机整机权限下，不提供进程级沙箱隔离，命令是否放行
 * 依赖 LocalShellPolicyGate 内容分级审批；对不可信输入应部署在容器/受限账号环境中。
 * </p>
 * @author yangqiong
 */
public class LocalShellTool implements AgentTool {

    /**
     * 默认执行超时时间
     */
    private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(30);

    /**
     * 默认最大输出字符数
     */
    private static final int DEFAULT_MAX_OUTPUT_LENGTH = 10000;

    /**
     * 读取输出线程的收尾等待上限
     */
    private static final long READER_JOIN_TIMEOUT_MILLIS = 5000L;

    /**
     * 工具名称
     */
    private final String name = "local_shell";

    /**
     * 工具描述
     */
    private final String description = "在本地默认工作目录执行Shell命令（Windows使用cmd.exe，其它系统使用sh），"
            + "返回合并的stdout与stderr输出及退出码，输出超过上限时截断并标注truncated:true，"
            + "默认超时30秒后强制终止进程。";

    /**
     * 默认工作目录，null表示继承当前进程工作目录
     */
    private final Path workDir;

    /**
     * 执行超时时间
     */
    private final Duration timeout;

    /**
     * 最大输出字符数，超出部分截断
     */
    private final int maxOutputLength;

    /**
     * 构造，默认超时30秒、最大输出10000字符
     * @param workDir 默认工作目录，null表示继承当前进程工作目录
     */
    public LocalShellTool(Path workDir) {
        this(workDir, DEFAULT_TIMEOUT, DEFAULT_MAX_OUTPUT_LENGTH);
    }

    /**
     * 全参构造
     * @param workDir 默认工作目录，null表示继承当前进程工作目录
     * @param timeout 执行超时时间，null时取默认30秒
     * @param maxOutputLength 最大输出字符数，非正数时取默认10000
     */
    public LocalShellTool(Path workDir, Duration timeout, int maxOutputLength) {
        this.workDir = workDir != null ? workDir.toAbsolutePath().normalize() : null;
        this.timeout = timeout != null ? timeout : DEFAULT_TIMEOUT;
        this.maxOutputLength = maxOutputLength > 0 ? maxOutputLength : DEFAULT_MAX_OUTPUT_LENGTH;
    }

    /**
     * 获取工具名称
     * @return
     */
    @Override
    public String getName() {
        return name;
    }

    /**
     * 获取工具描述
     * @return
     */
    @Override
    public String getDescription() {
        return description;
    }

    /**
     * 获取工具参数定义
     * @return
     */
    @Override
    public Map<String, Object> getParameters() {
        Map<String, Object> commandProp = new LinkedHashMap<>();
        commandProp.put("type", "string");
        commandProp.put("description", "要执行的Shell命令");

        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("command", commandProp);

        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("properties", properties);
        schema.put("required", List.of("command"));
        return schema;
    }

    /**
     * 执行Shell命令
     * @param param
     * @return
     */
    @Override
    public Mono<AgentToolResultBlock> callAsync(AgentToolCallParam param) {
        return Mono.fromCallable(() -> execute(param));
    }

    /**
     * Shell命令具备不可逆副作用，标记为高风险
     * @return
     */
    @Override
    public ToolRiskLevel getRiskLevel() {
        return ToolRiskLevel.HIGH;
    }

    /**
     * 同步执行命令并组装结果
     * @param param
     * @return
     */
    private AgentToolResultBlock execute(AgentToolCallParam param) {
        Map<String, Object> input = param != null ? param.getInput() : Map.of();
        Object commandObj = input.get("command");
        if (commandObj == null || commandObj.toString().isBlank()) {
            return AgentToolResultBlock.error("command 参数缺失");
        }
        String command = commandObj.toString().trim();

        ProcessBuilder builder = new ProcessBuilder(buildProcessCommand(command));
        if (workDir != null) {
            builder.directory(workDir.toFile());
        }
        // 合并stderr到stdout，单流读取避免双流交叉死锁
        builder.redirectErrorStream(true);

        Process process;
        try {
            process = builder.start();
        } catch (IOException e) {
            return AgentToolResultBlock.error("命令启动失败: " + e.getMessage());
        }

        // 后台线程持续排水输出，防止输出超出管道缓冲区导致进程无法退出
        StringBuilder outputBuffer = new StringBuilder();
        Thread outputReader = startOutputReader(process, outputBuffer);

        boolean finished;
        try {
            finished = process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            destroyForciblyWithDescendants(process);
            Thread.currentThread().interrupt();
            return AgentToolResultBlock.error("命令执行被中断: " + command);
        }

        if (!finished) {
            destroyForciblyWithDescendants(process);
            return AgentToolResultBlock.error(
                    "命令执行超时（上限" + timeout.toMillis() + "毫秒），已强制终止: " + command);
        }

        joinQuietly(outputReader);
        return assembleResult(process.exitValue(), outputBuffer.toString());
    }

    /**
     * 按操作系统组装进程命令
     * @param command
     * @return
     */
    private List<String> buildProcessCommand(String command) {
        if (isWindows()) {
            return List.of("cmd.exe", "/c", command);
        }
        return List.of("sh", "-c", command);
    }

    /**
     * 启动后台输出读取线程
     * @param process
     * @param buffer
     * @return
     */
    private Thread startOutputReader(Process process, StringBuilder buffer) {
        Thread thread = new Thread(() -> {
            try {
                buffer.append(new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8));
            } catch (IOException e) {
                // 进程被强制终止导致流关闭时结束读取，保留已捕获内容
            }
        });
        thread.setDaemon(true);
        thread.start();
        return thread;
    }

    /**
     * 组装包含退出码与截断标注的结果
     * @param exitCode
     * @param output
     * @return
     */
    private AgentToolResultBlock assembleResult(int exitCode, String output) {
        String normalized = output == null ? "" : output.strip();
        boolean truncated = normalized.length() > maxOutputLength;
        String content = truncated ? normalized.substring(0, maxOutputLength) : normalized;

        StringBuilder text = new StringBuilder();
        text.append("exit code: ").append(exitCode);
        if (truncated) {
            text.append("\ntruncated: true");
        }
        text.append("\n\n").append(content);
        return AgentToolResultBlock.of(List.of(AgentTextBlock.builder().text(text.toString()).build()));
    }

    /**
     * 强制终止进程及其全部子进程
     * @param process
     * @return
     */
    private void destroyForciblyWithDescendants(Process process) {
        process.descendants().forEach(ProcessHandle::destroyForcibly);
        process.destroyForcibly();
    }

    /**
     * 等待输出读取线程收尾
     * @param thread
     * @return
     */
    private void joinQuietly(Thread thread) {
        try {
            thread.join(READER_JOIN_TIMEOUT_MILLIS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * 判断当前是否Windows系统
     * @return
     */
    private boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
    }
}
