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

/**
 * 检查点存储
 * <p>
 * 持久执行存储三SPI之二：完整运行状态快照的权威读写口。
 * save 带乐观锁语义（版本冲突抛异常），latest 按复合键取最近检查点。
 * </p>
 * @author yangqiong
 */
public interface CheckpointStore {

    /**
     * 保存检查点，版本号必须大于当前版本（乐观锁）
     * @param checkpoint
     * @throws IllegalStateException 版本冲突或回退时
     */
    void save(AgentCheckpoint checkpoint);

    /**
     * 按复合键取最近检查点
     * @param scopeId
     * @param sessionId
     * @return
     */
    Optional<AgentCheckpoint> latest(String scopeId, String sessionId);

    /**
     * 按运行ID列出全部检查点（版本升序）
     * @param runId
     * @return
     */
    List<AgentCheckpoint> findByRunId(String runId);

    /**
     * 清理运行检查点
     * @param runId
     */
    void clear(String runId);
}
