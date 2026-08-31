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
package com.yangqiong.agent.harness.engine;

import java.time.Duration;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.yangqiong.agent.harness.core.event.AgentEvent;
import com.yangqiong.agent.harness.core.event.AgentEventType;
import com.yangqiong.agent.harness.core.message.AgentMessage;
import com.yangqiong.agent.harness.core.message.AgentToolResultBlock;
import com.yangqiong.agent.harness.core.message.AgentToolUseBlock;
import com.yangqiong.agent.harness.durable.AgentCheckpoint;
import com.yangqiong.agent.harness.durable.AgentRunRecord;
import com.yangqiong.agent.harness.durable.AgentRunState;
import com.yangqiong.agent.harness.durable.AgentRunStore;
import com.yangqiong.agent.harness.durable.ApprovalRecord;
import com.yangqiong.agent.harness.durable.ApprovalStore;
import com.yangqiong.agent.harness.durable.CheckpointStore;
import com.yangqiong.agent.harness.durable.RunLockStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 持久执行状态跟踪器
 * <p>
 * ReActEngine状态持久化器独立抽取：runId生命周期、运行记录状态机迁移、
 * 完整检查点落盘与审批记录落库统一在此收敛。三存储SPI任一为空即降级为空操作，
 * 持久化异常仅告警不阻断主流程。
 * </p>
 * @author yangqiong
 */
public class DurableExecutionTracker {

    private static final Logger log = LoggerFactory.getLogger(DurableExecutionTracker.class);

    /**
     * 运行锁默认过期时长：节点崩溃后锁自动释放，其他节点可接管
     */
    private static final Duration DEFAULT_LOCK_TTL = Duration.ofMinutes(10);

    /**
     * 属性键：运行ID
     */
    public static final String ATTR_RUN_ID = "harness.runId";

    /**
     * 属性键：已完成执行的工具调用ID集合
     */
    public static final String ATTR_COMPLETED_TOOL_IDS = "harness.completedToolUseIds";

    /**
     * 运行记录存储（可空）
     */
    private final AgentRunStore runStore;

    /**
     * 检查点存储（可空）
     */
    private final CheckpointStore checkpointStore;

    /**
     * 审批存储（可空）
     */
    private final ApprovalStore approvalStore;

    /**
     * 运行锁存储（可空，多节点部署注入）
     */
    private final RunLockStore runLockStore;

    /**
     * 本节点唯一标识（运行锁持有者标识）
     */
    private final String ownerNodeId;

    /**
     * 运行锁过期时长
     */
    private final Duration lockTtl;

    /**
     * runId到最近落盘检查点版本的内存索引（跨run沿用复合键最新版本，防版本回退）
     */
    private final ConcurrentHashMap<String, Long> lastCheckpointVersion = new ConcurrentHashMap<>();

    public DurableExecutionTracker(AgentRunStore runStore, CheckpointStore checkpointStore,
                                   ApprovalStore approvalStore) {
        this(runStore, checkpointStore, approvalStore, null, null, DEFAULT_LOCK_TTL);
    }

    public DurableExecutionTracker(AgentRunStore runStore, CheckpointStore checkpointStore,
                                   ApprovalStore approvalStore, RunLockStore runLockStore,
                                   String ownerNodeId) {
        this(runStore, checkpointStore, approvalStore, runLockStore, ownerNodeId, DEFAULT_LOCK_TTL);
    }

    public DurableExecutionTracker(AgentRunStore runStore, CheckpointStore checkpointStore,
                                   ApprovalStore approvalStore, RunLockStore runLockStore,
                                   String ownerNodeId, Duration lockTtl) {
        this.runStore = runStore;
        this.checkpointStore = checkpointStore;
        this.approvalStore = approvalStore;
        this.runLockStore = runLockStore;
        this.ownerNodeId = ownerNodeId;
        this.lockTtl = lockTtl != null ? lockTtl : DEFAULT_LOCK_TTL;
    }

    /**
     * 是否启用持久执行（任一存储SPI存在即启用）
     * @return
     */
    public boolean isEnabled() {
        return runStore != null || checkpointStore != null || approvalStore != null;
    }

