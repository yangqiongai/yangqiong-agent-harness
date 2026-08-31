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
import com.fasterxml.jackson.core.type.TypeReference;
import java.util.Map;

/**
 * MCP提示词消息，承载角色的文本或图片内容
 * @author yangqiong
 */
public record McpPromptMessage(String role, Map<String, Object> content) {

    /**
     * 从JSON节点解析提示词消息
     * @param node
     * @return
     */
    public static McpPromptMessage fromJson(JsonNode node) {
        Map<String, Object> content = McpJsonRpc.mapper().convertValue(
                node.path("content"), new TypeReference<Map<String, Object>>() {
                });
        return new McpPromptMessage(node.path("role").asText("user"), content);
    }
}