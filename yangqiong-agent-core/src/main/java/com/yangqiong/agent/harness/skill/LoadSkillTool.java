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
 * 加载技能工具
 * <p>
 * Level 2 渐进加载：LLM 通过调用此工具按名称加载技能的完整内容，
 * 避免在系统提示词中一次性注入所有技能详情。
 * </p>
 * @author yangqiong
 */
public class LoadSkillTool implements AgentTool {

    /**
     * 工具名称
     */
    private static final String TOOL_NAME = "load_skill";

    /**
     * 工具描述
     */
    private static final String TOOL_DESCRIPTION = "按名称加载技能的完整内容。"
            + "当需要从可用技能列表中获取详细指令时使用此工具。";

    /**
     * 技能管理器
     */
    private final SkillManager skillManager;

    public LoadSkillTool(SkillManager skillManager) {
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
        skillNameProp.put("description", "要加载的技能名称。");
        properties.put("skill_name", skillNameProp);
        schema.put("properties", properties);
        List<String> required = List.of("skill_name");
        schema.put("required", required);
        return schema;
    }

    /**
     * 异步加载技能完整内容
     * @param param 工具调用参数，需包含 skill_name
     * @return 技能内容文本块，未找到时返回错误结果
     */
    @Override
    public Mono<AgentToolResultBlock> callAsync(AgentToolCallParam param) {
        if (param == null || param.getInput() == null) {
            return Mono.just(AgentToolResultBlock.error("参数为空"));
        }
        Object nameObj = param.getInput().get("skill_name");
        if (nameObj == null || nameObj.toString().isBlank()) {
            return Mono.just(AgentToolResultBlock.error("skill_name 参数缺失"));
        }
        String skillName = nameObj.toString();
        AgentSkill skill = skillManager.findSkill(skillName);
        if (skill == null) {
            return Mono.just(AgentToolResultBlock.error("技能未找到: " + skillName));
        }
        String content = skill.getSkillContent();
        if (content == null || content.isBlank()) {
            return Mono.just(AgentToolResultBlock.error("技能内容为空: " + skillName));
        }
        AgentTextBlock textBlock = AgentTextBlock.builder().text(content).build();
        return Mono.just(AgentToolResultBlock.of(List.of(textBlock)));
    }
}
