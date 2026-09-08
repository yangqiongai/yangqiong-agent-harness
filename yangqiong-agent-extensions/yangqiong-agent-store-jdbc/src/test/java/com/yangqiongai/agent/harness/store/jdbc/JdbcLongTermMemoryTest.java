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
package com.yangqiongai.agent.harness.store.jdbc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.yangqiongai.agent.harness.core.memory.AgentLongTermMemory;
import com.yangqiongai.agent.harness.store.jdbc.entity.LongTermMemoryEntity;
import com.yangqiongai.agent.harness.store.jdbc.mapper.LongTermMemoryMapper;

/**
 * 长期记忆JDBC实现测试
 * @author yangqiong
 */
class JdbcLongTermMemoryTest {

    /**
     * 长期记忆
     */
    private AgentLongTermMemory memory;

    /**
     * 长期记忆映射器
     */
    private LongTermMemoryMapper mapper;

    @BeforeEach
    void setUp() {
        mapper = JdbcTestSupport.newFactory().getMapper(LongTermMemoryMapper.class);
        memory = new JdbcLongTermMemory(mapper);
    }

    /**
     * 存储并按分词LIKE检索
     */
    @Test
    void storeAndSearch() {
        memory.store("scope1", "user1", "session1", "用户偏好编程与音乐", Map.of("type", "preference"));

        List<String> results = memory.search("scope1", "user1", "编程", 10);

        assertThat(results).hasSize(1);
        assertThat(results.get(0)).isEqualTo("用户偏好编程与音乐");
    }

    /**
     * 检索无命中返回空
     */
    @Test
    void searchNoMatch() {
        memory.store("scope1", "user1", "session1", "用户偏好编程", Map.of());

        assertThat(memory.search("scope1", "user1", "旅行", 10)).isEmpty();
        assertThat(memory.search("scope1", "user1", "  ", 10)).isEmpty();
        assertThat(memory.search("scope1", "user1", null, 10)).isEmpty();
    }

    /**
     * 检索按命中数排序并受limit限制
     */
    @Test
    void searchOrderAndLimit() {
        memory.store("scope1", "user1", "session1", "猫与狗都可爱", Map.of());
        memory.store("scope1", "user1", "session1", "猫喜欢睡觉", Map.of());
        memory.store("scope1", "user1", "session1", "狗喜欢奔跑", Map.of());

        List<String> results = memory.search("scope1", "user1", "猫 狗", 2);

        // 命中两词条目的排最前，limit限制为2条
        assertThat(results).hasSize(2);
        assertThat(results.get(0)).isEqualTo("猫与狗都可爱");
    }

    /**
     * 记忆按租户作用域隔离
     */
    @Test
    void scopeIsolation() {
        memory.store("scope1", "user1", "session1", "租户A的记忆", Map.of());
        memory.store("scope2", "user1", "session1", "租户B的记忆", Map.of());

        assertThat(memory.search("scope1", "user1", "记忆", 10)).containsExactly("租户A的记忆");
        assertThat(memory.search("scope2", "user1", "记忆", 10)).containsExactly("租户B的记忆");
        assertThat(memory.search("scope1", "user2", "记忆", 10)).isEmpty();
    }

    /**
     * 按ID删除记忆
     */
    @Test
    void deleteById() throws Exception {
        memory.store("scope1", "user1", "session1", "待删除记忆", Map.of());
        String id = queryFirstId();

        memory.delete(id);

        assertThat(memory.search("scope1", "user1", "待删除", 10)).isEmpty();
    }

    /**
     * 跨租户删除抛SecurityException
     */
    @Test
    void deleteWrongScopeThrows() throws Exception {
        memory.store("scope1", "user1", "session1", "租户A记忆", Map.of());
        String id = queryFirstId();

        assertThatThrownBy(() -> memory.delete("scope2", id))
                .isInstanceOf(SecurityException.class);
        assertThat(memory.search("scope1", "user1", "租户A", 10)).hasSize(1);
    }

    /**
     * 同租户删除成功
     */
    @Test
    void deleteSameScopeSuccess() throws Exception {
        memory.store("scope1", "user1", "session1", "租户A记忆", Map.of());
        String id = queryFirstId();

        memory.delete("scope1", id);

        assertThat(memory.search("scope1", "user1", "租户A", 10)).isEmpty();
    }

    /**
     * 容量边界：limit为0返回全部命中
     */
    @Test
    void searchLimitZeroReturnsAll() {
        memory.store("scope1", "user1", "session1", "记忆甲", Map.of());
        memory.store("scope1", "user1", "session1", "记忆乙", Map.of());

        assertThat(memory.search("scope1", "user1", "记忆", 0)).hasSize(2);
    }

    /**
     * 查询首条记忆ID
     * @return
     */
    private String queryFirstId() {
        return mapper.selectList(Wrappers.<LongTermMemoryEntity>lambdaQuery()
                .orderByAsc(LongTermMemoryEntity::getCreatedAt)
                .last("LIMIT 1")).get(0).getId();
    }
}