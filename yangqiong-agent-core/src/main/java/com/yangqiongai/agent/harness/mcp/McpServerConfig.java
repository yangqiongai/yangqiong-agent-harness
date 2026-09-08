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

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * MCP服务端配置
 * @author yangqiong
 */
public final class McpServerConfig {

    /**
     * 服务端逻辑名称，用于工具名前缀与日志标识
     */
    private final String name;

    /**
     * 传输类型
     */
    private final McpTransportType transport;

    /**
     * 服务端地址（STREAMABLE_HTTP/SSE传输使用）
     */
    private final String endpoint;

    /**
     * 子进程命令（STDIO传输使用）
     */
    private final String command;

    /**
     * 子进程命令参数（STDIO传输使用）
     */
    private final List<String> args;

    /**
     * 子进程环境变量（STDIO传输使用）
     */
    private final Map<String, String> env;

    /**
     * 附加请求头（含鉴权头，HTTP传输使用）
     */
    private final Map<String, String> headers;

    /**
     * 初始化握手超时
     */
    private final Duration initTimeout;

    /**
     * 单次请求超时
     */
    private final Duration requestTimeout;

    /**
     * 是否为注册工具名添加服务端前缀（默认开启，避免多服务端工具重名冲突）
     */
    private final boolean prefixToolNames;

    /**
     * 客户端请求的协议版本，缺省由客户端选用最新稳定版
     */
    private final String protocolVersion;

    /**
     * 客户端能力声明
     */
    private final McpClientCapabilities clientCapabilities;

    private McpServerConfig(Builder builder) {
        this.name = builder.name;
        this.transport = builder.transport;
        this.endpoint = builder.endpoint;
        this.command = builder.command;
        this.args = List.copyOf(builder.args);
        this.env = Map.copyOf(builder.env);
        this.headers = Map.copyOf(builder.headers);
        this.initTimeout = builder.initTimeout;
        this.requestTimeout = builder.requestTimeout;
        this.prefixToolNames = builder.prefixToolNames;
        this.protocolVersion = builder.protocolVersion;
        this.clientCapabilities = builder.clientCapabilities;
    }

    /**
     * 创建配置构建器
     * @param name
     * @param transport
     * @return
     */
    public static Builder builder(String name, McpTransportType transport) {
        return new Builder(name, transport);
    }

    /**
     * 获取服务端逻辑名称
     * @return
     */
    public String getName() {
        return name;
    }

    /**
     * 获取传输类型
     * @return
     */
    public McpTransportType getTransport() {
        return transport;
    }

    /**
     * 获取服务端地址
     * @return
     */
    public String getEndpoint() {
        return endpoint;
    }

    /**
     * 获取子进程命令
     * @return
     */
    public String getCommand() {
        return command;
    }

    /**
     * 获取子进程命令参数
     * @return
     */
    public List<String> getArgs() {
        return args;
    }

    /**
     * 获取子进程环境变量
     * @return
     */
    public Map<String, String> getEnv() {
        return env;
    }

    /**
     * 获取附加请求头
     * @return
     */
    public Map<String, String> getHeaders() {
        return headers;
    }

    /**
     * 获取初始化握手超时
     * @return
     */
    public Duration getInitTimeout() {
        return initTimeout;
    }

    /**
     * 获取单次请求超时
     * @return
     */
    public Duration getRequestTimeout() {
        return requestTimeout;
    }

    /**
     * 是否为工具名添加服务端前缀
     * @return
     */
    public boolean isPrefixToolNames() {
        return prefixToolNames;
    }

    /**
     * 获取客户端请求的协议版本
     * @return
     */
    public String getProtocolVersion() {
        return protocolVersion;
    }

    /**
     * 获取客户端能力声明
     * @return
     */
    public McpClientCapabilities getClientCapabilities() {
        return clientCapabilities;
    }

    /**
     * MCP服务端配置构建器
     * @author yangqiong
     */
    public static final class Builder {

        private final String name;

        private final McpTransportType transport;

        private String endpoint;

        private String command;

        private List<String> args = new ArrayList<>();

        private Map<String, String> env = new HashMap<>();

        private Map<String, String> headers = new HashMap<>();

        private Duration initTimeout = Duration.ofSeconds(30);

        private Duration requestTimeout = Duration.ofSeconds(60);

        private boolean prefixToolNames = true;

        private String protocolVersion;

        private McpClientCapabilities clientCapabilities = McpClientCapabilities.none();

        private Builder(String name, McpTransportType transport) {
            if (name == null || name.isBlank()) {
                throw new IllegalArgumentException("MCP服务端名称不能为空");
            }
            if (transport == null) {
                throw new IllegalArgumentException("MCP传输类型不能为空");
            }
            this.name = name;
            this.transport = transport;
        }

        /**
         * 设置服务端地址
         * @param endpoint
         * @return
         */
        public Builder endpoint(String endpoint) {
            this.endpoint = endpoint;
            return this;
        }

        /**
         * 设置子进程命令
         * @param command
         * @return
         */
        public Builder command(String command) {
            this.command = command;
            return this;
        }

        /**
         * 设置子进程命令参数
         * @param args
         * @return
         */
        public Builder args(List<String> args) {
            this.args = args != null ? new ArrayList<>(args) : new ArrayList<>();
            return this;
        }

        /**
         * 设置子进程环境变量
         * @param env
         * @return
         */
        public Builder env(Map<String, String> env) {
            this.env = env != null ? new HashMap<>(env) : new HashMap<>();
            return this;
        }

        /**
         * 设置附加请求头
         * @param headers
         * @return
         */
        public Builder headers(Map<String, String> headers) {
            this.headers = headers != null ? new HashMap<>(headers) : new HashMap<>();
            return this;
        }

        /**
         * 添加单个鉴权头
         * @param name
         * @param value
         * @return
         */
        public Builder header(String name, String value) {
            this.headers.put(name, value);
            return this;
        }

        /**
         * 设置初始化握手超时
         * @param initTimeout
         * @return
         */
        public Builder initTimeout(Duration initTimeout) {
            this.initTimeout = initTimeout;
            return this;
        }

        /**
         * 设置单次请求超时
         * @param requestTimeout
         * @return
         */
        public Builder requestTimeout(Duration requestTimeout) {
            this.requestTimeout = requestTimeout;
            return this;
        }

        /**
         * 设置是否为工具名添加服务端前缀
         * @param prefixToolNames
         * @return
         */
        public Builder prefixToolNames(boolean prefixToolNames) {
            this.prefixToolNames = prefixToolNames;
            return this;
        }

        /**
         * 设置客户端请求的协议版本
         * @param protocolVersion
         * @return
         */
        public Builder protocolVersion(String protocolVersion) {
            this.protocolVersion = protocolVersion;
            return this;
        }

        /**
         * 设置客户端能力声明
         * @param clientCapabilities
         * @return
         */
        public Builder clientCapabilities(McpClientCapabilities clientCapabilities) {
            this.clientCapabilities = clientCapabilities != null
                    ? clientCapabilities : McpClientCapabilities.none();
            return this;
        }

        /**
         * 构建配置并校验传输必填项
         * @return
         */
        public McpServerConfig build() {
            if (transport == McpTransportType.STDIO) {
                if (command == null || command.isBlank()) {
                    throw new IllegalArgumentException("STDIO传输必须配置command：" + name);
                }
            } else {
                if (endpoint == null || endpoint.isBlank()) {
                    throw new IllegalArgumentException(transport + "传输必须配置endpoint：" + name);
                }
            }
            return new McpServerConfig(this);
        }
    }
}
