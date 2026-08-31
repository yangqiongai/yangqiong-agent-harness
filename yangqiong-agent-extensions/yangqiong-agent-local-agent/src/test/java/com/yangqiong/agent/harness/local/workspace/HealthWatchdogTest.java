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
package com.yangqiong.agent.harness.local.workspace;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import com.yangqiong.agent.harness.core.event.AgentEvent;
import com.yangqiong.agent.harness.core.event.AgentEventType;

import org.junit.jupiter.api.Test;

/**
 * 监控告警与自愈测试
 * @author yangqiong
 */
class HealthWatchdogTest {

    @Test
    void consecutiveAlarmsTriggerRecoveryOnce() throws Exception {
        AtomicInteger recovered = new AtomicInteger();
        HealthWatchdog watchdog = new HealthWatchdog(2, Duration.ofSeconds(60), Duration.ofSeconds(60));
        watchdog.recoveryAction(recovered::incrementAndGet);

        watchdog.onEvent(AgentEvent.of(AgentEventType.ERROR, null));
        assertThat(watchdog.consecutiveAlarms()).isEqualTo(1);

        watchdog.onEvent(AgentEvent.of(AgentEventType.ERROR, null));
        // 自愈异步执行，等待其完成
        awaitRecovery(recovered, 1);
        assertThat(watchdog.recoveryCount()).isEqualTo(1);
        assertThat(watchdog.consecutiveAlarms()).isZero();
    }

    @Test
    void healthyCompletionResetsAlarmCounter() {
        HealthWatchdog watchdog = new HealthWatchdog(3, Duration.ofSeconds(60), Duration.ofSeconds(60));

        watchdog.onEvent(AgentEvent.of(AgentEventType.ERROR, null));
        watchdog.onEvent(AgentEvent.of(AgentEventType.ERROR, null));
        watchdog.onEvent(AgentEvent.completed());

        assertThat(watchdog.consecutiveAlarms()).isZero();
    }

    @Test
    void stalledHeartbeatIsDetected() {
        HealthWatchdog watchdog = new HealthWatchdog(3, Duration.ofSeconds(1), Duration.ofSeconds(1));
        watchdog.simulateDeadlineElapsed();

        assertThat(watchdog.isStalled()).isTrue();
    }

    @Test
    void startAndStopAreIdempotent() {
        HealthWatchdog watchdog = new HealthWatchdog(3, Duration.ofSeconds(60), Duration.ofMinutes(1));
        watchdog.start();
        watchdog.start();
        watchdog.stop();
        watchdog.stop();
    }

    private void awaitRecovery(AtomicInteger counter, int expected) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (counter.get() < expected && System.nanoTime() < deadline) {
            TimeUnit.MILLISECONDS.sleep(10);
        }
        assertThat(counter.get()).isGreaterThanOrEqualTo(expected);
    }
}