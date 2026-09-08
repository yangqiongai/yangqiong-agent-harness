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

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;

import com.yangqiongai.agent.harness.core.message.AgentToolResultBlock;
import com.yangqiongai.agent.harness.core.tool.AgentToolCallParam;
import com.yangqiongai.agent.harness.core.tool.ToolRiskLevel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * 本地文件glob搜索工具测试
 * @author yangqiong
 */
class LocalFileGlobToolTest {

    @TempDir
    Path tempDir;

    LocalFileGlobTool tool;

    @BeforeEach
    void setUp() throws Exception {
        Files.writeString(tempDir.resolve("a.txt"), "a");
        Files.writeString(tempDir.resolve("b.log"), "b");
        Files.createDirectories(tempDir.resolve("sub"));
        Files.writeString(tempDir.resolve("sub/c.txt"), "c");
        tool = new LocalFileGlobTool(new LocalSandbox(tempDir));
    }

    @Test
    void matchesRecursivelyWithDoubleStar() {
        AgentToolResultBlock result = tool.callAsync(new AgentToolCallParam(
                        Map.of("pattern", "**/*.txt")))
                .block(Duration.ofSeconds(5));

        assertThat(result).isNotNull();
        assertThat(result.isError()).isFalse();
        assertThat(result.getTextContent()).contains("c.txt");
        assertThat(result.getTextContent()).doesNotContain("b.log");
    }

    @Test
    void matchesInCurrentDirWithSingleStar() {
        AgentToolResultBlock result = tool.callAsync(new AgentToolCallParam(
                        Map.of("pattern", "*.txt")))
                .block(Duration.ofSeconds(5));

        assertThat(result).isNotNull();
        assertThat(result.isError()).isFalse();
        assertThat(result.getTextContent()).contains("a.txt");
        assertThat(result.getTextContent()).doesNotContain("sub/c.txt");
    }

    @Test
    void matchesFromBaseDir() {
        AgentToolResultBlock result = tool.callAsync(new AgentToolCallParam(
                        Map.of("pattern", "c.txt", "baseDir", "sub")))
                .block(Duration.ofSeconds(5));

        assertThat(result).isNotNull();
        assertThat(result.isError()).isFalse();
        assertThat(result.getTextContent()).contains("c.txt");
    }

    @Test
    void matchesRootAndNestedWithCombinedGlob() {
        AgentToolResultBlock result = tool.callAsync(new AgentToolCallParam(
                        Map.of("pattern", "{*.txt,**/*.txt}")))
                .block(Duration.ofSeconds(5));

        assertThat(result).isNotNull();
        assertThat(result.isError()).isFalse();
        assertThat(result.getTextContent()).contains("a.txt");
        assertThat(result.getTextContent()).contains("c.txt");
    }

    @Test
    void returnsEmptyWhenNoMatch() {
        AgentToolResultBlock result = tool.callAsync(new AgentToolCallParam(
                        Map.of("pattern", "*.pdf")))
                .block(Duration.ofSeconds(5));

        assertThat(result).isNotNull();
        assertThat(result.isError()).isFalse();
        assertThat(result.getTextContent()).contains("未找到匹配");
    }

    @Test
    void rejectsBaseDirEscapingSandbox() {
        AgentToolResultBlock result = tool.callAsync(new AgentToolCallParam(
                        Map.of("pattern", "*", "baseDir", "../")))
                .block(Duration.ofSeconds(5));

        assertThat(result).isNotNull();
        assertThat(result.isError()).isTrue();
        assertThat(result.getTextContent()).contains("沙箱");
    }

    @Test
    void truncatesWhenExceedingLimit() throws Exception {
        for (int i = 0; i < 250; i++) {
            Files.writeString(tempDir.resolve("f" + i + ".txt"), "x");
        }
        AgentToolResultBlock result = tool.callAsync(new AgentToolCallParam(
                        Map.of("pattern", "*.txt")))
                .block(Duration.ofSeconds(5));

        assertThat(result).isNotNull();
        assertThat(result.isError()).isFalse();
        assertThat(result.getTextContent()).contains("上限");
    }

    @Test
    void toolMetadataMatchesDefinition() {
        assertThat(tool.getName()).isEqualTo("file_glob");
        assertThat(tool.getDescription()).isNotBlank();
        assertThat(tool.isReadOnly()).isTrue();
        assertThat(tool.getRiskLevel()).isEqualTo(ToolRiskLevel.LOW);
        assertThat(tool.getParameters().get("required"))
                .asList().containsExactly("pattern");
    }
}