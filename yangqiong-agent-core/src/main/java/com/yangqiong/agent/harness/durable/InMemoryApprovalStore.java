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
package com.yangqiong.agent.harness.durable;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 审批存储内存实现
 * @author yangqiong
 */
public class InMemoryApprovalStore implements ApprovalStore {

    /**
     * 最大审批记录数，超限淘汰创建时间最旧的记录，防止内存无界增长
     */
    public static final int MAX_RECORDS = 1000;

    /**
     * 按审批ID索引
     */
    private final ConcurrentHashMap<String, ApprovalRecord> byApprovalId = new ConcurrentHashMap<>();

    /**
     * 按工具调用ID索引的最近审批
     */
    private final ConcurrentHashMap<String, ApprovalRecord> byToolCallId = new ConcurrentHashMap<>();

    @Override
    public ApprovalRecord create(ApprovalRecord record) {
        if (record == null || record.getApprovalId() == null) {
            throw new IllegalArgumentException("审批记录或approvalId不能为空");
        }
        byApprovalId.put(record.getApprovalId(), record);
        if (record.getToolCallId() != null) {
            byToolCallId.put(record.getToolCallId(), record);
        }
        evictOverflow(record);
        return record;
    }

    /**
     * 容量超限时淘汰创建时间最旧记录并保持双索引一致
     * @param protectedRecord 本次保存的记录，禁止作为淘汰对象
     */
    private void evictOverflow(ApprovalRecord protectedRecord) {
        if (byApprovalId.size() <= MAX_RECORDS) {
            return;
        }
        byApprovalId.values().stream()
                .filter(r -> r != protectedRecord)
                .min(java.util.Comparator.comparingLong(ApprovalRecord::getCreatedAt))
                .ifPresent(victim -> {
                    byApprovalId.remove(victim.getApprovalId(), victim);
                    if (victim.getToolCallId() != null) {
                        byToolCallId.remove(victim.getToolCallId(), victim);
                    }
                });
    }

    @Override
    public Optional<ApprovalRecord> findByApprovalId(String approvalId) {
        return Optional.ofNullable(byApprovalId.get(approvalId));
    }

    @Override
    public Optional<ApprovalRecord> findByToolCallId(String toolCallId) {
        return Optional.ofNullable(byToolCallId.get(toolCallId));
    }

    @Override
    public ApprovalRecord resolve(String approvalId, boolean approved, String reason) {
        ApprovalRecord record = byApprovalId.get(approvalId);
        if (record == null) {
            throw new IllegalStateException("审批记录不存在: " + approvalId);
        }
        if (record.getState() != ApprovalRecord.ApprovalState.PENDING) {
            return record;
        }
        ApprovalRecord resolved = record.resolve(approved, reason);
        byApprovalId.put(approvalId, resolved);
        if (resolved.getToolCallId() != null) {
            byToolCallId.put(resolved.getToolCallId(), resolved);
        }
        return resolved;
    }

    @Override
    public List<ApprovalRecord> findPending(String scopeId) {
        return byApprovalId.values().stream()
                .filter(r -> r.getState() == ApprovalRecord.ApprovalState.PENDING)
                .filter(r -> scopeId == null || scopeId.equals(r.getScopeId()))
                .toList();
    }

    @Override
    public List<ApprovalRecord> findByRunId(String runId) {
        if (runId == null) {
            return List.of();
        }
        return byApprovalId.values().stream()
                .filter(r -> runId.equals(r.getRunId()))
                .toList();
    }
}
