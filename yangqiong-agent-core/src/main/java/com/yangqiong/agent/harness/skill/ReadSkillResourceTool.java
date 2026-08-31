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
package com.yangqiong.agent.harness.skill;

import com.yangqiong.agent.harness.core.message.AgentTextBlock;
import com.yangqiong.agent.harness.core.message.AgentToolResultBlock;
import com.yangqiong.agent.harness.core.skill.AgentSkill;
import com.yangqiong.agent.harness.core.tool.AgentTool;
import com.yangqiong.agent.harness.core.tool.AgentToolCallParam;
import reactor.core.publisher.Mono;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 读取技能资源工具
 * <p>
 * Level 3 渐进加载：LLM 通过调用此工具读取技能绑定的资源文件内容，
 * 例如技能附带的脚本、模板、配置等。
 * </p>
 * @author yangqiong
 */
public class ReadSkillResourceTool implements AgentTool {

    /**
     * 工具名称
     */
    private static final String TOOL_NAME = "read_skill_resource";

    /**
     * 工具描述
     */
    private static final String TOOL_DESCRIPTION = "按技能名称和资源路径读取技能中的资源文件。"
            + "用于访问技能附带的脚本、模板或配置。";

    /**
     * 技能管理器
     */
    private final SkillManager skillManager;

    public ReadSkillResourceTool(SkillManager skillManager) {
        this.skillManager = skillManager;
    }

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
        return TOOL_DESCRIPTION;
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
        Map<String, Object> skillNameProp = new LinkedHashMap<>();
        skillNameProp.put("type", "string");
        skillNameProp.put("description", "技能名称。");
        properties.put("skill_name", skillNameProp);
        Map<String, Object> resourcePathProp = new LinkedHashMap<>();
        resourcePathProp.put("type", "string");
        resourcePathProp.put("description", "技能内的资源路径。");
        properties.put("resource_path", resourcePathProp);
        schema.put("properties", properties);
        List<String> required = List.of("skill_name", "resource_path");
        schema.put("required", required);
        return schema;
    }

    /**
     * 异步读取技能资源内容
     * @param param 工具调用参数，需包含 skill_name 和 resource_path
     * @return 资源内容文本块，未找到时返回错误结果
     */
    @Override
    public Mono<AgentToolResultBlock> callAsync(AgentToolCallParam param) {
        if (param == null || param.getInput() == null) {
            return Mono.just(AgentToolResultBlock.error("参数为空"));
        }
        Object nameObj = param.getInput().get("skill_name");
        Object pathObj = param.getInput().get("resource_path");
        if (nameObj == null || nameObj.toString().isBlank()) {
            return Mono.just(AgentToolResultBlock.error("skill_name 参数缺失"));
        }
        if (pathObj == null || pathObj.toString().isBlank()) {
            return Mono.just(AgentToolResultBlock.error("resource_path 参数缺失"));
        }
        String skillName = nameObj.toString();
        String resourcePath = pathObj.toString();
        AgentSkill skill = skillManager.findSkill(skillName);
        if (skill == null) {
            return Mono.just(AgentToolResultBlock.error("技能未找到: " + skillName));
        }
        Map<String, String> resources = skill.getResources();
        if (resources == null || resources.isEmpty()) {
            return Mono.just(AgentToolResultBlock.error("技能无资源: " + skillName));
        }
        String content = resources.get(resourcePath);
        if (content == null) {
            return Mono.just(AgentToolResultBlock.error(
                    "资源未找到: " + resourcePath + " (技能: " + skillName + ")"));
        }
        AgentTextBlock textBlock = AgentTextBlock.builder().text(content).build();
        return Mono.just(AgentToolResultBlock.of(List.of(textBlock)));
    }
}
