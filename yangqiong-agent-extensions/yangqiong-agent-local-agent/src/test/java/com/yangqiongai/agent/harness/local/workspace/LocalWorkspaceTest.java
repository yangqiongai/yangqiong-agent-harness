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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * 本地工作区测试
 * @author yangqiong
 */
class LocalWorkspaceTest {

    @TempDir
    Path tempDir;

    @Test
    void initCreatesAllSkeletonFilesAndDirs() {
        LocalWorkspace workspace = new LocalWorkspace(tempDir.resolve("workspace")).init();

        assertThat(workspace.root()).exists();
        assertThat(workspace.agentsFile()).exists().isRegularFile();
        assertThat(workspace.memoryFile()).exists().isRegularFile();
        assertThat(workspace.sessionsDir()).exists().isDirectory();
        assertThat(workspace.memoryDir()).exists().isDirectory();
        assertThat(workspace.skillsDir()).exists().isDirectory();
        assertThat(workspace.knowledgeDir()).exists().isDirectory();
    }

    @Test
    void skeletonIsCollectedUnderHiddenStateDir() {
        LocalWorkspace workspace = new LocalWorkspace(tempDir.resolve("workspace")).init();

        assertThat(workspace.sessionsDir()).startsWith(workspace.stateDir());
        assertThat(workspace.memoryDir()).startsWith(workspace.stateDir());
        assertThat(workspace.skillsDir()).startsWith(workspace.stateDir());
        assertThat(workspace.knowledgeDir()).startsWith(workspace.stateDir());
        assertThat(workspace.agentsFile()).startsWith(workspace.stateDir());
        assertThat(workspace.memoryFile()).startsWith(workspace.stateDir());
    }

    @Test
    void initWritesPlaceholderContent() throws IOException {
        LocalWorkspace workspace = new LocalWorkspace(tempDir.resolve("workspace")).init();

        assertThat(Files.readString(workspace.agentsFile(), StandardCharsets.UTF_8))
                .isEqualTo("# Agent Instructions\n");
        assertThat(Files.readString(workspace.memoryFile(), StandardCharsets.UTF_8))
                .isEqualTo("# Memory\n");
    }

    @Test
    void initDoesNotOverwriteExistingFiles() throws IOException {
        LocalWorkspace workspace = new LocalWorkspace(tempDir.resolve("workspace"));
        Files.createDirectories(workspace.stateDir());
        Files.writeString(workspace.agentsFile(), "自定义指令内容", StandardCharsets.UTF_8);
        Files.writeString(workspace.memoryFile(), "自定义记忆内容", StandardCharsets.UTF_8);

        workspace.init();

        assertThat(Files.readString(workspace.stateDir().resolve("AGENTS.md"), StandardCharsets.UTF_8))
                .isEqualTo("自定义指令内容");
        assertThat(Files.readString(workspace.stateDir().resolve("MEMORY.md"), StandardCharsets.UTF_8))
                .isEqualTo("自定义记忆内容");
    }

    @Test
    void initIsIdempotentForRepeatedCalls() {
        LocalWorkspace workspace = new LocalWorkspace(tempDir.resolve("workspace")).init();

        workspace.init();

        assertThat(workspace.agentsFile()).exists();
        assertThat(workspace.sessionsDir()).exists();
    }

    @Test
    void resolveReturnsPathInsideRoot() {
        LocalWorkspace workspace = new LocalWorkspace(tempDir.resolve("workspace")).init();

        assertThat(workspace.resolve("login.html"))
                .isEqualTo(workspace.root().resolve("login.html"));
        assertThat(workspace.resolve("css/style.css"))
                .isEqualTo(workspace.root().resolve("css/style.css"));
    }

    @Test
    void resolveRejectsPathEscapingRoot() {
        LocalWorkspace workspace = new LocalWorkspace(tempDir.resolve("workspace")).init();

        assertThatThrownBy(() -> workspace.resolve("../outside.txt"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> workspace.resolve("memory/../../escape.txt"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void resolveRejectsBlankPath() {
        LocalWorkspace workspace = new LocalWorkspace(tempDir.resolve("workspace")).init();

        assertThatThrownBy(() -> workspace.resolve(" "))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
