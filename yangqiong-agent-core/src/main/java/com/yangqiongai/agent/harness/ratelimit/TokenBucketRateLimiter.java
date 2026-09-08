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

/**
 * 令牌桶速率限制器
 * <p>
 * 以 capacity 为突发容量、refillPerSec 为每秒补充速率，按单调时钟累计令牌。
 * 每次调用预留（消耗）1个令牌并返回等待时间；无可用令牌时按当前速率计算补满所需时长。
 * 线程安全（关键路径 synchronized）。
 * </p>
 * @author yangqiong
 */
public class TokenBucketRateLimiter implements RateLimiter {

    /**
     * 突发容量（最大令牌数）
     */
    private final double capacity;

    /**
     * 每秒补充速率
     */
    private final double refillPerSec;

    /**
     * 当前可用令牌数
     */
    private double tokens;

    /**
     * 上次补充时间戳（纳秒）
     */
    private long lastRefillNanos;

    public TokenBucketRateLimiter(double capacity, double refillPerSec) {
        if (capacity <= 0) {
            throw new IllegalArgumentException("令牌桶容量必须为正数: " + capacity);
        }
        if (refillPerSec <= 0) {
            throw new IllegalArgumentException("补充速率必须为正数: " + refillPerSec);
        }
        this.capacity = capacity;
        this.refillPerSec = refillPerSec;
        this.tokens = capacity;
    }

    @Override
    public synchronized long waitNanos() {
        refill();
        if (tokens >= 1.0) {
            tokens -= 1.0;
            return 0L;
        }
        // 无可用令牌：按当前速率计算补满1个令牌的等待时长，并预留该令牌
        double deficit = 1.0 - tokens;
        long waitNanos = (long) (deficit / refillPerSec * 1_000_000_000.0);
        tokens -= 1.0;
        return waitNanos;
    }

    @Override
    public double getCurrentRate() {
        return refillPerSec;
    }

    /**
     * 获取当前可用令牌数（含即时补充），供观测
     * @return
     */
    public synchronized double availableTokens() {
        refill();
        return tokens;
    }

    /**
     * 按单调时钟补充令牌
     */
    private void refill() {
        long now = System.nanoTime();
        if (lastRefillNanos == 0) {
            lastRefillNanos = now;
            return;
        }
        double elapsedSec = (now - lastRefillNanos) / 1_000_000_000.0;
        tokens = Math.min(capacity, tokens + elapsedSec * refillPerSec);
        lastRefillNanos = now;
    }
}