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

/**
 * 租户作用域复合键
 * <p>
 * 统一存储键为 (scopeId, sessionId) 复合键，scopeId 为租户标识代名词
 * （ai-platform-tenant 之外一律使用 scopeId）。
 * 任意一段为空时退化为另一段，保证与既有单键语义兼容。
 * </p>
 * @author yangqiong
 */
public final class ScopeKeys {

    private ScopeKeys() {
    }

    /**
     * 构造scopeId与sessionId的复合存储键
     * @param scopeId
     * @param sessionId
     * @return
     */
    public static String key(String scopeId, String sessionId) {
        String scope = scopeId != null ? scopeId : "";
        String session = sessionId != null ? sessionId : "";
        return scope + "::" + session;
    }

    /**
     * 构造scopeId与userId的记忆桶键
     * @param scopeId
     * @param userId
     * @return
     */
    public static String memoryBucket(String scopeId, String userId) {
        String scope = scopeId != null ? scopeId : "";
        String user = userId != null ? userId : "";
        return scope + "::" + user;
    }
}
