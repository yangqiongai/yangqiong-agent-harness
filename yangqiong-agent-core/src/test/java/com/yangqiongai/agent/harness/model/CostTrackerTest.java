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
package com.yangqiongai.agent.harness.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.stream.IntStream;

import org.junit.jupiter.api.Test;

import com.yangqiongai.agent.harness.core.message.AgentChatUsage;

/**
 * 成本追踪器测试
 * @author yangqiong
 */
class CostTrackerTest {

    @Test
    void shouldStartAtZero() {
        CostTracker tracker = new CostTracker();
        assertThat(tracker.getTotalCostUsd()).isZero();
    }

    @Test
    void shouldAccumulatePromptAndCompletionCost() {
        CostTracker tracker = new CostTracker();
        AgentChatUsage usage = new AgentChatUsage(1000, 500, 1500);
        ModelPricing pricing = new ModelPricing("gpt-4o", 2.5, 10.0);
        double expected = (1000 / 1000.0) * 2.5 + (500 / 1000.0) * 10.0;
        assertThat(tracker.accumulate(usage, pricing)).isEqualTo(expected);
        assertThat(tracker.getTotalCostUsd()).isEqualTo(expected);
    }

    @Test
    void shouldSumMultipleAccumulations() {
        CostTracker tracker = new CostTracker();
        ModelPricing pricing = new ModelPricing("gpt-4o", 2.5, 10.0);
        tracker.accumulate(new AgentChatUsage(1000, 500, 1500), pricing);
        tracker.accumulate(new AgentChatUsage(500, 250, 750), pricing);
        double expected = (1000 / 1000.0) * 2.5 + (500 / 1000.0) * 10.0
                + (500 / 1000.0) * 2.5 + (250 / 1000.0) * 10.0;
        assertThat(tracker.getTotalCostUsd()).isEqualTo(expected);
    }

    @Test
    void shouldBeThreadSafe() {
        CostTracker tracker = new CostTracker();
        ModelPricing pricing = new ModelPricing("gpt-4o", 1.0, 2.0);
        int threads = 8;
        int iterations = 1000;
        IntStream.range(0, threads).parallel().forEach(t ->
                IntStream.range(0, iterations).forEach(i ->
                        tracker.accumulate(new AgentChatUsage(1000, 1000, 2000), pricing)));
        double expected = threads * iterations * (1.0 + 2.0);
        assertThat(tracker.getTotalCostUsd()).isEqualTo(expected);
    }
}