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
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.yangqiongai.agent.harness.local.memory.FileLongTermMemory;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 复盘记忆沉淀
 * <p>
 * 定时消化会话转录：读取会话目录下的会话JSONL，把对话内容沉淀为一条长期记忆
 * 入库到 memory/ 目录，并以已处理标记文件实现幂等，重复执行不会重复沉淀同一会话。
 * </p>
 * @author yangqiong
 */
public class MemoryConsolidator {

    /**
     * 日志
     */
    private static final Logger log = LoggerFactory.getLogger(MemoryConsolidator.class);

    /**
     * 单条记忆内容最大字符数
     */
    private static final int MAX_CONTENT_CHARS = 4000;

    /**
     * 已处理标记文件后缀
     */
    private static final String SUFFIX = ".jsonl";

    /**
     * 记忆语义标识
     */
    private static final String KIND = "session_consolidation";

    /**
     * 会话转录存储
     */
    private final SessionTranscriptStore transcript;

    /**
     * 长期记忆落点
     */
    private final FileLongTermMemory memory;

    /**
     * 已处理会话标记文件
     */
    private final Path markerFile;

    /**
     * 记忆属主作用域
     */
    private final String scopeId;

    /**
     * 记忆属主用户
     */
    private final String userId;

    /**
     * 构造
     * @param transcript
     * @param memory
     * @param markerFile
     * @param scopeId
     * @param userId
     */
    public MemoryConsolidator(SessionTranscriptStore transcript, FileLongTermMemory memory,
                              Path markerFile, String scopeId, String userId) {
        if (transcript == null) {
            throw new IllegalArgumentException("转录存储不能为空");
        }
        if (memory == null) {
            throw new IllegalArgumentException("长期记忆不能为空");
        }
        if (markerFile == null) {
            throw new IllegalArgumentException("标记文件不能为空");
        }
        this.transcript = transcript;
        this.memory = memory;
        this.markerFile = markerFile.toAbsolutePath().normalize();
        this.scopeId = scopeId != null ? scopeId : "";
        this.userId = userId != null ? userId : "";
    }

    /**
     * 沉淀全部未处理会话，返回本次沉淀的会话数
     * @return
     */
    public int consolidateAll() {
        Set<String> processed = loadProcessed();
        int count = 0;
        for (String sessionId : transcript.listSessionIds()) {
            if (processed.contains(sessionId)) {
                continue;
            }
            if (consolidateSession(sessionId)) {
                count++;
            }
            processed.add(sessionId);
        }
        persistProcessed(processed);
        return count;
    }

    /**
     * 沉淀单个会话，会话无有效内容或已处理返回false
     * @param sessionId
     * @return
     */
    public boolean consolidateSession(String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            return false;
        }
        List<Map<String, Object>> records = transcript.readAll(sessionId);
        String content = buildSummary(records);
        if (content.isBlank()) {
            return false;
        }
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("kind", KIND);
        metadata.put("sessionId", sessionId);
        memory.store(scopeId, userId, sessionId, content, metadata);
        log.info("会话复盘已沉淀为长期记忆: sessionId={}", sessionId);
        return true;
    }

    /**
     * 把会话对话记录聚合为一条记忆内容，仅保留文本型用户/助手消息并截断
     * @param records
     * @return
     */
    private String buildSummary(List<Map<String, Object>> records) {
        StringBuilder builder = new StringBuilder();
        for (Map<String, Object> record : records) {
            if (!"text".equals(String.valueOf(record.get("type")))) {
                continue;
            }
            Object role = record.get("role");
            Object content = record.get("content");
            if (content == null || String.valueOf(content).isBlank()) {
                continue;
            }
            builder.append('[').append(role == null ? "" : role).append("] ")
                    .append(content).append('\n');
            if (builder.length() >= MAX_CONTENT_CHARS) {
                break;
            }
        }
        return builder.length() > MAX_CONTENT_CHARS
                ? builder.substring(0, MAX_CONTENT_CHARS) : builder.toString();
    }

    /**
     * 读取已处理会话标记集合，文件不存在或不可读返回空集
     * @return
     */
    private Set<String> loadProcessed() {
        Set<String> processed = new HashSet<>();
        if (!Files.isRegularFile(markerFile)) {
            return processed;
        }
        try {
            for (String line : Files.readAllLines(markerFile, StandardCharsets.UTF_8)) {
                if (!line.isBlank()) {
                    processed.add(line.trim());
                }
            }
        } catch (IOException e) {
            log.warn("已处理标记读取失败，按全量重新沉淀: {}", markerFile, e);
            return new HashSet<>();
        }
        return processed;
    }

    /**
     * 持久化已处理会话标记，父目录不存在时自动创建
     * @param processed
     */
    private void persistProcessed(Set<String> processed) {
        try {
            Files.createDirectories(markerFile.getParent());
            Files.writeString(markerFile, String.join("\n", processed) + "\n",
                    StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("已处理标记写入失败: " + markerFile, e);
        }
    }
}