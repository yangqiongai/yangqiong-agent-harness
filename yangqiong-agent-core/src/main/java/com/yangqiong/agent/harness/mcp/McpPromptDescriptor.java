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
import java.util.ArrayList;
import java.util.List;

/**
 * MCP提示词描述，对应prompts/list的单个提示词项
 * @author yangqiong
 */
public record McpPromptDescriptor(String name, String description, List<McpPromptArgument> arguments) {

    /**
     * 从JSON节点解析提示词描述
     * @param node
     * @return
     */
    public static McpPromptDescriptor fromJson(JsonNode node) {
        List<McpPromptArgument> argumentList = new ArrayList<>();
        for (JsonNode argument : node.path("arguments")) {
            argumentList.add(McpPromptArgument.fromJson(argument));
        }
        return new McpPromptDescriptor(
                node.path("name").asText(),
                node.path("description").asText(""),
                argumentList);
    }
}