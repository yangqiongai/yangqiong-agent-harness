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
package com.yangqiong.agent.harness.paradigms.support;

import java.util.Map;

/**
 * 计划步骤
 * @author yangqiong
 */
public class PlanStep {

    /**
     * 工具步类型标识
     */
    public static final String TYPE_TOOL = "tool";

    /**
     * 推理步类型标识
     */
    public static final String TYPE_REASON = "reason";

    /**
     * 步骤序号，从1开始按计划顺序递增
     */
    private final int stepId;

    /**
     * 步骤类型，取值为 tool 或 reason
     */
    private final String type;

    /**
     * 工具名称，仅工具步有值
     */
    private final String toolName;

    /**
     * 工具调用参数，仅工具步有值
     */
    private final Map<String, Object> arguments;

    /**
     * 步骤描述
     */
    private final String description;

    /**
     * 私有构造，统一走静态工厂
     * @param stepId
     * @param type
     * @param toolName
     * @param arguments
     * @param description
     */
    private PlanStep(int stepId, String type, String toolName, Map<String, Object> arguments, String description) {
        this.stepId = stepId;
        this.type = type;
        this.toolName = toolName;
        this.arguments = arguments;
        this.description = description;
    }

    /**
     * 构建工具步
     * @param stepId
     * @param toolName
     * @param arguments
     * @param description
     * @return
     */
    public static PlanStep ofTool(int stepId, String toolName, Map<String, Object> arguments, String description) {
        return new PlanStep(stepId, TYPE_TOOL, toolName, arguments, description);
    }

    /**
     * 构建推理步
     * @param stepId
     * @param description
     * @return
     */
    public static PlanStep ofReason(int stepId, String description) {
        return new PlanStep(stepId, TYPE_REASON, null, null, description);
    }

    /**
     * 获取步骤序号
     * @return
     */
    public int getStepId() {
        return stepId;
    }

    /**
     * 获取步骤类型
     * @return
     */
    public String getType() {
        return type;
    }

    /**
     * 获取工具名称
     * @return
     */
    public String getToolName() {
        return toolName;
    }

    /**
     * 获取工具调用参数
     * @return
     */
    public Map<String, Object> getArguments() {
        return arguments;
    }

    /**
     * 获取步骤描述
     * @return
     */
    public String getDescription() {
        return description;
    }
}
