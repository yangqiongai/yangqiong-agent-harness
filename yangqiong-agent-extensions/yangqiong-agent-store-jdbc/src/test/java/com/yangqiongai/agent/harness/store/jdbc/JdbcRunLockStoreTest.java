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

import java.time.Duration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.yangqiongai.agent.harness.durable.RunLockStore;
import com.yangqiongai.agent.harness.store.jdbc.mapper.RunLockMapper;

/**
 * 运行锁存储JDBC实现测试
 * @author yangqiong
 */
class JdbcRunLockStoreTest {

    /**
     * 运行锁存储
     */
    private RunLockStore store;

    @BeforeEach
    void setUp() {
        store = new JdbcRunLockStore(JdbcTestSupport.newFactory().getMapper(RunLockMapper.class));
    }

    /**
     * 首次加锁成功且可查询持有者
     */
    @Test
    void tryLockAcquire() {
        boolean acquired = store.tryLock("run1", "node1", Duration.ofMinutes(5));

        assertThat(acquired).isTrue();
        assertThat(store.owner("run1")).hasValue("node1");
    }

    /**
     * 同节点重复加锁视为续期成功
     */
    @Test
    void tryLockSameOwnerRenews() {
        store.tryLock("run1", "node1", Duration.ofMinutes(5));

        boolean renewed = store.tryLock("run1", "node1", Duration.ofMinutes(10));

        assertThat(renewed).isTrue();
        assertThat(store.owner("run1")).hasValue("node1");
    }

    /**
     * 其他节点在锁未过期时加锁失败
     */
    @Test
    void tryLockDifferentOwnerBlocked() {
        store.tryLock("run1", "node1", Duration.ofMinutes(5));

        boolean blocked = store.tryLock("run1", "node2", Duration.ofMinutes(5));

        assertThat(blocked).isFalse();
        assertThat(store.owner("run1")).hasValue("node1");
    }

    /**
     * 锁过期后其他节点可接管
     */
    @Test
    void tryLockAfterExpiryTakeover() throws InterruptedException {
        store.tryLock("run1", "node1", Duration.ofMillis(50));
        assertThat(store.owner("run1")).hasValue("node1");

        Thread.sleep(80);

        assertThat(store.owner("run1")).isEmpty();
        boolean acquired = store.tryLock("run1", "node2", Duration.ofMinutes(5));
        assertThat(acquired).isTrue();
        assertThat(store.owner("run1")).hasValue("node2");
    }

    /**
     * 持有者续期延长过期时间
     */
    @Test
    void renewLock() throws InterruptedException {
        store.tryLock("run1", "node1", Duration.ofMillis(60));

        Thread.sleep(30);
        store.renew("run1", "node1", Duration.ofMinutes(5));

        // 续期后短时间内不过期
        assertThat(store.owner("run1")).hasValue("node1");
        Thread.sleep(60);
        assertThat(store.owner("run1")).hasValue("node1");
    }

    /**
     * 非持有者续期不生效
     */
    @Test
    void renewByOtherOwnerIgnored() {
        store.tryLock("run1", "node1", Duration.ofMinutes(5));

        store.renew("run1", "node2", Duration.ofMinutes(5));

        assertThat(store.owner("run1")).hasValue("node1");
    }

    /**
     * 持有者解锁后可被其他节点获取
     */
    @Test
    void unlockByOwner() {
        store.tryLock("run1", "node1", Duration.ofMinutes(5));
        store.unlock("run1", "node1");

        assertThat(store.owner("run1")).isEmpty();
        assertThat(store.tryLock("run1", "node2", Duration.ofMinutes(5))).isTrue();
    }

    /**
     * 非持有者解锁无效
     */
    @Test
    void unlockByOtherOwnerIgnored() {
        store.tryLock("run1", "node1", Duration.ofMinutes(5));

        store.unlock("run1", "node2");

        assertThat(store.owner("run1")).hasValue("node1");
    }
}