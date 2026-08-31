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
import java.util.Base64;

/**
 * MCP资源内容，对应resources/read的单个资源内容，文本与二进制至少其一
 * @author yangqiong
 */
public record McpResourceContent(String uri, String mimeType, String text, byte[] blob) {

    /**
     * 从JSON节点解析资源内容，按text或base64 blob取承载
     * @param node
     * @return
     */
    public static McpResourceContent fromJson(JsonNode node) {
        String uri = node.path("uri").asText();
        String mimeType = node.path("mimeType").asText(null);
        if (node.has("text") && !node.path("text").isMissingNode()) {
            return new McpResourceContent(uri, mimeType, node.path("text").asText(""), null);
        }
        String blob = node.path("blob").asText(null);
        byte[] bytes = blob != null ? Base64.getDecoder().decode(blob) : null;
        return new McpResourceContent(uri, mimeType, null, bytes);
    }
}