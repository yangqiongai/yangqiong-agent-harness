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
package com.yangqiongai.agent.harness.core.tool;

import java.util.List;

/**
 * Agent工具箱
 * @author yangqiong
 */
public interface AgentToolkit {

    /**
     * 获取工具列表
     * @return
     */
    List<AgentTool> getTools();

    /**
     * 工具箱是否为空
     * @return
     */
    boolean isEmpty();

    /**
     * 添加工具
     * @param tool
     */
    void addTool(AgentTool tool);
}
