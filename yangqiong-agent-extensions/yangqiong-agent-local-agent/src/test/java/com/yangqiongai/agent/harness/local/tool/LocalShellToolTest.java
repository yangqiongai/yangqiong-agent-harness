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

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import com.yangqiongai.agent.harness.core.message.AgentToolResultBlock;
import com.yangqiongai.agent.harness.core.tool.AgentToolCallParam;
import com.yangqiongai.agent.harness.core.tool.ToolRiskLevel;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * 本地Shell工具测试
 * @author yangqiong
 */
class LocalShellToolTest {

    @TempDir
    Path tempDir;

    @Test
    void executeEchoCommandReturnsOutputWithExitCode() {
        LocalShellTool tool = new LocalShellTool(tempDir);

        AgentToolResultBlock result = tool.callAsync(new AgentToolCallParam(Map.of("command", "echo hello")))
                .block(Duration.ofSeconds(30));

        assertThat(result).isNotNull();
        assertThat(result.isError()).isFalse();
        assertThat(result.getTextContent()).contains("hello");
        assertThat(result.getTextContent()).contains("exit code: 0");
    }

    @Test
    void executeReturnsErrorWhenCommandMissing() {
        LocalShellTool tool = new LocalShellTool(tempDir);

        AgentToolResultBlock result = tool.callAsync(new AgentToolCallParam(Map.of()))
                .block(Duration.ofSeconds(5));

        assertThat(result).isNotNull();
        assertThat(result.isError()).isTrue();
        assertThat(result.getTextContent()).contains("command");
    }

    @Test
    void outputIsTruncatedAndMarkedWhenExceedingLimit() {
        LocalShellTool tool = new LocalShellTool(tempDir, Duration.ofSeconds(10), 10);

        AgentToolResultBlock result = tool.callAsync(
                        new AgentToolCallParam(Map.of("command", "echo abcdefghijklmnopqrstuvwxyz")))
                .block(Duration.ofSeconds(30));

        assertThat(result).isNotNull();
        assertThat(result.isError()).isFalse();
        assertThat(result.getTextContent()).contains("exit code: 0");
        assertThat(result.getTextContent()).contains("truncated: true");
        assertThat(result.getTextContent()).doesNotContain("klmnop");
    }

    @Test
    void timeoutTerminatesLongRunningCommand() {
        // 工作目录取当前进程目录，避免被终止进程短暂占用临时目录导致清理竞争
        LocalShellTool tool = new LocalShellTool(
                Path.of(System.getProperty("user.dir")), Duration.ofSeconds(2), 10000);
        String command = isWindows() ? "ping -n 30 127.0.0.1" : "sleep 30";

        long start = System.currentTimeMillis();
        AgentToolResultBlock result = tool.callAsync(new AgentToolCallParam(Map.of("command", command)))
                .block(Duration.ofSeconds(45));
        long elapsed = System.currentTimeMillis() - start;

        assertThat(result).isNotNull();
        assertThat(result.isError()).isTrue();
        assertThat(result.getTextContent()).contains("超时");
        assertThat(elapsed).isGreaterThanOrEqualTo(1500L);
    }

    @Test
    void toolMetadataMatchesDefinition() {
        LocalShellTool tool = new LocalShellTool(tempDir);

        assertThat(tool.getName()).isEqualTo("local_shell");
        assertThat(tool.getDescription()).isNotBlank();
        assertThat(tool.isReadOnly()).isFalse();
        assertThat(tool.getRiskLevel()).isEqualTo(ToolRiskLevel.HIGH);

        Map<String, Object> parameters = tool.getParameters();
        assertThat(parameters).containsEntry("type", "object");
        assertThat(parameters.get("required")).asList().containsExactly("command");
        Object properties = parameters.get("properties");
        assertThat(properties).isInstanceOf(Map.class);
        assertThat(((Map<?, ?>) properties).get("command")).isNotNull();
    }

    private boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
    }
}
