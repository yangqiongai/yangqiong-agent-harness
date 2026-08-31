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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * MCP默认客户端，基于传输层实现initialize握手、工具发现与调用
 * @author yangqiong
 */
public class DefaultMcpClient implements McpClient {

    private static final Logger log = LoggerFactory.getLogger(DefaultMcpClient.class);

    /**
     * 客户端请求的协议版本
     */
    private static final String REQUESTED_PROTOCOL_VERSION = "2025-06-18";

    /**
     * 分页拉取工具列表的安全上限
     */
    private static final int MAX_LIST_PAGES = 50;

    /**
     * 服务端配置
     */
    private final McpServerConfig config;

    /**
     * 底层传输
     */
    private final McpTransport transport;

    /**
     * 协商后的协议版本
     */
    private volatile String negotiatedProtocolVersion;

    /**
     * 握手协商后的服务端能力
     */
    private volatile McpServerCapabilities serverCapabilities;

    /**
     * 构造默认客户端
     * @param config
     * @param transport
     */
    public DefaultMcpClient(McpServerConfig config, McpTransport transport) {
        this.config = config;
        this.transport = transport;
    }

    /**
     * 按配置创建传输并构造客户端
     * @param config
     * @return
     */
    public static DefaultMcpClient create(McpServerConfig config) {
        return new DefaultMcpClient(config, createTransport(config));
    }

    /**
     * 按传输类型创建传输实现
     * @param config
     * @return
     */
    public static McpTransport createTransport(McpServerConfig config) {
        return switch (config.getTransport()) {
            case STREAMABLE_HTTP -> new McpHttpStreamableTransport(
                    config.getEndpoint(), config.getHeaders(), config.getRequestTimeout());
            case SSE -> new McpHttpSseTransport(
                    config.getEndpoint(), config.getHeaders(), config.getRequestTimeout());
            case STDIO -> new McpStdioTransport(
                    config.getCommand(), config.getArgs(), config.getEnv(), config.getRequestTimeout());
        };
    }

    /**
     * 执行initialize握手并发送initialized通知
     * @return
     */
    @Override
    public Mono<Void> initialize() {
        String requestedVersion = config.getProtocolVersion() != null
                ? config.getProtocolVersion() : REQUESTED_PROTOCOL_VERSION;
        ObjectNode params = McpJsonRpc.mapper().createObjectNode();
        params.put("protocolVersion", requestedVersion);
        ObjectNode capabilities = McpJsonRpc.mapper().createObjectNode();
        if (config.getClientCapabilities() != null) {
            config.getClientCapabilities().applyTo(capabilities);
        }
        params.set("capabilities", capabilities);
        ObjectNode clientInfo = McpJsonRpc.mapper().createObjectNode();
        clientInfo.put("name", "yangqiong-agent-harness");
        clientInfo.put("version", "1.0.0");
        params.set("clientInfo", clientInfo);
        return transport.call("initialize", params)
                .timeout(config.getInitTimeout())
                .doOnNext(result -> {
                    negotiateVersion(result, requestedVersion, config.getName());
                    serverCapabilities = McpServerCapabilities.from(
                            result, config.getName(), negotiatedProtocolVersion);
                })
                .then(transport.notify("notifications/initialized"))
                .onErrorMap(e -> !(e instanceof McpRpcException),
                        e -> new McpRpcException(-32603, "initialize",
                                "MCP服务端[" + config.getName() + "]握手失败：" + e.getMessage()));
    }

    /**
     * 发现服务端工具列表，支持游标分页
     * @return
     */
    @Override
    public Mono<List<McpToolDescriptor>> listTools() {
        List<JsonNode> raw = new ArrayList<>();
        return collectPages("tools/list", null, raw, 0, "tools")
                .then(Mono.fromCallable(() -> raw.stream().map(DefaultMcpClient::toToolDescriptor)
                        .collect(java.util.stream.Collectors.toList())));
    }

    /**
     * 发现服务端资源列表，支持游标分页
     * @return
     */
    @Override
    public Mono<List<McpResourceDescriptor>> listResources() {
        List<JsonNode> raw = new ArrayList<>();
        return collectPages("resources/list", null, raw, 0, "resources")
                .then(Mono.fromCallable(() -> raw.stream().map(McpResourceDescriptor::fromJson)
                        .collect(java.util.stream.Collectors.toList())));
    }

    /**
     * 发现服务端资源模板列表，支持游标分页
     * @return
     */
    @Override
    public Mono<List<McpResourceTemplate>> listResourceTemplates() {
        List<JsonNode> raw = new ArrayList<>();
        return collectPages("resources/templates/list", null, raw, 0, "resourceTemplates")
                .then(Mono.fromCallable(() -> raw.stream().map(McpResourceTemplate::fromJson)
                        .collect(java.util.stream.Collectors.toList())));
    }

