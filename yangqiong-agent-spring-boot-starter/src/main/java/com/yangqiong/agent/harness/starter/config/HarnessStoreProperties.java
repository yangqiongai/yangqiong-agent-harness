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
package com.yangqiong.agent.harness.starter.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 共享存储配置
 * <p>
 * 前缀 ai.harness.store，type 支持 memory/jdbc/redis。
 * jdbc/redis 由扩展模块提供，本starter仅内置memory实现。
 * </p>
 * @author yangqiong
 */
@ConfigurationProperties(prefix = "ai.harness.store")
public class HarnessStoreProperties {

    /**
     * 存储类型：memory | jdbc | redis
     */
    private String type = "memory";

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }
}
