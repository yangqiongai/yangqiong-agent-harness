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
 * 跨会话记忆简报文件
 * <p>
 * 读写工作区根目录下的 MEMORY.md：装配时读入其内容作为记忆简报注入系统提示词（正向），
 * 亦可供宿主程序化回写更新（反向），与按目录组织的 L3 文件记忆语义互补、互不覆盖。
 * </p>
 * @author yangqiong
 */
public final class MemoryFile {

    /**
     * 记忆简报文件名
     */
    public static final String FILE_NAME = "MEMORY.md";

    private MemoryFile() {
    }

    /**
     * 读取记忆简报内容，文件不存在返回空串
     * @param root
     * @return
     */
    public static String read(Path root) {
        Path file = root.resolve(FILE_NAME);
        if (!Files.exists(file)) {
            return "";
        }
        try {
            return new String(Files.readAllBytes(file), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("读取 MEMORY.md 失败: " + file, e);
        }
    }

    /**
     * 写回记忆简报内容，目录不存在时自动创建
     * @param root
     * @param content
     */
    public static void write(Path root, String content) {
        Path file = root.resolve(FILE_NAME);
        try {
            Files.createDirectories(root);
            Files.writeString(file, content == null ? "" : content, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("写回 MEMORY.md 失败: " + file, e);
        }
    }
}