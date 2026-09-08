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
import java.util.Map;

import com.yangqiongai.agent.harness.core.message.AgentToolResultBlock;
import com.yangqiongai.agent.harness.core.tool.AgentToolCallParam;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * 本地文件删除工具测试
 * @author yangqiong
 */
class LocalFileDeleteToolTest {

    /**
     * 临时沙箱根目录
     */
    @TempDir
    Path tempDir;

    /**
     * 删除沙箱内的单个文件成功
     */
    @Test
    void 删除文件成功() throws Exception {
        LocalSandbox sandbox = new LocalSandbox(tempDir);
        Path file = tempDir.resolve("a.txt");
        Files.writeString(file, "hello");
        AgentToolResultBlock result = call(sandbox, file.toString(), false);
        assertThat(result.getTextContent()).contains("删除文件成功");
        assertThat(file).doesNotExist();
    }

    /**
     * 删除空目录成功
     */
    @Test
    void 删除空目录成功() throws Exception {
        LocalSandbox sandbox = new LocalSandbox(tempDir);
        Path dir = tempDir.resolve("empty");
        Files.createDirectories(dir);
        AgentToolResultBlock result = call(sandbox, dir.toString(), false);
        assertThat(result.getTextContent()).contains("删除空目录成功");
        assertThat(dir).doesNotExist();
    }

    /**
     * 非空目录未开递归时拒绝删除并提示
     */
    @Test
    void 非空目录未开递归_拒绝删除() throws Exception {
        LocalSandbox sandbox = new LocalSandbox(tempDir);
        Path dir = tempDir.resolve("dir");
        Files.createDirectories(dir);
        Files.writeString(dir.resolve("f.txt"), "x");
        AgentToolResultBlock result = call(sandbox, dir.toString(), false);
        assertThat(result.isError()).isTrue();
        assertThat(result.getTextContent()).contains("recursive=true");
        assertThat(dir).exists();
    }

    /**
     * 非空目录开启递归后删除成功
     */
    @Test
    void 非空目录开启递归_删除成功() throws Exception {
        LocalSandbox sandbox = new LocalSandbox(tempDir);
        Path dir = tempDir.resolve("dir");
        Files.createDirectories(dir.resolve("sub"));
        Files.writeString(dir.resolve("a.txt"), "x");
        Files.writeString(dir.resolve("sub").resolve("b.txt"), "y");
        AgentToolResultBlock result = call(sandbox, dir.toString(), true);
        assertThat(result.getTextContent()).contains("递归删除目录成功");
        assertThat(dir).doesNotExist();
    }

    /**
     * 禁止删除沙箱根目录
     */
    @Test
    void 禁止删除沙箱根目录() {
        LocalSandbox sandbox = new LocalSandbox(tempDir);
        AgentToolResultBlock result = call(sandbox, tempDir.toString(), true);
        assertThat(result.isError()).isTrue();
        assertThat(result.getTextContent()).contains("禁止删除沙箱根目录");
    }

    /**
     * 路径越出沙箱时拒绝删除
     */
    @Test
    void 路径越出沙箱_拒绝删除() throws Exception {
        // 在沙箱根（tempDir）之外的目录明确造文件，与当前工作目录解耦
        Path outside = Files.createTempFile(tempDir.getParent(), "escape-delete", ".txt");
        LocalSandbox sandbox = new LocalSandbox(tempDir);
        try {
            AgentToolResultBlock result = call(sandbox, outside.toString(), false);
            assertThat(result.isError()).isTrue();
            assertThat(result.getTextContent()).contains("沙箱");
        } finally {
            Files.deleteIfExists(outside);
        }
    }

    /**
     * 目标不存在时返回错误
     */
    @Test
    void 目标不存在_返回错误() {
        LocalSandbox sandbox = new LocalSandbox(tempDir);
        AgentToolResultBlock result = call(sandbox, "not-exist.txt", false);
        assertThat(result.isError()).isTrue();
        assertThat(result.getTextContent()).contains("目标不存在");
    }

    /**
     * 执行一次删除调用
     * @param sandbox
     * @param path
     * @param recursive
     * @return
     */
    private AgentToolResultBlock call(LocalSandbox sandbox, String path, boolean recursive) {
        return new LocalFileDeleteTool(sandbox).callAsync(
                new AgentToolCallParam(Map.of("path", path, "recursive", recursive))).block();
    }
}
