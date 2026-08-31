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

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;

import com.yangqiong.agent.harness.core.message.AgentToolResultBlock;
import com.yangqiong.agent.harness.core.tool.AgentToolCallParam;
import com.yangqiong.agent.harness.core.tool.ToolRiskLevel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * 本地文件编辑工具测试
 * @author yangqiong
 */
class LocalFileEditToolTest {

    @TempDir
    Path tempDir;

    LocalFileEditTool tool;

    @BeforeEach
    void setUp() {
        tool = new LocalFileEditTool(new LocalSandbox(tempDir));
    }

    @Test
    void replacesSingleMatch() throws Exception {
        Path target = tempDir.resolve("a.txt");
        Files.writeString(target, "foo bar foo");

        AgentToolResultBlock result = tool.callAsync(new AgentToolCallParam(
                        Map.of("path", "a.txt", "oldText", "bar", "newText", "BAZ")))
                .block(Duration.ofSeconds(5));

        assertThat(result).isNotNull();
        assertThat(result.isError()).isFalse();
        assertThat(result.getTextContent()).contains("已替换 1 处");
        assertThat(Files.readString(target)).isEqualTo("foo BAZ foo");
    }

    @Test
    void removesTextWhenNewTextEmpty() throws Exception {
        Path target = tempDir.resolve("a.txt");
        Files.writeString(target, "hello world");

        AgentToolResultBlock result = tool.callAsync(new AgentToolCallParam(
                        Map.of("path", "a.txt", "oldText", " world")))
                .block(Duration.ofSeconds(5));

        assertThat(result).isNotNull();
        assertThat(result.isError()).isFalse();
        assertThat(Files.readString(target)).isEqualTo("hello");
    }

    @Test
    void rejectsWhenOldTextMissing() throws Exception {
        Files.writeString(tempDir.resolve("a.txt"), "content");
        AgentToolResultBlock result = tool.callAsync(new AgentToolCallParam(
                        Map.of("path", "a.txt", "oldText", "不存在")))
                .block(Duration.ofSeconds(5));

        assertThat(result).isNotNull();
        assertThat(result.isError()).isTrue();
        assertThat(result.getTextContent()).contains("未找到匹配");
    }

    @Test
    void rejectsAmbiguousMatchWithoutReplaceAll() throws Exception {
        Files.writeString(tempDir.resolve("a.txt"), "xx xx");
        AgentToolResultBlock result = tool.callAsync(new AgentToolCallParam(
                        Map.of("path", "a.txt", "oldText", "xx", "newText", "y")))
                .block(Duration.ofSeconds(5));

        assertThat(result).isNotNull();
        assertThat(result.isError()).isTrue();
        assertThat(result.getTextContent()).contains("replaceAll");
    }

    @Test
    void replacesAllWhenReplaceAllTrue() throws Exception {
        Path target = tempDir.resolve("a.txt");
        Files.writeString(target, "xx xx");

        AgentToolResultBlock result = tool.callAsync(new AgentToolCallParam(
                        Map.of("path", "a.txt", "oldText", "xx", "newText", "y", "replaceAll", "true")))
                .block(Duration.ofSeconds(5));

        assertThat(result).isNotNull();
        assertThat(result.isError()).isFalse();
        assertThat(Files.readString(target)).isEqualTo("y y");
    }

    @Test
    void rejectsPathEscapingSandbox() {
        AgentToolResultBlock result = tool.callAsync(new AgentToolCallParam(
                        Map.of("path", "../a.txt", "oldText", "x")))
                .block(Duration.ofSeconds(5));

        assertThat(result).isNotNull();
        assertThat(result.isError()).isTrue();
        assertThat(result.getTextContent()).contains("沙箱");
    }

    @Test
    void toolMetadataMatchesDefinition() {
        assertThat(tool.getName()).isEqualTo("file_edit");
        assertThat(tool.getDescription()).isNotBlank();
        assertThat(tool.isReadOnly()).isFalse();
        assertThat(tool.getRiskLevel()).isEqualTo(ToolRiskLevel.MEDIUM);
        assertThat(tool.getParameters().get("required"))
                .asList().containsExactly("path", "oldText");
    }
}