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
package com.yangqiong.agent.harness;

import com.yangqiong.agent.harness.core.AdvancedAgentRuntimeBuilder;
import com.yangqiong.agent.harness.core.AgentRuntimeFactory;
import com.yangqiong.agent.harness.core.model.AgentModelFactory;
import com.yangqiong.agent.harness.subagent.orchestration.SubagentSpecGenerator;

/**
 * Agent运行时工厂
 * @author yangqiong
 */
public class HarnessRuntimeFactory implements AgentRuntimeFactory {

    /**
     * 模型工厂（可选，用于子代理按modelCode获取模型）
     */
    private AgentModelFactory modelFactory;

    /**
     * 自动编排全局开关
     */
    private boolean autoOrchestrationEnabled = true;

    /**
     * 最大嵌套深度
     */
    private int subagentMaxDepth = 3;

    /**
     * 是否允许子代理递归编排
     */
    private boolean allowRecursiveOrchestration = true;

    /**
     * 子代理声明生成器（可选，用于LLM动态任务分解）
     */
    private SubagentSpecGenerator specGenerator;

    /**
     * LLM动态生成声明的最大子代理数
     */
    private int maxSubagents = 5;

    /**
     * 声明生成使用的模型编码（可空）
     */
    private String generationModelCode;

    /**
     * 设置模型工厂
     * @param modelFactory
     */
    public void setModelFactory(AgentModelFactory modelFactory) {
        this.modelFactory = modelFactory;
    }

    /**
     * 设置自动编排开关
     * @param autoOrchestrationEnabled
     */
    public void setAutoOrchestrationEnabled(boolean autoOrchestrationEnabled) {
        this.autoOrchestrationEnabled = autoOrchestrationEnabled;
    }

    /**
     * 设置最大嵌套深度
     * @param subagentMaxDepth
     */
    public void setSubagentMaxDepth(int subagentMaxDepth) {
        this.subagentMaxDepth = subagentMaxDepth;
    }

    /**
     * 设置是否允许递归编排
     * @param allowRecursiveOrchestration
     */
    public void setAllowRecursiveOrchestration(boolean allowRecursiveOrchestration) {
        this.allowRecursiveOrchestration = allowRecursiveOrchestration;
    }

    /**
     * 设置子代理声明生成器
     * @param specGenerator
     */
    public void setSpecGenerator(SubagentSpecGenerator specGenerator) {
        this.specGenerator = specGenerator;
    }

    /**
     * 设置最大子代理数
     * @param maxSubagents
     */
    public void setMaxSubagents(int maxSubagents) {
        this.maxSubagents = maxSubagents;
    }

    /**
     * 设置声明生成模型编码
     * @param generationModelCode
     */
    public void setGenerationModelCode(String generationModelCode) {
        this.generationModelCode = generationModelCode;
    }

    /**
     * 创建运行时构建器
     * @return
     */
    @Override
    public AdvancedAgentRuntimeBuilder createBuilder() {
        HarnessRuntimeBuilder builder = new HarnessRuntimeBuilder();
        if (modelFactory != null) {
            builder.modelFactory(modelFactory);
        }
        builder.runtimeFactory(this);
        builder.autoOrchestrationEnabled(autoOrchestrationEnabled);
        builder.setSubagentMaxDepth(subagentMaxDepth);
        builder.setAllowRecursiveOrchestration(allowRecursiveOrchestration);
        if (specGenerator != null) {
            builder.setSpecGenerator(specGenerator);
        }
        builder.setMaxSubagents(maxSubagents);
        if (generationModelCode != null && !generationModelCode.isBlank()) {
            builder.setGenerationModelCode(generationModelCode);
        }
        return builder;
    }
}
