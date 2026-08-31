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
package com.yangqiong.agent.harness.local.runtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.net.ServerSocket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import com.yangqiong.agent.harness.config.AgentApprovalMode;
import com.yangqiong.agent.harness.local.workspace.LocalWorkspace;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * 本地智能体一键装配测试
 * <p>
 * 测试环境无真实本地推理服务，仅验证装配链：探测失败提示、显式模型装配的产物完整性、
 * 无检查点时恢复入口的空提示行为，不发起真实模型对话。
 * </p>
 * @author yangqiong
 */
class LocalAgentHarnessTest {

    /**
     * 临时装配根目录
     */
    @TempDir
    Path tempDir;

    @Test
    void shouldThrowWithInstallHintWhenNoLocalModelServiceDiscovered() throws IOException {
        int freePort = findFreePort();
        LocalHarnessConfig config = LocalHarnessConfig.builder()
                .rootDir(tempDir)
                .addExtraEndpointBaseUrl("http://localhost:" + freePort + "/v1")
                .build();

        assertThatThrownBy(() -> LocalAgentHarness.create(config))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Ollama")
                .hasMessageContaining("LM Studio");
    }

    @Test
    void shouldAssembleSessionWithExplicitModelWithoutCallingIt() {
        LocalHarnessConfig config = LocalHarnessConfig.builder()
                .rootDir(tempDir)
                .endpointBaseUrl("http://localhost:11434")
                .modelName("llama3")
                .build();

        LocalAgentSession session = LocalAgentHarness.create(config);
        try {
            assertThat(session).isNotNull();
            assertThat(session.runtime()).isNotNull();
            assertThat(session.workspace()).isNotNull();
            assertThat(session.stores()).isNotNull();

            assertThat(tempDir.resolve(".harness").resolve("harness.db")).exists();

            assertThat(session.workspace().agentsFile()).exists();
            assertThat(session.workspace().memoryFile()).exists();
            assertThat(session.workspace().sessionsDir()).isDirectory();
            assertThat(session.workspace().memoryDir()).isDirectory();
            assertThat(session.workspace().skillsDir()).isDirectory();
            assertThat(session.workspace().knowledgeDir()).isDirectory();

            session.transcript().append("session-explicit",
                    Map.of("role", "user", "text", "你好，本地装配"));
            List<Map<String, Object>> records = session.transcript().readAll("session-explicit");
            assertThat(records).hasSize(1);
            assertThat(records.get(0)).containsEntry("role", "user")
                    .containsEntry("text", "你好，本地装配");
        } finally {
            assertThatCode(session::close).doesNotThrowAnyException();
        }
    }

    @Test
    void shouldAssembleSessionWithAutoApprovalMode() {
        LocalHarnessConfig config = LocalHarnessConfig.builder()
                .rootDir(tempDir)
                .endpointBaseUrl("http://localhost:11434")
                .modelName("llama3")
                .approvalMode(AgentApprovalMode.AUTO)
                .aiApprovalGuidance("工作区内文件操作放行")
                .build();

        assertThat(config.approvalMode()).isEqualTo(AgentApprovalMode.AUTO);
        assertThat(config.aiApprovalGuidance()).isEqualTo("工作区内文件操作放行");

        LocalAgentSession session = LocalAgentHarness.create(config);
        try {
            assertThat(session).isNotNull();
            assertThat(session.runtime()).isNotNull();
        } finally {
            assertThatCode(session::close).doesNotThrowAnyException();
        }
    }

    @Test
    void shouldAssembleSessionWithFullAccessApprovalMode() {
        LocalHarnessConfig config = LocalHarnessConfig.builder()
                .rootDir(tempDir)
                .endpointBaseUrl("http://localhost:11434")
                .modelName("llama3")
                .approvalMode(AgentApprovalMode.FULL_ACCESS)
                .build();

        assertThat(config.approvalMode()).isEqualTo(AgentApprovalMode.FULL_ACCESS);

        LocalAgentSession session = LocalAgentHarness.create(config);
        try {
            assertThat(session).isNotNull();
            assertThat(session.runtime()).isNotNull();
        } finally {
            assertThatCode(session::close).doesNotThrowAnyException();
        }
    }

    @Test
    void shouldDefaultToCustomApprovalMode() {
        LocalHarnessConfig config = LocalHarnessConfig.builder()
                .rootDir(tempDir)
                .endpointBaseUrl("http://localhost:11434")
                .modelName("llama3")
                .build();

        assertThat(config.approvalMode()).isEqualTo(AgentApprovalMode.CUSTOM);
        assertThat(config.aiApprovalGuidance()).isNull();
    }

    @Test
    void shouldAssembleWithWorkspaceSyncEnabledByDefault() throws IOException {
        // 预置 AGENTS.md 与一个技能，验证装配时 AGENTS读入 + 技能发现 + 会话监听注册不报错
        Path state = LocalWorkspace.harnessStateDir(tempDir);
        Files.createDirectories(state.resolve("skills/data-query/resources"));
        Files.writeString(state.resolve("AGENTS.md"), "# 本地助手指令");
        Files.writeString(state.resolve("skills/data-query/SKILL.md"),
                "description: 数据查询助手\n# 数据查询\n检索本地知识库。");

        LocalHarnessConfig config = LocalHarnessConfig.builder()
                .rootDir(tempDir)
                .endpointBaseUrl("http://localhost:11434")
                .modelName("llama3")
                .build();
        assertThat(config.workspaceSyncEnabled()).as("双向同步默认开启").isTrue();

        LocalAgentSession session = LocalAgentHarness.create(config);
        try {
            assertThat(session).isNotNull();
            assertThat(session.transcript()).isNotNull();
        } finally {
            assertThatCode(session::close).doesNotThrowAnyException();
        }
    }

