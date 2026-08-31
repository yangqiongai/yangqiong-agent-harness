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

import com.yangqiong.agent.harness.core.message.AgentToolResultBlock;
import com.yangqiong.agent.harness.core.skill.AgentSkill;
import com.yangqiong.agent.harness.core.skill.AgentSkillBox;
import com.yangqiong.agent.harness.core.tool.AgentToolCallParam;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 加载技能工具测试
 * @author yangqiong
 */
class LoadSkillToolTest {

    @Test
    void shouldReturnCorrectToolMetadata() {
        SkillManager manager = new SkillManager(null);
        LoadSkillTool tool = new LoadSkillTool(manager);

        assertEquals("load_skill", tool.getName());
        assertNotNull(tool.getDescription());
        Map<String, Object> params = tool.getParameters();
        assertEquals("object", params.get("type"));
        assertNotNull(params.get("properties"));
        assertNotNull(params.get("required"));
    }

    @Test
    void shouldLoadSkillContentSuccessfully() {
        AgentSkill skill = new AgentSkill("code-review", "BUILTIN", "Full skill content here", Map.of());
        SkillManager manager = new SkillManager(createSkillBox(List.of(skill)));
        LoadSkillTool tool = new LoadSkillTool(manager);

        AgentToolCallParam param = new AgentToolCallParam(Map.of("skill_name", "code-review"));
        Mono<AgentToolResultBlock> result = tool.callAsync(param);

        StepVerifier.create(result)
                .assertNext(block -> {
                    assertFalse(block.isError());
                    assertEquals("Full skill content here", block.getTextContent());
                })
                .verifyComplete();
    }

    @Test
    void shouldReturnErrorWhenSkillNotFound() {
        SkillManager manager = new SkillManager(createSkillBox(List.of()));
        LoadSkillTool tool = new LoadSkillTool(manager);

        AgentToolCallParam param = new AgentToolCallParam(Map.of("skill_name", "nonexistent"));
        StepVerifier.create(tool.callAsync(param))
                .assertNext(block -> {
                    assertTrue(block.isError());
                    assertTrue(block.getTextContent().contains("技能未找到"));
                })
                .verifyComplete();
    }

    @Test
    void shouldReturnErrorWhenParamMissing() {
        SkillManager manager = new SkillManager(null);
        LoadSkillTool tool = new LoadSkillTool(manager);

        StepVerifier.create(tool.callAsync(new AgentToolCallParam(Map.of())))
                .assertNext(block -> {
                    assertTrue(block.isError());
                    assertTrue(block.getTextContent().contains("skill_name"));
                })
                .verifyComplete();
    }

    @Test
    void shouldReturnErrorWhenParamIsNull() {
        SkillManager manager = new SkillManager(null);
        LoadSkillTool tool = new LoadSkillTool(manager);

        StepVerifier.create(tool.callAsync(null))
                .assertNext(block -> assertTrue(block.isError()))
                .verifyComplete();
    }

    @Test
    void shouldReturnErrorWhenSkillContentBlank() {
        AgentSkill skill = new AgentSkill("empty", "BUILTIN", "  ", Map.of());
        SkillManager manager = new SkillManager(createSkillBox(List.of(skill)));
        LoadSkillTool tool = new LoadSkillTool(manager);

        AgentToolCallParam param = new AgentToolCallParam(Map.of("skill_name", "empty"));
        StepVerifier.create(tool.callAsync(param))
                .assertNext(block -> {
                    assertTrue(block.isError());
                    assertTrue(block.getTextContent().contains("技能内容为空"));
                })
                .verifyComplete();
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
