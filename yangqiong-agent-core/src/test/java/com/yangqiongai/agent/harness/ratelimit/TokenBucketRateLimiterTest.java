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
package com.yangqiongai.agent.harness.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

/**
 * 令牌桶速率限制器测试
 * @author yangqiong
 */
class TokenBucketRateLimiterTest {

    @Test
    void initialBurstAllowsImmediateCalls() {
        TokenBucketRateLimiter limiter = new TokenBucketRateLimiter(2.0, 1.0);
        assertThat(limiter.waitNanos()).isEqualTo(0L);
        assertThat(limiter.waitNanos()).isEqualTo(0L);
        // 突发容量耗尽，第三次需等待
        assertThat(limiter.waitNanos()).isGreaterThan(0L);
    }

    @Test
    void refillRestoresTokensOverTime() throws InterruptedException {
        TokenBucketRateLimiter limiter = new TokenBucketRateLimiter(2.0, 100.0);
        assertThat(limiter.waitNanos()).isEqualTo(0L);
        assertThat(limiter.waitNanos()).isEqualTo(0L);
        assertThat(limiter.waitNanos()).isGreaterThan(0L);
        // 等待足够时间让令牌补充回满
        Thread.sleep(50);
        assertThat(limiter.waitNanos()).isEqualTo(0L);
    }

    @Test
    void availableTokensReflectsUsage() {
        // 补充速率低，两次调用间隔的补充量可忽略
        TokenBucketRateLimiter limiter = new TokenBucketRateLimiter(3.0, 0.0001);
        assertThat(limiter.availableTokens()).isEqualTo(3.0);
        limiter.waitNanos();
        assertThat(limiter.availableTokens()).isCloseTo(2.0, within(0.0001));
    }

    @Test
    void concurrentAcquisitionDoesNotOverIssue() throws InterruptedException {
        // 容量5、极慢补充：10个并发调用仅前5个立即可用
        TokenBucketRateLimiter limiter = new TokenBucketRateLimiter(5.0, 0.0001);
        int threads = 10;
        AtomicInteger immediate = new AtomicInteger();
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threads);
        for (int i = 0; i < threads; i++) {
            pool.submit(() -> {
                try {
                    start.await();
                    if (limiter.waitNanos() == 0L) {
                        immediate.incrementAndGet();
                    }
                } catch (InterruptedException ignored) {
                    Thread.currentThread().interrupt();
                } finally {
                    done.countDown();
                }
            });
        }
        start.countDown();
        done.await(5, TimeUnit.SECONDS);
        pool.shutdownNow();
        assertThat(immediate.get()).isEqualTo(5);
    }

    @Test
    void invalidCapacityRejected() {
        org.assertj.core.api.Assertions.assertThatThrownBy(
                        () -> new TokenBucketRateLimiter(0, 1.0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void invalidRefillRateRejected() {
        org.assertj.core.api.Assertions.assertThatThrownBy(
                        () -> new TokenBucketRateLimiter(1.0, 0))
                .isInstanceOf(IllegalArgumentException.class);
    }
}