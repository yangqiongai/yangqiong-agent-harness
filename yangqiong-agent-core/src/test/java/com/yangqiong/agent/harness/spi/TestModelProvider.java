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
 * SPI模型提供者测试实现
 * @author yangqiong
 */
public class TestModelProvider implements DefaultModelProvider {

    @Override
    public List<AgentModel> provideModels() {
        return List.of(new TestAgentModel());
    }
}