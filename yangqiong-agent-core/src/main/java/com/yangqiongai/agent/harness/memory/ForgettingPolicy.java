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

/**
 * 记忆遗忘策略
 * <p>
 * 判定记忆条目是否应被遗忘，配合向量长期记忆在存储时清理过期条目。
 * </p>
 * @author yangqiong
 */
public interface ForgettingPolicy {

    /**
     * 记忆条目信息快照
     * @param memoryId
     * @param content
     * @param createdAt
     * @param lastAccessedAt
     * @param accessCount
     */
    record EntryInfo(String memoryId, String content, long createdAt,
                     long lastAccessedAt, int accessCount) {
    }

    /**
     * 判定记忆条目是否应被遗忘
     * @param info
     * @return
     */
    boolean shouldForget(EntryInfo info);
}
