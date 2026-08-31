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

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yangqiong.agent.harness.durable.serialization.HarnessObjectMapper;
import com.yangqiong.agent.harness.local.knowledge.FsKnowledgeBase;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 知识库与记忆自维护
 * <p>
 * 定时重扫知识库索引，并对长期记忆做去重与过期归档：
 * 相同内容的记忆保留最新一条、其余归档；超过最大存活时长的记忆整体归档，
 * 归档移入 memory/.archive 目录以便复核，实现无人值守的自维护。
 * </p>
 * @author yangqiong
 */
public class KnowledgeMaintainer {

    /**
     * 日志
     */
    private static final Logger log = LoggerFactory.getLogger(KnowledgeMaintainer.class);

    /**
     * 记忆文件后缀
     */
    private static final String FILE_SUFFIX = ".jsonl";

    /**
     * 归档子目录名
     */
    private static final String ARCHIVE_DIR = ".archive";

    /**
     * 记忆记录反序列化类型
     */
    private static final TypeReference<Map<String, Object>> RECORD_TYPE = new TypeReference<Map<String, Object>>() {
    };

    /**
     * 统一序列化映射器
     */
    private static final ObjectMapper MAPPER = HarnessObjectMapper.get();

    /**
     * 记录内容key
     */
    private static final String KEY_CONTENT = "content";

    /**
     * 记录时间戳key
     */
    private static final String KEY_TS = "ts";

    /**
     * 知识库
     */
    private final FsKnowledgeBase knowledgeBase;

    /**
     * 知识库目录
     */
    private final Path knowledgeDir;

    /**
     * 记忆目录
     */
    private final Path memoryDir;

    /**
     * 归档目录
     */
    private final Path archiveDir;

    /**
     * 构造
     * @param knowledgeDir
     * @param memoryDir
     */
    public KnowledgeMaintainer(Path knowledgeDir, Path memoryDir) {
        if (knowledgeDir == null) {
            throw new IllegalArgumentException("知识目录不能为空");
        }
        if (memoryDir == null) {
            throw new IllegalArgumentException("记忆目录不能为空");
        }
        this.knowledgeDir = knowledgeDir.toAbsolutePath().normalize();
        this.memoryDir = memoryDir.toAbsolutePath().normalize();
        this.archiveDir = this.memoryDir.resolve(ARCHIVE_DIR);
        this.knowledgeBase = FsKnowledgeBase.open(this.knowledgeDir);
    }

    /**
     * 重扫知识库索引
     * @return
     */
    public int reindex() {
        knowledgeBase.reindex();
        log.info("知识库索引已刷新: docs={}", knowledgeBase.size());
        return knowledgeBase.size();
    }

    /**
     * 触发一次完整自维护：重建知识索引 + 记忆去重与过期归档
     * @param maxAgeMillis
     * @return
     */
    public MaintenanceResult maintain(long maxAgeMillis) {
        int docs = reindex();
        MemoryPrune prune = pruneMemories(maxAgeMillis);
        return new MaintenanceResult(docs, prune.archivedCount(), prune.dedupedCount());
    }

    /**
     * 对长期记忆执行去重与过期归档，返回归档/去重条数
     * @param maxAgeMillis 记忆最大存活时长，过期即归档
     * @return
     */
    public MemoryPrune pruneMemories(long maxAgeMillis) {
        if (maxAgeMillis <= 0) {
            return new MemoryPrune(0, 0);
        }
        List<MemoryRecord> records = scanRecords();
        long cutoff = System.currentTimeMillis() - maxAgeMillis;

        int archived = 0;
        List<MemoryRecord> active = new ArrayList<>();
        for (MemoryRecord record : records) {
            if (record.ts < cutoff) {
                mirror(record.file);
                archived++;
            } else {
                active.add(record);
            }
        }
        Map<String, MemoryRecord> kept = new LinkedHashMap<>();
        int deduped = 0;
        for (MemoryRecord record : active) {
            MemoryRecord existing = kept.get(record.content);
            if (existing == null) {
                kept.put(record.content, record);
            } else {
                // 相同内容保留时间戳更晚的一条，旧的归档
                MemoryRecord keep = record.ts >= existing.ts ? record : existing;
                MemoryRecord dup = keep == record ? existing : record;
                kept.put(keep.content, keep);
                mirror(dup.file);
                deduped++;
            }
        }
        log.info("记忆维护完成: archived={}, deduped={}", archived, deduped);
        return new MemoryPrune(archived, deduped);
    }

    /**
     * 扫描记忆目录下全部记忆记录，含文件名与内容、时间戳
     * @return
     */
    private List<MemoryRecord> scanRecords() {
        List<MemoryRecord> records = new ArrayList<>();
        if (!Files.isDirectory(memoryDir)) {
            return records;
        }
        try (var stream = Files.list(memoryDir)) {
            stream.filter(Files::isRegularFile)
                    .filter(file -> file.getFileName().toString().endsWith(FILE_SUFFIX))
                    .forEach(file -> readRecord(file).ifPresent(records::add));
        } catch (IOException e) {
            throw new UncheckedIOException("记忆目录扫描失败: " + memoryDir, e);
        }
        return records;
    }

    /**
     * 读取单条记忆记录，解析失败或为空时返回空
     * @param file
     * @return
     */
    private java.util.Optional<MemoryRecord> readRecord(Path file) {
        try {
            String line = Files.readString(file, StandardCharsets.UTF_8);
            if (line.isBlank()) {
                return java.util.Optional.empty();
            }
            Map<String, Object> record = MAPPER.readValue(line, RECORD_TYPE);
            Object content = record.get(KEY_CONTENT);
            if (content == null || String.valueOf(content).isBlank()) {
                return java.util.Optional.empty();
            }
            long ts = record.get(KEY_TS) instanceof Number number ? number.longValue() : 0L;
            return java.util.Optional.of(new MemoryRecord(file, String.valueOf(content), ts));
        } catch (IOException e) {
            log.warn("记忆记录读取失败，已跳过: {}", file, e);
            return java.util.Optional.empty();
        }
    }

    /**
     * 把记忆文件归档移动到 .archive 目录，同名冲突时覆盖
     * @param file
     */
    private void mirror(Path file) {
        try {
            Files.createDirectories(archiveDir);
            Files.move(file, archiveDir.resolve(file.getFileName()),
                    StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new UncheckedIOException("记忆归档失败: " + file, e);
        }
    }

    /**
     * 单条记忆记录
     * @author yangqiong
     */
    private record MemoryRecord(Path file, String content, long ts) {
    }

    /**
     * 记忆整理结果
     * @author yangqiong
     */
    public record MemoryPrune(int archivedCount, int dedupedCount) {
    }

    /**
     * 自维护结果
     * @author yangqiong
     */
    public record MaintenanceResult(int indexedDocs, int archivedMemories, int dedupedMemories) {
    }
}