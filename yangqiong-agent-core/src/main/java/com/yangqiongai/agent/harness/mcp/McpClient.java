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
import java.util.List;
import java.util.Map;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * MCP客户端契约，覆盖initialize握手、工具/资源/提示词发现调用与服务端通知订阅
 * @author yangqiong
 */
public interface McpClient {

    /**
     * 执行initialize握手并发送initialized通知
     * @return
     */
    Mono<Void> initialize();

    /**
     * 发现服务端工具列表（含游标分页）
     * @return
     */
    Mono<List<McpToolDescriptor>> listTools();

    /**
     * 调用服务端工具
     * @param name
     * @param arguments
     * @return
     */
    Mono<McpToolCallResult> callTool(String name, Map<String, Object> arguments);

    /**
     * 发现服务端资源列表（含游标分页）
     * @return
     */
    default Mono<List<McpResourceDescriptor>> listResources() {
        throw new UnsupportedOperationException("当前MCP客户端未实现资源发现");
    }

    /**
     * 发现服务端资源模板列表（含游标分页）
     * @return
     */
    default Mono<List<McpResourceTemplate>> listResourceTemplates() {
        throw new UnsupportedOperationException("当前MCP客户端未实现资源模板发现");
    }

    /**
     * 读取指定URI的资源内容
     * @param uri
     * @return
     */
    default Mono<McpResourceContent> readResource(String uri) {
        throw new UnsupportedOperationException("当前MCP客户端未实现资源读取");
    }

    /**
     * 发现服务端提示词列表（含游标分页）
     * @return
     */
    default Mono<List<McpPromptDescriptor>> listPrompts() {
        throw new UnsupportedOperationException("当前MCP客户端未实现提示词发现");
    }

    /**
     * 按名称与参数展开提示词
     * @param name
     * @param arguments
     * @return
     */
    default Mono<McpPromptResult> getPrompt(String name, Map<String, Object> arguments) {
        throw new UnsupportedOperationException("当前MCP客户端未实现提示词展开");
    }

    /**
     * 订阅服务端主动推送的JSON-RPC通知
     * @return
     */
    default Flux<JsonNode> notifications() {
        return Flux.empty();
    }

    /**
     * 获取握手协商后的服务端能力
     * @return
     */
    default McpServerCapabilities getServerCapabilities() {
        return null;
    }

    /**
     * 关闭客户端
     * @return
     */
    Mono<Void> close();
}
