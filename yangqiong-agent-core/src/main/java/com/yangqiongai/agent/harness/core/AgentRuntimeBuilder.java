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
package com.yangqiongai.agent.harness.core;

import com.yangqiongai.agent.harness.core.middleware.AgentMiddleware;
import com.yangqiongai.agent.harness.core.model.AgentGenerateOptions;
import com.yangqiongai.agent.harness.core.model.AgentModel;
import com.yangqiongai.agent.harness.core.tool.AgentToolkit;

/**
 * Agent运行时构建器（基础）
 * <p>
 * 所有运行时实现必须支持的核心配置项。
 * 高级配置见 {@link AdvancedAgentRuntimeBuilder}，
 * Harness特性见 {@link HarnessAgentRuntimeBuilder}。
 * </p>
 * @author yangqiong
 */
public interface AgentRuntimeBuilder {

    /**
     * 设置Agent名称
     * @param name
     * @return
     */
    AgentRuntimeBuilder name(String name);

    /**
     * 设置模型
     * @param model
     * @return
     */
    AgentRuntimeBuilder model(AgentModel model);

    /**
     * 设置系统提示词
     * @param systemPrompt
     * @return
     */
    AgentRuntimeBuilder systemPrompt(String systemPrompt);

    /**
     * 设置最大迭代次数
     * @param maxIters
     * @return
     */
    AgentRuntimeBuilder maxIters(int maxIters);

    /**
     * 设置工具箱
     * @param toolkit
     * @return
     */
    AgentRuntimeBuilder toolkit(AgentToolkit toolkit);

    /**
     * 设置中间件
     * @param middleware
     * @return
     */
    AgentRuntimeBuilder middleware(AgentMiddleware middleware);

    /**
     * 设置模型生成选项
     * @param options
     * @return
     */
    AgentRuntimeBuilder generateOptions(AgentGenerateOptions options);

    /**
     * 构建Agent运行时
     * @return
     */
    AgentRuntime build();
}
