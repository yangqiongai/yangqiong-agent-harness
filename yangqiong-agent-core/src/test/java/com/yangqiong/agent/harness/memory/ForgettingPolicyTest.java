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

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * 记忆遗忘策略测试
 * @author yangqiong
 */
class ForgettingPolicyTest {

    /**
     * 一天的毫秒数
     */
    private static final long DAY_MILLIS = 24L * 60L * 60L * 1000L;

    @Test
    void agePolicyForgetsOldEntries() {
        long now = System.currentTimeMillis();
        ForgettingPolicy policy = new AgeForgettingPolicy(7);
        ForgettingPolicy.EntryInfo oldEntry =
                new ForgettingPolicy.EntryInfo("1", "内容", now - 10 * DAY_MILLIS, now, 0);
        ForgettingPolicy.EntryInfo recentEntry =
                new ForgettingPolicy.EntryInfo("2", "内容", now - DAY_MILLIS, now, 0);

        assertThat(policy.shouldForget(oldEntry)).isTrue();
        assertThat(policy.shouldForget(recentEntry)).isFalse();
    }

    @Test
    void idlePolicyForgetsUnusedEntries() {
        long now = System.currentTimeMillis();
        ForgettingPolicy policy = new AccessForgettingPolicy(3);
        ForgettingPolicy.EntryInfo idleEntry =
                new ForgettingPolicy.EntryInfo("1", "内容", now - 10 * DAY_MILLIS, now - 5 * DAY_MILLIS, 1);
        ForgettingPolicy.EntryInfo activeEntry =
                new ForgettingPolicy.EntryInfo("2", "内容", now - 10 * DAY_MILLIS, now - DAY_MILLIS, 1);

        assertThat(policy.shouldForget(idleEntry)).isTrue();
        assertThat(policy.shouldForget(activeEntry)).isFalse();
    }
}
