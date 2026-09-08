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
package com.yangqiongai.agent.harness.starter.autoconfigure;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

import com.yangqiongai.agent.harness.core.trace.TraceEmitter;
import com.yangqiongai.agent.harness.starter.config.HarnessTracingProperties;
import com.yangqiongai.agent.harness.starter.trace.LoggingTraceEmitter;

/**
 * 追踪自动装配
 * <p>
 * 启用 ai.harness.tracing 后装配日志型追踪导出器，span生命周期回调输出日志。
 * </p>
 * @author yangqiong
 */
@AutoConfiguration
@EnableConfigurationProperties(HarnessTracingProperties.class)
@ConditionalOnProperty(prefix = "ai.harness.tracing", name = "enabled", havingValue = "true")
public class AgentTracingAutoConfiguration {

    /**
     * 日志追踪导出器Bean
     * @return
     */
    @Bean
    @ConditionalOnMissingBean
    public TraceEmitter loggingTraceEmitter() {
        return new LoggingTraceEmitter();
    }
}
