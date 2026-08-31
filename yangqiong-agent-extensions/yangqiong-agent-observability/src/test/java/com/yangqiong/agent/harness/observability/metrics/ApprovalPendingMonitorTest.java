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
package com.yangqiong.agent.harness.observability.metrics;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

import com.yangqiong.agent.harness.durable.ApprovalRecord;
import com.yangqiong.agent.harness.durable.ApprovalStore;

/**
 * 跨进程审批等待监控测试
 * <p>
 * 以内存假实现驱动ApprovalStore，验证pending计数、最大/平均等待秒数gauge与手动刷新。
 * </p>
 * @author yangqiong
 */
class ApprovalPendingMonitorTest {

    /**
     * 测试注册表
     */
    private SimpleMeterRegistry registry;

    /**
     * 被测监控
     */
    private ApprovalPendingMonitor monitor;

    /**
     * 释放资源
     */
    @AfterEach
    void tearDown() {
        if (monitor != null) {
            monitor.close();
        }
    }

    /**
     * 验证单scope多记录的gauge产出
     */
    @Test
    void refreshShouldUpdatePendingCountAndWaits() {
        registry = new SimpleMeterRegistry();
        long now = System.currentTimeMillis();
        ApprovalRecord waited10s = ApprovalRecord.pending("a1", "run1", "t1", "file_write", "default", null);
        ApprovalRecord waited1s = ApprovalRecord.pending("a2", "run1", "t2", "shell", "default", null);
        List<ApprovalRecord> pending = List.of(
                shiftCreatedAt(waited10s, now - 10_000L),
                shiftCreatedAt(waited1s, now - 1_000L));
        monitor = new ApprovalPendingMonitor(fakeStore(pending), registry, List.of("default"), 0);

        monitor.refresh();

        assertThat(registry.get("agent.approval.pending.count").tag("scope", "default").gauge().value())
                .isEqualTo(2.0);
        assertThat(registry.get("agent.approval.pending.wait.seconds.max").tag("scope", "default")
                .gauge().value()).isBetween(9.0, 11.0);
        assertThat(registry.get("agent.approval.pending.wait.seconds.avg").tag("scope", "default")
                .gauge().value()).isBetween(4.0, 6.0);
    }

    /**
     * 验证多scope独立产出与空scope归零
     */
    @Test
    void refreshShouldTrackScopesIndependently() {
        registry = new SimpleMeterRegistry();
        long now = System.currentTimeMillis();
        ApprovalRecord pendingRecord = shiftCreatedAt(
                ApprovalRecord.pending("a1", "run1", "t1", "file_write", "tenantA", null), now - 30_000L);
        monitor = new ApprovalPendingMonitor(fakeStore(List.of(pendingRecord)), registry,
                List.of("tenantA", "tenantB"), 0);

        monitor.refresh();

        assertThat(registry.get("agent.approval.pending.count").tag("scope", "tenantA").gauge().value())
                .isEqualTo(1.0);
        assertThat(registry.get("agent.approval.pending.count").tag("scope", "tenantB").gauge().value())
                .isZero();
        assertThat(registry.get("agent.approval.pending.wait.seconds.max").tag("scope", "tenantA")
                .gauge().value()).isBetween(29.0, 31.0);
    }

    /**
     * 验证存储抛异常时gauge归零且不外抛
     */
    @Test
    void refreshShouldTolerateStoreFailure() {
        registry = new SimpleMeterRegistry();
        ApprovalStore failingStore = new ApprovalStore() {
            @Override
            public ApprovalRecord create(ApprovalRecord record) {
                throw new UnsupportedOperationException();
            }

            @Override
            public Optional<ApprovalRecord> findByApprovalId(String approvalId) {
                throw new UnsupportedOperationException();
            }

            @Override
            public Optional<ApprovalRecord> findByToolCallId(String toolCallId) {
                throw new UnsupportedOperationException();
            }

            @Override
            public ApprovalRecord resolve(String approvalId, boolean approved, String reason) {
                throw new UnsupportedOperationException();
            }

            @Override
            public List<ApprovalRecord> findPending(String scopeId) {
                throw new IllegalStateException("db down");
            }

            @Override
            public List<ApprovalRecord> findByRunId(String runId) {
                throw new UnsupportedOperationException();
            }
        };
        monitor = new ApprovalPendingMonitor(failingStore, registry, List.of("default"), 0);

        monitor.refresh();

        assertThat(registry.get("agent.approval.pending.count").tag("scope", "default").gauge().value())
                .isZero();
    }

    /**
     * 构造内存假审批存储（按scopeId过滤待审批记录）
     * @param pending
     * @return
     */
    private static ApprovalStore fakeStore(List<ApprovalRecord> pending) {
        return new ApprovalStore() {
            @Override
            public ApprovalRecord create(ApprovalRecord record) {
                throw new UnsupportedOperationException();
            }

            @Override
            public Optional<ApprovalRecord> findByApprovalId(String approvalId) {
                throw new UnsupportedOperationException();
            }

            @Override
            public Optional<ApprovalRecord> findByToolCallId(String toolCallId) {
                throw new UnsupportedOperationException();
            }

            @Override
            public ApprovalRecord resolve(String approvalId, boolean approved, String reason) {
                throw new UnsupportedOperationException();
            }

            @Override
            public List<ApprovalRecord> findPending(String scopeId) {
                return pending.stream()
                        .filter(record -> scopeId.equals(record.getScopeId()))
                        .toList();
            }

            @Override
            public List<ApprovalRecord> findByRunId(String runId) {
                throw new UnsupportedOperationException();
            }
        };
    }

    /**
     * 派生指定createdAt的记录（工厂方法固定取当前时间，需位移测试）
     * @param record
     * @param createdAt
     * @return
     */
    private static ApprovalRecord shiftCreatedAt(ApprovalRecord record, long createdAt) {
        return new ApprovalRecord(record.getApprovalId(), record.getRunId(), record.getToolCallId(),
                record.getToolName(), record.getScopeId(), record.getApproverId(), record.getState(),
                record.getReason(), createdAt);
    }
}
