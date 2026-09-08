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
package com.yangqiongai.agent.harness.subagent.orchestration;

import com.yangqiongai.agent.harness.core.AgentRuntime;

/**
 * Handoff目标
 * @author yangqiong
 */
public record HandoffTarget(AgentRuntime runtime, String roleDescription, HandoffContextFilter filter) {

    /**
     * 全参构造
     * @param runtime
     * @param roleDescription
     * @param filter
     */
    public HandoffTarget {
        // 未指定过滤器时使用默认过滤器
        filter = filter != null ? filter : HandoffContextFilter.defaultFilter();
    }

    /**
     * 获取目标Agent名称
     * @return
     */
    public String agentName() {
        return runtime != null ? runtime.getName() : null;
    }
}
