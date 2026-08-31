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
 * 读取技能资源工具测试
 * @author yangqiong
 */
class ReadSkillResourceToolTest {

    @Test
    void shouldReturnCorrectToolMetadata() {
        ReadSkillResourceTool tool = new ReadSkillResourceTool(new SkillManager(null));

        assertEquals("read_skill_resource", tool.getName());
        assertNotNull(tool.getDescription());
        Map<String, Object> params = tool.getParameters();
        assertEquals("object", params.get("type"));
        @SuppressWarnings("unchecked")
        List<String> required = (List<String>) params.get("required");
        assertTrue(required.contains("skill_name"));
        assertTrue(required.contains("resource_path"));
    }

    @Test
    void shouldReadResourceSuccessfully() {
        Map<String, String> resources = Map.of("script.py", "print('hello')");
        AgentSkill skill = new AgentSkill("python-runner", "BUILTIN", "Run Python scripts", resources);
        SkillManager manager = new SkillManager(createSkillBox(List.of(skill)));
        ReadSkillResourceTool tool = new ReadSkillResourceTool(manager);

        AgentToolCallParam param = new AgentToolCallParam(Map.of(
                "skill_name", "python-runner",
                "resource_path", "script.py"));
        Mono<AgentToolResultBlock> result = tool.callAsync(param);

        StepVerifier.create(result)
                .assertNext(block -> {
                    assertFalse(block.isError());
                    assertEquals("print('hello')", block.getTextContent());
                })
                .verifyComplete();
    }

    @Test
    void shouldReturnErrorWhenSkillNotFound() {
        ReadSkillResourceTool tool = new ReadSkillResourceTool(new SkillManager(null));

        AgentToolCallParam param = new AgentToolCallParam(Map.of(
                "skill_name", "nonexistent",
                "resource_path", "script.py"));
        StepVerifier.create(tool.callAsync(param))
                .assertNext(block -> {
                    assertTrue(block.isError());
                    assertTrue(block.getTextContent().contains("技能未找到"));
                })
                .verifyComplete();
    }

    @Test
    void shouldReturnErrorWhenResourcePathMissing() {
        AgentSkill skill = new AgentSkill("test", "BUILTIN", "content", Map.of("a", "b"));
        ReadSkillResourceTool tool = new ReadSkillResourceTool(new SkillManager(createSkillBox(List.of(skill))));

        AgentToolCallParam param = new AgentToolCallParam(Map.of("skill_name", "test"));
        StepVerifier.create(tool.callAsync(param))
                .assertNext(block -> {
                    assertTrue(block.isError());
                    assertTrue(block.getTextContent().contains("resource_path"));
                })
                .verifyComplete();
    }

    @Test
    void shouldReturnErrorWhenResourceNotFound() {
        AgentSkill skill = new AgentSkill("test", "BUILTIN", "content", Map.of("existing.txt", "data"));
        SkillManager manager = new SkillManager(createSkillBox(List.of(skill)));
        ReadSkillResourceTool tool = new ReadSkillResourceTool(manager);

        AgentToolCallParam param = new AgentToolCallParam(Map.of(
                "skill_name", "test",
                "resource_path", "nonexistent.txt"));
        StepVerifier.create(tool.callAsync(param))
                .assertNext(block -> {
                    assertTrue(block.isError());
                    assertTrue(block.getTextContent().contains("资源未找到"));
                })
                .verifyComplete();
    }

    @Test
    void shouldReturnErrorWhenSkillHasNoResources() {
        AgentSkill skill = new AgentSkill("empty", "BUILTIN", "content", Map.of());
        SkillManager manager = new SkillManager(createSkillBox(List.of(skill)));
        ReadSkillResourceTool tool = new ReadSkillResourceTool(manager);

        AgentToolCallParam param = new AgentToolCallParam(Map.of(
                "skill_name", "empty",
                "resource_path", "any"));
        StepVerifier.create(tool.callAsync(param))
                .assertNext(block -> {
                    assertTrue(block.isError());
                    assertTrue(block.getTextContent().contains("技能无资源"));
                })
                .verifyComplete();
    }

    @Test
    void shouldReturnErrorWhenParamIsNull() {
        ReadSkillResourceTool tool = new ReadSkillResourceTool(new SkillManager(null));

        StepVerifier.create(tool.callAsync(null))
                .assertNext(block -> assertTrue(block.isError()))
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
