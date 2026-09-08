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
package com.yangqiongai.agent.harness.mcp;

import com.yangqiongai.agent.harness.core.tool.AgentToolkit;
import com.yangqiongai.agent.harness.core.tool.AgentTool;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * MCP工具箱，管理多服务端客户端生命周期并将工具批量注册进引擎工具箱
 * @author yangqiong
 */
public class McpToolkit {

    private static final Logger log = LoggerFactory.getLogger(McpToolkit.class);

    /**
     * 服务端配置列表
     */
    private final List<McpServerConfig> configs;

    /**
     * 客户端工厂，测试可注入假实现
     */
    private final McpClientFactory clientFactory;

    /**
     * 已建立的服务端客户端表
     */
    private final Map<String, McpClient> clients = new LinkedHashMap<>();

    /**
     * 各服务端发现的工具表
     */
    private final Map<String, List<McpToolDescriptor>> toolsByServer = new ConcurrentHashMap<>();

    /**
     * 是否已完成握手与工具发现（用于预热幂等）
     */
    private volatile boolean initialized;

    /**
     * 以默认客户端工厂构造
     * @param configs
     */
    public McpToolkit(List<McpServerConfig> configs) {
        this(configs, DefaultMcpClient::create);
    }

    /**
     * 以自定义客户端工厂构造
     * @param configs
     * @param clientFactory
     */
    public McpToolkit(List<McpServerConfig> configs, McpClientFactory clientFactory) {
        this.configs = List.copyOf(configs);
        this.clientFactory = clientFactory;
    }

    /**
     * 并行完成全部服务端握手与工具发现
     * <p>
     * 具备幂等保护：已完成初始化的场景直接返回，避免预热与build反复握手。
     * </p>
     * @return
     */
    public Mono<Void> initializeAll() {
        if (initialized) {
            return Mono.empty();
        }
        return Flux.fromIterable(configs)
                .flatMap(config -> connectServer(config)
                        .onErrorResume(e -> {
                            log.warn("MCP服务端[{}]接入失败，已跳过：{}", config.getName(), e.getMessage());
                            return Mono.empty();
                        }))
                .then()
                .doOnSuccess(v -> initialized = true)
                .then();
    }

    /**
     * 是否已完成握手与工具发现
     * @return
     */
    public boolean isInitialized() {
        return initialized;
    }

    /**
     * 将全部已发现工具注册进引擎工具箱，工具名默认带服务端前缀
     * @param toolkit
     * @return
     */
    public Mono<Void> register(AgentToolkit toolkit) {
        return initializeAll().then(Mono.fromRunnable(() -> registerAll(toolkit)));
    }

    /**
     * 同步注册全部工具（要求已完成initializeAll）
     * @param toolkit
     * @return
     */
    public int registerAll(AgentToolkit toolkit) {
        int count = 0;
        for (Map.Entry<String, List<McpToolDescriptor>> entry : toolsByServer.entrySet()) {
            McpClient client = clients.get(entry.getKey());
            for (McpToolDescriptor descriptor : entry.getValue()) {
                AgentTool tool = new McpToolAdapter(exposeName(entry.getKey(), descriptor), client, descriptor);
                toolkit.addTool(tool);
                count++;
            }
        }
        return count;
    }

    /**
     * 关闭全部服务端客户端
     * @return
     */
    public Mono<Void> close() {
        return Flux.fromIterable(clients.values())
                .flatMap(McpClient::close)
                .then();
    }

    /**
     * 按服务端名称获取客户端
     * @param serverName
     * @return
     */
    public McpClient getClient(String serverName) {
        return clients.get(serverName);
    }

    /**
     * 获取全部已接入服务端名称
     * @return
     */
    public List<String> getServerNames() {
        return new ArrayList<>(clients.keySet());
    }

    /**
     * 获取指定服务端发现的工具列表
     * @param serverName
     * @return
     */
    public List<McpToolDescriptor> getTools(String serverName) {
        return toolsByServer.getOrDefault(serverName, List.of());
    }

    private Mono<Void> connectServer(McpServerConfig config) {
        return Mono.fromCallable(() -> clientFactory.create(config))
                .flatMap(client -> client.initialize()
                        .then(client.listTools())
                        .doOnNext(tools -> {
                            clients.put(config.getName(), client);
                            toolsByServer.put(config.getName(), tools);
                            log.info("MCP服务端[{}]接入成功，发现{}个工具",
                                    config.getName(), tools.size());
                        }))
                .then();
    }

    private String exposeName(String serverName, McpToolDescriptor descriptor) {
        McpServerConfig config = configs.stream()
                .filter(c -> c.getName().equals(serverName))
                .findFirst()
                .orElse(null);
        if (config != null && config.isPrefixToolNames()) {
            return serverName + "__" + descriptor.getName();
        }
        return descriptor.getName();
    }

    /**
     * MCP客户端工厂
     * @author yangqiong
     */
    @FunctionalInterface
    public interface McpClientFactory {

        /**
         * 按服务端配置创建客户端
         * @param config
         * @return
         */
        McpClient create(McpServerConfig config);
    }
}
