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
package com.yangqiongai.agent.harness.server;

import com.yangqiongai.agent.harness.core.event.ConfirmResult;

/**
 * 审批决策
 * @author yangqiong
 * @param toolCallId 工具调用ID，精确匹配键，可为空
 * @param toolName 工具名称
 * @param approved 是否批准
 * @param reason 审批理由
 */
public record ConfirmDecision(String toolCallId, String toolName, boolean approved, String reason) {

    /**
     * 转换为内核确认结果
     * @return
     */
    public ConfirmResult toCore() {
        return new ConfirmResult(toolCallId, toolName, approved, reason);
    }
}