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
package com.yangqiongai.agent.harness.model.routing;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.Test;

/**
 * 模型健康登记测试
 * @author yangqiong
 */
class ModelHealthRegistryTest {

    @Test
    void shouldReturnPerfectScoreWithoutRecords() {
        ModelHealthRegistry registry = new ModelHealthRegistry();
        assertThat(registry.isHealthy("m1")).isTrue();
        assertThat(registry.healthScore("m1")).isEqualTo(1.0d);
    }

    @Test
    void shouldComputeSuccessRateFromWindow() {
        ModelHealthRegistry registry = new ModelHealthRegistry();
        registry.recordSuccess("m1");
        registry.recordSuccess("m1");
        registry.recordSuccess("m1");
        registry.recordFailure("m1");
        assertThat(registry.healthScore("m1")).isEqualTo(0.75d);
        assertThat(registry.isHealthy("m1")).isTrue();
    }

    @Test
    void shouldMarkUnhealthyAfterConsecutiveFailures() {
        ModelHealthRegistry registry = new ModelHealthRegistry();
        for (int i = 0; i < 4; i++) {
            registry.recordFailure("m1");
        }
        assertThat(registry.isHealthy("m1")).isTrue();
        registry.recordFailure("m1");
        assertThat(registry.isHealthy("m1")).isFalse();
    }

    @Test
    void shouldRecoverAfterCooldown() {
        AtomicLong clock = new AtomicLong(0L);
        ModelHealthRegistry registry = new ModelHealthRegistry(1000L, clock::get);
        for (int i = 0; i < 5; i++) {
            registry.recordFailure("m1");
        }
        assertThat(registry.isHealthy("m1")).isFalse();
        clock.set(999L);
        assertThat(registry.isHealthy("m1")).isFalse();
        clock.set(1000L);
        assertThat(registry.isHealthy("m1")).isTrue();
        assertThat(registry.healthScore("m1")).isZero();
    }

    @Test
    void shouldResetConsecutiveFailuresOnSuccess() {
        ModelHealthRegistry registry = new ModelHealthRegistry();
        for (int i = 0; i < 5; i++) {
            registry.recordFailure("m1");
        }
        assertThat(registry.isHealthy("m1")).isFalse();
        registry.recordSuccess("m1");
        assertThat(registry.isHealthy("m1")).isTrue();
    }

    @Test
    void shouldKeepWindowWithinLatestRecords() {
        ModelHealthRegistry registry = new ModelHealthRegistry();
        for (int i = 0; i < 18; i++) {
            registry.recordFailure("m1");
        }
        registry.recordSuccess("m1");
        registry.recordSuccess("m1");
        registry.recordSuccess("m1");
        assertThat(registry.healthScore("m1")).isEqualTo(0.15d);
    }
}
