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
package com.yangqiong.agent.harness.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import com.yangqiong.agent.harness.config.CostBudgetPolicy.ExceedAction;

/**
 * 成本预算策略测试
 * @author yangqiong
 */
class CostBudgetPolicyTest {

    @Test
    void shouldCreateWarnOnlyPolicy() {
        CostBudgetPolicy policy = CostBudgetPolicy.warnOnly(10.0);
        assertThat(policy.getWarnUsd()).isEqualTo(10.0);
        assertThat(policy.getHardLimitUsd()).isZero();
        assertThat(policy.getExceedAction()).isEqualTo(ExceedAction.WARN);
    }

    @Test
    void shouldCreateHardLimitPolicy() {
        CostBudgetPolicy policy = CostBudgetPolicy.hardLimit(10.0, 50.0);
        assertThat(policy.getWarnUsd()).isEqualTo(10.0);
        assertThat(policy.getHardLimitUsd()).isEqualTo(50.0);
        assertThat(policy.getExceedAction()).isEqualTo(ExceedAction.ABORT);
    }

    @Test
    void shouldDetectWarnExceeded() {
        CostBudgetPolicy policy = CostBudgetPolicy.warnOnly(10.0);
        assertThat(policy.isWarnExceeded(9.0)).isFalse();
        assertThat(policy.isWarnExceeded(10.1)).isTrue();
    }

    @Test
    void shouldDetectHardExceeded() {
        CostBudgetPolicy policy = CostBudgetPolicy.hardLimit(10.0, 50.0);
        assertThat(policy.isHardExceeded(50.0)).isFalse();
        assertThat(policy.isHardExceeded(50.1)).isTrue();
    }

    @Test
    void shouldNotEnforceWhenThresholdZero() {
        CostBudgetPolicy policy = new CostBudgetPolicy(0.0, 0.0, ExceedAction.WARN);
        assertThat(policy.isWarnExceeded(100.0)).isFalse();
        assertThat(policy.isHardExceeded(100.0)).isFalse();
    }

    @Test
    void shouldDefaultToWarnActionWhenNull() {
        CostBudgetPolicy policy = new CostBudgetPolicy(10.0, 20.0, null);
        assertThat(policy.getExceedAction()).isEqualTo(ExceedAction.WARN);
    }
}