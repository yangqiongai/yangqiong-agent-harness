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
package com.yangqiong.agent.harness.store.redis;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import com.yangqiong.agent.harness.durable.ApprovalRecord;
import com.yangqiong.agent.harness.durable.ApprovalRecord.ApprovalState;
import com.yangqiong.agent.harness.durable.ApprovalStore;
import redis.clients.jedis.JedisPooled;

/**
 * 审批存储Redis实现
 * <p>
 * 审批记录按 approvalId 全局唯一存储，另建工具调用ID索引，
 * resolve 重复落定返回当前状态保证幂等。
 * </p>
 * @author yangqiong
 */
public class RedisApprovalStore implements ApprovalStore {

    private static final String RECORD_KEY = "harness:approval:%s";
    private static final String TOOL_INDEX = "harness:approval:tool:%s";
    private static final String SCOPE_SET = "harness:%s:approvals";

    /**
     * Redis客户端（线程安全）
     */
    private final JedisPooled jedis;

    /**
     * 构建审批存储
     * @param jedis
     */
    public RedisApprovalStore(JedisPooled jedis) {
        this.jedis = jedis;
    }

    @Override
    public ApprovalRecord create(ApprovalRecord record) {
        if (record == null || record.getApprovalId() == null) {
            throw new IllegalArgumentException("审批记录或approvalId不能为空");
        }
        jedis.set(recordKey(record.getApprovalId()), StoreJson.toApproval(record));
        if (record.getToolCallId() != null) {
            jedis.set(toolIndex(record.getToolCallId()), record.getApprovalId());
        }
        jedis.zadd(scopeSet(record.getScopeId()), record.getCreatedAt(), record.getApprovalId());
        return record;
    }

    @Override
    public Optional<ApprovalRecord> findByApprovalId(String approvalId) {
        String json = jedis.get(recordKey(approvalId));
        return json == null ? Optional.empty() : Optional.of(StoreJson.fromApproval(json));
    }

    @Override
    public Optional<ApprovalRecord> findByToolCallId(String toolCallId) {
        String approvalId = jedis.get(toolIndex(toolCallId));
        return approvalId == null ? Optional.empty() : findByApprovalId(approvalId);
    }

    @Override
    public ApprovalRecord resolve(String approvalId, boolean approved, String reason) {
        ApprovalRecord record = findByApprovalId(approvalId)
                .orElseThrow(() -> new IllegalStateException("审批记录不存在: " + approvalId));
        if (record.getState() != ApprovalState.PENDING) {
            return record;
        }
        ApprovalRecord resolved = record.resolve(approved, reason);
        jedis.set(recordKey(approvalId), StoreJson.toApproval(resolved));
        return resolved;
    }

    @Override
    public List<ApprovalRecord> findPending(String scopeId) {
        return listScope(scopeId).stream()
                .filter(r -> r.getState() == ApprovalState.PENDING)
                .filter(r -> scopeId == null || scopeId.equals(r.getScopeId()))
                .toList();
    }

    @Override
    public List<ApprovalRecord> findByRunId(String runId) {
        if (runId == null) {
            return List.of();
        }
        return listScope(null).stream()
                .filter(r -> runId.equals(r.getRunId()))
                .toList();
    }

    /**
     * 列出全部审批记录（findByRunId 需跨作用域扫描，先枚举所有作用域有序集合）
     * @param scopeId
     * @return
     */
    private List<ApprovalRecord> listScope(String scopeId) {
        if (scopeId == null) {
            Set<String> scopes = jedis.keys("harness:*:approvals");
            return scopes.stream()
                    .flatMap(key -> jedis.zrange(key, 0, -1).stream())
                    .map(this::findByApprovalId)
                    .flatMap(Optional::stream)
                    .toList();
        }
        List<String> ids = jedis.zrange(scopeSet(scopeId), 0, -1);
        return ids.stream()
                .map(this::findByApprovalId)
                .flatMap(Optional::stream)
                .toList();
    }

    private static String recordKey(String approvalId) {
        return String.format(RECORD_KEY, approvalId);
    }

    private static String toolIndex(String toolCallId) {
        return String.format(TOOL_INDEX, toolCallId);
    }

    private static String scopeSet(String scopeId) {
        return String.format(SCOPE_SET, scope(scopeId));
    }

    /**
     * 归一化作用域
     * @param scopeId
     * @return
     */
    private static String scope(String scopeId) {
        return scopeId != null ? scopeId : "";
    }
}