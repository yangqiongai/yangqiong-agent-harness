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
package com.yangqiongai.agent.harness.tool;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yangqiongai.agent.harness.core.message.AgentTextBlock;
import com.yangqiongai.agent.harness.core.message.AgentToolResultBlock;
import com.yangqiongai.agent.harness.core.tool.AgentTool;
import com.yangqiongai.agent.harness.core.tool.AgentToolCallParam;
import reactor.core.publisher.Mono;

/**
 * 启用工具元工具
 * <p>
 * Level 2 渐进加载：LLM 通过调用此工具按名称启用延迟池中的工具，
 * 工具结果直接返回完整schema文本（当轮即可构造参数），下一轮schema自动生效。
 * </p>
 * @author yangqiong
 */
public class LoadToolTool implements AgentTool {

    /**
     * 工具名称
     */
    private static final String TOOL_NAME = "load_tool";

    /**
     * 工具描述
     */
    private static final String TOOL_DESCRIPTION = "按需启用工具。"
            + "当需要使用可用工具目录中的某个工具时，先调用此工具获取其完整参数定义并启用，之后即可直接调用该工具。";

    /**
     * JSON序列化器（schema文本输出用）
     */
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    /**
     * 工具箱
     */
    private final HarnessToolkit toolkit;

    /**
     * 渐进加载状态（转正写入，与EngineContext共享）
     */
    private final ToolLoadingState loadingState;

    /**
     * 全参构造
     * @param toolkit 工具箱
     * @param loadingState 渐进加载状态
     */
    public LoadToolTool(HarnessToolkit toolkit, ToolLoadingState loadingState) {
        this.toolkit = toolkit;
        this.loadingState = loadingState;
    }

    /**
     * 获取工具名称
     * @return
     */
    @Override
    public String getName() {
        return TOOL_NAME;
    }

    /**
     * 获取工具描述
     * @return
     */
    @Override
    public String getDescription() {
        return TOOL_DESCRIPTION;
    }

    /**
     * 获取工具参数定义
     * @return
     */
    @Override
    public Map<String, Object> getParameters() {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        Map<String, Object> properties = new LinkedHashMap<>();
        Map<String, Object> toolNameProp = new LinkedHashMap<>();
        toolNameProp.put("type", "string");
        toolNameProp.put("description", "要启用的工具名称，须来自可用工具目录。");
        properties.put("tool_name", toolNameProp);
        schema.put("properties", properties);
        List<String> required = List.of("tool_name");
        schema.put("required", required);
        return schema;
    }

    /**
     * 是否为只读工具（仅改变渐进加载状态，无外部副作用）
     * @return
     */
    @Override
    public boolean isReadOnly() {
        return true;
    }

    /**
     * 异步启用工具并返回其完整schema文本
     * @param param 工具调用参数，需包含 tool_name
     * @return schema文本块，参数缺失、工具未找到或工具常驻时返回错误结果
     */
    @Override
    public Mono<AgentToolResultBlock> callAsync(AgentToolCallParam param) {
        if (param == null || param.getInput() == null) {
            return Mono.just(AgentToolResultBlock.error("参数为空"));
        }
        Object nameObj = param.getInput().get("tool_name");
        if (nameObj == null || nameObj.toString().isBlank()) {
            return Mono.just(AgentToolResultBlock.error("tool_name 参数缺失"));
        }
        String toolName = nameObj.toString();
        if (toolkit == null) {
            return Mono.just(AgentToolResultBlock.error("工具箱未初始化"));
        }
        AgentTool tool = toolkit.find(toolName);
        if (tool == null) {
            return Mono.just(AgentToolResultBlock.error("工具未找到: " + toolName));
        }
        if (loadingState != null && loadingState.isAlwaysOn(toolName)) {
            return Mono.just(AgentToolResultBlock.error("该工具已常驻可用，无需启用"));
        }
        if (loadingState != null) {
            // 幂等转正：重复启用直接返回schema文本
            loadingState.activate(toolName);
        }
        String schemaJson = toSchemaJson(tool);
        String text = "工具 " + toolName + " 已启用，本轮起可直接调用。完整参数定义：\n" + schemaJson;
        AgentTextBlock textBlock = AgentTextBlock.builder().text(text).build();
        return Mono.just(AgentToolResultBlock.of(List.of(textBlock)));
    }

    /**
     * 序列化工具schema为JSON文本
     * @param tool
     * @return
     */
    private String toSchemaJson(AgentTool tool) {
        try {
            return OBJECT_MAPPER.writeValueAsString(ToolSchemaBuilder.build(tool));
        } catch (Exception e) {
            // 序列化失败时降级为参数定义原文，保证load_tool仍可用
            return String.valueOf(tool.getParameters());
        }
    }
}
