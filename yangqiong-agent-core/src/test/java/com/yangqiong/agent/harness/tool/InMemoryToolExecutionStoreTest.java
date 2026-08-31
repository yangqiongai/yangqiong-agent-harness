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
package com.yangqiong.agent.harness.tool;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import com.yangqiong.agent.harness.core.message.AgentTextBlock;
import com.yangqiong.agent.harness.core.message.AgentToolResultBlock;
import org.junit.jupiter.api.Test;

/**
 * 内存工具执行记录存储测试
 * @author yangqiong
 */
class InMemoryToolExecutionStoreTest {

    private AgentToolResultBlock result(String text) {
        return AgentToolResultBlock.of("call-1", List.of(AgentTextBlock.builder().text(text).build()));
    }

    @Test
    void shouldDeduplicateByIdempotencyKey() {
        InMemoryToolExecutionStore store = new InMemoryToolExecutionStore();
        AgentToolResultBlock first = result("首次执行结果");

        store.record("key-1", first);
        store.record("key-1", result("第二次执行结果"));

        assertThat(store.isCompleted("key-1")).isTrue();
        assertThat(store.getResult("key-1").getTextContent()).isEqualTo("首次执行结果");
    }

    @Test
    void shouldHandleNullOrMissingKey() {
        InMemoryToolExecutionStore store = new InMemoryToolExecutionStore();

        store.record(null, result("无幂等键"));
        store.record("key-1", null);

        assertThat(store.isCompleted(null)).isFalse();
        assertThat(store.getResult(null)).isNull();
        assertThat(store.isCompleted("key-1")).isFalse();
    }

    @Test
    void shouldCapEntriesAtLimit() throws Exception {
        InMemoryToolExecutionStore store = new InMemoryToolExecutionStore();

        for (int i = 0; i <= 10000; i++) {
            store.record("key-" + i, result("结果" + i));
        }

        // 最新键必然存在，总条目数封顶不超限
        assertThat(store.isCompleted("key-10000")).isTrue();
        java.lang.reflect.Field field = InMemoryToolExecutionStore.class.getDeclaredField("completedByKey");
        field.setAccessible(true);
        java.util.Map<String, AgentToolResultBlock> entries =
                (java.util.Map<String, AgentToolResultBlock>) field.get(store);
        assertThat(entries.size()).isLessThanOrEqualTo(10000);
    }
}