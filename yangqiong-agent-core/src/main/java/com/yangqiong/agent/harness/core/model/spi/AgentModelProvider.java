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
package com.yangqiong.agent.harness.core.model.spi;

import com.yangqiong.agent.harness.core.model.AgentModel;

/**
 * 模型提供者SPI
 * @author yangqiong
 */
public interface AgentModelProvider {

    /**
     * 获取Provider标识
     * @return
     */
    String providerId();

    /**
     * 是否支持指定模型
     * @param provider
     * @param modelName
     * @return
     */
    boolean supports(String provider, String modelName);

    /**
     * 创建模型实例
     * @param provider
     * @param modelName
     * @param context
     * @return
     */
    AgentModel create(String provider, String modelName, AgentModelCreationContext context);
}
