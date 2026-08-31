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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * 本地工具沙箱路径边界测试
 * @author yangqiong
 */
class LocalSandboxTest {

    /**
     * 临时沙箱根目录
     */
    @TempDir
    Path tempDir;

    /**
     * 沙箱内普通路径正常解析
     */
    @Test
    void shouldResolveNormalPathWithinSandbox() throws Exception {
        Path root = tempDir.resolve("sandbox");
        Files.createDirectories(root.resolve("sub"));
        LocalSandbox sandbox = new LocalSandbox(root);
        assertThat(sandbox.resolve("sub/a.txt")).isEqualTo(root.resolve("sub/a.txt"));
        assertThat(sandbox.resolve("a.txt")).isEqualTo(root.resolve("a.txt"));
    }

    /**
     * 相对路径穿越沙箱边界被拒绝
     */
    @Test
    void shouldRejectTraversalEscape() {
        LocalSandbox sandbox = new LocalSandbox(tempDir.resolve("sandbox"));
        assertThatThrownBy(() -> sandbox.resolve("../outside.txt"))
                .isInstanceOf(SecurityException.class);
        assertThatThrownBy(() -> sandbox.resolve("../../outside.txt"))
                .isInstanceOf(SecurityException.class);
    }

    /**
     * 软链指向沙箱外目录时被拒绝，无创建软链权限则跳过
     */
    @Test
    void shouldRejectSymlinkEscape() throws Exception {
        Path root = tempDir.resolve("sandbox");
        Path outside = tempDir.resolve("outside");
        Files.createDirectories(root);
        Files.createDirectories(outside);
        Path link = root.resolve("link-to-outside");
        try {
            Files.createSymbolicLink(link, outside);
        } catch (Exception e) {
            // 当前环境无符号链接创建权限时跳过该用例
            Assumptions.assumeTrue(false, "无符号链接创建权限，跳过软链逃逸用例");
            return;
        }
        LocalSandbox sandbox = new LocalSandbox(root);
        assertThatThrownBy(() -> sandbox.resolve("link-to-outside/secret.txt"))
                .isInstanceOf(SecurityException.class);
    }
}
