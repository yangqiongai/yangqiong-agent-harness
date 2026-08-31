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
package com.yangqiong.agent.harness.core.event;

import java.util.Objects;

/**
 * 模型调用信息
 * @author yangqiong
 */
public final class ModelCallInfo {

    /**
     * Agent名称
     */
    private final String agentName;

    /**
     * 模型名称
     */
    private final String modelName;

    /**
     * 迭代轮次
     */
    private final int iteration;

    public ModelCallInfo(String agentName, String modelName, int iteration) {
        this.agentName = agentName;
        this.modelName = modelName;
        this.iteration = iteration;
    }

    /**
     * 获取Agent名称
     * @return
     */
    public String getAgentName() {
        return agentName;
    }

    /**
     * 获取模型名称
     * @return
     */
    public String getModelName() {
        return modelName;
    }

    /**
     * 获取迭代轮次
     * @return
     */
    public int getIteration() {
        return iteration;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ModelCallInfo that = (ModelCallInfo) o;
        return iteration == that.iteration
                && Objects.equals(agentName, that.agentName)
                && Objects.equals(modelName, that.modelName);
    }

    @Override
    public int hashCode() {
        return Objects.hash(agentName, modelName, iteration);
    }

    @Override
    public String toString() {
        return "ModelCallInfo{agentName=" + agentName + ", modelName=" + modelName
                + ", iteration=" + iteration + "}";
    }
}
