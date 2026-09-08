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
import java.nio.file.DirectoryNotEmptyException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import com.yangqiongai.agent.harness.core.message.AgentTextBlock;
import com.yangqiongai.agent.harness.core.message.AgentToolResultBlock;
import com.yangqiongai.agent.harness.core.tool.AgentTool;
import com.yangqiongai.agent.harness.core.tool.AgentToolCallParam;
import com.yangqiongai.agent.harness.core.tool.ToolRiskLevel;

/**
 * 本地文件删除工具
 * <p>
 * 在沙箱根内删除文件或目录，支持递归删除非空目录；目录非空时需显式开启
 * recursive，禁止删除沙箱根目录本身，路径越出沙箱根时拒绝执行。
 * </p>
 * @author yangqiong
 */
public class LocalFileDeleteTool implements AgentTool {

    /**
     * 工具名称
     */
    private final String name = "file_delete";

    /**
     * 工具描述
     */
    private final String description = "在沙箱工作区内删除文件或目录。"
            + "目录默认仅允许删除空目录，删除非空目录需设置 recursive=true 递归删除；"
            + "禁止删除沙箱根目录本身，路径不能越出沙箱。";

    /**
     * 沙箱边界
     */
    private final LocalSandbox sandbox;

    /**
     * 构造
     * @param sandbox
     */
    public LocalFileDeleteTool(LocalSandbox sandbox) {
        this.sandbox = sandbox;
    }

    /**
     * 删除文件或目录
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
        boolean recursive = input.get("recursive") != null
                && Boolean.parseBoolean(input.get("recursive").toString());
        Path target;
        try {
            target = sandbox.resolve(pathObj.toString());
        } catch (SecurityException e) {
            return Mono.just(AgentToolResultBlock.error(e.getMessage()));
        }
        return Mono.fromCallable(() -> delete(target, recursive));
    }

    /**
     * 同步删除并组装结果
     * @param target
     * @param recursive
     * @return
     */
    private AgentToolResultBlock delete(Path target, boolean recursive) {
        try {
            if (target.equals(sandbox.root())) {
                return AgentToolResultBlock.error("禁止删除沙箱根目录");
            }
            if (!Files.exists(target)) {
                return AgentToolResultBlock.error("目标不存在: " + target);
            }
            if (Files.isDirectory(target)) {
                return deleteDirectory(target, recursive);
            }
            Files.delete(target);
            return success("删除文件成功: " + target);
        } catch (IOException e) {
            return AgentToolResultBlock.error("删除失败: " + e.getMessage());
        }
    }

    /**
     * 删除目录：空目录直接删除，非空目录需递归删除
     * @param target
     * @param recursive
     * @return
     * @throws IOException
     */
    private AgentToolResultBlock deleteDirectory(Path target, boolean recursive) throws IOException {
        if (!recursive) {
            try {
                Files.delete(target);
                return success("删除空目录成功: " + target);
            } catch (DirectoryNotEmptyException e) {
                return AgentToolResultBlock.error("目录非空，删除需设置 recursive=true: " + target);
            }
        }
        try (Stream<Path> walk = Files.walk(target)) {
            Iterator<Path> iterator = walk.sorted(Comparator.reverseOrder()).iterator();
            while (iterator.hasNext()) {
                Files.deleteIfExists(iterator.next());
            }
        }
        return success("递归删除目录成功: " + target);
    }

    /**
     * 组装成功结果
     * @param text
     * @return
     */
    private AgentToolResultBlock success(String text) {
        return AgentToolResultBlock.of(List.of(AgentTextBlock.builder().text(text).build()));
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
        pathProp.put("description", "要删除的文件或目录路径（相对或绝对路径，必须位于沙箱工作区内）");

        Map<String, Object> recursiveProp = new LinkedHashMap<>();
        recursiveProp.put("type", "boolean");
        recursiveProp.put("description", "目录非空时是否递归删除内容，默认false仅允许删除空目录或文件");

        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("path", pathProp);
        properties.put("recursive", recursiveProp);

        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("properties", properties);
        schema.put("required", List.of("path"));
        return schema;
    }

    /**
     * 文件删除具备副作用
     * @return
     */
    @Override
    public boolean isReadOnly() {
        return false;
    }

    /**
     * 文件删除为破坏性操作，标为高风险
     * @return
     */
    @Override
    public ToolRiskLevel getRiskLevel() {
        return ToolRiskLevel.HIGH;
    }
}
