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
package com.yangqiongai.agent.harness.starter.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 追踪配置
 * <p>
 * 前缀 ai.harness.tracing，启用后装配日志型{@link com.yangqiongai.agent.harness.core.trace.TraceEmitter}。
 * </p>
 * @author yangqiong
 */
@ConfigurationProperties(prefix = "ai.harness.tracing")
public class HarnessTracingProperties {

    /**
     * 是否启用追踪
     */
    private boolean enabled;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }
}