    /**
     * 获取或生成运行ID，并将运行记录迁移到RUNNING
     * <p>
     * 新运行创建记录（CREATED→RUNNING）；恢复运行自WAITING_APPROVAL迁回RUNNING。
     * </p>
     * @param context
     * @param agentName
     * @return
     */
    public String ensureRunning(AgentRuntimeContext context, String agentName) {
        if (context == null) {
            return null;
        }
        String runId = (String) context.get(ATTR_RUN_ID);
        if (runId == null) {
            runId = UUID.randomUUID().toString();
            context.put(ATTR_RUN_ID, runId);
        }
        if (runStore != null) {
            try {
                Optional<AgentRunRecord> existing = runStore.findByRunId(runId);
                if (existing.isEmpty()) {
                    AgentRunRecord record = new AgentRunRecord(runId, context.getScopeId(),
                            context.getSessionId(), context.getUserId(), agentName);
                    runStore.create(record);
                    record.transitionTo(AgentRunState.RUNNING, "run start");
                    runStore.saveTransition(record);
                } else {
                    AgentRunRecord record = existing.get();
                    if (record.getState() == AgentRunState.WAITING_APPROVAL) {
                        record.transitionTo(AgentRunState.RUNNING, "resume");
                        runStore.saveTransition(record);
                    }
                }
            } catch (Exception e) {
                log.warn("运行记录状态迁移失败: runId={}", runId, e);
            }
        }
        tryAcquireRunLock(runId);
        return runId;
    }

    /**
     * 尝试抢运行锁，防止多节点并发续跑同一运行；抢锁失败仅告警不阻断
     * @param runId
     */
    private void tryAcquireRunLock(String runId) {
        if (runLockStore == null || ownerNodeId == null) {
            return;
        }
        try {
            if (!runLockStore.tryLock(runId, ownerNodeId, lockTtl)) {
                log.warn("运行锁抢占失败，可能由其他节点执行: runId={}, owner={}", runId, ownerNodeId);
            }
        } catch (Exception e) {
            log.warn("运行锁抢占异常: runId={}", runId, e);
        }
    }

    /**
     * 释放运行锁（仅持有者可释放）
     * @param runId
     */
    public void unlockRunLock(String runId) {
        if (runLockStore == null || ownerNodeId == null || runId == null) {
            return;
        }
        try {
            runLockStore.unlock(runId, ownerNodeId);
        } catch (Exception e) {
            log.warn("运行锁释放异常: runId={}", runId, e);
        }
    }

    /**
     * 运行锁续期（长任务超出默认TTL时由调用方周期性调用）
     * @param runId
     */
    public void renewRunLock(String runId) {
        if (runLockStore == null || ownerNodeId == null || runId == null) {
            return;
        }
        try {
            runLockStore.renew(runId, ownerNodeId, lockTtl);
        } catch (Exception e) {
            log.warn("运行锁续期异常: runId={}", runId, e);
        }
    }

    /**
     * 迁移到等待审批状态并落审批记录与检查点
     * @param context
     * @param runId
     * @param askCalls
     * @param messages
     * @param iteration
     */
    public void markWaitingApproval(AgentRuntimeContext context, String runId,
                                    List<AgentToolUseBlock> askCalls,
                                    List<AgentMessage> messages, int iteration) {
        if (runId == null) {
            return;
        }
        try {
            if (runStore != null) {
                runStore.findByRunId(runId).ifPresent(record -> {
                    if (record.getState() == AgentRunState.RUNNING) {
                        record.transitionTo(AgentRunState.WAITING_APPROVAL, "await approval");
                        runStore.saveTransition(record);
                    }
                });
            }
            if (approvalStore != null && askCalls != null) {
                for (AgentToolUseBlock call : askCalls) {
                    approvalStore.create(ApprovalRecord.pending(UUID.randomUUID().toString(),
                            runId, call.getToolUseId(), call.getToolName(),
                            context != null ? context.getScopeId() : null,
                            context != null ? context.getUserId() : null));
                }
            }
        } catch (Exception e) {
            log.warn("等待审批状态落盘失败: runId={}", runId, e);
        }
        saveCheckpoint(context, runId, iteration, messages, askCalls, completedToolIds(context));
    }

    /**
     * 审批落定（幂等，重复落定返回当前状态）
     * @param context
     * @param toolCallId
     * @param approved
     * @param reason
     */
    public void resolveApproval(AgentRuntimeContext context, String toolCallId,
                                boolean approved, String reason) {
        if (approvalStore == null || toolCallId == null) {
            return;
        }
        try {
            approvalStore.findByToolCallId(toolCallId)
                    .filter(record -> record.getState() == ApprovalRecord.ApprovalState.PENDING)
                    .ifPresent(record -> approvalStore.resolve(record.getApprovalId(), approved, reason));
        } catch (Exception e) {
            log.warn("审批落定失败: toolCallId={}", toolCallId, e);
        }
    }

