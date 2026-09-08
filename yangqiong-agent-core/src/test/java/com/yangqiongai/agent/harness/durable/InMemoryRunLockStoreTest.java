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
package com.yangqiongai.agent.harness.durable;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;

import org.junit.jupiter.api.Test;

/**
 * 内存运行锁存储测试
 * @author yangqiong
 */
class InMemoryRunLockStoreTest {

    @Test
    void shouldAcquireLockWhenFree() {
        InMemoryRunLockStore store = new InMemoryRunLockStore();

        assertThat(store.tryLock("run-1", "node-A", Duration.ofSeconds(30))).isTrue();
        assertThat(store.owner("run-1")).contains("node-A");
    }

    @Test
    void shouldRejectDifferentOwnerWhileHeld() {
        InMemoryRunLockStore store = new InMemoryRunLockStore();
        store.tryLock("run-1", "node-A", Duration.ofSeconds(30));

        assertThat(store.tryLock("run-1", "node-B", Duration.ofSeconds(30))).isFalse();
        assertThat(store.owner("run-1")).contains("node-A");
    }

    @Test
    void shouldReacquireBySameOwner() {
        InMemoryRunLockStore store = new InMemoryRunLockStore();
        store.tryLock("run-1", "node-A", Duration.ofSeconds(30));

        // 同节点重复加锁视为续期（幂等）
        assertThat(store.tryLock("run-1", "node-A", Duration.ofSeconds(60))).isTrue();
        assertThat(store.owner("run-1")).contains("node-A");
    }

    @Test
    void shouldExpireLockAndAllowTakeover() throws Exception {
        InMemoryRunLockStore store = new InMemoryRunLockStore();
        store.tryLock("run-1", "node-A", Duration.ofMillis(50));

        Thread.sleep(80);
        assertThat(store.owner("run-1")).isEmpty();
        assertThat(store.tryLock("run-1", "node-B", Duration.ofSeconds(30))).isTrue();
        assertThat(store.owner("run-1")).contains("node-B");
    }

    @Test
    void shouldRenewOnlyByOwner() throws Exception {
        InMemoryRunLockStore store = new InMemoryRunLockStore();
        store.tryLock("run-1", "node-A", Duration.ofMillis(60));

        store.renew("run-1", "node-B", Duration.ofSeconds(30));
        Thread.sleep(100);
        // 非持有者续期不生效，锁照常过期
        assertThat(store.owner("run-1")).isEmpty();

        store.tryLock("run-1", "node-A", Duration.ofMillis(60));
        store.renew("run-1", "node-A", Duration.ofSeconds(30));
        Thread.sleep(80);
        assertThat(store.owner("run-1")).contains("node-A");
    }

    @Test
    void shouldUnlockOnlyByOwner() {
        InMemoryRunLockStore store = new InMemoryRunLockStore();
        store.tryLock("run-1", "node-A", Duration.ofSeconds(30));

        store.unlock("run-1", "node-B");
        assertThat(store.owner("run-1")).contains("node-A");

        store.unlock("run-1", "node-A");
        assertThat(store.owner("run-1")).isEmpty();
    }

    @Test
    void shouldReturnEmptyOwnerForUnknownOrNull() {
        InMemoryRunLockStore store = new InMemoryRunLockStore();
        assertThat(store.owner("run-x")).isEmpty();
        assertThat(store.owner(null)).isEmpty();
        assertThat(store.tryLock(null, "node-A", Duration.ofSeconds(30))).isFalse();
        assertThat(store.tryLock("run-1", null, Duration.ofSeconds(30))).isFalse();
    }

    @Test
    void shouldSerializeConcurrentContention() throws Exception {
        InMemoryRunLockStore store = new InMemoryRunLockStore();
        int workers = 8;
        int attempts = 500;
        java.util.concurrent.atomic.AtomicInteger winners = new java.util.concurrent.atomic.AtomicInteger();
        java.util.concurrent.CountDownLatch latch = new java.util.concurrent.CountDownLatch(workers);
        java.util.List<Thread> threads = new java.util.ArrayList<>();
        for (int w = 0; w < workers; w++) {
            int owner = w;
            Thread t = new Thread(() -> {
                latch.countDown();
                try {
                    latch.await();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
                if (store.tryLock("run-1", "node-" + owner, Duration.ofSeconds(30))) {
                    winners.incrementAndGet();
                }
            });
            threads.add(t);
            t.start();
        }
        new java.util.ArrayList<>(threads).forEach(t -> {
            try {
                t.join();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
        assertThat(winners.get()).isEqualTo(1);
        assertThat(store.owner("run-1")).isPresent();
    }
}