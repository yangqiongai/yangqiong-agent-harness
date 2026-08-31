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
package com.yangqiong.agent.harness.local.tool;

import reactor.core.publisher.Mono;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.yangqiong.agent.harness.core.message.AgentTextBlock;
import com.yangqiong.agent.harness.core.message.AgentToolResultBlock;
import com.yangqiong.agent.harness.core.tool.AgentTool;
import com.yangqiong.agent.harness.core.tool.AgentToolCallParam;
import com.yangqiong.agent.harness.core.tool.ToolRiskLevel;

/**
 * 本地文件写入工具
 * <p>
 * 在沙箱根内新增或覆盖文件，支持追加写；父目录不存在时自动创建，
 * 路径越出沙箱根时拒绝执行。
 * </p>
 * @author yangqiong
 */
public class LocalFileWriteTool implements AgentTool {

    /**
     * 工具名称
     */
    private final String name = "file_write";

    /**
     * 工具描述
     */
    private final String description = "在沙箱工作区内写入或覆盖文件，支持追加模式。"
            + "父目录不存在时自动创建，只能写入沙箱根目录内的文件，路径不能越出沙箱。";

    /**
     * 沙箱边界
     */
    private final LocalSandbox sandbox;

    /**
     * 构造
     * @param sandbox
     */
    public LocalFileWriteTool(LocalSandbox sandbox) {
        this.sandbox = sandbox;
    }

    /**
     * 写入文件
     * @param param
     * @return
     */
    @Override
    public Mono<AgentToolResultBlock> callAsync(AgentToolCallParam param) {
        Map<String, Object> input = param != null ? param.getInput() : null;
        if (input == null || input.isEmpty()) {
            return Mono.just(AgentToolResultBlock.error("工具参数为空"));
        }
        Object pathObj = input.get("path");
        if (pathObj == null || pathObj.toString().isBlank()) {
            return Mono.just(AgentToolResultBlock.error("path 参数缺失"));
        }
        Object contentObj = input.get("content");
        if (contentObj == null) {
            return Mono.just(AgentToolResultBlock.error("content 参数缺失"));
        }
        boolean append = input.get("append") != null && Boolean.parseBoolean(input.get("append").toString());
        Path filePath;
        try {
            filePath = sandbox.resolve(pathObj.toString());
        } catch (SecurityException e) {
            return Mono.just(AgentToolResultBlock.error(e.getMessage()));
        }
        return Mono.fromCallable(() -> write(filePath, contentObj.toString(), append));
    }

    /**
     * 同步写入并组装结果
     * @param filePath
     * @param content
     * @param append
     * @return
     */
    private AgentToolResultBlock write(Path filePath, String content, boolean append) {
        try {
            Path parent = filePath.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            byte[] bytes = content.getBytes(StandardCharsets.UTF_8);
            if (append && Files.exists(filePath)) {
                Files.write(filePath, bytes, java.nio.file.StandardOpenOption.APPEND);
            } else {
                Files.write(filePath, bytes);
            }
            String mode = append && Files.size(filePath) > bytes.length ? "追加" : "写入";
            return AgentToolResultBlock.of(List.of(AgentTextBlock.builder()
                    .text(mode + "文件成功: " + filePath + "（" + content.length() + " 字符）").build()));
        } catch (IOException e) {
            return AgentToolResultBlock.error("写入文件失败: " + e.getMessage());
        }
    }

    /**
     * 获取工具名称
     * @return
     */
    @Override
    public String getName() {
        return name;
    }

    /**
     * 获取工具描述
     * @return
     */
    @Override
    public String getDescription() {
        return description;
    }

    /**
     * 获取工具参数定义
     * @return
     */
    @Override
    public Map<String, Object> getParameters() {
        Map<String, Object> pathProp = new LinkedHashMap<>();
        pathProp.put("type", "string");
        pathProp.put("description", "要写入的文件路径（相对或绝对路径，必须位于沙箱工作区内）");

        Map<String, Object> contentProp = new LinkedHashMap<>();
        contentProp.put("type", "string");
        contentProp.put("description", "要写入的文件内容");

        Map<String, Object> appendProp = new LinkedHashMap<>();
        appendProp.put("type", "boolean");
        appendProp.put("description", "是否追加写入，默认false覆盖已有内容");

        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("path", pathProp);
        properties.put("content", contentProp);
        properties.put("append", appendProp);

        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("properties", properties);
        schema.put("required", List.of("path", "content"));
        return schema;
    }

    /**
     * 文件写入具备副作用
     * @return
     */
    @Override
    public boolean isReadOnly() {
        return false;
    }

    /**
     * 文件写入可在沙箱内被改写，标为中风险
     * @return
     */
    @Override
    public ToolRiskLevel getRiskLevel() {
        return ToolRiskLevel.MEDIUM;
    }
}