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
package com.yangqiong.agent.harness.mcp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.MonoSink;
import reactor.core.publisher.Sinks;

/**
 * MCP标准输入输出传输，拉起子进程并按行分隔JSON-RPC通信
 * @author yangqiong
 */
public class McpStdioTransport implements McpTransport {

    private static final Logger log = LoggerFactory.getLogger(McpStdioTransport.class);

    /**
     * 子进程命令
     */
    private final String command;

    /**
     * 子进程命令参数
     */
    private final List<String> args;

    /**
     * 子进程环境变量
     */
    private final Map<String, String> env;

    /**
     * 单次请求超时
     */
    private final Duration requestTimeout;

    /**
     * 请求id自增序列
     */
    private final AtomicLong idSequence = new AtomicLong(1);

    /**
     * 待应答请求挂起表
     */
    private final Map<Long, MonoSink<JsonNode>> pending = new ConcurrentHashMap<>();

    /**
     * 服务端主动推送通知转发
     */
    private final Sinks.Many<JsonNode> notificationsSink = Sinks.many().multicast().onBackpressureBuffer();

    /**
     * 子进程实例
     */
    private volatile Process process;

    /**
     * 子进程标准输入写入器
     */
    private volatile BufferedWriter stdinWriter;

    /**
     * 构造Stdio传输
     * @param command
     * @param args
     * @param env
     * @param requestTimeout
     */
    public McpStdioTransport(String command, List<String> args, Map<String, String> env,
            Duration requestTimeout) {
        this.command = command;
        this.args = args != null ? List.copyOf(args) : List.of();
        this.env = env != null ? Map.copyOf(env) : Map.of();
        this.requestTimeout = requestTimeout != null ? requestTimeout : Duration.ofSeconds(60);
    }

    /**
     * 发送JSON-RPC请求，应答经标准输出按行回送
     * @param method
     * @param params
     * @return
     */
    @Override
    public Mono<JsonNode> call(String method, ObjectNode params) {
        return Mono.<JsonNode>create(sink -> {
            try {
                Process current = ensureStarted();
                long id = idSequence.getAndIncrement();
                pending.put(id, sink);
                sink.onDispose(() -> pending.remove(id));
                writeLine(McpJsonRpc.request(id, method, params).toString());
                log.debug("MCP stdio请求已发送：method={} id={} pid={}", method, id, current.pid());
            } catch (Exception e) {
                sink.error(new McpRpcException(-32603, method, method + "stdio请求发送失败：" + e.getMessage()));
            }
        }).timeout(requestTimeout);
    }

    /**
     * 发送JSON-RPC通知
     * @param method
     * @return
     */
    @Override
    public Mono<Void> notify(String method) {
        return notify(method, null);
    }

    /**
     * 发送带参数的JSON-RPC通知
     * @param method
     * @param params
     * @return
     */
    @Override
    public Mono<Void> notify(String method, ObjectNode params) {
        return Mono.fromRunnable(() -> {
            try {
                ensureStarted();
                writeLine(McpJsonRpc.notification(method, params).toString());
            } catch (Exception e) {
                throw new McpRpcException(-32603, method, method + "stdio通知发送失败：" + e.getMessage());
            }
        });
    }

    /**
     * 订阅服务端主动推送的JSON-RPC通知
     * @return
     */
    @Override
    public Flux<JsonNode> notifications() {
        return notificationsSink.asFlux();
    }

    /**
     * 销毁子进程并失败所有挂起请求
     * @return
     */
    @Override
    public Mono<Void> close() {
        return Mono.fromRunnable(() -> {
            Process current = process;
            if (current != null && current.isAlive()) {
                current.destroy();
            }
            pending.forEach((id, sink) -> sink.error(new McpRpcException(-32603, "close", "传输已关闭")));
            pending.clear();
            notificationsSink.tryEmitComplete();
        });
    }

    private synchronized Process ensureStarted() throws IOException {
        if (process != null) {
            return process;
        }
        ProcessBuilder builder = new ProcessBuilder(buildCommand());
        builder.environment().putAll(env);
        builder.redirectError(ProcessBuilder.Redirect.INHERIT);
        Process started = builder.start();
        this.process = started;
        this.stdinWriter = new BufferedWriter(new OutputStreamWriter(started.getOutputStream(), StandardCharsets.UTF_8));
        Thread reader = new Thread(() -> drainStdout(started), "mcp-stdio-reader-" + started.pid());
        reader.setDaemon(true);
        reader.start();
        return started;
    }

    private List<String> buildCommand() {
        java.util.ArrayList<String> full = new java.util.ArrayList<>(args.size() + 1);
        full.add(command);
        full.addAll(args);
        return full;
    }

    private synchronized void writeLine(String line) throws IOException {
        BufferedWriter writer = stdinWriter;
        if (writer == null) {
            throw new IllegalStateException("stdio传输未启动");
        }
        writer.write(line);
        writer.write('\n');
        writer.flush();
    }

    private void drainStdout(Process started) {
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(started.getInputStream(), StandardCharsets.UTF_8))) {
            String line = reader.readLine();
            while (line != null) {
                dispatchLine(line);
                line = reader.readLine();
            }
        } catch (IOException e) {
            log.debug("MCP stdio标准输出读取结束：{}", e.getMessage());
        }
    }

    private void dispatchLine(String line) {
        JsonNode json;
        try {
            json = McpJsonRpc.mapper().readTree(line);
        } catch (Exception e) {
            log.debug("MCP stdio非JSON行已忽略：{}", line);
            return;
        }
        if (!McpJsonRpc.isResponse(json)) {
            notificationsSink.tryEmitNext(json);
            return;
        }
        JsonNode idNode = json.get("id");
        if (idNode == null || !idNode.isIntegralNumber()) {
            return;
        }
        MonoSink<JsonNode> sink = pending.remove(idNode.asLong());
        if (sink == null) {
            return;
        }
        JsonNode error = json.get("error");
        if (error != null && !error.isNull()) {
            int code = error.path("code").asInt(-32603);
            String message = error.path("message").asText("MCP调用失败");
            sink.error(new McpRpcException(code, "", "stdio应答失败：" + message + "（code=" + code + "）"));
            return;
        }
        JsonNode result = json.get("result");
        sink.success(result != null ? result : McpJsonRpc.mapper().createObjectNode());
    }
}
