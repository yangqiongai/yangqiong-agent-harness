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
package com.yangqiong.agent.harness.local.memory;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yangqiong.agent.harness.core.memory.AgentLongTermMemory;
import com.yangqiong.agent.harness.durable.serialization.HarnessObjectMapper;

/**
 * 文件长期记忆
 * <p>
 * 以工作区 memory/ 目录为落点：每个记忆条目为一个 {memoryId}.jsonl 文件，
 * 单行存储 {id, scope, user, session, content, metadata, ts}；
 * 检索采用分词包含匹配打分排序，删除带所有权校验，scope 不匹配抛出 SecurityException。
 * </p>
 * @author yangqiong
 */
public class FileLongTermMemory implements AgentLongTermMemory {

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
     * 记忆内容key
     */
    private static final String KEY_CONTENT = "content";

    /**
     * 记忆属主scope key
     */
    private static final String KEY_SCOPE = "scope";

    /**
     * 记忆创建时间key
     */
    private static final String KEY_TS = "ts";

    /**
     * 记忆目录
     */
    private final Path root;

    /**
     * 读写锁，保证并发安全
     */
    private final Object lock = new Object();

    /**
     * 记忆内存缓存（memoryId -> 记录），检索时避免重复磁盘读与反序列化
     */
    private Map<String, Map<String, Object>> cache = new LinkedHashMap<>();

    /**
     * 缓存是否已与磁盘对齐
     */
    private boolean cacheLoaded = false;

    /**
     * 缓存建立时的文件名集合，用于检测外部增删文件触发重载
     */
    private Set<String> knownFileNames = new HashSet<>();

    /**
     * 构造
     * @param memoryDir 记忆目录，如 {rootDir}/workspace/memory
     */
    public FileLongTermMemory(Path memoryDir) {
        if (memoryDir == null) {
            throw new IllegalArgumentException("记忆目录不能为空");
        }
        this.root = memoryDir.toAbsolutePath().normalize();
    }

    /**
     * 存储记忆
     * @param userId
     * @param sessionId
     * @param content
     * @param metadata
     */
    @Override
    public void store(String userId, String sessionId, String content, Map<String, Object> metadata) {
        store(null, userId, sessionId, content, metadata);
    }

    /**
     * 按租户作用域存储记忆，落盘为 {memoryId}.jsonl 文件
     * @param scopeId
     * @param userId
     * @param sessionId
     * @param content
     * @param metadata
     */
    @Override
    public void store(String scopeId, String userId, String sessionId,
                      String content, Map<String, Object> metadata) {
        if (content == null || content.isBlank()) {
            return;
        }
        String memoryId = UUID.randomUUID().toString();
        synchronized (lock) {
            Map<String, Object> record = new LinkedHashMap<>();
            record.put("id", memoryId);
            record.put(KEY_SCOPE, normalize(scopeId));
            record.put("user", normalize(userId));
            record.put("session", sessionId);
            record.put(KEY_CONTENT, content);
            record.put("metadata", metadata);
            record.put(KEY_TS, System.currentTimeMillis());
            writeRecord(memoryId, record);
            if (cacheLoaded) {
                cache.put(memoryId, record);
            }
            knownFileNames.add(memoryId + FILE_SUFFIX);
        }
    }

    /**
     * 检索记忆
     * @param userId
     * @param query
     * @param limit
     * @return
     */
    @Override
    public List<String> search(String userId, String query, int limit) {
        return search(null, userId, query, limit);
    }

