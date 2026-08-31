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
package com.yangqiong.agent.harness.core.tool;

/**
 * 工具风险等级
 * @author yangqiong
 */
public enum ToolRiskLevel {

    /**
     * 低风险（只读查询类）
     */
    LOW,

    /**
     * 中风险（有限副作用，可补偿）
     */
    MEDIUM,

    /**
     * 高风险（不可逆副作用，必须审批且禁自动重试）
     */
    HIGH
}
