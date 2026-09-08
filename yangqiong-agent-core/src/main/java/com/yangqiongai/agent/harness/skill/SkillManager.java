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
package com.yangqiongai.agent.harness.skill;

import com.yangqiongai.agent.harness.core.skill.AgentSkill;
import com.yangqiongai.agent.harness.core.skill.AgentSkillBox;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 技能管理器
 * <p>
 * 持有 {@link AgentSkillBox}，提供技能查询与摘要生成能力，
 * 供 SkillPromptInjector / LoadSkillTool / ReadSkillResourceTool 共享使用。
 * </p>
 * @author yangqiong
 */
public class SkillManager {

    /**
     * 技能箱
     */
    private final AgentSkillBox skillBox;

    /**
     * 按名称索引的技能映射
     */
    private final Map<String, AgentSkill> skillIndex;

    public SkillManager(AgentSkillBox skillBox) {
        this.skillBox = skillBox;
        this.skillIndex = buildIndex(skillBox);
    }

    /**
     * 技能列表是否为空
     * @return
     */
    public boolean isEmpty() {
        return skillBox == null || skillBox.isEmpty();
    }

    /**
     * 按名称查找技能
     * @param name 技能名称
     * @return 技能实例，未找到时返回 null
     */
    public AgentSkill findSkill(String name) {
        if (name == null || name.isBlank()) {
            return null;
        }
        return skillIndex.get(name);
    }

    /**
     * 获取所有技能列表
     * @return 不可变技能列表
     */
    public List<AgentSkill> getSkills() {
        if (skillBox == null) {
            return Collections.emptyList();
        }
        return skillBox.getSkills();
    }

    /**
     * 构建技能摘要（Level 1: Advertise）
     * <p>
     * 优先使用技能YAML中的description字段，无description时
     * 从技能内容中提取首句作为摘要，控制在约 100 tokens/skill，
     * 供系统提示词注入使用。
     * </p>
     * @return 技能摘要文本，无技能时返回空字符串
     */
    public String buildSummaries() {
        if (isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder("可用技能:\n");
        for (AgentSkill skill : getSkills()) {
            sb.append("- ").append(skill.getName());
            String summary = getSummary(skill);
            if (!summary.isEmpty()) {
                sb.append(": ").append(summary);
            }
            sb.append("\n");
        }
        return sb.toString();
    }

    /**
     * 获取技能摘要，优先使用description字段，回退到内容提取
     * @param skill
     * @return
     */
    private String getSummary(AgentSkill skill) {
        // 优先使用YAML description字段
        String desc = skill.getDescription();
        if (desc != null && !desc.isBlank()) {
            return desc.length() > 120 ? desc.substring(0, 117) + "..." : desc;
        }
        // 回退：从技能内容中提取首句
        String content = skill.getSkillContent();
        if (content == null || content.isBlank()) {
            return "";
        }
        return extractSentence(content);
    }

    /**
     * 从技能内容中提取首句作为摘要
     * @param content
     * @return
     */
    private String extractSentence(String content) {
        String trimmed = content.trim();
        // 先去掉 # 开头的标题行
        String body = trimmed.replaceFirst("^#+\\s+[^\\n]*\\n?", "");
        // 取第一个句号/问号/感叹号/换行前的文本
        int end = -1;
        for (char c : new char[]{'。', '？', '！', '.', '?', '!', '\n'}) {
            int idx = body.indexOf(c);
            if (idx >= 0 && (end < 0 || idx < end)) {
                end = idx;
            }
        }
        String sentence = end > 0 ? body.substring(0, end).trim() : body;
        if (sentence.length() > 120) {
            return sentence.substring(0, 117) + "...";
        }
        return sentence;
    }

    /**
     * 构建技能名称到技能实例的索引
     * @param box 技能箱
     * @return 名称索引映射
     */
    private Map<String, AgentSkill> buildIndex(AgentSkillBox box) {
        if (box == null || box.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<String, AgentSkill> index = new LinkedHashMap<>();
        for (AgentSkill skill : box.getSkills()) {
            if (skill != null && skill.getName() != null) {
                index.put(skill.getName(), skill);
            }
        }
        return Collections.unmodifiableMap(index);
    }
}
