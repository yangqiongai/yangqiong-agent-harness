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
package com.yangqiong.agent.harness.config;

/**
 * Agent审批模式
 * <p>
 * 统一审批配置入口的四类模式，覆盖既有权限能力的适配与新增AI自动审批：
 * MANUAL为人工审批（破坏性工具暂停等待人工确认）、AUTO为AI自动审批（由审批模型
 * 依据工具调用入参判定放行或拒绝，判定失败可回退人工）、FULL_ACCESS为完全访问
 * （跳过全部审批门控）、CUSTOM为自定义（沿用权限模式、规则、名单与策略门的既有细粒度配置）。
 * </p>
 * @author yangqiong
 */
public enum AgentApprovalMode {

    /**
     * 人工审批：破坏性工具调用暂停并发出待确认事件，由调用方通过resume注入审批结果
     */
    MANUAL,

    /**
     * AI自动审批：由审批模型依据工具名与调用入参判定放行或拒绝，无需人工介入
     */
    AUTO,

    /**
     * 完全访问：跳过全部权限与审批门控，所有工具直接放行
     */
    FULL_ACCESS,

    /**
     * 自定义：沿用permissionState规则、白/黑名单、requireApproval与toolPolicyGate既有细粒度配置
     */
    CUSTOM
}
