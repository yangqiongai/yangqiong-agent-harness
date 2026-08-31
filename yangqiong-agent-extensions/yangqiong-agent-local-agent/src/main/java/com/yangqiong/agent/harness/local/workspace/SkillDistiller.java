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
package com.yangqiong.agent.harness.local.workspace;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.yangqiong.agent.harness.core.event.AgentEvent;
import com.yangqiong.agent.harness.core.middleware.AgentMiddleware;
import com.yangqiong.agent.harness.core.message.AgentMessage;
import com.yangqiong.agent.harness.core.skill.AgentSkill;
import com.yangqiong.agent.harness.engine.AgentRuntimeContext;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.function.Function;
import reactor.core.publisher.Flux;

/**
 * 技能沉淀
 * <p>
 * 作为运行时中间件挂载：在一次成功执行的Agent运行中采集工具调用序列，
 * 当工具序列条数达到阈值时自动蒸馏为一条技能写回 skills/ 目录，
 * 相同序列的重复蒸馏会按技能名去重，实现从"执行者"到"会学习"的闭环。
 * </p>
 * @author yangqiong
 */
public class SkillDistiller implements AgentMiddleware {

    /**
     * 日志
     */
    private static final Logger log = LoggerFactory.getLogger(SkillDistiller.class);

    /**
     * 蒸馏技能来源标识
     */
    private static final String SOURCE = "AGENT_CREATED";

    /**
     * 技能名默认前缀
     */
    private static final String NAME_PREFIX = "distilled-";

    /**
     * 采集到的工具序列按键分组，键为会话标识
     */
    private final Map<String, LinkedHashSet<String>> attempts = new ConcurrentHashMap<>();

    /**
     * 技能落盘目录
     */
    private final FsSkillSaver saver;

    /**
     * 触发蒸馏的最少工具调用条数
     */
    private final int minToolCalls;

    /**
     * 构造
     * @param skillsDir
     * @param minToolCalls
     */
    public SkillDistiller(java.nio.file.Path skillsDir, int minToolCalls) {
        if (skillsDir == null) {
            throw new IllegalArgumentException("技能目录不能为空");
        }
        if (minToolCalls <= 0) {
            throw new IllegalArgumentException("最少工具条数必须为正数: " + minToolCalls);
        }
        this.saver = new FsSkillSaver(skillsDir);
        this.minToolCalls = minToolCalls;
    }

    /**
     * 构造，默认触发条数3
     * @param skillsDir
     */
    public SkillDistiller(java.nio.file.Path skillsDir) {
        this(skillsDir, 3);
    }

    /**
     * 工具调用前钩子：按会话累计工具调用序列
     * @param toolName
     * @param input
     * @param context
     * @return
     */
    @Override
    public Map<String, Object> onToolCall(String toolName, Map<String, Object> input,
                                          AgentRuntimeContext context) {
        if (toolName != null && !toolName.isBlank()) {
            attempts.computeIfAbsent(keyOf(context), key -> new LinkedHashSet<>()).add(toolName);
        }
        return input;
    }

    /**
     * Agent整体调用拦截：正常完成时把本次采集的工具序列蒸馏为技能，失败时丢弃采集结果
     * @param context
     * @param input
     * @param next
     * @return
     */
    @Override
    public Flux<AgentEvent> onAgent(AgentRuntimeContext context, List<AgentMessage> input,
                                    Function<List<AgentMessage>, Flux<AgentEvent>> next) {
        String key = keyOf(context);
        return Flux.defer(() -> next.apply(input)).doOnComplete(() -> flush(key))
                .doOnError(error -> attempts.remove(key));
    }

    /**
     * 按会话触发蒸馏：工具序列达到阈值且技能不存在时写回，返回写入的技能数
     * @param key
     */
    private void flush(String key) {
        List<String> sequence = new ArrayList<>(attempts.remove(key));
        distillIfApplicable(key, sequence);
    }

    /**
     * 判断是否满足蒸馏条件并落盘技能，已存在同样技能时跳过，返回本词是否写入
     * @param key 会话键，用于提示性命名
     * @param toolSequence
     * @return
     */
    public boolean distillIfApplicable(String key, List<String> toolSequence) {
        if (toolSequence == null || toolSequence.size() < minToolCalls) {
            return false;
        }
        String name = buildName(key, toolSequence);
        if (saver.exists(name)) {
            return false;
        }
        String description = String.join(", ", toolSequence);
        String content = buildSkillContent(toolSequence);
        AgentSkill skill = new AgentSkill(name, SOURCE, description, content, Map.of());
        saver.save(skill);
        log.info("执行复盘已蒸馏为新技能: name={}", name);
        return true;
    }

    /**
     * 返回某会话键当前累计的工具调用序列快照，供测试与观测
     * @param key
     * @return
     */
    List<String> collectedTraces(String key) {
        LinkedHashSet<String> set = attempts.get(key);
        return set == null ? List.of() : new ArrayList<>(set);
    }

    /**
     * 由会话键与工具序列派生稳定的技能名，保证相同序列可去重
     * @param key
     * @param sequence
     * @return
     */
    private static String buildName(String key, List<String> sequence) {
        String safe = key == null ? "" : key.replaceAll("[^a-zA-Z0-9_-]", "");
        String hash = shortHash(String.join("|", sequence));
        String name = NAME_PREFIX + hash;
        if (!safe.isBlank()) {
            String tail = safe.length() > 8 ? safe.substring(0, 8) : safe;
            name = NAME_PREFIX + tail + "-" + hash;
        }
        return name;
    }

    /**
     * 生成技能主体的Markdown内容
     * @param sequence
     * @return
     */
    private String buildSkillContent(List<String> sequence) {
        StringBuilder builder = new StringBuilder("# 蒸馏技能\n\n## 工具调用序列\n\n");
        for (String tool : sequence) {
            builder.append("- `").append(tool).append("`\n");
        }
        builder.append("\n通过运行复盘自动蒸馏生成，请按需补充入参与执行细节后使用。\n");
        return builder.toString();
    }

    /**
     * 计算会话键，会话ID为空时回退为默认键
     * @param context
     * @return
     */
    private static String keyOf(AgentRuntimeContext context) {
        String sessionId = context == null ? null : context.getSessionId();
        return sessionId != null && !sessionId.isBlank() ? sessionId : "default";
    }

    /**
     * 对文本生成8位十六进制短哈希
     * @param text
     * @return
     */
    private static String shortHash(String text) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(text.getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder();
            for (int i = 0; i < 4; i++) {
                builder.append(String.format("%02x", digest[i]));
            }
            return builder.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256不可用", e);
        }
    }

    /**
     * 技能文件写回封装，便于测试打桩
     * @author yangqiong
     */
    static class FsSkillSaver {

        /**
         * 技能目录
         */
        private final java.nio.file.Path skillsDir;

        /**
         * 构造
         * @param skillsDir
         */
        FsSkillSaver(java.nio.file.Path skillsDir) {
            this.skillsDir = skillsDir;
        }

        /**
         * 判断技能名是否已存在
         * @param name
         * @return
         */
        boolean exists(String name) {
            return FsSkillBox.discover(skillsDir).getSkills().stream()
                    .anyMatch(skill -> skill.getName().equals(name));
        }

        /**
         * 保存技能到技能目录
         * @param skill
         */
        void save(AgentSkill skill) {
            FsSkillBox.saveSkill(skillsDir, skill);
        }
    }
}