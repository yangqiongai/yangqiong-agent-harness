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
package com.yangqiong.agent.harness.store.jdbc;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.yangqiong.agent.harness.core.message.AgentTextBlock;
import com.yangqiong.agent.harness.core.message.AgentToolResultBlock;
import com.yangqiong.agent.harness.store.jdbc.mapper.ToolExecutionMapper;
import com.yangqiong.agent.harness.tool.ToolExecutionStore;

/**
 * 工具执行记录存储JDBC实现测试
 * @author yangqiong
 */
class JdbcToolExecutionStoreTest {

    /**
     * 工具执行记录存储
     */
    private ToolExecutionStore store;

    @BeforeEach
    void setUp() {
        store = new JdbcToolExecutionStore(JdbcTestSupport.newFactory().getMapper(ToolExecutionMapper.class));
    }

    /**
     * 记录后查询完成状态与结果
     */
    @Test
    void recordAndQuery() {
        AgentToolResultBlock result = AgentToolResultBlock.of(List.of(text("查询结果A")));

        store.record("key-1", result);

        assertThat(store.isCompleted("key-1")).isTrue();
        AgentToolResultBlock found = store.getResult("key-1");
        assertThat(found).isNotNull();
        assertThat(found.getTextContent()).isEqualTo("查询结果A");
    }

    /**
     * 未记录或空键返回未完成
     */
    @Test
    void queryMissing() {
        assertThat(store.isCompleted("missing")).isFalse();
        assertThat(store.getResult("missing")).isNull();
        assertThat(store.isCompleted(null)).isFalse();
        assertThat(store.getResult(null)).isNull();
    }

    /**
     * 幂等记录：同键首次结果落地后重复记录不覆盖
     */
    @Test
    void recordIdempotent() {
        store.record("key-1", AgentToolResultBlock.of(List.of(text("首次结果"))));
        store.record("key-1", AgentToolResultBlock.of(List.of(text("重复结果"))));

        assertThat(store.getResult("key-1").getTextContent()).isEqualTo("首次结果");
    }

    /**
     * 空参数记录被忽略
     */
    @Test
    void recordNullIgnored() {
        store.record(null, AgentToolResultBlock.of(List.of(text("结果"))));
        store.record("key-1", null);

        assertThat(store.isCompleted("key-1")).isFalse();
    }

    /**
     * 构造文本块
     * @param text
     * @return
     */
    private AgentTextBlock text(String text) {
        return AgentTextBlock.builder().text(text).build();
    }
}