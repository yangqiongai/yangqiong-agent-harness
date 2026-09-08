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

import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * MCP客户端能力声明，用于initialize握手中client capabilities
 * @author yangqiong
 */
public record McpClientCapabilities(boolean roots, boolean sampling) {

    /**
     * 声明无进阶能力
     * @return
     */
    public static McpClientCapabilities none() {
        return new McpClientCapabilities(false, false);
    }

    /**
     * 声明全部支持的进阶能力
     * @return
     */
    public static McpClientCapabilities all() {
        return new McpClientCapabilities(true, true);
    }

    /**
     * 按能力开关写入初始化参数的能力节点
     * @param capabilities
     */
    public void applyTo(ObjectNode capabilities) {
        if (roots) {
            capabilities.set("roots", capabilities.objectNode());
        }
        if (sampling) {
            capabilities.set("sampling", capabilities.objectNode());
        }
    }
}