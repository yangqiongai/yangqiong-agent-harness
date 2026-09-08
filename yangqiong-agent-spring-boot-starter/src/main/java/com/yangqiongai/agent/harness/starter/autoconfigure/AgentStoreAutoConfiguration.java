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

import com.yangqiongai.agent.harness.durable.DistributedStores;
import com.yangqiongai.agent.harness.durable.MemoryDistributedStores;
import com.yangqiongai.agent.harness.starter.config.HarnessStoreProperties;

/**
 * 共享存储自动装配
 * <p>
 * 按 ai.harness.store.type 装配存储聚合，默认 memory 使用内存实现。
 * jdbc/redis 由扩展模块提供，使用方注入自定义{@link DistributedStores}Bean后本装配自动失效。
 * </p>
 * @author yangqiong
 */
@AutoConfiguration
@EnableConfigurationProperties(HarnessStoreProperties.class)
@ConditionalOnProperty(prefix = "ai.harness.store", name = "type", havingValue = "memory", matchIfMissing = true)
public class AgentStoreAutoConfiguration {

    /**
     * 内存存储聚合Bean
     * @return
     */
    @Bean
    @ConditionalOnMissingBean
    public DistributedStores memoryDistributedStores() {
        return new MemoryDistributedStores();
    }
}
