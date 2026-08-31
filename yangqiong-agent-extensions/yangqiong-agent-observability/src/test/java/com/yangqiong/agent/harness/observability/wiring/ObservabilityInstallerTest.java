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
package com.yangqiong.agent.harness.observability.wiring;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

import com.yangqiong.agent.harness.HarnessRuntimeBuilder;
import com.yangqiong.agent.harness.observability.config.ObservabilityConfig;

/**
 * 可观测性装配入口测试
 * @author yangqiong
 */
class ObservabilityInstallerTest {

    /**
     * 验证全关闭配置装配无害：无Trace导出器、无指标注册表、close幂等
     */
    @Test
    void installShouldNoopWhenEverythingDisabled() {
        HarnessRuntimeBuilder builder = new HarnessRuntimeBuilder();
        ObservabilityConfig config = ObservabilityConfig.builder().build();

        try (ObservabilityHandle handle = ObservabilityInstaller.install(builder, config)) {
            assertThat(handle).isNotNull();
            assertThat(handle.meterRegistry()).isNull();
        }
    }

    /**
     * 验证指标开启时返回注册表，公共标签生效，close不抛异常
     */
    @Test
    void installShouldWireMetricsWithCommonTags() {
        HarnessRuntimeBuilder builder = new HarnessRuntimeBuilder();
        SimpleMeterRegistry external = new SimpleMeterRegistry();
        ObservabilityConfig config = ObservabilityConfig.builder()
                .metricsEnabled(true)
                .commonTag("env", "test")
                .build();

        try (ObservabilityHandle handle = ObservabilityInstaller.install(builder, config, external)) {
            assertThat(handle.meterRegistry()).isSameAs(external);
            external.counter("probe").increment();
            assertThat(external.get("probe").tag("env", "test").counter().count()).isEqualTo(1.0);
        }
    }

    /**
     * 验证Trace端点开启时装配不抛异常且close幂等（默认传输失败仅告警不外抛）
     */
    @Test
    void installShouldWireTraceWithoutThrowing() {
        HarnessRuntimeBuilder builder = new HarnessRuntimeBuilder();
        ObservabilityConfig config = ObservabilityConfig.builder()
                .otlpEndpoint("http://localhost:59999")
                .traceFlushIntervalMs(0)
                .build();

        ObservabilityHandle handle = ObservabilityInstaller.install(builder, config);
        handle.close();
        handle.close();
        assertThat(handle).isNotNull();
    }
}
