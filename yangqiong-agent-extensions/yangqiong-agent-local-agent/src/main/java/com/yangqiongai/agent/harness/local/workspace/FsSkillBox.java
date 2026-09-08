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

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.yangqiongai.agent.harness.core.skill.AgentSkill;
import com.yangqiongai.agent.harness.core.skill.AgentSkillBox;

/**
 * 文件系统技能箱
 * <p>
 * 扫描工作区 skills/ 目录为技能列表，目录结构约定：
 * skills/{name}/SKILL.md（技能主体，首行可选 description: xxx）+ resources/（可选附件）；
 * 为宿主提供写回保存能力，与运行时按需加载形成双向闭环。
 * </p>
 * @author yangqiong
 */
public class FsSkillBox implements AgentSkillBox {

    /**
     * 技能主体文件名
     */
    private static final String SKILL_FILE = "SKILL.md";

    /**
     * 技能资源目录名
     */
    private static final String RESOURCES_DIR = "resources";

    /**
     * 描述前置key
     */
    private static final String DESCRIPTION_PREFIX = "description:";

    /**
     * 文件系统发现的技能来源标识
     */
    private static final String SOURCE_FS = "TRUSTED";

    /**
     * 技能列表
     */
    private final List<AgentSkill> skills;

    /**
     * 以目录解析出的技能箱
     * @param skills
     */
    public FsSkillBox(List<AgentSkill> skills) {
        this.skills = skills != null ? List.copyOf(skills) : List.of();
    }

    /**
     * 扫描技能目录并以约定格式解析为技能箱，目录不存在或为空返回空技能箱
     * @param skillsDir
     * @return
     */
    public static FsSkillBox discover(Path skillsDir) {
        if (skillsDir == null || !Files.isDirectory(skillsDir)) {
            return new FsSkillBox(List.of());
        }
        List<AgentSkill> result = new ArrayList<>();
        Path[] dirs;
        try {
            dirs = Files.list(skillsDir).filter(Files::isDirectory)
                    .sorted().toArray(Path[]::new);
        } catch (IOException e) {
            throw new UncheckedIOException("技能目录扫描失败: " + skillsDir, e);
        }
        for (Path dir : dirs) {
            Path skillFile = dir.resolve(SKILL_FILE);
            if (!Files.isRegularFile(skillFile)) {
                continue;
            }
            result.add(parseSkill(dir));
        }
        return new FsSkillBox(result);
    }

    /**
     * 解析单个技能子目录
     * @param dir
     * @return
     */
    private static AgentSkill parseSkill(Path dir) {
        String name = dir.getFileName().toString();
        String content = readSafe(dir.resolve(SKILL_FILE));
        String description = extractDescription(content);
        Map<String, String> resources = parseResources(dir.resolve(RESOURCES_DIR));
        String body = description != null ? stripDescriptionLine(content) : content;
        return new AgentSkill(name, SOURCE_FS, description != null ? description : "", body, resources);
    }

    /**
     * 解析技能资源目录为 文件名->内容 映射
     * @param resourcesDir
     * @return
     */
    private static Map<String, String> parseResources(Path resourcesDir) {
        Map<String, String> resources = new LinkedHashMap<>();
        if (resourcesDir == null || !Files.isDirectory(resourcesDir)) {
            return resources;
        }
        try {
            Files.list(resourcesDir).filter(Files::isRegularFile)
                    .sorted().forEach(file -> resources.put(file.getFileName().toString(),
                            readSafe(file)));
        } catch (IOException e) {
            throw new UncheckedIOException("技能资源读取失败: " + resourcesDir, e);
        }
        return resources;
    }

    /**
     * 从内容首行解析 description，无前置key返回null
     * @param content
     * @return
     */
    private static String extractDescription(String content) {
        String first = content.lines().findFirst().orElse("").trim();
        if (first.regionMatches(true, 0, DESCRIPTION_PREFIX, 0, DESCRIPTION_PREFIX.length())) {
            String desc = first.substring(DESCRIPTION_PREFIX.length()).trim();
            return desc.isEmpty() ? null : desc;
        }
        return null;
    }

    /**
     * 移除首行description行，保留技能主体
     * @param content
     * @return
     */
    private static String stripDescriptionLine(String content) {
        int newline = content.indexOf('\n');
        return newline >= 0 ? content.substring(newline + 1).replaceFirst("^\\n+", "") : "";
    }

    /**
     * 安全读取文件为UTF-8字符串
     * @param file
     * @return
     */
    private static String readSafe(Path file) {
        try {
            return new String(Files.readAllBytes(file), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("技能文件读取失败: " + file, e);
        }
    }

    /**
     * 写回技能到技能目录，目录不存在自动创建，供宿主程序化保存
     * @param skillsDir
     * @param skill
     */
    public static void saveSkill(Path skillsDir, AgentSkill skill) {
        if (skill == null) {
            throw new IllegalArgumentException("技能不能为空");
        }
        String name = requireSingleSegment(skill.getName());
        Path dir = skillsDir.resolve(name);
        try {
            Files.createDirectories(dir);
            StringBuilder sb = new StringBuilder();
            if (skill.getDescription() != null && !skill.getDescription().isBlank()) {
                sb.append(DESCRIPTION_PREFIX).append(' ').append(skill.getDescription()).append('\n');
            }
            sb.append(skill.getSkillContent());
            Files.writeString(dir.resolve(SKILL_FILE), sb.toString(), StandardCharsets.UTF_8);
            if (skill.getResources() != null && !skill.getResources().isEmpty()) {
                Path resDir = dir.resolve(RESOURCES_DIR);
                Files.createDirectories(resDir);
                for (Map.Entry<String, String> entry : skill.getResources().entrySet()) {
                    Files.writeString(resDir.resolve(entry.getKey()), entry.getValue(),
                            StandardCharsets.UTF_8);
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException("技能保存失败: " + dir, e);
        }
    }

    /**
     * 校验技能名为单段路径片段，杜绝越出技能目录
     * @param name
     * @return
     */
    private static String requireSingleSegment(String name) {
        if (name == null || name.isBlank() || name.contains("/") || name.contains("\\")
                || name.equals(".") || name.equals("..")) {
            throw new IllegalArgumentException("非法技能名: " + name);
        }
        return name;
    }

    @Override
    public List<AgentSkill> getSkills() {
        return skills;
    }

    @Override
    public boolean isEmpty() {
        return skills.isEmpty();
    }

    @Override
    public void addSkill(AgentSkill skill) {
        throw new UnsupportedOperationException("文件夹技能箱不支持运行时增删，请通过目录文件管理");
    }
}