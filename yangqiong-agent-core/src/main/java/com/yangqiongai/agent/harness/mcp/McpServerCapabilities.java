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

import com.fasterxml.jackson.databind.JsonNode;
import java.util.Map;

/**
 * MCP服务端能力，封装握手结果为客户端提供的实际能力与协商版本
 * @author yangqiong
 */
public class McpServerCapabilities {

    /**
     * 服务端逻辑名称
     */
    private final String serverName;

    /**
     * 协商后的协议版本
     */
    private final String protocolVersion;

    /**
     * 原始能力字段键值
     */
    private final Map<String, Object> capabilities;

    /**
     * 默认构造
     * @param serverName
     * @param protocolVersion
     * @param capabilities
     */
    public McpServerCapabilities(String serverName, String protocolVersion, Map<String, Object> capabilities) {
        this.serverName = serverName;
        this.protocolVersion = protocolVersion;
        this.capabilities = capabilities;
    }

    /**
     * 从握手结果解析服务端能力
     * @param initResult
     * @param serverName
     * @param protocolVersion
     * @return
     */
    public static McpServerCapabilities from(JsonNode initResult, String serverName, String protocolVersion) {
        Map<String, Object> capabilities = McpJsonRpc.mapper().convertValue(
                initResult.path("capabilities"), new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() {
                });
        return new McpServerCapabilities(serverName, protocolVersion, capabilities);
    }

    /**
     * 获取服务端逻辑名称
     * @return
     */
    public String getServerName() {
        return serverName;
    }

    /**
     * 获取协商后的协议版本
     * @return
     */
    public String getProtocolVersion() {
        return protocolVersion;
    }

    /**
     * 获取原始能力字段
     * @return
     */
    public Map<String, Object> getCapabilities() {
        return capabilities;
    }

    /**
     * 判断服务端是否声明了指定能力
     * @param capability
     * @return
     */
    public boolean supports(String capability) {
        return capabilities != null && capabilities.containsKey(capability);
    }
}