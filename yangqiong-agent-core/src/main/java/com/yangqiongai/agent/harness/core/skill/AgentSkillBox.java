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
package com.yangqiongai.agent.harness.core.skill;

import java.util.List;

/**
 * Agent技能箱
 * @author yangqiong
 */
public interface AgentSkillBox {

    /**
     * 获取技能列表
     * @return
     */
    List<AgentSkill> getSkills();

    /**
     * 技能箱是否为空
     * @return
     */
    boolean isEmpty();

    /**
     * 添加技能
     * @param skill
     */
    void addSkill(AgentSkill skill);

    /**
     * 构建系统提示词
     * <p>
     * 优先使用技能 description 字段作为摘要，无 description 时回退到 skillContent 首句，实现渐进加载。
     * </p>
     * @return
     */
    default String buildSystemPrompt() {
        if (isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder("Available skills:\n");
        for (AgentSkill skill : getSkills()) {
            sb.append("- ").append(skill.getName());
            String desc = skill.getDescription();
            if (desc != null && !desc.isBlank()) {
                sb.append(": ").append(desc);
            } else {
                String content = skill.getSkillContent();
                if (content != null && !content.isBlank()) {
                    String summary = extractFirstSentence(content);
                    if (!summary.isEmpty()) {
                        sb.append(": ").append(summary);
                    }
                }
            }
            sb.append("\n");
        }
        return sb.toString();
    }

    /**
     * 从内容中提取首句作为摘要
     * @param content
     * @return
     */
    private static String extractFirstSentence(String content) {
        String trimmed = content.trim();
        String body = trimmed.replaceFirst("^#+\\s+[^\\n]*\\n?", "");
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
}
