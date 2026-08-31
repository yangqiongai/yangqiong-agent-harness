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
package com.yangqiong.agent.harness.subagent.orchestration;

import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * 编排计划
 * @author yangqiong
 */
@Data
@Builder
public class OrchestrationPlan {

    /**
     * 编排策略
     */
    private OrchestrationStrategy strategy;

    /**
     * 原始任务描述
     */
    private String task;

    /**
     * 子代理名称列表（按执行顺序）
     */
    private List<String> subagentNames;

    /**
     * 子代理输入列表（与subagentNames一一对应，SEQUENTIAL模式下前一个的输出作为后一个的输入）
     */
    private List<String> subagentInputs;

    /**
     * 编排状态
     */
    @Builder.Default
    private OrchestrationStatus status = OrchestrationStatus.PENDING;

    /**
     * 编排状态枚举
     */
    public enum OrchestrationStatus {
        PENDING,
        RUNNING,
        COMPLETED,
        FAILED
    }
}
