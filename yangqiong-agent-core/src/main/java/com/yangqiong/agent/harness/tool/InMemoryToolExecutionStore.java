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

import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.yangqiong.agent.harness.core.message.AgentToolResultBlock;

/**
 * 内存工具执行记录存储
 * <p>
 * 单进程兜底实现：幂等键到已完成结果的索引，超限淘汰既有条目防内存无界增长。
 * </p>
 * @author yangqiong
 */
public class InMemoryToolExecutionStore implements ToolExecutionStore {

    /**
     * 最大幂等条目数，超限淘汰既有条目防内存无界增长（默认场景零增长，仅业务工具opt-in后生效）
     */
    private static final int MAX_ENTRIES = 10000;

    /**
     * 幂等键到已完成结果的索引
     */
    private final Map<String, AgentToolResultBlock> completedByKey = new ConcurrentHashMap<>();

    @Override
    public void record(String idempotencyKey, AgentToolResultBlock result) {
        if (idempotencyKey != null && result != null) {
            if (completedByKey.size() >= MAX_ENTRIES && !completedByKey.containsKey(idempotencyKey)) {
                Iterator<String> it = completedByKey.keySet().iterator();
                if (it.hasNext()) {
                    it.next();
                    it.remove();
                }
            }
            completedByKey.putIfAbsent(idempotencyKey, result);
        }
    }

    @Override
    public boolean isCompleted(String idempotencyKey) {
        return idempotencyKey != null && completedByKey.containsKey(idempotencyKey);
    }

    @Override
    public AgentToolResultBlock getResult(String idempotencyKey) {
        return idempotencyKey != null ? completedByKey.get(idempotencyKey) : null;
    }
}