    @Test
    void shouldDisableWorkspaceSyncAndHonorExplicitPrompt() {
        LocalHarnessConfig config = LocalHarnessConfig.builder()
                .rootDir(tempDir)
                .endpointBaseUrl("http://localhost:11434")
                .modelName("llama3")
                .systemPrompt("仅做本地产出")
                .workspaceSyncEnabled(false)
                .build();

        assertThat(config.workspaceSyncEnabled()).isFalse();

        LocalAgentSession session = LocalAgentHarness.create(config);
        try {
            assertThat(session).isNotNull();
        } finally {
            assertThatCode(session::close).doesNotThrowAnyException();
        }
    }

    @Test
    void shouldWireMemoryKnowledgeAndMemoryFileOnAssembly() throws IOException {
        // 预置 MEMORY.md 与 knowledge/ 文档，验证装配时记忆简报读入、memory/ 落点、knowledge检索器注册不报错
        Path state = LocalWorkspace.harnessStateDir(tempDir);
        Files.createDirectories(state.resolve("knowledge"));
        Files.writeString(state.resolve("MEMORY.md"), "# 记忆简报\n用户偏好在命令行环境执行任务。");
        Files.writeString(state.resolve("knowledge/guide.md"), "本地服务配置指南：如何设置Ollama端点");

        LocalHarnessConfig config = LocalHarnessConfig.builder()
                .rootDir(tempDir)
                .endpointBaseUrl("http://localhost:11434")
                .modelName("llama3")
                .build();
        assertThat(config.memoryFileEnabled()).as("记忆简报默认开启").isTrue();
        assertThat(config.knowledgeRagEnabled()).as("知识库RAG默认开启").isTrue();

        LocalAgentSession session = LocalAgentHarness.create(config);
        try {
            assertThat(session).isNotNull();
            assertThat(session.workspace().readMemory()).contains("命令行环境");
        } finally {
            assertThatCode(session::close).doesNotThrowAnyException();
        }
    }

    @Test
    void shouldAllowDisablingMemoryFileAndKnowledgeRag() {
        LocalHarnessConfig config = LocalHarnessConfig.builder()
                .rootDir(tempDir)
                .endpointBaseUrl("http://localhost:11434")
                .modelName("llama3")
                .memoryFileEnabled(false)
                .knowledgeRagEnabled(false)
                .build();

        assertThat(config.memoryFileEnabled()).isFalse();
        assertThat(config.knowledgeRagEnabled()).isFalse();
    }

    @Test
    void shouldAssembleAutomatedResidentComponentsWhenEnabled() throws IOException {
        Path state = LocalWorkspace.harnessStateDir(tempDir);
        Files.createDirectories(state.resolve("knowledge"));
        Files.writeString(state.resolve("knowledge/guide.md"), "配置指南");

        LocalHarnessConfig config = LocalHarnessConfig.builder()
                .rootDir(tempDir)
                .endpointBaseUrl("http://localhost:11434")
                .modelName("llama3")
                .memoryConsolidateEnabled(true)
                .skillDistillEnabled(true)
                .knowledgeMaintainEnabled(true)
                .healthWatchdogEnabled(true)
                .build();
        assertThat(config.memoryConsolidateEnabled()).isTrue();
        assertThat(config.skillDistillEnabled()).isTrue();
        assertThat(config.knowledgeMaintainEnabled()).isTrue();
        assertThat(config.healthWatchdogEnabled()).isTrue();

        LocalAgentSession session = LocalAgentHarness.create(config);
        try {
            assertThat(session.memoryConsolidator()).isNotNull();
            assertThat(session.skillDistiller()).isNotNull();
            assertThat(session.knowledgeMaintainer()).isNotNull();
            assertThat(session.healthWatchdog()).isNotNull();
        } finally {
            assertThatCode(session::close).doesNotThrowAnyException();
        }
    }

    @Test
    void shouldReturnEmptyResumeWhenNoCheckpointExists() {
        LocalHarnessConfig config = LocalHarnessConfig.builder()
                .rootDir(tempDir)
                .endpointBaseUrl("http://localhost:11434")
                .modelName("llama3")
                .build();

        LocalAgentResume resume = LocalAgentHarness.resumeLatest(config);
        try {
            assertThat(resume).isNotNull();
            assertThat(resume.session()).isNotNull();
            assertThat(resume.hasCheckpoint()).isFalse();
            assertThat(resume.checkpoint()).isEmpty();
            assertThat(resume.resume().collectList().block()).isEmpty();
        } finally {
            assertThatCode(resume::close).doesNotThrowAnyException();
        }
    }

    /**
     * 获取一个当前空闲的本机端口用于探测失败的端点地址
     * @return
     */
    private int findFreePort() throws IOException {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }
}
