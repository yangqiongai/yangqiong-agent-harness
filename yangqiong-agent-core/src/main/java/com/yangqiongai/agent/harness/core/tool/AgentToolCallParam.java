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
package com.yangqiongai.agent.harness.core.tool;

import java.util.Collections;
import java.util.Map;

/**
 * Agent工具调用参数
 * <p>
 * 携带租户与用户标识供工具做作用域隔离（如长期记忆复合桶），
 * 未提供时为null，工具按无作用域处理。
 * </p>
 * @author yangqiong
 */
public final class AgentToolCallParam {

    /**
     * 工具调用输入参数
     */
    private final Map<String, Object> input;

    /**
     * 租户标识（可空）
     */
    private final String scopeId;

    /**
     * 用户ID（可空）
     */
    private final String userId;

    public AgentToolCallParam(Map<String, Object> input) {
        this(input, null, null);
    }

    /**
     * 全参构造
     * @param input
     * @param scopeId
     * @param userId
     */
    public AgentToolCallParam(Map<String, Object> input, String scopeId, String userId) {
        this.input = input != null ? Map.copyOf(input) : Collections.emptyMap();
        this.scopeId = scopeId;
        this.userId = userId;
    }

    /**
     * 获取工具调用输入参数
     * @return
     */
    public Map<String, Object> getInput() {
        return input;
    }

    /**
     * 获取租户标识
     * @return
     */
    public String getScopeId() {
        return scopeId;
    }

    /**
     * 获取用户ID
     * @return
     */
    public String getUserId() {
        return userId;
    }
}
