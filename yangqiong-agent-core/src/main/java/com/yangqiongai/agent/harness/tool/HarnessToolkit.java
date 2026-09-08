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
package com.yangqiongai.agent.harness.tool;

import com.yangqiongai.agent.harness.core.tool.AgentTool;
import com.yangqiongai.agent.harness.core.tool.AgentToolkit;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 工具箱
 * @author yangqiong
 */
public class HarnessToolkit implements AgentToolkit {

    /**
     * 工具映射（name -> AgentTool），线程安全
     */
    private final Map<String, AgentTool> tools = new ConcurrentHashMap<>();

    /**
     * 工具Schema映射（name -> schema），线程安全，避免重复注册时产生冗余schema
     */
    private final Map<String, Map<String, Object>> toolSchemas = new ConcurrentHashMap<>();

    public HarnessToolkit(AgentToolkit toolkit) {
        if (toolkit != null && !toolkit.isEmpty()) {
            for (AgentTool tool : toolkit.getTools()) {
                if (tool != null) {
                    addTool(tool);
                }
            }
        }
    }

    /**
     * 添加工具
     * <p>
     * 同名工具会被覆盖，对应的schema也会替换旧的，避免重复schema问题。
     * </p>
     * @param tool
     */
    @Override
    public void addTool(AgentTool tool) {
        if (tool == null) {
            return;
        }
        tools.put(tool.getName(), tool);
        toolSchemas.put(tool.getName(), ToolSchemaBuilder.build(tool));
    }

    /**
     * 获取工具列表
     * @return
     */
    @Override
    public List<AgentTool> getTools() {
        return new ArrayList<>(tools.values());
    }

    /**
     * 工具箱是否为空
     * @return
     */
    @Override
    public boolean isEmpty() {
        return tools.isEmpty();
    }

    /**
     * 按名称查找工具
     * @param name
     * @return
     */
    public AgentTool find(String name) {
        return tools.get(name);
    }

    /**
     * 获取工具Schema列表
     * <p>
     * 返回不可变副本，防止外部修改破坏内部状态。
     * </p>
     * @return
     */
    public List<Map<String, Object>> getToolSchemas() {
        return Collections.unmodifiableList(new ArrayList<>(toolSchemas.values()));
    }

    /**
     * 获取工具名到Schema的映射视图
     * <p>
     * 返回不可变副本，供渐进加载模式按工具名裁剪schema。
     * </p>
     * @return
     */
    public Map<String, Map<String, Object>> getToolSchemasMap() {
        return Collections.unmodifiableMap(new LinkedHashMap<>(toolSchemas));
    }
}
