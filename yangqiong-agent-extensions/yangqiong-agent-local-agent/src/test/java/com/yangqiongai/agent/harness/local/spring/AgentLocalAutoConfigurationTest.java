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

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;

import com.yangqiongai.agent.harness.local.store.LocalDistributedStores;

/**
 * 本地存储自动装配测试
 * @author yangqiong
 */
class AgentLocalAutoConfigurationTest {

    /**
     * 临时存储根目录
     */
    @TempDir
    Path tempDir;

    /**
     * enabled为true且rootDir非空时装配本地存储Bean并在根目录生成库文件
     * @return
     */
    @Test
    void shouldCreateStoresBeanWhenEnabled() {
        AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
        try {
            context.setEnvironment(buildEnvironment(Map.of(
                    "yangqiong.agent.local.enabled", "true",
                    "yangqiong.agent.local.rootDir", tempDir.toString()
            )));
            context.register(AgentLocalAutoConfiguration.class);
            context.refresh();

            assertThat(context.getBean(LocalDistributedStores.class)).isNotNull();
            assertThat(tempDir.resolve("harness.db")).exists();
        } finally {
            context.close();
        }
    }

    /**
     * enabled未设置时条件不成立，容器不装配本地存储Bean
     * @return
     */
    @Test
    void shouldNotCreateStoresBeanWhenEnabledMissing() {
        AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
        try {
            context.setEnvironment(buildEnvironment(Map.of(
                    "yangqiong.agent.local.rootDir", tempDir.toString()
            )));
            context.register(AgentLocalAutoConfiguration.class);
            context.refresh();

            assertThat(context.getBeansOfType(LocalDistributedStores.class)).isEmpty();
            assertThat(tempDir.resolve("harness.db")).doesNotExist();
        } finally {
            context.close();
        }
    }

    /**
     * 构建带测试属性的环境
     * @param properties
     * @return
     */
    private StandardEnvironment buildEnvironment(Map<String, Object> properties) {
        StandardEnvironment environment = new StandardEnvironment();
        environment.getPropertySources().addFirst(new MapPropertySource("localStoreTest", properties));
        return environment;
    }
}
