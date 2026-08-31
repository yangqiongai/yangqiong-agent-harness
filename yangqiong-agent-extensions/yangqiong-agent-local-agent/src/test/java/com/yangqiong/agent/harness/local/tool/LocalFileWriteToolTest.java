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

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;

import com.yangqiong.agent.harness.core.message.AgentToolResultBlock;
import com.yangqiong.agent.harness.core.tool.AgentToolCallParam;
import com.yangqiong.agent.harness.core.tool.ToolRiskLevel;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * 本地文件写入工具测试
 * @author yangqiong
 */
class LocalFileWriteToolTest {

    @TempDir
    Path tempDir;

    /**
     * 写入新建文件成功
     */
    @Test
    void writesNewFileSuccessfully() {
        LocalFileWriteTool tool = new LocalFileWriteTool(new LocalSandbox(tempDir));

        AgentToolResultBlock result = tool.callAsync(new AgentToolCallParam(
                        Map.of("path", "hello.txt", "content", "你好")))
                .block(Duration.ofSeconds(5));

        assertThat(result).isNotNull();
        assertThat(result.isError()).isFalse();
        assertThat(tempDir.resolve("hello.txt")).exists();
        assertThat(readText(tempDir.resolve("hello.txt"))).isEqualTo("你好");
    }

    @Test
    void overwritesExistingFile() throws Exception {
        Path target = tempDir.resolve("a.txt");
        Files.writeString(target, "old");
        LocalFileWriteTool tool = new LocalFileWriteTool(new LocalSandbox(tempDir));

        AgentToolResultBlock result = tool.callAsync(new AgentToolCallParam(
                        Map.of("path", "a.txt", "content", "new")))
                .block(Duration.ofSeconds(5));

        assertThat(result).isNotNull();
        assertThat(result.isError()).isFalse();
        assertThat(readText(target)).isEqualTo("new");
    }

    @Test
    void appendsContentWhenAppendTrue() throws Exception {
        Path target = tempDir.resolve("a.txt");
        Files.writeString(target, "base");
        LocalFileWriteTool tool = new LocalFileWriteTool(new LocalSandbox(tempDir));

        AgentToolResultBlock result = tool.callAsync(new AgentToolCallParam(
                        Map.of("path", "a.txt", "content", "+more", "append", "true")))
                .block(Duration.ofSeconds(5));

        assertThat(result).isNotNull();
        assertThat(result.isError()).isFalse();
        assertThat(readText(target)).isEqualTo("base+more");
    }

    @Test
    void autoCreatesParentDirectories() {
        LocalFileWriteTool tool = new LocalFileWriteTool(new LocalSandbox(tempDir));

        AgentToolResultBlock result = tool.callAsync(new AgentToolCallParam(
                        Map.of("path", "sub/deep/f.txt", "content", "x")))
                .block(Duration.ofSeconds(5));

        assertThat(result).isNotNull();
        assertThat(result.isError()).isFalse();
        assertThat(tempDir.resolve("sub/deep/f.txt")).exists();
    }

    @Test
    void rejectsPathEscapingSandbox() {
        LocalFileWriteTool tool = new LocalFileWriteTool(new LocalSandbox(tempDir));

        AgentToolResultBlock result = tool.callAsync(new AgentToolCallParam(
                        Map.of("path", "../escape.txt", "content", "x")))
                .block(Duration.ofSeconds(5));

        assertThat(result).isNotNull();
        assertThat(result.isError()).isTrue();
        assertThat(result.getTextContent()).contains("沙箱");
    }

    @Test
    void returnsErrorWhenPathMissing() {
        LocalFileWriteTool tool = new LocalFileWriteTool(new LocalSandbox(tempDir));

        AgentToolResultBlock result = tool.callAsync(new AgentToolCallParam(Map.of("content", "x")))
                .block(Duration.ofSeconds(5));

        assertThat(result).isNotNull();
        assertThat(result.isError()).isTrue();
        assertThat(result.getTextContent()).contains("path");
    }

    @Test
    void toolMetadataMatchesDefinition() {
        LocalFileWriteTool tool = new LocalFileWriteTool(new LocalSandbox(tempDir));

        assertThat(tool.getName()).isEqualTo("file_write");
        assertThat(tool.getDescription()).isNotBlank();
        assertThat(tool.isReadOnly()).isFalse();
        assertThat(tool.getRiskLevel()).isEqualTo(ToolRiskLevel.MEDIUM);
        assertThat(tool.getParameters().get("required"))
                .asList().containsExactly("path", "content");
    }

    private String readText(Path path) {
        try {
            return Files.readString(path, StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}