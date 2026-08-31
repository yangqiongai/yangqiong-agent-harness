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

import com.yangqiong.agent.harness.engine.AgentRuntimeContext;
import com.yangqiong.agent.harness.core.middleware.AgentMiddleware;

/**
 * 技能摘要注入器
 * <p>
 * Level 1 渐进加载：将所有技能的名称与简短摘要注入系统提示词，
 * 让 LLM 感知可用技能，再通过 load_skill 工具按需加载完整内容。
 * </p>
 * @author yangqiong
 */
public class SkillPromptInjector implements AgentMiddleware {

    /**
     * 技能管理器
     */
    private final SkillManager skillManager;

    public SkillPromptInjector(SkillManager skillManager) {
        this.skillManager = skillManager;
    }

    /**
     * 在系统提示词末尾追加技能摘要
     * @param systemPrompt 原始系统提示词
     * @param context 运行时上下文
     * @return 追加技能摘要后的系统提示词
     */
    @Override
    public String onSystemPrompt(String systemPrompt, AgentRuntimeContext context) {
        if (skillManager == null || skillManager.isEmpty()) {
            return systemPrompt;
        }
        String summaries = skillManager.buildSummaries();
        if (summaries == null || summaries.isBlank()) {
            return systemPrompt;
        }
        String base = systemPrompt == null ? "" : systemPrompt;
        if (!base.isEmpty() && !base.endsWith("\n")) {
            base = base + "\n\n";
        }
        return base + summaries;
    }
}
