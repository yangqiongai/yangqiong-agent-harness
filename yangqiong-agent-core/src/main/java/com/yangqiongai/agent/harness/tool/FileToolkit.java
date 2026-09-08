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

import com.yangqiongai.agent.harness.core.message.AgentTextBlock;
import com.yangqiongai.agent.harness.core.message.AgentToolResultBlock;
import com.yangqiongai.agent.harness.core.tool.AgentTool;
import com.yangqiongai.agent.harness.core.tool.AgentToolCallParam;
import com.yangqiongai.agent.harness.rag.DocumentParser;
import com.yangqiongai.agent.harness.rag.TextDocumentParser;
import reactor.core.publisher.Mono;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Agent文件工具箱
 * <p>
 * 提供file_read（读取沙箱内文件内容，支持注入解析器解析文档）与
 * file_list（列出沙箱内目录）两个工具。
 * 所有文件访问受沙箱根目录约束，路径规范化后校验，防止路径逃逸。
 * </p>
 * @author yangqiong
 */
public class FileToolkit {

    /**
     * 沙箱根目录
     */
    private final Path sandboxRoot;

    /**
     * 沙箱根目录的真实路径（解析符号链接后），用于真实路径二次校验
     */
    private final Path sandboxRootReal;

    /**
     * 文档解析器（可注入平台Tika实现，默认纯文本）
     */
    private final DocumentParser parser;

    /**
     * 默认最大读取字节数，防止超大文件耗尽上下文
     */
    private static final int MAX_READ_BYTES = 512 * 1024;

    /**
     * 默认最大列目录条数
     */
    private static final int MAX_LIST_ENTRIES = 200;

    /**
     * 默认沙箱根目录
     */
    private static final String DEFAULT_SANDBOX_ROOT = ".yangqiong/workspace";

    public FileToolkit(String sandboxRoot, DocumentParser parser) {
        String root = (sandboxRoot != null && !sandboxRoot.isBlank())
                ? sandboxRoot : DEFAULT_SANDBOX_ROOT;
        this.sandboxRoot = Paths.get(root).toAbsolutePath().normalize();
        this.parser = parser != null ? parser : new TextDocumentParser();
        ensureSandboxExists();
        try {
            this.sandboxRootReal = this.sandboxRoot.toRealPath();
        } catch (IOException e) {
            throw new IllegalStateException("无法解析沙箱根目录真实路径: " + e.getMessage(), e);
        }
    }

    public FileToolkit(String sandboxRoot) {
        this(sandboxRoot, null);
    }

    /**
     * 创建file_read工具
     * @return
     */
    public AgentTool readTool() {
        return new FileReadTool();
    }

    /**
     * 创建file_list工具
     * @return
     */
    public AgentTool listTool() {
        return new FileListTool();
    }

    /**
     * 规范化并校验路径是否在沙箱内，逃逸时抛出异常
     * <p>
     * 词法normalize不解析符号链接：沙箱内指向沙箱外的软链接/junction可通过
     * startsWith检查，因此解析真实路径后需二次校验；目标不存在时上溯最近的
     * 现存祖先目录解析后拼回剩余路径。
     * </p>
     * @param raw
     * @return
     */
    private Path resolveSafePath(String raw) {
        Path rawPath = Paths.get(raw);
        Path candidate = rawPath.isAbsolute()
                ? rawPath.toAbsolutePath().normalize()
                : sandboxRoot.resolve(rawPath).normalize();
        if (!candidate.startsWith(sandboxRoot)) {
            throw new SecurityException("路径逃逸沙箱限制: " + raw);
        }
        Path realPath = toRealPathWithinSandbox(candidate);
        if (realPath == null || !realPath.startsWith(sandboxRootReal)) {
            throw new SecurityException("路径逃逸沙箱限制: " + raw);
        }
        return candidate;
    }

    /**
     * 解析路径的真实路径，目标不存在时上溯最近的现存祖先解析后拼回剩余路径
     * @param candidate
     * @return
     */
    private Path toRealPathWithinSandbox(Path candidate) {
        Path current = candidate;
        Path suffix = null;
        while (current != null) {
            try {
                Path real = current.toRealPath();
                return suffix == null ? real : real.resolve(suffix);
            } catch (IOException e) {
                if (suffix == null) {
                    suffix = current.getFileName();
                } else if (current.getFileName() != null) {
                    suffix = current.getFileName().resolve(suffix);
                }
                current = current.getParent();
            }
        }
        return null;
    }

    /**
     * 校验沙箱根目录是否存在，不存在时创建
     */
    private void ensureSandboxExists() {
        try {
            Files.createDirectories(sandboxRoot);
        } catch (IOException e) {
            throw new IllegalStateException("无法创建沙箱目录: " + e.getMessage(), e);
        }
    }

