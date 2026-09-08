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
package com.yangqiongai.agent.harness.subagent.orchestration;

import com.yangqiongai.agent.harness.core.message.AgentToolResultBlock;
import com.yangqiongai.agent.harness.core.tool.AgentTool;
import com.yangqiongai.agent.harness.core.tool.AgentToolCallParam;
import reactor.core.publisher.Mono;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 向用户提问工具
 * <p>
 * 模型在任务中途缺少关键信息时，通过 ask_user 工具主动向用户提问。
 * 结果块带澄清标记（不走普通结果路径），引擎据此暂停循环并发出
 * RequireUserClarificationEvent 等待用户注入答案。
 * </p>
 * @author yangqiong
 */
public class AskUserTool implements AgentTool {

    /**
     * 工具名称
     */
    private static final String TOOL_NAME = "ask_user";

    /**
     * 获取工具名称
     * @return
     */
    @Override
    public String getName() {
        return TOOL_NAME;
    }

    /**
     * 获取工具描述
     * @return
     */
    @Override
    public String getDescription() {
        return "在任务执行中途向用户提出澄清问题。仅当任务关键信息缺失、无法继续时使用，引擎会暂停等待用户答复。";
    }

    /**
     * 获取工具参数定义
     * @return
     */
    @Override
    public Map<String, Object> getParameters() {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");

        Map<String, Object> properties = new LinkedHashMap<>();

        Map<String, Object> questionProp = new LinkedHashMap<>();
        questionProp.put("type", "string");
        questionProp.put("description", "向用户提出的澄清问题");
        properties.put("question", questionProp);

        schema.put("properties", properties);
        schema.put("required", List.of("question"));
        return schema;
    }

    /**
     * 执行提问，返回带澄清标记的结果块
     * @param param
     * @return
     */
    @Override
    public Mono<AgentToolResultBlock> callAsync(AgentToolCallParam param) {
        Map<String, Object> input = param != null ? param.getInput() : null;
        if (input == null || input.isEmpty()) {
            return Mono.just(AgentToolResultBlock.error("工具参数为空"));
        }
        Object questionObj = input.get("question");
        if (questionObj == null || questionObj.toString().isBlank()) {
            return Mono.just(AgentToolResultBlock.error("question 参数缺失"));
        }
        return Mono.just(AgentToolResultBlock.clarification(questionObj.toString()));
    }
}