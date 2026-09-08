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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import com.yangqiongai.agent.harness.core.skill.AgentSkill;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * 文件系统技能箱测试
 * @author yangqiong
 */
class FsSkillBoxTest {

    @TempDir
    Path tempDir;

    @Test
    void shouldReturnEmptyWhenDirMissing() {
        assertThat(FsSkillBox.discover(tempDir.resolve("skills")).isEmpty()).isTrue();
    }

    @Test
    void shouldParseSkillWithDescriptionAndResources() throws Exception {
        Path skills = Files.createDirectories(tempDir.resolve("skills/data-query"));
        String content = "description: 数据查询助手\n# 数据查询\n按语义检索本地知识库。";
        Files.writeString(skills.resolve("SKILL.md"), content);
        Files.createDirectories(skills.resolve("resources"));
        Files.writeString(skills.resolve("resources").resolve("query.sql"), "select 1;");

        FsSkillBox box = FsSkillBox.discover(tempDir.resolve("skills"));

        assertThat(box.isEmpty()).isFalse();
        assertThat(box.getSkills()).hasSize(1);
        AgentSkill skill = box.getSkills().get(0);
        assertThat(skill.getName()).isEqualTo("data-query");
        assertThat(skill.getDescription()).isEqualTo("数据查询助手");
        assertThat(skill.getSkillContent()).doesNotContain("description:");
        assertThat(skill.getResources().get("query.sql")).isEqualTo("select 1;");
        assertThat(box.buildSystemPrompt()).contains("数据查询助手");
    }

    @Test
    void shouldSkipDirectoriesWithoutSkillFile() throws Exception {
        Files.createDirectories(tempDir.resolve("skills/empty-skill"));
        FsSkillBox box = FsSkillBox.discover(tempDir.resolve("skills"));
        assertThat(box.isEmpty()).isTrue();
    }

    @Test
    void shouldRoundTripAfterSaveSkill() {
        AgentSkill skill = new AgentSkill("reporter", "TRUSTED", "报告生成器",
                "# 报告生成\n产出结构化报告。", Map.of("template.md", "# 模板"));
        FsSkillBox.saveSkill(tempDir.resolve("skills"), skill);

        FsSkillBox box = FsSkillBox.discover(tempDir.resolve("skills"));
        AgentSkill reloaded = box.getSkills().get(0);
        assertThat(reloaded.getName()).isEqualTo("reporter");
        assertThat(reloaded.getDescription()).isEqualTo("报告生成器");
        assertThat(reloaded.getResources()).containsKey("template.md");
    }

    @Test
    void shouldRejectIllegalSkillName() {
        assertThatThrownBy(() -> FsSkillBox.saveSkill(tempDir.resolve("skills"),
                new AgentSkill("../evil", "TRUSTED", "x", "x", Map.of())))
                .isInstanceOf(IllegalArgumentException.class);
    }
}