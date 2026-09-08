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
package com.yangqiongai.agent.harness.local.workspace;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 本地工作区
 * <p>
 * 工作区根目录仅承载用户与Agent产生的业务内容；智能体脚手架
 * （AGENTS.md、MEMORY.md、memory、skills、knowledge、sessions）统一收纳到
 * 智能体根目录（工作区上一级）下的 .harness 隐藏目录，与业务内容隔离且不可被
 * 沙箱文件工具触及。已存在的文件与目录保持原样绝不覆盖，resolve 统一做边界
 * 校验防止路径越出根目录。
 * </p>
 * @author yangqiong
 */
public class LocalWorkspace {

    /**
     * 智能体脚手架收纳目录名
     */
    private static final String HARNESS_DIR = ".harness";

    /**
     * 智能体指令文件名
     */
    private static final String AGENTS_FILE = "AGENTS.md";

    /**
     * 记忆文件名
     */
    private static final String MEMORY_FILE = "MEMORY.md";

    /**
     * 会话目录名
     */
    private static final String SESSIONS_DIR = "sessions";

    /**
     * 记忆目录名
     */
    private static final String MEMORY_DIR = "memory";

    /**
     * 技能目录名
     */
    private static final String SKILLS_DIR = "skills";

    /**
     * 知识目录名
     */
    private static final String KNOWLEDGE_DIR = "knowledge";

    /**
     * 工作区根目录（绝对路径并已规范化）
     */
    private final Path root;

    /**
     * 构造
     * @param root 工作区根目录，如 {rootDir}/workspace
     */
    public LocalWorkspace(Path root) {
        if (root == null) {
            throw new IllegalArgumentException("工作区根目录不能为空");
        }
        this.root = root.toAbsolutePath().normalize();
    }

    /**
     * 初始化工作区骨架，已存在的文件与目录保持原样不覆盖
     * @return
     */
    public LocalWorkspace init() {
        try {
            Files.createDirectories(root);
            Files.createDirectories(stateDir());
            writeFileIfAbsent(agentsFile(), "# Agent Instructions\n");
            writeFileIfAbsent(memoryFile(), "# Memory\n");
            Files.createDirectories(sessionsDir());
            Files.createDirectories(memoryDir());
            Files.createDirectories(skillsDir());
            Files.createDirectories(knowledgeDir());
            return this;
        } catch (IOException e) {
            throw new UncheckedIOException("工作区初始化失败: " + root, e);
        }
    }

    /**
     * 仅在文件不存在时写入初始内容
     * @param file
     * @param content
     * @return
     */
    private void writeFileIfAbsent(Path file, String content) throws IOException {
        if (!Files.exists(file)) {
            Files.writeString(file, content, StandardCharsets.UTF_8);
        }
    }

    /**
     * 获取工作区根目录
     * @return
     */
    public Path root() {
        return root;
    }

    /**
     * 获取智能体脚手架收纳目录（隐藏目录 .harness，位于智能体根目录下、与工作区同级）
     * @return
     */
    public Path stateDir() {
        Path base = root.getParent() != null ? root.getParent() : root;
        return harnessStateDir(base);
    }

    /**
     * 按智能体根目录计算脚手架收纳目录
     * @param rootDir
     * @return
     */
    public static Path harnessStateDir(Path rootDir) {
        return rootDir.resolve(HARNESS_DIR);
    }

    /**
     * 获取会话目录
     * @return
     */
    public Path sessionsDir() {
        return stateDir().resolve(SESSIONS_DIR);
    }

    /**
     * 获取记忆目录
     * @return
     */
    public Path memoryDir() {
        return stateDir().resolve(MEMORY_DIR);
    }

    /**
     * 获取技能目录
     * @return
     */
    public Path skillsDir() {
        return stateDir().resolve(SKILLS_DIR);
    }

    /**
     * 获取知识目录
     * @return
     */
    public Path knowledgeDir() {
        return stateDir().resolve(KNOWLEDGE_DIR);
    }

    /**
     * 获取智能体指令文件路径
     * @return
     */
    public Path agentsFile() {
        return stateDir().resolve(AGENTS_FILE);
    }

    /**
     * 读取智能体指令内容，文件不存在返回空串
     * @return
     */
    public String readAgentsInstructions() {
        return AgentsFile.read(stateDir());
    }

    /**
     * 写回智能体指令内容（供宿主程序化更新）
     * @param content
     */
    public void updateAgentsInstructions(String content) {
        AgentsFile.write(stateDir(), content);
    }

    /**
     * 获取跨会话记忆简报文件路径
     * @return
     */
    public Path memoryFile() {
        return stateDir().resolve(MEMORY_FILE);
    }

    /**
     * 读取跨会话记忆简报内容，文件不存在返回空串
     * @return
     */
    public String readMemory() {
        return MemoryFile.read(stateDir());
    }

    /**
     * 写回跨会话记忆简报内容（供宿主程序化更新）
     * @param content
     */
    public void updateMemory(String content) {
        MemoryFile.write(stateDir(), content);
    }

    /**
     * 解析工作区内的相对路径，规范化后越出根目录时抛出IllegalArgumentException
     * @param relative
     * @return
     */
    public Path resolve(String relative) {
        if (relative == null || relative.isBlank()) {
            throw new IllegalArgumentException("相对路径不能为空");
        }
        Path resolved = root.resolve(relative).normalize();
        if (!resolved.startsWith(root)) {
            throw new IllegalArgumentException("路径越出工作区边界: " + relative);
        }
        return resolved;
    }
}
