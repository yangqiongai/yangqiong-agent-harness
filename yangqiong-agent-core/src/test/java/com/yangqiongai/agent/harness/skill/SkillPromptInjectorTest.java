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

import com.yangqiongai.agent.harness.engine.AgentRuntimeContext;
import com.yangqiongai.agent.harness.core.skill.AgentSkill;
import com.yangqiongai.agent.harness.core.skill.AgentSkillBox;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 技能摘要注入器测试
 * @author yangqiong
 */
class SkillPromptInjectorTest {

    @Test
    void shouldReturnOriginalPromptWhenManagerEmpty() {
        SkillManager manager = new SkillManager(null);
        SkillPromptInjector injector = new SkillPromptInjector(manager);

        String result = injector.onSystemPrompt("You are an assistant", AgentRuntimeContext.empty());
        assertEquals("You are an assistant", result);
    }

    @Test
    void shouldAppendSkillSummariesToPrompt() {
        AgentSkill skill = new AgentSkill("code-review", "BUILTIN", "Review code quality", Map.of());
        SkillManager manager = new SkillManager(createSkillBox(List.of(skill)));
        SkillPromptInjector injector = new SkillPromptInjector(manager);

        String result = injector.onSystemPrompt("You are an assistant", AgentRuntimeContext.empty());
        assertTrue(result.startsWith("You are an assistant"));
        assertTrue(result.contains("code-review"));
        assertTrue(result.contains("Review code quality"));
    }

    @Test
    void shouldHandleNullSystemPrompt() {
        AgentSkill skill = new AgentSkill("test", "BUILTIN", "Test skill", Map.of());
        SkillManager manager = new SkillManager(createSkillBox(List.of(skill)));
        SkillPromptInjector injector = new SkillPromptInjector(manager);

        String result = injector.onSystemPrompt(null, AgentRuntimeContext.empty());
        assertTrue(result.contains("test"));
        assertTrue(result.contains("Test skill"));
    }

    @Test
    void shouldAddNewlineBetweenPromptAndSummaries() {
        AgentSkill skill = new AgentSkill("test", "BUILTIN", "Test", Map.of());
        SkillManager manager = new SkillManager(createSkillBox(List.of(skill)));
        SkillPromptInjector injector = new SkillPromptInjector(manager);

        String result = injector.onSystemPrompt("Base prompt", AgentRuntimeContext.empty());
        assertTrue(result.contains("Base prompt\n\n"));
    }

    @Test
    void shouldNotAddExtraNewlineWhenPromptEndsWithNewline() {
        AgentSkill skill = new AgentSkill("test", "BUILTIN", "Test", Map.of());
        SkillManager manager = new SkillManager(createSkillBox(List.of(skill)));
        SkillPromptInjector injector = new SkillPromptInjector(manager);

        String result = injector.onSystemPrompt("Base prompt\n", AgentRuntimeContext.empty());
        assertTrue(result.startsWith("Base prompt\n"));
        assertTrue(result.contains("test"));
    }

    private AgentSkillBox createSkillBox(List<AgentSkill> skills) {
        return new AgentSkillBox() {
            private final List<AgentSkill> list = new ArrayList<>(skills);

            @Override
            public List<AgentSkill> getSkills() {
                return list;
            }

            @Override
            public boolean isEmpty() {
                return list.isEmpty();
            }

            @Override
            public void addSkill(AgentSkill skill) {
                list.add(skill);
            }
        };
    }
}
