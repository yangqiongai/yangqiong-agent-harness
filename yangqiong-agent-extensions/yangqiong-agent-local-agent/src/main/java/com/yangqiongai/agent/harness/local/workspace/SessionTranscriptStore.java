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

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yangqiongai.agent.harness.durable.serialization.HarnessObjectMapper;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 会话JSONL落盘存储
 * <p>
 * 以单行JSON追加方式将会话记录逐行写入 sessions/{sessionId}.jsonl，
 * 读写均加锁保证线程安全，会话文件不存在时读取返回空列表。
 * </p>
 * @author yangqiong
 */
public class SessionTranscriptStore {

    /**
     * JSONL文件后缀
     */
    private static final String FILE_SUFFIX = ".jsonl";

    /**
     * 统一序列化映射器
     */
    private static final ObjectMapper MAPPER = HarnessObjectMapper.get();

    /**
     * 记录反序列化类型
     */
    private static final TypeReference<Map<String, Object>> RECORD_TYPE = new TypeReference<Map<String, Object>>() {
    };

    /**
     * 所属本地工作区
     */
    private final LocalWorkspace workspace;

    /**
     * 构造
     * @param workspace 本地工作区
     */
    public SessionTranscriptStore(LocalWorkspace workspace) {
        if (workspace == null) {
            throw new IllegalArgumentException("工作区不能为空");
        }
        this.workspace = workspace;
    }

    /**
     * 追加一条会话记录到对应会话的JSONL文件
     * @param sessionId
     * @param record
     */
    public synchronized void append(String sessionId, Map<String, Object> record) {
        if (record == null) {
            throw new IllegalArgumentException("会话记录不能为空");
        }
        Path file = transcriptFile(sessionId);
        try {
            String line = MAPPER.writeValueAsString(record);
            Files.createDirectories(file.getParent());
            Files.write(file, (line + "\n").getBytes(StandardCharsets.UTF_8),
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException e) {
            throw new UncheckedIOException("会话记录写入失败: " + file, e);
        }
    }

    /**
     * 读取会话全部记录并保持写入顺序，文件不存在时返回空列表
     * @param sessionId
     * @return
     */
    public synchronized List<Map<String, Object>> readAll(String sessionId) {
        Path file = transcriptFile(sessionId);
        if (!Files.exists(file)) {
            return new ArrayList<>();
        }
        try {
            List<Map<String, Object>> records = new ArrayList<>();
            for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
                if (line.isBlank()) {
                    continue;
                }
                records.add(MAPPER.readValue(line, RECORD_TYPE));
            }
            return records;
        } catch (IOException e) {
            throw new UncheckedIOException("会话记录读取失败: " + file, e);
        }
    }

    /**
     * 列出会话目录下全部已有会话ID，保持文件名序，目录不存在时返回空列表
     * @return
     */
    public synchronized List<String> listSessionIds() {
        Path sessionsDir = workspace.sessionsDir();
        if (!Files.isDirectory(sessionsDir)) {
            return new ArrayList<>();
        }
        try (var stream = Files.list(sessionsDir)) {
            return stream.filter(Files::isRegularFile)
                    .filter(file -> file.getFileName().toString().endsWith(FILE_SUFFIX))
                    .map(file -> file.getFileName().toString())
                    .map(name -> name.substring(0, name.length() - FILE_SUFFIX.length()))
                    .sorted()
                    .toList();
        } catch (IOException e) {
            throw new UncheckedIOException("会话目录扫描失败: " + sessionsDir, e);
        }
    }

    /**
     * 定位会话JSONL文件并校验会话ID未越出会话目录
     * @param sessionId
     * @return
     */
    private Path transcriptFile(String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            throw new IllegalArgumentException("会话ID不能为空");
        }
        Path sessionsDir = workspace.sessionsDir().normalize();
        Path file = sessionsDir.resolve(sessionId + FILE_SUFFIX).normalize();
        if (!file.startsWith(sessionsDir)) {
            throw new IllegalArgumentException("非法会话ID: " + sessionId);
        }
        return file;
    }
}
