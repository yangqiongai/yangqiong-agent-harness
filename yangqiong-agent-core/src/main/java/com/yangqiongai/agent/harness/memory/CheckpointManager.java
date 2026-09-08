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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import com.yangqiongai.agent.harness.core.message.AgentMessage;

/**
 * 检查点管理器
 * <p>
 * 在每轮ReAct迭代后快照对话历史与迭代状态，支持崩溃后从最近检查点恢复执行。
 * 检查点按sessionId隔离，内存存储，可通过继承或替换实现持久化。
 * 超过maxCheckpoints时按LRU策略淘汰最旧检查点。
 * </p>
 * @author yangqiong
 */
public class CheckpointManager {

    /**
     * 按sessionId隔离的检查点
     */
    private final Map<String, Checkpoint> checkpoints = new ConcurrentHashMap<>();

    /**
     * 最大保留检查点数量（按sessionId）
     */
    private final int maxCheckpoints;

    /**
     * 访问序号生成器（用于LRU淘汰）
     */
    private final AtomicLong accessSequence = new AtomicLong(0);

    public CheckpointManager() {
        this(5);
    }

    public CheckpointManager(int maxCheckpoints) {
        this.maxCheckpoints = maxCheckpoints > 0 ? maxCheckpoints : 5;
    }

    /**
     * 保存检查点
     * <p>
     * 当检查点数量超过maxCheckpoints时，按LRU策略淘汰最久未访问的检查点。
     * </p>
     * @param sessionId
     * @param messages 当前对话历史快照
     * @param iteration 当前迭代轮次
     */
    public synchronized void save(String sessionId, List<AgentMessage> messages, int iteration) {
        if (sessionId == null) {
            return;
        }
        Checkpoint checkpoint = new Checkpoint(
                new ArrayList<>(messages != null ? messages : List.of()),
                iteration,
                System.currentTimeMillis(),
                accessSequence.incrementAndGet());
        checkpoints.put(sessionId, checkpoint);
        // 超过容量上限时按LRU淘汰最旧检查点
        while (checkpoints.size() > maxCheckpoints) {
            evictOldest();
        }
    }

    /**
     * 淘汰访问序号最小的检查点（LRU策略）
     */
    private void evictOldest() {
        String oldestKey = null;
        long oldestSeq = Long.MAX_VALUE;
        for (Map.Entry<String, Checkpoint> entry : checkpoints.entrySet()) {
            if (entry.getValue().getAccessSeq() < oldestSeq) {
                oldestSeq = entry.getValue().getAccessSeq();
                oldestKey = entry.getKey();
            }
        }
        if (oldestKey != null) {
            checkpoints.remove(oldestKey);
        }
    }

    /**
     * 获取检查点
     * @param sessionId
     * @return
     */
    public Checkpoint get(String sessionId) {
        Checkpoint checkpoint = checkpoints.get(sessionId);
        if (checkpoint != null) {
            // 更新访问序号（LRU）
            checkpoint.setAccessSeq(accessSequence.incrementAndGet());
        }
        return checkpoint;
    }

    /**
     * 从检查点恢复对话历史
     * @param sessionId
     * @return 检查点中的消息列表，无检查点时返回空列表
     */
    public List<AgentMessage> restore(String sessionId) {
        Checkpoint checkpoint = get(sessionId);
        return checkpoint != null ? new ArrayList<>(checkpoint.getMessages()) : new ArrayList<>();
    }

    /**
     * 从检查点恢复迭代轮次
     * @param sessionId
     * @return 检查点中的迭代轮次，无检查点时返回0
     */
    public int restoreIteration(String sessionId) {
        Checkpoint checkpoint = get(sessionId);
        return checkpoint != null ? checkpoint.getIteration() : 0;
    }

    /**
     * 清理会话检查点
     * @param sessionId
     */
    public void clear(String sessionId) {
        checkpoints.remove(sessionId);
    }

    /**
     * 是否存在检查点
     * @param sessionId
     * @return
     */
    public boolean hasCheckpoint(String sessionId) {
        return sessionId != null && checkpoints.containsKey(sessionId);
    }

    /**
     * 检查点快照
     * @author yangqiong
     */
    public static class Checkpoint {

        /**
         * 对话历史快照
         */
        private final List<AgentMessage> messages;

        /**
         * 迭代轮次
         */
        private final int iteration;

        /**
         * 快照时间戳
         */
        private final long timestamp;

        /**
         * 访问序号（用于LRU淘汰）
         */
        private volatile long accessSeq;

        public Checkpoint(List<AgentMessage> messages, int iteration, long timestamp) {
            this(messages, iteration, timestamp, 0);
        }

        public Checkpoint(List<AgentMessage> messages, int iteration, long timestamp, long accessSeq) {
            this.messages = messages;
            this.iteration = iteration;
            this.timestamp = timestamp;
            this.accessSeq = accessSeq;
        }

        public List<AgentMessage> getMessages() {
            return messages;
        }

        public int getIteration() {
            return iteration;
        }

        public long getTimestamp() {
            return timestamp;
        }

        public long getAccessSeq() {
            return accessSeq;
        }

        public void setAccessSeq(long accessSeq) {
            this.accessSeq = accessSeq;
        }
    }
}
