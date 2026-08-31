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

import com.yangqiong.agent.harness.core.message.AgentTextBlock;
import com.yangqiong.agent.harness.core.message.AgentToolResultBlock;
import com.yangqiong.agent.harness.core.tool.AgentTool;
import com.yangqiong.agent.harness.core.tool.AgentToolCallParam;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.yangqiong.agent.harness.core.message.AgentContentBlock;
import reactor.core.publisher.Mono;

/**
 * MCP工具适配器，将服务端工具包装为引擎AgentTool
 * @author yangqiong
 */
public class McpToolAdapter implements AgentTool {

    /**
     * MCP工具类别标识
     */
    public static final String TOOL_CATEGORY = "mcp";

    /**
     * 对外暴露的工具名（可能带服务端前缀）
     */
    private final String exposedName;

    /**
     * MCP客户端
     */
    private final McpClient client;

    /**
     * 服务端工具描述
     */
    private final McpToolDescriptor descriptor;

    /**
     * 构造工具适配器
     * @param exposedName
     * @param client
     * @param descriptor
     */
    public McpToolAdapter(String exposedName, McpClient client, McpToolDescriptor descriptor) {
        this.exposedName = exposedName;
        this.client = client;
        this.descriptor = descriptor;
    }

    /**
     * 创建带服务端前缀的适配器
     * @param serverName
     * @param client
     * @param descriptor
     * @return
     */
    public static McpToolAdapter prefixed(String serverName, McpClient client, McpToolDescriptor descriptor) {
        return new McpToolAdapter(serverName + "__" + descriptor.getName(), client, descriptor);
    }

    /**
     * 获取对外暴露的工具名
     * @return
     */
    @Override
    public String getName() {
        return exposedName;
    }

    /**
     * 获取工具描述
     * @return
     */
    @Override
    public String getDescription() {
        return descriptor.getDescription();
    }

    /**
     * 获取工具参数定义，MCP inputSchema即JSON Schema直接透传
     * @return
     */
    @Override
    public Map<String, Object> getParameters() {
        return descriptor.getInputSchema();
    }

    /**
     * 异步调用MCP工具
     * @param param
     * @return
     */
    @Override
    public Mono<AgentToolResultBlock> callAsync(AgentToolCallParam param) {
        return client.callTool(descriptor.getName(), param.getInput())
                .map(this::toResultBlock)
                .onErrorResume(e -> Mono.just(AgentToolResultBlock.error(
                        "MCP工具[" + exposedName + "]调用失败：" + e.getMessage())));
    }

    /**
     * 返回MCP工具类别，供权限策略按类别区分
     * @return
     */
    @Override
    public String getToolCategory() {
        return TOOL_CATEGORY;
    }

    /**
     * 获取服务端原始工具描述
     * @return
     */
    public McpToolDescriptor getDescriptor() {
        return descriptor;
    }

    private AgentToolResultBlock toResultBlock(McpToolCallResult result) {
        if (result.isError()) {
            return AgentToolResultBlock.error(
                    "MCP工具[" + exposedName + "]执行失败：" + result.extractText());
        }
        List<AgentContentBlock> blocks = new ArrayList<>();
        for (Map<String, Object> item : result.getContent()) {
            blocks.add(AgentTextBlock.builder().text(renderContent(item)).build());
        }
        if (blocks.isEmpty()) {
            blocks.add(AgentTextBlock.builder().text("").build());
        }
        return AgentToolResultBlock.of(blocks);
    }

    private String renderContent(Map<String, Object> item) {
        Object type = item.get("type");
        Object text = item.get("text");
        if ("text".equals(type) && text != null) {
            return String.valueOf(text);
        }
        return McpJsonRpc.mapper().valueToTree(item).toString();
    }
}
