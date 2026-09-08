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
package com.yangqiongai.agent.harness.core.event;

/**
 * Agent事件类型
 * @author yangqiong
 */
public enum AgentEventType {

    TEXT_BLOCK_DELTA,

    THINKING_BLOCK_DELTA,

    TOOL_CALL_DELTA,

    AGENT_START,

    AGENT_END,

    MODEL_CALL_START,

    MODEL_CALL_END,

    TOOL_CALL_START,

    TOOL_CALL_END,

    AGENT_RESULT,

    ENGINE_ROUTED,

    REQUIRE_USER_CONFIRM,

    REQUIRE_USER_CLARIFICATION,

    INTERRUPTED,

    COMPLETED,

    TOKEN_BUDGET_WARN,

    TOKEN_BUDGET_EXCEEDED,

    COST_BUDGET_WARN,

    COST_BUDGET_EXCEEDED,

    ERROR
}
