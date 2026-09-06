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
package com.yangqiong.agent.harness.tool;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;

/**
 * 工具渐进加载状态测试
 * @author yangqiong
 */
class ToolLoadingStateTest {

    @Test
    void shouldDefaultToNonProgressiveWithEmptyAlwaysOn() {
        ToolLoadingState state = new ToolLoadingState(false, null);
        assertThat(state.isProgressive()).isFalse();
        assertThat(state.getAlwaysOnTools()).isEmpty();
    }

    @Test
    void shouldKeepAlwaysOnToolsImmutable() {
        ToolLoadingState state = new ToolLoadingState(true, Set.of("a", "b"));
        assertThat(state.getAlwaysOnTools()).containsExactlyInAnyOrder("a", "b");
        assertThat(state.isAlwaysOn("a")).isTrue();
        assertThat(state.isAlwaysOn("c")).isFalse();
    }

    @Test
    void shouldActivateToolIdempotently() {
        ToolLoadingState state = new ToolLoadingState(true, Set.of());
        assertThat(state.activate("x")).isTrue();
        assertThat(state.activate("x")).isFalse();
        assertThat(state.isActivated("x")).isTrue();
    }

    @Test
    void shouldNotActivateNullName() {
        ToolLoadingState state = new ToolLoadingState(true, Set.of());
        assertThat(state.activate(null)).isFalse();
        assertThat(state.isActivated(null)).isFalse();
        assertThat(state.isAlwaysOn(null)).isFalse();
    }

    @Test
    void shouldTreatMetaToolsAsSchemaVisible() {
        ToolLoadingState state = new ToolLoadingState(true, Set.of());
        for (String metaTool : ToolLoadingState.META_TOOL_NAMES) {
            assertThat(state.isSchemaVisible(metaTool)).isTrue();
        }
        assertThat(state.isSchemaVisible("deferred_tool")).isFalse();
    }

    @Test
    void shouldReportSchemaVisibleForAlwaysOnAndActivated() {
        ToolLoadingState state = new ToolLoadingState(true, Set.of("resident"));
        assertThat(state.isSchemaVisible("resident")).isTrue();
        state.activate("loaded");
        assertThat(state.isSchemaVisible("loaded")).isTrue();
        assertThat(state.isSchemaVisible("deferred")).isFalse();
    }

    @Test
    void shouldSupportConcurrentActivation() throws InterruptedException {
        ToolLoadingState state = new ToolLoadingState(true, Set.of());
        int threads = 16;
        int loops = 100;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch latch = new CountDownLatch(threads);
        for (int i = 0; i < threads; i++) {
            pool.submit(() -> {
                try {
                    for (int j = 0; j < loops; j++) {
                        state.activate("tool_" + (j % 20));
                    }
                } finally {
                    latch.countDown();
                }
            });
        }
        assertThat(latch.await(10, TimeUnit.SECONDS)).isTrue();
        pool.shutdownNow();
        assertThat(state.isProgressive()).isTrue();
    }
}
