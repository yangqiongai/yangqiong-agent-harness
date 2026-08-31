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
package com.yangqiong.agent.harness.planmode;

import com.yangqiong.agent.harness.engine.AgentRuntimeContext;

/**
 * 计划模式管理器
 * @author yangqiong
 */
public class PlanModeManager {

    /**
     * 上下文属性键：计划模式状态
     */
    public static final String ATTR_PLAN_MODE_ACTIVE = "_planModeActive";

    /**
     * 上下文属性键：计划内容
     */
    public static final String ATTR_PLAN_CONTENT = "_planContent";

    /**
     * 进入计划模式
     * @param ctx
     */
    public void enterPlanMode(AgentRuntimeContext ctx) {
        ctx.getAttributes().put(ATTR_PLAN_MODE_ACTIVE, Boolean.TRUE);
    }

    /**
     * 退出计划模式
     * @param ctx
     */
    public void exitPlanMode(AgentRuntimeContext ctx) {
        ctx.getAttributes().put(ATTR_PLAN_MODE_ACTIVE, Boolean.FALSE);
    }

    /**
     * 写入计划内容
     * @param ctx
     * @param content
     */
    public void writePlan(AgentRuntimeContext ctx, String content) {
        ctx.getAttributes().put(ATTR_PLAN_CONTENT, content != null ? content : "");
    }

    /**
     * 是否处于计划模式
     * @param ctx
     * @return
     */
    public boolean isPlanModeActive(AgentRuntimeContext ctx) {
        Object val = ctx.getAttributes().get(ATTR_PLAN_MODE_ACTIVE);
        return val instanceof Boolean b && b;
    }

    /**
     * 获取计划内容
     * @param ctx
     * @return
     */
    public String getPlanContent(AgentRuntimeContext ctx) {
        Object val = ctx.getAttributes().get(ATTR_PLAN_CONTENT);
        return val instanceof String s ? s : "";
    }
}
