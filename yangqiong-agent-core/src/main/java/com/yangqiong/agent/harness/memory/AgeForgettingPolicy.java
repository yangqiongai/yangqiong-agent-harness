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
package com.yangqiong.agent.harness.memory;

/**
 * 按年龄遗忘策略
 * <p>
 * 记忆自创建起超过指定天数即被判定为遗忘，适用于有时间价值的短期知识。
 * </p>
 * @author yangqiong
 */
public class AgeForgettingPolicy implements ForgettingPolicy {

    /**
     * 最大存活时长（毫秒）
     */
    private final long maxAgeMillis;

    /**
     * 按最大存活天数构建遗忘策略
     * @param maxAgeDays
     */
    public AgeForgettingPolicy(int maxAgeDays) {
        if (maxAgeDays <= 0) {
            throw new IllegalArgumentException("最大存活天数必须为正数: " + maxAgeDays);
        }
        this.maxAgeMillis = maxAgeDays * 24L * 60L * 60L * 1000L;
    }

    @Override
    public boolean shouldForget(EntryInfo info) {
        long now = System.currentTimeMillis();
        return now - info.createdAt() >= maxAgeMillis;
    }
}