    /**
     * 按租户作用域检索记忆，遍历目录按关键字命中打分排序
     * @param scopeId
     * @param userId
     * @param query
     * @param limit
     * @return
     */
    @Override
    public List<String> search(String scopeId, String userId, String query, int limit) {
        if (query == null || query.isBlank()) {
            return List.of();
        }
        List<String> tokens = tokenize(query);
        if (tokens.isEmpty()) {
            return List.of();
        }
        synchronized (lock) {
            ensureCacheLoaded();
            List<Scored> scored = new ArrayList<>();
            for (Map<String, Object> record : cache.values()) {
                if (record == null || !matchesScope(record, scopeId) || !matchesUser(record, userId)) {
                    continue;
                }
                String content = (String) record.get(KEY_CONTENT);
                if (content == null) {
                    continue;
                }
                int hits = score(content, tokens);
                if (hits > 0) {
                    scored.add(new Scored(content, hits, longValue(record.get(KEY_TS))));
                }
            }
            return scored.stream()
                    .sorted(Comparator.comparingInt((Scored item) -> item.score).reversed()
                            .thenComparing(Comparator.comparingLong((Scored item) -> item.ts).reversed()))
                    .limit(limit > 0 ? limit : Long.MAX_VALUE)
                    .map(item -> item.content)
                    .toList();
        }
    }

    /**
     * 确保内存缓存与磁盘对齐，外部增删文件后自动重载
     */
    private void ensureCacheLoaded() {
        if (!Files.isDirectory(root)) {
            cacheLoaded = false;
            cache.clear();
            knownFileNames.clear();
            return;
        }
        List<Path> files = listMemoryFiles();
        Set<String> currentNames = new HashSet<>();
        for (Path file : files) {
            currentNames.add(file.getFileName().toString());
        }
        if (cacheLoaded && currentNames.equals(knownFileNames)) {
            return;
        }
        cache.clear();
        knownFileNames = currentNames;
        for (Path file : files) {
            String name = file.getFileName().toString();
            String id = name.substring(0, name.length() - FILE_SUFFIX.length());
            Map<String, Object> record = readRecord(file);
            if (record != null) {
                cache.put(id, record);
            }
        }
        cacheLoaded = true;
    }

    /**
     * 删除记忆
     * @param memoryId
     */
    @Override
    public void delete(String memoryId) {
        if (memoryId == null || memoryId.isBlank()) {
            return;
        }
        synchronized (lock) {
            Path file = memoryFile(memoryId, true);
            if (file == null || !Files.exists(file)) {
                return;
            }
            deleteQuietly(file);
            removeFromCache(file);
        }
    }

    /**
     * 按租户作用域删除记忆
     * @param scopeId
     * @param memoryId
     */
    @Override
    public void delete(String scopeId, String memoryId) {
        if (memoryId == null || memoryId.isBlank()) {
            return;
        }
        synchronized (lock) {
            Path file = memoryFile(memoryId, true);
            if (file == null || !Files.exists(file)) {
                return;
            }
            Map<String, Object> record = readRecord(file);
            String ownerScope = record != null ? (String) record.get(KEY_SCOPE) : null;
            if (ownerScope == null) {
                deleteQuietly(file);
                removeFromCache(file);
                return;
            }
            // 所有权校验：记忆必须属于当前租户作用域才允许删除
            if (!ownerScope.equals(normalize(scopeId))) {
                throw new SecurityException("记忆删除所有权校验失败: memoryId=" + memoryId);
            }
            deleteQuietly(file);
            removeFromCache(file);
        }
    }

    /**
     * 删除文件后同步移除缓存条目
     * @param file
     */
    private void removeFromCache(Path file) {
        String name = file.getFileName().toString();
        cache.remove(name.substring(0, name.length() - FILE_SUFFIX.length()));
        knownFileNames.remove(name);
    }

