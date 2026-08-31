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
package com.yangqiong.agent.harness.event;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.atomic.AtomicInteger;

import com.yangqiong.agent.harness.core.event.AgentEvent;
import com.yangqiong.agent.harness.core.event.AgentEventType;
import org.junit.jupiter.api.Test;

/**
 * 事件监听器注册中心测试
 * @author yangqiong
 */
class EventBusTest {

    @Test
    void shouldBroadcastEventToAllRegisteredListeners() {
        EventBus bus = new EventBus();
        AtomicInteger count = new AtomicInteger();
        bus.register(event -> count.incrementAndGet());

        bus.publish(AgentEvent.of(AgentEventType.AGENT_START, "start"));
        bus.publish(AgentEvent.of(AgentEventType.AGENT_END, "end"));

        assertThat(count.get()).isEqualTo(2);
        assertThat(bus.listenerCount()).isEqualTo(1);
    }

    @Test
    void shouldFilterEventByListenerInterest() {
        EventBus bus = new EventBus();
        AtomicInteger completed = new AtomicInteger();
        bus.register(new AgentEventListener() {
            @Override
            public boolean isInterestedIn(AgentEventType type) {
                return type == AgentEventType.COMPLETED;
            }

            @Override
            public void onEvent(AgentEvent event) {
                completed.incrementAndGet();
            }
        });

        bus.publish(AgentEvent.of(AgentEventType.AGENT_START, "ignored"));
        bus.publish(AgentEvent.of(AgentEventType.COMPLETED, "done"));

        assertThat(completed.get()).as("仅感兴宇事件应投递").isEqualTo(1);
    }

    @Test
    void shouldStopDeliveryAfterUnregister() {
        EventBus bus = new EventBus();
        AtomicInteger count = new AtomicInteger();
        AgentEventListener listener = event -> count.incrementAndGet();
        bus.register(listener);
        bus.publish(AgentEvent.completed());

        bus.unregister(listener);
        bus.publish(AgentEvent.completed());

        assertThat(count.get()).isEqualTo(1);
        assertThat(bus.listenerCount()).isZero();
    }

    @Test
    void shouldIsolateListenerException() {
        EventBus bus = new EventBus();
        AtomicInteger healthy = new AtomicInteger();
        bus.register(event -> {
            throw new IllegalStateException("监听器故障");
        });
        bus.register(event -> healthy.incrementAndGet());

        bus.publish(AgentEvent.completed());

        assertThat(healthy.get()).as("故障监听器不应影响其他监听器").isEqualTo(1);
    }

    @Test
    void shouldIgnoreNullEventAndNullListener() {
        EventBus bus = new EventBus();
        AtomicInteger count = new AtomicInteger();
        bus.register(null);
        bus.register(event -> count.incrementAndGet());

        bus.publish(null);
        bus.publish(AgentEvent.completed());

        assertThat(count.get()).isEqualTo(1);
    }
}