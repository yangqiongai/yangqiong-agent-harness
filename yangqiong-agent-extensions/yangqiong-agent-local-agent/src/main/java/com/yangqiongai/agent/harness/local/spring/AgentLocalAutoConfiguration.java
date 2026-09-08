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
package com.yangqiongai.agent.harness.local.spring;

import java.nio.file.Path;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

import com.yangqiongai.agent.harness.local.store.LocalDistributedStores;

/**
 * 本地存储自动装配
 * <p>
 * 按 yangqiong.agent.local.enabled 开关装配SQLite本地存储，rootDir 为空时使用默认目录。
 * 使用方注入自定义{@link LocalDistributedStores}Bean后本装配自动失效，容器销毁时自动关闭连接。
 * </p>
 * @author yangqiong
 */
@AutoConfiguration
@EnableConfigurationProperties(LocalStoreProperties.class)
@ConditionalOnProperty(prefix = "yangqiong.agent.local", name = "enabled", havingValue = "true")
public class AgentLocalAutoConfiguration {

    /**
     * SQLite本地存储Bean，随容器销毁自动释放数据库连接
     * @param properties
     * @return
     */
    @Bean(destroyMethod = "close")
    @ConditionalOnMissingBean
    public LocalDistributedStores localDistributedStores(LocalStoreProperties properties) {
        String rootDir = properties.getRootDir();
        if (rootDir == null || rootDir.isBlank()) {
            return LocalDistributedStores.create();
        }
        return LocalDistributedStores.create(Path.of(rootDir));
    }
}
