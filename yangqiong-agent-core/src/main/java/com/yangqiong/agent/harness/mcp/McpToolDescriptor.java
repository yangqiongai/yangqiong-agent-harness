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

import java.util.List;
import java.util.Map;

/**
 * MCP工具描述
 * @author yangqiong
 */
public final class McpToolDescriptor {

    /**
     * 工具名称（服务端原始名称）
     */
    private final String name;

    /**
     * 工具描述
     */
    private final String description;

    /**
     * 工具参数JSON Schema定义
     */
    private final Map<String, Object> inputSchema;

    public McpToolDescriptor(String name, String description, Map<String, Object> inputSchema) {
        this.name = name;
        this.description = description != null ? description : "";
        this.inputSchema = inputSchema != null ? Map.copyOf(inputSchema) : Map.of();
    }

    /**
     * 获取工具名称
     * @return
     */
    public String getName() {
        return name;
    }

    /**
     * 获取工具描述
     * @return
     */
    public String getDescription() {
        return description;
    }

    /**
     * 获取工具参数JSON Schema定义
     * @return
     */
    public Map<String, Object> getInputSchema() {
        return inputSchema;
    }
}
