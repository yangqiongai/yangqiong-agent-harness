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
import com.yangqiongai.agent.harness.core.tool.AgentToolSpec;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 工具Schema构建器
 * @author yangqiong
 */
public final class ToolSchemaBuilder {

    private ToolSchemaBuilder() {
    }

    /**
     * 构建工具Schema（OpenAI function格式）
     * @param tool
     * @return
     */
    public static Map<String, Object> build(AgentTool tool) {
        AgentToolSpec spec = tool.getSpec();
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "function");
        Map<String, Object> function = new LinkedHashMap<>();
        function.put("name", spec.getName());
        function.put("description", spec.getDescription());
        function.put("parameters", spec.getParameters() != null ? spec.getParameters() : Collections.emptyMap());
        schema.put("function", function);
        return schema;
    }

    /**
     * 构建多个工具Schema列表
     * @param tools
     * @return
     */
    public static List<Map<String, Object>> buildAll(List<AgentTool> tools) {
        if (tools == null || tools.isEmpty()) {
            return Collections.emptyList();
        }
        List<Map<String, Object>> schemas = new ArrayList<>(tools.size());
        for (AgentTool tool : tools) {
            if (tool != null) {
                schemas.add(build(tool));
            }
        }
        return schemas;
    }
}