    /**
     * 读取文件内容
     */
    private final class FileReadTool implements AgentTool {

        /**
         * 读取文件内容
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
            Path filePath;
            try {
                filePath = resolveSafePath(pathObj.toString());
            } catch (SecurityException e) {
                return Mono.just(AgentToolResultBlock.error(e.getMessage()));
            }
            if (!Files.isRegularFile(filePath)) {
                return Mono.just(AgentToolResultBlock.error("文件不存在或不是普通文件: " + pathObj));
            }
            try {
                if (Files.size(filePath) > MAX_READ_BYTES) {
                    return Mono.just(AgentToolResultBlock.error("文件超过读取大小限制: " + pathObj));
                }
                byte[] bytes = Files.readAllBytes(filePath);
                String text = parser.parse(bytes, filePath.getFileName().toString());
                return Mono.just(AgentToolResultBlock.of(List.of(
                        AgentTextBlock.builder().text(text).build())));
            } catch (Exception e) {
                return Mono.just(AgentToolResultBlock.error("读取文件失败: " + e.getMessage()));
            }
        }

        /**
         * 工具名称
         * @return
         */
        @Override
        public String getName() {
            return "file_read";
        }

        /**
         * 工具描述
         * @return
         */
        @Override
        public String getDescription() {
            return "读取沙箱工作区内文件的内容，支持纯文本与注入解析器的文档格式。"
                    + "只能访问沙箱根目录内的文件，路径不能越出沙箱。";
        }

        /**
         * 工具参数定义
         * @return
         */
        @Override
        public Map<String, Object> getParameters() {
            Map<String, Object> schema = new LinkedHashMap<>();
            schema.put("type", "object");
            Map<String, Object> properties = new LinkedHashMap<>();
            Map<String, Object> pathProp = new LinkedHashMap<>();
            pathProp.put("type", "string");
            pathProp.put("description", "要读取的文件路径（相对或绝对路径，必须位于沙箱工作区内）");
            properties.put("path", pathProp);
            schema.put("properties", properties);
            schema.put("required", List.of("path"));
            return schema;
        }

        /**
         * 是否只读
         * @return
         */
        @Override
        public boolean isReadOnly() {
            return true;
        }
    }

    /**
     * 列出目录内容
     */
    private final class FileListTool implements AgentTool {

        /**
         * 列出目录内容
         * @param param
         * @return
         */
        @Override
        public Mono<AgentToolResultBlock> callAsync(AgentToolCallParam param) {
            Map<String, Object> input = param != null ? param.getInput() : null;
            String dir = (input != null && input.get("path") != null)
                    ? input.get("path").toString() : ".";
            Path dirPath;
            try {
                dirPath = resolveSafePath(dir);
            } catch (SecurityException e) {
                return Mono.just(AgentToolResultBlock.error(e.getMessage()));
            }
            if (!Files.isDirectory(dirPath)) {
                return Mono.just(AgentToolResultBlock.error("目录不存在: " + dir));
            }
            try {
                List<Path> entries = new ArrayList<>();
                try (var stream = Files.list(dirPath)) {
                    stream.limit(MAX_LIST_ENTRIES).forEach(entries::add);
                }
                entries.sort(Comparator.comparing(Path::getFileName));
                StringBuilder sb = new StringBuilder();
                for (Path entry : entries) {
                    boolean isDir = Files.isDirectory(entry);
                    sb.append(isDir ? "[D] " : "[F] ").append(entry.getFileName()).append('\n');
                }
                return Mono.just(AgentToolResultBlock.of(List.of(
                        AgentTextBlock.builder().text(sb.toString()).build())));
            } catch (IOException e) {
                return Mono.just(AgentToolResultBlock.error("列目录失败: " + e.getMessage()));
            }
        }

        /**
         * 工具名称
         * @return
         */
        @Override
        public String getName() {
            return "file_list";
        }

        /**
         * 工具描述
         * @return
         */
        @Override
        public String getDescription() {
            return "列出沙箱工作区内指定目录下的文件与子目录（仅限沙箱根目录内）。"
                    + "目录参数省略时列出沙箱根目录。";
        }

        /**
         * 工具参数定义
         * @return
         */
        @Override
        public Map<String, Object> getParameters() {
            Map<String, Object> schema = new LinkedHashMap<>();
            schema.put("type", "object");
            Map<String, Object> properties = new LinkedHashMap<>();
            Map<String, Object> pathProp = new LinkedHashMap<>();
            pathProp.put("type", "string");
            pathProp.put("description", "要列出的目录路径（相对或绝对路径，必须位于沙箱工作区内），省略时列出沙箱根目录");
            properties.put("path", pathProp);
            schema.put("properties", properties);
            return schema;
        }

        /**
         * 是否只读
         * @return
         */
        @Override
        public boolean isReadOnly() {
            return true;
        }
    }
}