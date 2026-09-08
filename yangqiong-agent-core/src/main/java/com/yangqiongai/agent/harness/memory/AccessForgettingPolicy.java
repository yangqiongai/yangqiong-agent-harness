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
package com.yangqiongai.agent.harness.memory;

/**
 * 按空闲时间遗忘策略
 * <p>
 * 记忆自最近一次访问起超过指定天数未再被访问即被判定为遗忘，
 * 适用于频繁变动、长期未用即失效的知识。
 * </p>
 * @author yangqiong
 */
public class AccessForgettingPolicy implements ForgettingPolicy {

    /**
     * 最大空闲时长（毫秒）
     */
    private final long maxIdleMillis;

    /**
     * 按最大空闲天数构建遗忘策略
     * @param maxIdleDays
     */
    public AccessForgettingPolicy(int maxIdleDays) {
        if (maxIdleDays <= 0) {
            throw new IllegalArgumentException("最大空闲天数必须为正数: " + maxIdleDays);
        }
        this.maxIdleMillis = maxIdleDays * 24L * 60L * 60L * 1000L;
    }

    @Override
    public boolean shouldForget(EntryInfo info) {
        long now = System.currentTimeMillis();
        return now - info.lastAccessedAt() >= maxIdleMillis;
    }
}
