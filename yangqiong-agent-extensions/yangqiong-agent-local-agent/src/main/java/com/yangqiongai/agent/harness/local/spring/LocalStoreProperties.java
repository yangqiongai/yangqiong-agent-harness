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

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 本地存储配置
 * <p>
 * 前缀 yangqiong.agent.local，rootDir 指定SQLite库根目录，enabled 控制是否装配本地存储。
 * </p>
 * @author yangqiong
 */
@ConfigurationProperties(prefix = "yangqiong.agent.local")
public class LocalStoreProperties {

    /**
     * 本地存储根目录，空串时使用默认目录（user.home/.yangqiong-agent）
     */
    private String rootDir = "";

    /**
     * 是否启用本地存储装配，默认关闭
     */
    private boolean enabled = false;

    public String getRootDir() {
        return rootDir;
    }

    public void setRootDir(String rootDir) {
        this.rootDir = rootDir;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }
}
