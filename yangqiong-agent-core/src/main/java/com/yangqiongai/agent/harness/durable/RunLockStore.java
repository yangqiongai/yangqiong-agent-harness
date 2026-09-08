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
package com.yangqiongai.agent.harness.durable;

import java.time.Duration;
import java.util.Optional;

/**
 * 运行锁存储
 * <p>
 * 分布式运行归属控制的权威读写口：按 runId 提供带超时自动过期的锁，
 * 防止两个节点同时续跑同一运行。同节点重复加锁视为续期（幂等），
 * 仅持有者可续期/解锁，锁超时后其他节点可接管。
 * </p>
 * @author yangqiong
 */
public interface RunLockStore {

    /**
     * 尝试加锁：无锁、锁已过期或当前持有者为本节点时成功
     * @param runId
     * @param ownerNodeId
     * @param lockTtl
     * @return
     */
    boolean tryLock(String runId, String ownerNodeId, Duration lockTtl);

    /**
     * 持有者续期
     * @param runId
     * @param ownerNodeId
     * @param lockTtl
     */
    void renew(String runId, String ownerNodeId, Duration lockTtl);

    /**
     * 运行结束/释放时解锁，仅持有者可解锁
     * @param runId
     * @param ownerNodeId
     */
    void unlock(String runId, String ownerNodeId);

    /**
     * 查询当前持有者（诊断用）
     * @param runId
     * @return
     */
    Optional<String> owner(String runId);
}
