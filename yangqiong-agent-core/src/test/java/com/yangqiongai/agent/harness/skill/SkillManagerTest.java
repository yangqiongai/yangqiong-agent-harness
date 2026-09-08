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
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 技能管理器测试
 * @author yangqiong
 */
class SkillManagerTest {

    @Test
    void shouldReturnEmptyWhenSkillBoxIsNull() {
        SkillManager manager = new SkillManager(null);
        assertTrue(manager.isEmpty());
        assertNull(manager.findSkill("any"));
        assertTrue(manager.getSkills().isEmpty());
        assertEquals("", manager.buildSummaries());
    }

    @Test
    void shouldReturnEmptyWhenSkillBoxIsEmpty() {
        AgentSkillBox emptyBox = createSkillBox(List.of());
        SkillManager manager = new SkillManager(emptyBox);
        assertTrue(manager.isEmpty());
    }

    @Test
    void shouldFindSkillByName() {
        AgentSkill skill = new AgentSkill("code-review", "BUILTIN", "Review code quality", Map.of());
        AgentSkillBox box = createSkillBox(List.of(skill));
        SkillManager manager = new SkillManager(box);

        assertFalse(manager.isEmpty());
        AgentSkill found = manager.findSkill("code-review");
        assertNotNull(found);
        assertEquals("code-review", found.getName());
    }

    @Test
    void shouldReturnNullWhenSkillNotFound() {
        AgentSkill skill = new AgentSkill("code-review", "BUILTIN", "Review code quality", Map.of());
        SkillManager manager = new SkillManager(createSkillBox(List.of(skill)));

        assertNull(manager.findSkill("nonexistent"));
        assertNull(manager.findSkill(null));
        assertNull(manager.findSkill(""));
    }

    @Test
    void shouldBuildSummariesWithSkillNames() {
        AgentSkill skill1 = new AgentSkill("code-review", "BUILTIN", "Review code quality", Map.of());
        AgentSkill skill2 = new AgentSkill("refactor", "BUILTIN", "Refactor code\nDetailed steps", Map.of());
        SkillManager manager = new SkillManager(createSkillBox(List.of(skill1, skill2)));

        String summaries = manager.buildSummaries();
        assertTrue(summaries.contains("code-review"));
        assertTrue(summaries.contains("Review code quality"));
        assertTrue(summaries.contains("refactor"));
        assertTrue(summaries.contains("Refactor code"));
        assertFalse(summaries.contains("Detailed steps"));
    }

    @Test
    void shouldTruncateLongSummary() {
        String longContent = "This is a very long skill description that exceeds the one hundred twenty character limit and should be truncated with ellipsis at the end of the first line";
        AgentSkill skill = new AgentSkill("long-skill", "BUILTIN", longContent, Map.of());
        SkillManager manager = new SkillManager(createSkillBox(List.of(skill)));

        String summaries = manager.buildSummaries();
        assertTrue(summaries.contains("..."));
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
