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

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import com.yangqiongai.agent.harness.core.message.AgentToolResultBlock;
import com.yangqiongai.agent.harness.core.tool.AgentTool;
import com.yangqiongai.agent.harness.core.tool.AgentToolCallParam;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.util.Map;

/**
 * Agent文件工具箱测试
 * @author yangqiong
 */
class FileToolkitTest {

    @TempDir
    Path tempDir;

    /**
     * 在沙箱内创建文本文件
     * @param sandbox
     * @param name
     * @param content
     * @return
     */
    private Path createFile(Path sandbox, String name, String content) throws IOException {
        Path file = sandbox.resolve(name);
        Files.createDirectories(file.getParent());
        Files.writeString(file, content, StandardCharsets.UTF_8);
        return file;
    }

    @Test
    void shouldReadTextFileInsideSandbox() throws IOException {
        Path sandbox = tempDir.resolve("sandbox");
        createFile(sandbox, "notes.md", "hello harness");
        FileToolkit toolkit = new FileToolkit(sandbox.toString());
        AgentTool readTool = toolkit.readTool();

        AgentToolResultBlock result = readTool.callAsync(
                new AgentToolCallParam(Map.of("path", "notes.md"))).block();

        assertThat(result).isNotNull();
        assertThat(result.isError()).isFalse();
        assertThat(result.getTextContent()).isEqualTo("hello harness");
    }

    @Test
    void shouldRejectPathTraversal() throws IOException {
        Path sandbox = tempDir.resolve("sandbox");
        Path outside = tempDir.resolve("secret.txt");
        Files.writeString(outside, "secret", StandardCharsets.UTF_8);
        FileToolkit toolkit = new FileToolkit(sandbox.toString());
        AgentTool readTool = toolkit.readTool();

        AgentToolResultBlock result = readTool.callAsync(
                new AgentToolCallParam(Map.of("path", "../secret.txt"))).block();

        assertThat(result).isNotNull();
        assertThat(result.isError()).isTrue();
        assertThat(result.getTextContent()).contains("路径逃逸");
    }

    @Test
    void shouldRejectPathOutsideSandbox() throws IOException {
        Path sandbox = tempDir.resolve("sandbox");
        Path outside = tempDir.resolve("other");
        Files.createDirectories(outside);
        Files.writeString(outside.resolve("a.txt"), "x", StandardCharsets.UTF_8);
        FileToolkit toolkit = new FileToolkit(sandbox.toString());
        AgentTool readTool = toolkit.readTool();

        AgentToolResultBlock result = readTool.callAsync(
                new AgentToolCallParam(Map.of("path", outside.resolve("a.txt").toString()))).block();

        assertThat(result).isNotNull();
        assertThat(result.isError()).isTrue();
        assertThat(result.getTextContent()).contains("路径逃逸");
    }

    @Test
    void shouldRejectMissingFile() throws IOException {
        Path sandbox = tempDir.resolve("sandbox");
        FileToolkit toolkit = new FileToolkit(sandbox.toString());
        AgentTool readTool = toolkit.readTool();

        AgentToolResultBlock result = readTool.callAsync(
                new AgentToolCallParam(Map.of("path", "nope.txt"))).block();

        assertThat(result).isNotNull();
        assertThat(result.isError()).isTrue();
        assertThat(result.getTextContent()).contains("文件不存在");
    }

    @Test
    void shouldListDirectoryEntries() throws IOException {
        Path sandbox = tempDir.resolve("sandbox");
        createFile(sandbox, "a.txt", "a");
        createFile(sandbox, "sub/b.txt", "b");
        FileToolkit toolkit = new FileToolkit(sandbox.toString());
        AgentTool listTool = toolkit.listTool();

        AgentToolResultBlock result = listTool.callAsync(
                new AgentToolCallParam(Map.of("path", "."))).block();

        assertThat(result).isNotNull();
        assertThat(result.isError()).isFalse();
        assertThat(result.getTextContent()).contains("a.txt").contains("sub");
    }

    @Test
    void shouldExposeReadOnlyTools() {
        Path sandbox = tempDir.resolve("sandbox");
        FileToolkit toolkit = new FileToolkit(sandbox.toString());
        assertThat(toolkit.readTool().isReadOnly()).isTrue();
        assertThat(toolkit.listTool().isReadOnly()).isTrue();
        assertThat(toolkit.readTool().getName()).isEqualTo("file_read");
        assertThat(toolkit.listTool().getName()).isEqualTo("file_list");
    }
}