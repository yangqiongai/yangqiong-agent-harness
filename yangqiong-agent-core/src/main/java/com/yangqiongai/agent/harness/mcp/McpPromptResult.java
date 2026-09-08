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
import java.util.ArrayList;
import java.util.List;

/**
 * MCP提示词执行结果，对应prompts/get的提示词展开消息列表
 * @author yangqiong
 */
public record McpPromptResult(String description, List<McpPromptMessage> messages) {

    /**
     * 从JSON节点解析提示词执行结果
     * @param node
     * @return
     */
    public static McpPromptResult fromJson(JsonNode node) {
        List<McpPromptMessage> messageList = new ArrayList<>();
        for (JsonNode message : node.path("messages")) {
            messageList.add(McpPromptMessage.fromJson(message));
        }
        return new McpPromptResult(node.path("description").asText(""), messageList);
    }
}