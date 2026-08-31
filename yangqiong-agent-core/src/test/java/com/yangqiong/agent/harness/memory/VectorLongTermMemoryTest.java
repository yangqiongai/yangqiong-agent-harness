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
package com.yangqiong.agent.harness.memory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;

import com.yangqiong.agent.harness.model.embedding.HashingEmbeddingModel;
import org.junit.jupiter.api.Test;

/**
 * 向量长期记忆测试
 * @author yangqiong
 */
class VectorLongTermMemoryTest {

    /**
     * 构建使用哈希嵌入模型的向量长期记忆
     * @return
     */
    private VectorLongTermMemory newMemory() {
        return new VectorLongTermMemory(new HashingEmbeddingModel());
    }

    @Test
    void hybridSearchHitsRelevant() {
        VectorLongTermMemory memory = newMemory();
        memory.store("u1", "s1", "用户偏好使用Java开发后端服务", Map.of());
        memory.store("u1", "s1", "今天天气晴朗适合户外活动", Map.of());

        List<String> hits = memory.search("u1", "Java 开发", 5);

        assertThat(hits).hasSize(1);
        assertThat(hits.get(0)).contains("Java");
    }

    @Test
    void isolatedByScope() {
        VectorLongTermMemory memory = newMemory();
        memory.store("scopeA", "u1", "s1", "租户A的Java笔记", Map.of());
        memory.store("scopeB", "u1", "s1", "租户B的Java笔记", Map.of());

        assertThat(memory.search("scopeA", "u1", "Java", 5)).hasSize(1);
        assertThat(memory.search("scopeA", "u1", "Java", 5).get(0)).contains("租户A");
        assertThat(memory.search("scopeB", "u1", "Java", 5).get(0)).contains("租户B");
    }

    @Test
    void deleteWithOwnershipCheck() {
        VectorLongTermMemory memory = newMemory();
        memory.store("scopeA", "u1", "s1", "属于租户A的记忆", Map.of());
        String id = memory.listMemoryIds("scopeA", "u1").get(0);

        assertThatThrownBy(() -> memory.delete("scopeB", id))
                .isInstanceOf(SecurityException.class);

        memory.delete("scopeA", id);
        assertThat(memory.search("scopeA", "u1", "记忆", 5)).isEmpty();
    }

    @Test
    void boundedCapacity() {
        VectorLongTermMemory memory = newMemory();
        for (int i = 0; i < VectorLongTermMemory.MAX_ENTRIES + 1; i++) {
            memory.store("u1", "s1", "记忆条目内容" + i, Map.of());
        }

        assertThat(memory.size()).isEqualTo(VectorLongTermMemory.MAX_ENTRIES);
    }
}
