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

import org.junit.jupiter.api.Test;

/**
 * 模型速率限制注册表测试
 * @author yangqiong
 */
class ModelRateLimitRegistryTest {

    @Test
    void registerAndLookupByModelCode() {
        ModelRateLimitRegistry registry = new ModelRateLimitRegistry();
        TokenBucketRateLimiter limiter = new TokenBucketRateLimiter(2.0, 1.0);
        assertThat(registry.register("deepseek-chat", limiter)).isTrue();
        assertThat(registry.limiterFor("deepseek-chat")).isSameAs(limiter);
        assertThat(registry.limiterFor("unknown")).isNull();
        assertThat(registry.size()).isEqualTo(1);
    }

    @Test
    void nullOrBlankModelCodeIgnored() {
        ModelRateLimitRegistry registry = new ModelRateLimitRegistry();
        assertThat(registry.register(null, new TokenBucketRateLimiter(1.0, 1.0))).isFalse();
        assertThat(registry.register("  ", new TokenBucketRateLimiter(1.0, 1.0))).isFalse();
        assertThat(registry.size()).isZero();
    }

    @Test
    void boundedCapacityIgnoresNewEntries() {
        ModelRateLimitRegistry registry = new ModelRateLimitRegistry(2);
        assertThat(registry.register("a", new TokenBucketRateLimiter(1.0, 1.0))).isTrue();
        assertThat(registry.register("b", new TokenBucketRateLimiter(1.0, 1.0))).isTrue();
        // 超限新条目被忽略
        assertThat(registry.register("c", new TokenBucketRateLimiter(1.0, 1.0))).isFalse();
        assertThat(registry.size()).isEqualTo(2);
        // 已有条目覆盖仍允许
        assertThat(registry.register("a", new TokenBucketRateLimiter(2.0, 2.0))).isTrue();
        assertThat(registry.size()).isEqualTo(2);
    }
}