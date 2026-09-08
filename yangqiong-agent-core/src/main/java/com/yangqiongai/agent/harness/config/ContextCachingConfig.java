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
package com.yangqiongai.agent.harness.config;

/**
 * 上下文缓存配置
 * @author yangqiong
 */
public class ContextCachingConfig {

    /**
     * 是否启用上下文缓存
     */
    private final boolean enabled;

    /**
     * 是否缓存系统提示词
     */
    private final boolean cacheSystemPrompt;

    /**
     * 是否缓存工具定义
     */
    private final boolean cacheTools;

    public ContextCachingConfig() {
        this(false, false, false);
    }

    public ContextCachingConfig(boolean enabled, boolean cacheSystemPrompt, boolean cacheTools) {
        this.enabled = enabled;
        this.cacheSystemPrompt = cacheSystemPrompt;
        this.cacheTools = cacheTools;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public boolean isCacheSystemPrompt() {
        return cacheSystemPrompt;
    }

    public boolean isCacheTools() {
        return cacheTools;
    }
}