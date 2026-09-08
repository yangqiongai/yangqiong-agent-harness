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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * 本地工具沙箱
 * <p>
 * 为本地文件工具提供工作区根边界约束：路径规范化后校验是否越出沙箱根，
 * 防止路径逃逸；首次使用自动创建沙箱根目录。
 * </p>
 * @author yangqiong
 */
public final class LocalSandbox {

    /**
     * 沙箱根目录
     */
    private final Path sandboxRoot;

    /**
     * 以工作区根目录为沙箱边界
     * @param sandboxRoot
     */
    public LocalSandbox(Path sandboxRoot) {
        this.sandboxRoot = sandboxRoot.toAbsolutePath().normalize();
        ensureSandboxExists();
    }

    /**
     * 获取沙箱根目录
     * @return
     */
    Path root() {
        return sandboxRoot;
    }

    /**
     * 规范化并校验路径是否在沙箱内，逃逸时抛出异常
     * <p>
     * 先做词法规范化边界校验，再以真实路径解析校验，防止软链/junction
     * 将实际落点指向沙箱根之外的目录。
     * </p>
     * @param raw
     * @return
     */
    Path resolve(String raw) {
        Path rawPath = Paths.get(raw);
        Path candidate = rawPath.isAbsolute()
                ? rawPath.toAbsolutePath().normalize()
                : sandboxRoot.resolve(rawPath).normalize();
        if (!candidate.startsWith(sandboxRoot) || !isRealPathWithinSandbox(candidate)) {
            throw new SecurityException("路径经软链越出沙箱限制: " + raw);
        }
        return candidate;
    }

    /**
     * 校验候选路径经真实路径解析后仍位于沙箱内
     * <p>
     * 沙箱根取真实路径；候选路径沿不存在的片段向上取最近存在的祖先
     * 解析真实路径，命中越出根目录判定为非法，解析失败一律拒绝。
     * </p>
     * @param candidate
     * @return
     */
    private boolean isRealPathWithinSandbox(Path candidate) {
        Path realRoot = resolveRealPath(sandboxRoot);
        if (realRoot == null) {
            return false;
        }
        Path probe = candidate;
        while (probe != null && !Files.exists(probe)) {
            probe = probe.getParent();
        }
        if (probe == null) {
            probe = sandboxRoot;
        }
        Path real = resolveRealPath(probe);
        return real != null && real.startsWith(realRoot);
    }

    /**
     * 解析真实路径，失败返回null
     * @param path
     * @return
     */
    private Path resolveRealPath(Path path) {
        try {
            return path.toRealPath();
        } catch (IOException e) {
            return null;
        }
    }

    /**
     * 校验沙箱根目录是否存在，不存在时创建
     */
    private void ensureSandboxExists() {
        try {
            Files.createDirectories(sandboxRoot);
        } catch (IOException e) {
            throw new IllegalStateException("无法创建沙箱目录: " + e.getMessage(), e);
        }
    }
}