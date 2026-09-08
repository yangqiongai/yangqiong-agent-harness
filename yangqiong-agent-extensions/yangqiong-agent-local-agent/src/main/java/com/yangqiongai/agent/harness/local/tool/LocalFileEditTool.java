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
package com.yangqiongai.agent.harness.local.tool;

import reactor.core.publisher.Mono;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.yangqiongai.agent.harness.core.message.AgentTextBlock;
import com.yangqiongai.agent.harness.core.message.AgentToolResultBlock;
import com.yangqiongai.agent.harness.core.tool.AgentTool;
import com.yangqiongai.agent.harness.core.tool.AgentToolCallParam;
import com.yangqiongai.agent.harness.core.tool.ToolRiskLevel;

/**
 * 本地文件编辑工具
 * <p>
 * 在沙箱根内按 oldText 定位并替换为 newText，返回替换次数与结果片段预览；
 * 单次模式下oldText命中多处时拒绝执行以避免歧义覆盖，可开启replaceAll全量替换。
 * </p>
 * @author yangqiong
 */
public class LocalFileEditTool implements AgentTool {

    /**
     * 工具名称
     */
    private final String name = "file_edit";

    /**
     * 工具描述
     */
    private final String description = "在沙箱工作区内的文件中按指定文本定位并替换为新文本，返回替换次数与结果预览。"
            + "oldText未命中或多处命中（未开启replaceAll）时报错，路径不能越出沙箱。";

    /**
     * 预览截取的字符数
     */
    private static final int PREVIEW_LENGTH = 200;

    /**
     * 沙箱边界
     */
    private final LocalSandbox sandbox;

    /**
     * 构造
     * @param sandbox
     */
    public LocalFileEditTool(LocalSandbox sandbox) {
        this.sandbox = sandbox;
    }

    /**
     * 编辑文件
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
        Object oldObj = input.get("oldText");
        if (oldObj == null || oldObj.toString().isEmpty()) {
            return Mono.just(AgentToolResultBlock.error("oldText 参数缺失"));
        }
        String oldText = oldObj.toString();
        String newText = input.get("newText") != null ? input.get("newText").toString() : "";
        boolean replaceAll = input.get("replaceAll") != null && Boolean.parseBoolean(input.get("replaceAll").toString());
        Path filePath;
        try {
            filePath = sandbox.resolve(pathObj.toString());
        } catch (SecurityException e) {
            return Mono.just(AgentToolResultBlock.error(e.getMessage()));
        }
        return Mono.fromCallable(() -> edit(filePath, oldText, newText, replaceAll));
    }

    /**
     * 同步替换并组装结果
     * @param filePath
     * @param oldText
     * @param newText
     * @param replaceAll
     * @return
     */
    private AgentToolResultBlock edit(Path filePath, String oldText, String newText, boolean replaceAll) {
        if (!Files.isRegularFile(filePath)) {
            return AgentToolResultBlock.error("文件不存在或不是普通文件: " + filePath);
        }
        try {
            String original = Files.readString(filePath, StandardCharsets.UTF_8);
            int matchCount = countMatches(original, oldText);
            if (matchCount == 0) {
                return AgentToolResultBlock.error("未找到匹配的 oldText: " + oldText);
            }
            if (matchCount > 1 && !replaceAll) {
                return AgentToolResultBlock.error("oldText 命中 " + matchCount + " 处，请缩小匹配范围或设置 replaceAll=true");
            }
            String replaced = replaceAll ? original.replace(oldText, newText)
                    : original.replaceFirst(java.util.regex.Pattern.quote(oldText), newText);
            Files.writeString(filePath, replaced, StandardCharsets.UTF_8);
            String preview = replaced.length() > PREVIEW_LENGTH
                    ? replaced.substring(0, PREVIEW_LENGTH) + "…" : replaced;
            return AgentToolResultBlock.of(List.of(AgentTextBlock.builder()
                    .text("已替换 " + matchCount + " 处: " + filePath + "\n结果预览:\n" + preview).build()));
        } catch (IOException e) {
            return AgentToolResultBlock.error("编辑文件失败: " + e.getMessage());
        }
    }

    /**
     * 统计子串在文本中出现次数
     * @param text
     * @param needle
     * @return
     */
    private int countMatches(String text, String needle) {
        int count = 0;
        int index = 0;
        while ((index = text.indexOf(needle, index)) != -1) {
            count++;
            index += needle.length();
        }
        return count;
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
        pathProp.put("description", "要编辑的文件路径（相对或绝对路径，必须位于沙箱工作区内）");

        Map<String, Object> oldTextProp = new LinkedHashMap<>();
        oldTextProp.put("type", "string");
        oldTextProp.put("description", "要被替换的原文片段");

        Map<String, Object> newTextProp = new LinkedHashMap<>();
        newTextProp.put("type", "string");
        newTextProp.put("description", "替换后的新内容，为空表示删除匹配片段");

        Map<String, Object> replaceAllProp = new LinkedHashMap<>();
        replaceAllProp.put("type", "boolean");
        replaceAllProp.put("description", "是否替换所有匹配，默认false仅替换首处且命中多处时报错");

        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("path", pathProp);
        properties.put("oldText", oldTextProp);
        properties.put("newText", newTextProp);
        properties.put("replaceAll", replaceAllProp);

        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("properties", properties);
        schema.put("required", List.of("path", "oldText"));
        return schema;
    }

    /**
     * 文件编辑具备副作用
     * @return
     */
    @Override
    public boolean isReadOnly() {
        return false;
    }

    /**
     * 文件编辑可在沙箱内被改写，标为中风险
     * @return
     */
    @Override
    public ToolRiskLevel getRiskLevel() {
        return ToolRiskLevel.MEDIUM;
    }
}