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

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

import com.yangqiongai.agent.harness.HarnessRuntimeBuilder;
import com.yangqiongai.agent.harness.HarnessRuntimeFactory;
import com.yangqiongai.agent.harness.core.AgentRuntime;
import com.yangqiongai.agent.harness.core.model.AgentModel;
import com.yangqiongai.agent.harness.core.model.AgentModelFactory;
import com.yangqiongai.agent.harness.core.trace.TraceEmitter;
import com.yangqiongai.agent.harness.durable.DistributedStores;
import com.yangqiongai.agent.harness.durable.MemoryDistributedStores;
import com.yangqiongai.agent.harness.starter.config.HarnessRuntimeProperties;

/**
 * Agent运行时自动装配
 * <p>
 * 基于 ai.harness.runtime 配置构建{@link HarnessRuntimeBuilder}与{@link AgentRuntime}Bean，
 * 注入模型、共享存储、追踪等组件，模型缺失时自动跳过。
 * </p>
 * @author yangqiong
 */
@AutoConfiguration
@AutoConfigureAfter({AgentModelAutoConfiguration.class, AgentStoreAutoConfiguration.class,
        AgentTracingAutoConfiguration.class})
@EnableConfigurationProperties(HarnessRuntimeProperties.class)
@ConditionalOnProperty(prefix = "ai.harness.runtime", name = "enabled", havingValue = "true", matchIfMissing = true)
@ConditionalOnBean(AgentModel.class)
public class AgentRuntimeAutoConfiguration {

    /**
     * 运行时构建器Bean，供外部进一步定制后手动构建
     * @param properties
     * @param model
     * @param modelFactoryProvider
     * @param storesProvider
     * @param traceEmitterProvider
     * @return
     */
    @Bean
    @ConditionalOnMissingBean
    public HarnessRuntimeBuilder harnessRuntimeBuilder(HarnessRuntimeProperties properties,
                                                       AgentModel model,
                                                       ObjectProvider<AgentModelFactory> modelFactoryProvider,
                                                       ObjectProvider<DistributedStores> storesProvider,
                                                       ObjectProvider<TraceEmitter> traceEmitterProvider) {
        HarnessRuntimeFactory runtimeFactory = new HarnessRuntimeFactory();
        modelFactoryProvider.ifAvailable(runtimeFactory::setModelFactory);
        HarnessRuntimeBuilder builder = (HarnessRuntimeBuilder) runtimeFactory.createBuilder();
        builder.name(properties.getName())
                .model(model)
                .systemPrompt(properties.getSystemPrompt())
                .maxIters(properties.getMaxIters())
                .webEnabled(properties.isWebEnabled())
                .askUserEnabled(properties.isAskUserEnabled())
                .planModeEnabled(properties.isPlanModeEnabled());
        if (properties.isSubagentsEnabled()) {
            builder.enableSubagents();
        }
        DistributedStores stores = storesProvider.getIfAvailable(MemoryDistributedStores::new);
        builder.agentRunStore(stores.runStore())
                .checkpointStore(stores.checkpointStore())
                .approvalStore(stores.approvalStore())
                .sessionMemory(stores.sessionMemory())
                .longTermMemory(stores.longTermMemory())
                .toolExecutionStore(stores.toolExecutionStore())
                .runLockStore(stores.runLockStore());
        traceEmitterProvider.ifAvailable(builder::traceEmitter);
        return builder;
    }

    /**
     * Agent运行时Bean
     * @param builder
     * @return
     */
    @Bean
    @ConditionalOnMissingBean
    public AgentRuntime agentRuntime(HarnessRuntimeBuilder builder) {
        return builder.build();
    }
}