    /**
     * 记录本轮已执行完成的工具调用ID（恢复场景副作用去重依据）
     * @param context
     * @param toolResults
     */
    @SuppressWarnings("unchecked")
    public void recordCompletedToolCalls(AgentRuntimeContext context, List<AgentMessage> toolResults) {
        if (context == null || toolResults == null || toolResults.isEmpty()) {
            return;
        }
        Set<String> completed = (Set<String>) context.get(ATTR_COMPLETED_TOOL_IDS);
        if (completed == null) {
            completed = ConcurrentHashMap.newKeySet();
            context.put(ATTR_COMPLETED_TOOL_IDS, completed);
        }
        for (AgentMessage msg : toolResults) {
            if (msg == null || msg.getContent() == null) {
                continue;
            }
            for (Object block : msg.getContent()) {
                if (block instanceof AgentToolResultBlock resultBlock
                        && resultBlock.getToolUseId() != null) {
                    completed.add(resultBlock.getToolUseId());
                }
            }
        }
    }

    /**
     * 保存完整运行状态检查点（消息+迭代+待执行工具+已完成工具ID，乐观锁版本递增）
     * @param context
     * @param runId
     * @param iteration
     * @param messages
     * @param pendingToolCalls
     * @param completedToolUseIds
     */
    public void saveCheckpoint(AgentRuntimeContext context, String runId, int iteration,
                               List<AgentMessage> messages, List<AgentToolUseBlock> pendingToolCalls,
                               Set<String> completedToolUseIds) {
        if (checkpointStore == null || runId == null || context == null) {
            return;
        }
        try {
            String scopeId = context.getScopeId();
            String sessionId = context.getSessionId();
            long version = nextVersion(runId, scopeId, sessionId);
            checkpointStore.save(new AgentCheckpoint(runId, scopeId, sessionId, iteration,
                    messages, pendingToolCalls,
                    completedToolUseIds != null ? List.copyOf(completedToolUseIds) : List.of(),
                    System.currentTimeMillis(), version));
        } catch (Exception e) {
            log.warn("检查点保存失败: runId={}", runId, e);
        }
    }

    /**
     * 取最近检查点
     * @param scopeId
     * @param sessionId
     * @return
     */
    public Optional<AgentCheckpoint> latestCheckpoint(String scopeId, String sessionId) {
        if (checkpointStore == null) {
            return Optional.empty();
        }
        return checkpointStore.latest(scopeId, sessionId);
    }

    /**
     * 按事件类型驱动终态迁移（AGENT_RESULT→SUCCEEDED，ERROR→FAILED，INTERRUPTED→CANCELLED）
     * @param event
     * @param runId
     */
    public void onEvent(AgentEvent event, String runId) {
        if (event == null || runId == null) {
            return;
        }
        AgentEventType type = event.getType();
        if (type != AgentEventType.AGENT_RESULT
                && type != AgentEventType.ERROR
                && type != AgentEventType.INTERRUPTED) {
            return;
        }
        // run已终态，版本索引不再需要（同会话新run自存储基线恢复），及时清理防无界增长
        lastCheckpointVersion.remove(runId);
        unlockRunLock(runId);
        if (runStore == null) {
            return;
        }
        try {
            runStore.findByRunId(runId).ifPresent(record -> {
                AgentRunState state = record.getState();
                if (state.isTerminal()) {
                    return;
                }
                String payload = event.getPayload() != null ? String.valueOf(event.getPayload()) : null;
                if (type == AgentEventType.AGENT_RESULT) {
                    record.transitionTo(AgentRunState.SUCCEEDED, null);
                } else if (type == AgentEventType.ERROR) {
                    record.transitionTo(AgentRunState.FAILED, payload);
                } else {
                    record.transitionTo(AgentRunState.CANCELLED, payload);
                }
                runStore.saveTransition(record);
            });
        } catch (Exception e) {
            log.warn("终态迁移失败: runId={}", runId, e);
        }
    }

    /**
     * 读取运行上下文中已完成工具调用ID集合
     * @param context
     * @return
     */
    @SuppressWarnings("unchecked")
    Set<String> completedToolIds(AgentRuntimeContext context) {
        if (context == null) {
            return Set.of();
        }
        Set<String> completed = (Set<String>) context.get(ATTR_COMPLETED_TOOL_IDS);
        return completed != null ? new LinkedHashSet<>(completed) : Set.of();
    }

    /**
     * 计算下一检查点版本：复合键最新检查点版本与内存索引取大者加一
     * @param runId
     * @param scopeId
     * @param sessionId
     * @return
     */
    private long nextVersion(String runId, String scopeId, String sessionId) {
        long base = lastCheckpointVersion.getOrDefault(runId, 0L);
        if (checkpointStore != null) {
            Optional<AgentCheckpoint> latest = checkpointStore.latest(scopeId, sessionId);
            if (latest.isPresent()) {
                base = Math.max(base, latest.get().getVersion());
            }
        }
        long next = base + 1;
        lastCheckpointVersion.put(runId, next);
        return next;
    }
}
