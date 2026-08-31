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

/**
 * MCP资源模板，对应resources/list资源动态URI模板
 * @author yangqiong
 */
public record McpResourceTemplate(String uriTemplate, String name, String description, String mimeType) {

    /**
     * 从JSON节点解析资源模板
     * @param node
     * @return
     */
    public static McpResourceTemplate fromJson(JsonNode node) {
        return new McpResourceTemplate(
                node.path("uriTemplate").asText(),
                node.path("name").asText(""),
                node.path("description").asText(""),
                node.path("mimeType").asText(null));
    }
}