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
package com.yangqiong.agent.harness.store.jdbc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.yangqiong.agent.harness.durable.ApprovalRecord;
import com.yangqiong.agent.harness.durable.ApprovalStore;
import com.yangqiong.agent.harness.store.jdbc.mapper.ApprovalMapper;

/**
 * 审批存储JDBC实现测试
 * @author yangqiong
 */
class JdbcApprovalStoreTest {

    /**
     * 审批存储
     */
    private ApprovalStore store;

    @BeforeEach
    void setUp() {
        store = new JdbcApprovalStore(JdbcTestSupport.newFactory().getMapper(ApprovalMapper.class));
    }

    /**
     * 创建并查询审批记录
     */
    @Test
    void createAndFind() {
        ApprovalRecord record = ApprovalRecord.pending("appr1", "run1", "tool1", "search", "scope1", "user1");
        store.create(record);

        ApprovalRecord byId = store.findByApprovalId("appr1").orElseThrow();
        assertThat(byId.getState()).isEqualTo(ApprovalRecord.ApprovalState.PENDING);
        assertThat(byId.getRunId()).isEqualTo("run1");
        assertThat(byId.getToolCallId()).isEqualTo("tool1");

        ApprovalRecord byTool = store.findByToolCallId("tool1").orElseThrow();
        assertThat(byTool.getApprovalId()).isEqualTo("appr1");
    }

    /**
     * 审批落定成功
     */
    @Test
    void resolveApproved() {
        store.create(ApprovalRecord.pending("appr1", "run1", "tool1", "search", "scope1", "user1"));

        ApprovalRecord resolved = store.resolve("appr1", true, "同意执行");

        assertThat(resolved.getState()).isEqualTo(ApprovalRecord.ApprovalState.APPROVED);
        assertThat(resolved.getReason()).isEqualTo("同意执行");
        ApprovalRecord found = store.findByApprovalId("appr1").orElseThrow();
        assertThat(found.getState()).isEqualTo(ApprovalRecord.ApprovalState.APPROVED);
    }

    /**
     * 重复落定幂等返回当前状态
     */
    @Test
    void resolveIdempotent() {
        store.create(ApprovalRecord.pending("appr1", "run1", "tool1", "search", "scope1", "user1"));
        store.resolve("appr1", true, "同意执行");

        ApprovalRecord again = store.resolve("appr1", false, "改判拒绝");

        assertThat(again.getState()).isEqualTo(ApprovalRecord.ApprovalState.APPROVED);
        assertThat(again.getReason()).isEqualTo("同意执行");
    }

    /**
     * 审批不存在的记录抛异常
     */
    @Test
    void resolveMissingThrows() {
        assertThatThrownBy(() -> store.resolve("not-exist", true, "同意"))
                .isInstanceOf(IllegalStateException.class);
    }

    /**
     * 查询待审批与按运行ID查询
     */
    @Test
    void findPendingAndByRunId() {
        store.create(ApprovalRecord.pending("appr1", "run1", "tool1", "search", "scope1", "user1"));
        store.create(ApprovalRecord.pending("appr2", "run1", "tool2", "search", "scope1", "user1"));
        store.create(ApprovalRecord.pending("appr3", "run2", "tool3", "search", "scope2", "user1"));
        store.resolve("appr2", true, "同意");

        assertThat(store.findPending("scope1")).hasSize(1);
        assertThat(store.findPending("scope1").get(0).getApprovalId()).isEqualTo("appr1");
        assertThat(store.findPending(null)).hasSize(2);
        assertThat(store.findByRunId("run1")).hasSize(2);
        assertThat(store.findByRunId("not-exist")).isEmpty();
    }

    /**
     * 容量边界：同一运行多个审批记录互不影响
     */
    @Test
    void multipleRecordsPerRun() {
        for (int i = 0; i < 10; i++) {
            store.create(ApprovalRecord.pending("appr-" + i, "run1", "tool-" + i, "search", "scope1", "user1"));
        }
        assertThat(store.findByRunId("run1")).hasSize(10);
        assertThat(store.findPending("scope1")).hasSize(10);

        store.resolve("appr-0", true, "同意");
        assertThat(store.findPending("scope1")).hasSize(9);
    }
}