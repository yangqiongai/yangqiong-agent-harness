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
package com.yangqiong.agent.harness.server;

import com.yangqiong.agent.harness.core.event.ConfirmResult;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 审批决策映射测试
 * @author yangqiong
 */
class ConfirmDecisionTest {

    @Test
    void toCorePreservesFields() {
        ConfirmDecision decision = new ConfirmDecision("call-1", "local_shell", true, "已确认执行");

        ConfirmResult core = decision.toCore();

        assertThat(core.getToolCallId()).isEqualTo("call-1");
        assertThat(core.getToolName()).isEqualTo("local_shell");
        assertThat(core.isApproved()).isTrue();
        assertThat(core.getReason()).isEqualTo("已确认执行");
    }

    @Test
    void denyDecisionMapsApprovedFlag() {
        ConfirmDecision decision = new ConfirmDecision("call-2", "local_shell", false, "拒绝该命令");

        assertThat(decision.toCore().isApproved()).isFalse();
    }
}