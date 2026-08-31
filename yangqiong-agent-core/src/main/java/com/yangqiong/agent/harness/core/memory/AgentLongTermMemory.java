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
package com.yangqiong.agent.harness.core.memory;

import java.util.List;
import java.util.Map;

/**
 * Agent长期记忆
 * @author yangqiong
 */
public interface AgentLongTermMemory {

    /**
     * 存储记忆
     * @param userId
     * @param sessionId
     * @param content
     * @param metadata
     * @return
     */
    void store(String userId, String sessionId, String content, Map<String, Object> metadata);

    /**
     * 检索记忆
     * @param userId
     * @param query
     * @param limit
     * @return
     */
    List<String> search(String userId, String query, int limit);

    /**
     * 删除记忆
     * @param memoryId
     * @return
     */
    void delete(String memoryId);

    /**
     * 按租户作用域存储记忆（复合键隔离，推荐实现覆写）
     * @param scopeId
     * @param userId
     * @param sessionId
     * @param content
     * @param metadata
     */
    default void store(String scopeId, String userId, String sessionId,
                       String content, Map<String, Object> metadata) {
        store(userId, sessionId, content, metadata);
    }

    /**
     * 按租户作用域检索记忆（复合键隔离，推荐实现覆写）
     * @param scopeId
     * @param userId
     * @param query
     * @param limit
     * @return
     */
    default List<String> search(String scopeId, String userId, String query, int limit) {
        return search(userId, query, limit);
    }

    /**
     * 按租户作用域删除记忆，所有权校验失败抛出SecurityException
     * @param scopeId
     * @param memoryId
     */
    default void delete(String scopeId, String memoryId) {
        delete(memoryId);
    }
}
