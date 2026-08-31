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
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import com.yangqiong.agent.harness.core.message.AgentTextBlock;
import com.yangqiong.agent.harness.core.message.AgentToolResultBlock;
import com.yangqiong.agent.harness.core.tool.AgentTool;
import com.yangqiong.agent.harness.core.tool.AgentToolCallParam;
import com.yangqiong.agent.harness.core.tool.ToolRiskLevel;

/**
 * 本地文件glob搜索工具
 * <p>
 * 在沙箱根内的起始目录下递归匹配glob模式的文件与子目录，结果限定在沙箱根内
 * 并输出为相对起始目录的路径，超上限时截断。
 * </p>
 * @author yangqiong
 */
public class LocalFileGlobTool implements AgentTool {

    /**
     * 工具名称
     */
    private final String name = "file_glob";

    /**
     * 工具描述
     */
    private final String description = "在沙箱工作区内按glob模式递归搜索文件与目录，返回相对起始目录的匹配路径。"
            + "起始目录省略时从沙箱根开始，结果上限200条，路径不能越出沙箱。";

    /**
     * 结果上限条数
     */
    private static final int MAX_MATCHES = 200;

    /**
     * 沙箱边界
     */
    private final LocalSandbox sandbox;

    /**
     * 构造
     * @param sandbox
     */
    public LocalFileGlobTool(LocalSandbox sandbox) {
        this.sandbox = sandbox;
    }

    /**
     * 搜索匹配路径
     * @param param
     * @return
     */
    @Override
    public Mono<AgentToolResultBlock> callAsync(AgentToolCallParam param) {
        Map<String, Object> input = param != null ? param.getInput() : null;
        if (input == null || input.isEmpty()) {
            return Mono.just(AgentToolResultBlock.error("工具参数为空"));
        }
        Object patternObj = input.get("pattern");
        if (patternObj == null || patternObj.toString().isBlank()) {
            return Mono.just(AgentToolResultBlock.error("pattern 参数缺失"));
        }
        String pattern = patternObj.toString();
        String baseDir = input.get("baseDir") != null ? input.get("baseDir").toString() : ".";
        Path startDir;
        try {
            startDir = sandbox.resolve(baseDir);
        } catch (SecurityException e) {
            return Mono.just(AgentToolResultBlock.error(e.getMessage()));
        }
        return Mono.fromCallable(() -> glob(startDir, pattern));
    }

    /**
     * 同步递归搜索并组装结果
     * @param startDir
     * @param pattern
     * @return
     */
    private AgentToolResultBlock glob(Path startDir, String pattern) {
        if (!Files.isDirectory(startDir)) {
            return AgentToolResultBlock.error("起始目录不存在: " + startDir);
        }
        PathMatcher matcher;
        try {
            matcher = FileSystems.getDefault().getPathMatcher("glob:" + pattern);
        } catch (IllegalArgumentException e) {
            return AgentToolResultBlock.error("非法的glob模式: " + pattern);
        }
        List<String> matches = new ArrayList<>();
        boolean truncated = false;
        Path sandboxRoot = sandbox.root();
        try (Stream<Path> walk = Files.walk(startDir)) {
            List<Path> matched = walk.filter(path -> {
                        Path relative = startDir.relativize(path);
                        return matcher.matches(relative);
                    })
                    .sorted(Comparator.comparing(Path::toString))
                    .limit(MAX_MATCHES + 1)
                    .toList();
            if (matched.size() > MAX_MATCHES) {
                truncated = true;
            }
            for (Path path : matched.subList(0, Math.min(matched.size(), MAX_MATCHES))) {
                // 限定结果必须落在沙箱根内
                if (path.normalize().startsWith(sandboxRoot)) {
                    matches.add(startDir.relativize(path).toString());
                }
            }
        } catch (IOException e) {
            return AgentToolResultBlock.error("glob搜索失败: " + e.getMessage());
        }
        StringBuilder text = new StringBuilder();
        if (matches.isEmpty()) {
            text.append("未找到匹配项: ").append(pattern);
        }
        for (String match : matches) {
            text.append(match).append('\n');
        }
        if (truncated) {
            text.append("（结果超过上限，已截断至 ").append(MAX_MATCHES).append(" 条）\n");
        }
        return AgentToolResultBlock.of(List.of(AgentTextBlock.builder()
                .text(text.toString().stripTrailing()).build()));
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
        Map<String, Object> patternProp = new LinkedHashMap<>();
        patternProp.put("type", "string");
        patternProp.put("description", "glob匹配模式，如 **/*.txt、*.java");

        Map<String, Object> baseDirProp = new LinkedHashMap<>();
        baseDirProp.put("type", "string");
        baseDirProp.put("description", "起始搜索目录（相对或绝对路径，必须位于沙箱工作区内），省略时从沙箱根开始");

        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("pattern", patternProp);
        properties.put("baseDir", baseDirProp);

        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("properties", properties);
        schema.put("required", List.of("pattern"));
        return schema;
    }

    /**
     * 只读搜索无副作用
     * @return
     */
    @Override
    public boolean isReadOnly() {
        return true;
    }

    /**
     * 只读操作标为低风险
     * @return
     */
    @Override
    public ToolRiskLevel getRiskLevel() {
        return ToolRiskLevel.LOW;
    }
}