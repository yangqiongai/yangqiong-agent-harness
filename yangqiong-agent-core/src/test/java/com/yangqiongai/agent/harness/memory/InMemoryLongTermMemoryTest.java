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
package com.yangqiongai.agent.harness.memory;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

/**
 * 长期记忆内存默认实现测试
 * @author yangqiong
 */
class InMemoryLongTermMemoryTest {

    @Test
    void storeAndSearchByKeyword() {
        InMemoryLongTermMemory memory = new InMemoryLongTermMemory();
        memory.store("u1", "s1", "用户偏好使用Java开发后端服务", Map.of());
        memory.store("u1", "s1", "今天天气晴朗适合户外活动", Map.of());

        List<String> hits = memory.search("u1", "Java 开发", 5);
        assertThat(hits).hasSize(1);
        assertThat(hits.get(0)).contains("Java");
    }

    @Test
    void searchRanksByHitCount() {
        InMemoryLongTermMemory memory = new InMemoryLongTermMemory();
        memory.store("u1", "s1", "Java Python 开发", Map.of());
        memory.store("u1", "s1", "仅Java", Map.of());

        List<String> hits = memory.search("u1", "Java Python", 5);
        assertThat(hits).hasSize(2);
        // 命中两个词的条目应排在前面
        assertThat(hits.get(0)).contains("Java Python");
    }

    @Test
    void searchRespectsTopK() {
        InMemoryLongTermMemory memory = new InMemoryLongTermMemory();
        memory.store("u1", "s1", "记忆A包含关键词", Map.of());
        memory.store("u1", "s1", "记忆B包含关键词", Map.of());
        memory.store("u1", "s1", "记忆C包含关键词", Map.of());

        List<String> hits = memory.search("u1", "关键词", 2);
        assertThat(hits).hasSize(2);
    }

    @Test
    void searchReturnEmptyWhenNoHit() {
        InMemoryLongTermMemory memory = new InMemoryLongTermMemory();
        memory.store("u1", "s1", "完全无关的内容", Map.of());
        assertThat(memory.search("u1", "Java", 5)).isEmpty();
    }

    @Test
    void searchReturnEmptyWhenNoContent() {
        InMemoryLongTermMemory memory = new InMemoryLongTermMemory();
        assertThat(memory.search("u1", "任意", 5)).isEmpty();
    }

    @Test
    void searchReturnEmptyWhenQueryBlank() {
        InMemoryLongTermMemory memory = new InMemoryLongTermMemory();
        memory.store("u1", "s1", "内容", Map.of());
        assertThat(memory.search("u1", "  ", 5)).isEmpty();
    }

    @Test
    void isolatedByUserId() {
        InMemoryLongTermMemory memory = new InMemoryLongTermMemory();
        memory.store("u1", "s1", "用户1的Java笔记", Map.of());
        memory.store("u2", "s1", "用户2的Java笔记", Map.of());

        assertThat(memory.search("u1", "Java", 5)).hasSize(1);
        assertThat(memory.search("u2", "Java", 5)).hasSize(1);
        assertThat(memory.search("u1", "Java", 5).get(0)).contains("用户1");
    }

    @Test
    void deleteRemovesEntry() {
        InMemoryLongTermMemory memory = new InMemoryLongTermMemory();
        memory.store("u1", "s1", "待删除的记忆内容", Map.of());
        List<String> before = memory.search("u1", "记忆", 5);
        assertThat(before).hasSize(1);

        // 删除不存在的ID不影响
        memory.delete("nonexistent");
        assertThat(memory.search("u1", "记忆", 5)).hasSize(1);
    }

    @Test
    void storeIgnoreBlankContent() {
        InMemoryLongTermMemory memory = new InMemoryLongTermMemory();
        memory.store("u1", "s1", "  ", Map.of());
        memory.store("u1", "s1", null, Map.of());
        assertThat(memory.search("u1", "任意", 5)).isEmpty();
    }

    @Test
    void caseInsensitiveSearch() {
        InMemoryLongTermMemory memory = new InMemoryLongTermMemory();
        memory.store("u1", "s1", "I love Java programming", Map.of());
        assertThat(memory.search("u1", "java", 5)).hasSize(1);
    }
}
