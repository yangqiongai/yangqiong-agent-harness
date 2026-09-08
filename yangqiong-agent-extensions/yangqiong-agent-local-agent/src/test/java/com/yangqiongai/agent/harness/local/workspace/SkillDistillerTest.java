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
package com.yangqiongai.agent.harness.local.workspace;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import com.yangqiongai.agent.harness.core.skill.AgentSkill;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * 技能沉淀测试
 * @author yangqiong
 */
class SkillDistillerTest {

    @TempDir
    Path tempDir;

    @Test
    void distillUndersizedSequenceIsSkipped() {
        Path skillsDir = tempDir.resolve("skills");
        SkillDistiller distiller = new SkillDistiller(skillsDir, 3);

        boolean saved = distiller.distillIfApplicable("s1", List.of("tool_a", "tool_b"));

        assertThat(saved).isFalse();
        assertThat(FsSkillBox.discover(skillsDir).getSkills()).isEmpty();
    }

    @Test
    void distillQualifiedSequenceSavesSkillOnce() {
        Path skillsDir = tempDir.resolve("skills");
        SkillDistiller distiller = new SkillDistiller(skillsDir, 3);
        List<String> sequence = List.of("shell", "knowledge_search", "memory_store");

        assertThat(distiller.distillIfApplicable("s1", sequence)).isTrue();
        assertThat(distiller.distillIfApplicable("s1", sequence)).isFalse();

        List<AgentSkill> skills = FsSkillBox.discover(skillsDir).getSkills();
        assertThat(skills).hasSize(1);
        assertThat(skills.get(0).getDescription()).contains("shell").contains("memory_store");
        assertThat(skills.get(0).getSkillContent()).contains("- `shell`").contains("- `knowledge_search`");
    }

    @Test
    void onToolCallAccumulatesSequencePerSession() {
        Path skillsDir = tempDir.resolve("skills");
        SkillDistiller distiller = new SkillDistiller(skillsDir, 1);

        var context = com.yangqiongai.agent.harness.engine.AgentRuntimeContext.builder()
                .sessionId("sess-9").build();
        distiller.onToolCall("shell", Map.of(), context);
        distiller.onToolCall("file_read", Map.of(), context);

        assertThat(distiller.collectedTraces("sess-9"))
                .containsExactly("shell", "file_read");
    }
}