    /**
     * 写入单条记忆记录到 {memoryId}.jsonl
     * @param memoryId
     * @param record
     */
    private void writeRecord(String memoryId, Map<String, Object> record) {
        try {
            Files.createDirectories(root);
            Path file = root.resolve(memoryId + FILE_SUFFIX);
            String line = MAPPER.writeValueAsString(record);
            Files.write(file, (line + "\n").getBytes(StandardCharsets.UTF_8),
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        } catch (IOException e) {
            throw new UncheckedIOException("记忆条目写入失败: memoryId=" + memoryId, e);
        }
    }

    /**
     * 读取单条记忆记录，解析失败或文件不存在返回null
     * @param file
     * @return
     */
    private Map<String, Object> readRecord(Path file) {
        try {
            String line = Files.readString(file, StandardCharsets.UTF_8);
            if (line.isBlank()) {
                return null;
            }
            return MAPPER.readValue(line, RECORD_TYPE);
        } catch (IOException e) {
            throw new UncheckedIOException("记忆条目读取失败: " + file, e);
        }
    }

    /**
     * 列出记忆目录下全部 jsonl 文件
     * @return
     */
    private List<Path> listMemoryFiles() {
        try (var stream = Files.list(root)) {
            return stream.filter(Files::isRegularFile).filter(file -> file.getFileName()
                            .toString().endsWith(FILE_SUFFIX))
                    .sorted().toList();
        } catch (IOException e) {
            throw new UncheckedIOException("记忆目录扫描失败: " + root, e);
        }
    }

    /**
     * 校验记忆ID为单段路径片段并定位文件，非法返回null
     * @param memoryId
     * @param enforceSingleSegment 是否强制单段校验
     * @return
     */
    private Path memoryFile(String memoryId, boolean enforceSingleSegment) {
        if (enforceSingleSegment && (memoryId.contains("/") || memoryId.contains("\\")
                || memoryId.equals(".") || memoryId.equals(".."))) {
            throw new IllegalArgumentException("非法记忆ID: " + memoryId);
        }
        Path file = root.resolve(memoryId + FILE_SUFFIX).normalize();
        return file.startsWith(root) ? file : null;
    }

    /**
     * 静默删除文件，不存在时忽略
     * @param file
     */
    private void deleteQuietly(Path file) {
        try {
            Files.deleteIfExists(file);
        } catch (IOException e) {
            throw new UncheckedIOException("记忆条目删除失败: " + file, e);
        }
    }

    /**
     * 校验记录属于当前租户作用域
     * @param record
     * @param scopeId
     * @return
     */
    private boolean matchesScope(Map<String, Object> record, String scopeId) {
        Object scope = record.get(KEY_SCOPE);
        return scope != null && scope.toString().equals(normalize(scopeId));
    }

    /**
     * 校验记录属于当前用户
     * @param record
     * @param userId
     * @return
     */
    private boolean matchesUser(Map<String, Object> record, String userId) {
        Object user = record.get("user");
        return user != null && user.toString().equals(normalize(userId));
    }

    /**
     * 简单分词：按非字母数字字符切分并小写
     * @param query
     * @return
     */
    private List<String> tokenize(String query) {
        List<String> tokens = new ArrayList<>();
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < query.length(); i++) {
            char c = query.charAt(i);
            if (Character.isLetterOrDigit(c)) {
                builder.append(Character.toLowerCase(c));
            } else if (builder.length() > 0) {
                tokens.add(builder.toString());
                builder.setLength(0);
            }
        }
        if (builder.length() > 0) {
            tokens.add(builder.toString());
        }
        return tokens;
    }

    /**
     * 计算记忆内容对查询词的命中数
     * @param content
     * @param tokens
     * @return
     */
    private int score(String content, List<String> tokens) {
        String text = content.toLowerCase();
        int hits = 0;
        for (String token : tokens) {
            if (text.contains(token)) {
                hits++;
            }
        }
        return hits;
    }

    /**
     * 空值归一为空串，与检索匹配的复合桶键语义一致
     * @param value
     * @return
     */
    private String normalize(String value) {
        return value != null ? value : "";
    }

    /**
     * 时间戳值转long，异常兜底为0
     * @param value
     * @return
     */
    private static long longValue(Object value) {
        return value instanceof Number number ? number.longValue() : 0L;
    }

    /**
     * 带分值的检索命中
     * @author yangqiong
     */
    private static final class Scored {

        /**
         * 命中内容
         */
        final String content;

        /**
         * 命中分值
         */
        final int score;

        /**
         * 创建时间戳
         */
        final long ts;

        Scored(String content, int score, long ts) {
            this.content = content;
            this.score = score;
            this.ts = ts;
        }
    }
}