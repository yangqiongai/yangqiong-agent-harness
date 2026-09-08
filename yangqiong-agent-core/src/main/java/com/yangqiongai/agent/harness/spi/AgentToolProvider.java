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
package com.yangqiongai.agent.harness.spi;

import java.util.List;

import com.yangqiongai.agent.harness.core.tool.AgentTool;

/**
 * 工具插件提供者
 * <p>
 * 通过JDK SPI发现，{@code HarnessRuntimeBuilder.autoDiscover()} 启用后自动注册提供的工具。
 * </p>
 * @author yangqiong
 */
public interface AgentToolProvider {

    /**
     * 提供需要自动注册的工具
     * @return
     */
    List<AgentTool> provideTools();

    /**
     * 排序权重（越小越靠前），默认0
     * @return
     */
    default int order() {
        return 0;
    }
}