    /**
     * 读取指定URI的资源内容
     * @param uri
     * @return
     */
    @Override
    public Mono<McpResourceContent> readResource(String uri) {
        ObjectNode params = McpJsonRpc.mapper().createObjectNode();
        params.put("uri", uri);
        return transport.call("resources/read", params)
                .timeout(config.getRequestTimeout())
                .map(result -> {
                    JsonNode contents = result.path("contents");
                    JsonNode item = contents.isArray() && !contents.isEmpty() ? contents.get(0) : result;
                    return McpResourceContent.fromJson(item);
                });
    }

    /**
     * 发现服务端提示词列表，支持游标分页
     * @return
     */
    @Override
    public Mono<List<McpPromptDescriptor>> listPrompts() {
        List<JsonNode> raw = new ArrayList<>();
        return collectPages("prompts/list", null, raw, 0, "prompts")
                .then(Mono.fromCallable(() -> raw.stream().map(McpPromptDescriptor::fromJson)
                        .collect(java.util.stream.Collectors.toList())));
    }

    /**
     * 按名称与参数展开提示词
     * @param name
     * @param arguments
     * @return
     */
    @Override
    public Mono<McpPromptResult> getPrompt(String name, Map<String, Object> arguments) {
        ObjectNode params = McpJsonRpc.mapper().createObjectNode();
        params.put("name", name);
        if (arguments != null && !arguments.isEmpty()) {
            params.set("arguments", McpJsonRpc.mapper().valueToTree(arguments));
        }
        return transport.call("prompts/get", params)
                .timeout(config.getRequestTimeout())
                .map(McpPromptResult::fromJson);
    }

    /**
     * 调用服务端工具
     * @param name
     * @param arguments
     * @return
     */
    @Override
    public Mono<McpToolCallResult> callTool(String name, Map<String, Object> arguments) {
        ObjectNode params = McpJsonRpc.mapper().createObjectNode();
        params.put("name", name);
        params.set("arguments", McpJsonRpc.mapper().valueToTree(
                arguments != null ? arguments : Map.of()));
        return transport.call("tools/call", params)
                .timeout(config.getRequestTimeout())
                .map(this::toToolCallResult);
    }

    /**
     * 订阅服务端主动推送的JSON-RPC通知
     * @return
     */
    @Override
    public Flux<JsonNode> notifications() {
        return transport.notifications();
    }

    /**
     * 关闭底层传输
     * @return
     */
    @Override
    public Mono<Void> close() {
        return transport.close();
    }

    /**
     * 获取协商后的协议版本
     * @return
     */
    public String getNegotiatedProtocolVersion() {
        return negotiatedProtocolVersion;
    }

    /**
     * 获取握手协商后的服务端能力
     * @return
     */
    @Override
    public McpServerCapabilities getServerCapabilities() {
        return serverCapabilities;
    }

    private void negotiateVersion(JsonNode initializeResult, String requestedVersion, String serverName) {
        String serverVersion = initializeResult.path("protocolVersion").asText(null);
        if (serverVersion == null || serverVersion.isBlank()) {
            negotiatedProtocolVersion = requestedVersion;
            return;
        }
        if (!serverVersion.equals(requestedVersion)) {
            log.info("MCP服务端[{}]协议版本降级：请求{}实际{}", serverName, requestedVersion, serverVersion);
        }
        negotiatedProtocolVersion = serverVersion;
    }

    /**
     * 按游标分页收集指定方法返回的条目节点
     * @param method
     * @param cursor
     * @param collected
     * @param page
     * @param itemsField
     * @return
     */
    private Mono<Void> collectPages(String method, String cursor, List<JsonNode> collected, int page,
            String itemsField) {
        if (page >= MAX_LIST_PAGES) {
            return Mono.error(new McpRpcException(-32603, method,
                    "MCP服务端[" + config.getName() + "]" + method + "分页超过上限" + MAX_LIST_PAGES));
        }
        ObjectNode params = McpJsonRpc.mapper().createObjectNode();
        if (cursor != null) {
            params.put("cursor", cursor);
        }
        return transport.call(method, params)
                .timeout(config.getRequestTimeout())
                .flatMap(result -> {
                    for (JsonNode item : result.path(itemsField)) {
                        collected.add(item);
                    }
                    String nextCursor = result.path("nextCursor").asText(null);
                    if (nextCursor != null && !nextCursor.isBlank()) {
                        return collectPages(method, nextCursor, collected, page + 1, itemsField);
                    }
                    return Mono.empty();
                });
    }

    private static McpToolDescriptor toToolDescriptor(JsonNode tool) {
        Map<String, Object> schema = McpJsonRpc.mapper().convertValue(
                tool.path("inputSchema"), new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() {
                });
        return new McpToolDescriptor(
                tool.path("name").asText(),
                tool.path("description").asText(""),
                schema);
    }

    private McpToolCallResult toToolCallResult(JsonNode result) {
        List<Map<String, Object>> content = McpJsonRpc.mapper().convertValue(
                result.path("content"), new com.fasterxml.jackson.core.type.TypeReference<List<Map<String, Object>>>() {
                });
        boolean isError = result.path("isError").asBoolean(false);
        return new McpToolCallResult(content, isError);
    }
}
