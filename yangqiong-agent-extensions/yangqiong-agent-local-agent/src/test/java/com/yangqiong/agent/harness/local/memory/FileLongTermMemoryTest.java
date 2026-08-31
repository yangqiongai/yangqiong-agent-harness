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
package com.yangqiong.agent.harness.local.memory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import com.yangqiong.agent.harness.durable.serialization.HarnessObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * 文件长期记忆读写测试
 * @author yangqiong
 */
class FileLongTermMemoryTest {

    @TempDir
    Path tempDir;

    @Test
    void shouldSearchScopedEntry() throws Exception {
        Path memoryDir = tempDir.resolve("memory");
        FileLongTermMemory memory = new FileLongTermMemory(memoryDir);
        memory.store("scope-a", "user-1", "s1", "客户偏好用中文沟通", Map.of("k", "v"));

        List<String> hits = memory.search("scope-a", "user-1", "中文", 5);
        assertThat(hits).containsExactly("客户偏好用中文沟通");
        Path file = Files.list(memoryDir).findFirst().orElseThrow();
        assertThat(file.getFileName().toString()).endsWith(".jsonl");
    }

    @Test
    void shouldFilterByScopeAndUser() {
        FileLongTermMemory memory = new FileLongTermMemory(tempDir.resolve("memory"));
        memory.store("scope-a", "user-1", "s1", "偏好中文", null);
        memory.store("scope-b", "user-1", "s1", "偏好英文", null);
        memory.store("scope-a", "user-2", "s1", "偏好法文", null);

        assertThat(memory.search("scope-a", "user-1", "偏好", 5)).containsExactly("偏好中文");
    }

    @Test
    void shouldReturnEmptyWhenNoHitOrEmptyDir() {
        FileLongTermMemory memory = new FileLongTermMemory(tempDir.resolve("empty"));
        assertThat(memory.search("s", "u", "关键字", 5)).isEmpty();

        memory.store("s", "u", "s1", "主题内容", null);
        assertThat(memory.search("s", "u", "不存在的词", 5)).isEmpty();
    }

    @Test
    void shouldDeleteScopedEntryAndCheckOwnership() throws Exception {
        FileLongTermMemory memory = new FileLongTermMemory(tempDir.resolve("memory"));
        memory.store("scope-a", "user-1", "s1", "待删除记忆", null);
        List<String> before = memory.search("scope-a", "user-1", "待删除", 5);
        assertThat(before).hasSize(1);

        // 从文件名中截取memoryId用于删除
        String memoryId = memoryIdOf(memory, "scope-a", "user-1");
        assertThatCode(() -> memory.delete("scope-b", memoryId))
                .isInstanceOf(SecurityException.class);
        memory.delete("scope-a", memoryId);
        assertThat(memory.search("scope-a", "user-1", "待删除", 5)).isEmpty();
    }

    @Test
    void shouldRejectIllegalMemoryId() {
        FileLongTermMemory memory = new FileLongTermMemory(tempDir.resolve("memory"));
        assertThatThrownBy(() -> memory.delete("scope-a", "../../evil"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldReloadCacheWhenExternalFileAppears() throws Exception {
        Path memoryDir = tempDir.resolve("memory");
        FileLongTermMemory memory = new FileLongTermMemory(memoryDir);
        memory.store("scope-a", "user-1", "s1", "第一条记忆", null);
        assertThat(memory.search("scope-a", "user-1", "第一条", 5)).containsExactly("第一条记忆");

        // 外部新增文件后再次检索应命中，验证缓存失效重扫
        Files.writeString(memoryDir.resolve("external-1.jsonl"),
                "{\"id\":\"external-1\",\"scope\":\"scope-a\",\"user\":\"user-1\",\"session\":\"s1\","
                        + "\"content\":\"外部新增记忆\",\"ts\":1}\n");
        assertThat(memory.search("scope-a", "user-1", "外部新增", 5)).containsExactly("外部新增记忆");
    }

    /**
     * 读取目录下记忆文件的id字段作为记忆ID（此处仅一个条目，取唯一文件）
     * @param memory
     * @param scope
     * @param user
     * @return
     */
    private String memoryIdOf(FileLongTermMemory memory, String scope, String user) throws Exception {
        Path memoryDir = tempDir.resolve("memory");
        Path file = Files.list(memoryDir).filter(Files::isRegularFile).findFirst().orElseThrow();
        String line = Files.readString(file).trim();
        @SuppressWarnings("unchecked")
        Map<String, Object> record = HarnessObjectMapper.get()
                .readValue(line, Map.class);
        return (String) record.get("id");
    }
}