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
package com.yangqiong.agent.harness.spi;

import java.util.List;

import com.yangqiong.agent.harness.core.model.AgentModel;

/**
 * 默认模型插件提供者
 * <p>
 * 通过JDK SPI发现，{@code HarnessRuntimeBuilder.autoDiscover()} 启用后，
 * 在未显式配置AgentModel时可自动采用提供的模型作为整机默认模型。
 * </p>
 * <p>
 * 注意与{@code com.yangqiong.agent.harness.core.model.spi.AgentModelProvider}区分：
 * 本接口提供<b>现成的默认模型实例</b>（整机兜底，无模型ID路由语义）；
 * {@code AgentModelProvider}是<b>按"provider:modelName"模型ID路由创建模型</b>的工厂，
 * 注册进{@code AgentModelRegistry}，两者职责不同、互相独立。
 * </p>
 * @author yangqiong
 */
public interface DefaultModelProvider {

    /**
     * 提供可用的默认模型
     * @return
     */
    List<AgentModel> provideModels();

    /**
     * 排序权重（越小越靠前），默认0
     * @return
     */
    default int order() {
        return 0;
    }
}
