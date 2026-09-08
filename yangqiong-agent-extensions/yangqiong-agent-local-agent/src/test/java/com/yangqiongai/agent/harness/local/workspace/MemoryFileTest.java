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

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * 跨会话记忆简报文件读写测试
 * @author yangqiong
 */
class MemoryFileTest {

    @TempDir
    Path tempDir;

    @Test
    void shouldReadEmptyWhenFileMissing() {
        Path root = tempDir.resolve("ws");
        assertThat(MemoryFile.read(root)).isEmpty();
    }

    @Test
    void shouldReadUtf8Content() throws Exception {
        Path root = tempDir.resolve("ws");
        Files.createDirectories(root);
        String content = "# 记忆简报\n用户偏好在命令行环境执行任务。";
        Files.writeString(root.resolve(MemoryFile.FILE_NAME), content, StandardCharsets.UTF_8);

        assertThat(MemoryFile.read(root)).isEqualTo(content);
    }

    @Test
    void shouldWriteBackAndCreateParent() {
        Path root = tempDir.resolve("nested/ws");
        MemoryFile.write(root, "更新后的记忆\n");

        assertThat(MemoryFile.read(root)).isEqualTo("更新后的记忆\n");
    }

    @Test
    void shouldExposeViaLocalWorkspace() {
        LocalWorkspace ws = new LocalWorkspace(tempDir.resolve("ws")).init();
        String content = "# 记忆";
        MemoryFile.write(ws.stateDir(), content);

        assertThat(ws.readMemory()).isEqualTo(content);
        ws.updateMemory("# 新记忆");
        assertThat(ws.readMemory()).isEqualTo("# 新记忆");
    }
}