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
package com.yangqiong.agent.harness.tool;

import com.yangqiong.agent.harness.core.message.AgentToolResultBlock;

/**
 * 工具执行记录存储
 * <p>
 * 以幂等键去重工具调用：同键首次结果落地后，重复调用直接复用，
 * 用于恢复场景副作用零重复执行。外部可替换为共享存储实现实现跨节点去重。
 * </p>
 * @author yangqiong
 */
public interface ToolExecutionStore {

    /**
     * 记录幂等键已完成
     * @param idempotencyKey
     * @param result
     */
    void record(String idempotencyKey, AgentToolResultBlock result);

    /**
     * 查询幂等键是否已完成
     * @param idempotencyKey
     * @return
     */
    boolean isCompleted(String idempotencyKey);

    /**
     * 取幂等键的既有结果
     * @param idempotencyKey
     * @return
     */
    AgentToolResultBlock getResult(String idempotencyKey);
}
