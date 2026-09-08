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
package com.yangqiongai.agent.harness.spi;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.yangqiongai.agent.harness.HarnessRuntimeBuilder;
import com.yangqiongai.agent.harness.core.AgentRuntime;
import org.junit.jupiter.api.Test;

/**
 * JDK SPI插件发现机制测试
 * @author yangqiong
 */
class HarnessDiscoveryTest {

    @Test
    void discoverShouldAggregateAllProviderTypes() {
        DiscoveredExtensions ext = HarnessDiscovery.discover();

        assertThat(ext.middlewares()).isNotEmpty();
        assertThat(ext.tools()).extracting(t -> t.getName()).contains("spi_test_tool");
        assertThat(ext.models()).isNotEmpty();
        assertThat(ext.stores()).isNotEmpty();
        assertThat(ext.detectors()).isNotEmpty();
    }

    @Test
    void builderAutoDiscoverShouldApplyDiscoveredModelAndStore() {
        AgentRuntime runtime = new HarnessRuntimeBuilder()
                .name("spi-agent")
                .autoDiscover()
                .build();

        assertThat(runtime).isNotNull();
        assertThat(runtime.getName()).isEqualTo("spi-agent");
    }

    @Test
    void builderWithoutAutoDiscoverShouldFailWhenNoModelConfigured() {
        assertThatThrownBy(() -> new HarnessRuntimeBuilder().name("no-model").build())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("AgentModel");
    }
}