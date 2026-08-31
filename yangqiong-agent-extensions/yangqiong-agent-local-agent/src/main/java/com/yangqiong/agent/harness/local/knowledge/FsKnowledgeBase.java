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
package com.yangqiong.agent.harness.local.knowledge;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Stream;

import com.yangqiong.agent.harness.rag.InMemoryRetriever;
import com.yangqiong.agent.harness.rag.Retriever;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 文件系统知识库
 * <p>
 * 扫描工作区 knowledge/ 目录下的常见文档（文本/Office/PDF/网页/数据等）构建检索索引
 * （文件相对路径=文档id），关键词检索 + Lucene 全文索引混合检索；
 * saveDoc 支持宿主写回文档（反向），目录不存在或为空时返回空检索器，无副作用。
 * </p>
 * @author yangqiong
 */
public class FsKnowledgeBase {

    /**
     * 日志
     */
    private static final Logger log = LoggerFactory.getLogger(FsKnowledgeBase.class);

    /**
     * 支持的知识文档扩展名
     */
    private static final String[] SUPPORTED_SUFFIXES = {".md", ".txt", ".markdown",
            ".docx", ".doc", ".pdf", ".xlsx", ".xls", ".pptx", ".ppt", ".csv",
            ".ods", ".odt", ".odp", ".html", ".htm", ".xml", ".json", ".yaml", ".yml", ".rtf"};

    /**
     * 知识目录
     */
    private final Path root;

    /**
     * 检索器（索引快照，写回或 reindex 后重建）
     */
    private Retriever retriever;

    /**
     * 知识库是否无可用文档
     */
    private boolean empty;

    /**
     * 已索引文档数
     */
    private int count;

    /**
     * 索引重建串行锁
     */
    private final Object lock = new Object();

    /**
     * 以目录构建知识库
     * @param knowledgeDir
     */
    public FsKnowledgeBase(Path knowledgeDir) {
        if (knowledgeDir == null) {
            throw new IllegalArgumentException("知识目录不能为空");
        }
        this.root = knowledgeDir.toAbsolutePath().normalize();
        rebuildIndex();
    }

    /**
     * 打开知识库，目录不存在或为空时返回空检索器
     * @param knowledgeDir
     * @return
     */
    public static FsKnowledgeBase open(Path knowledgeDir) {
        return new FsKnowledgeBase(knowledgeDir);
    }

    /**
     * 获取检索器
     * @return
     */
    public Retriever retriever() {
        return retriever;
    }

    /**
     * 判断知识库是否为空（无可用文档）
     * @return
     */
    public boolean isEmpty() {
        return empty;
    }

    /**
     * 获取已索引文档数
     * @return
     */
    public int size() {
        return count;
    }

    /**
     * 重新扫描知识目录并热刷新检索索引
     */
    public void reindex() {
        synchronized (lock) {
            rebuildIndex();
        }
    }

    /**
     * 写回知识文档到当前目录并原地重建索引，供宿主程序化保存
     * @param relativePath
     * @param content
     * @return
     */
    public FsKnowledgeBase saveDoc(String relativePath, String content) {
        synchronized (lock) {
            Path resolved = resolveBound(root, relativePath);
            try {
                Files.createDirectories(resolved.getParent());
                Files.writeString(resolved, content == null ? "" : content, StandardCharsets.UTF_8);
            } catch (IOException e) {
                throw new UncheckedIOException("知识文档保存失败: " + resolved, e);
            }
            rebuildIndex();
            return this;
        }
    }

    /**
     * 扫描目录重建关键词与全文混合检索器
     */
    private void rebuildIndex() {
        Map<String, String> documents = new LinkedHashMap<>();
        if (Files.isDirectory(root)) {
            indexDocuments(documents);
        }
        InMemoryRetriever keyword = new InMemoryRetriever(documents);
        LuceneFullTextRetriever fullText = new LuceneFullTextRetriever(documents);
        this.retriever = new HybridRetriever(keyword, fullText);
        this.empty = documents.isEmpty();
        this.count = documents.size();
    }

    /**
     * 递归扫描知识目录，抽取文档文本构建相对路径->内容索引
     * @param documents
     */
    private void indexDocuments(Map<String, String> documents) {
        try (Stream<Path> walk = Files.walk(root)) {
            walk.filter(Files::isRegularFile)
                    .filter(this::isSupportedDoc)
                    .sorted()
                    .forEach(file -> {
                        try {
                            documents.put(relativeKey(file), DocumentTextExtractor.extract(file));
                        } catch (RuntimeException e) {
                            // 单文件解析失败不影响整体索引
                            log.warn("知识文档解析失败，已跳过: {}", file, e);
                        }
                    });
        } catch (IOException e) {
            throw new UncheckedIOException("知识目录扫描失败: " + root, e);
        }
    }

    /**
     * 计算文件相对知识目录的路径作为文档id
     * @param file
     * @return
     */
    private String relativeKey(Path file) {
        return root.relativize(file).toString().replace('\\', '/');
    }

    /**
     * 判断是否为支持的知识文档
     * @param file
     * @return
     */
    private boolean isSupportedDoc(Path file) {
        String name = file.getFileName().toString().toLowerCase();
        for (String suffix : SUPPORTED_SUFFIXES) {
            if (name.endsWith(suffix)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 写回知识文档到目录并重建索引，供宿主程序化保存
     * <p>
     * 注意：新实例而非原地更新；装配前调用以纳入本次运行时索引。
     * </p>
     * @param knowledgeDir
     * @param relativePath
     * @param content
     * @return
     */
    public static FsKnowledgeBase saveDoc(Path knowledgeDir, String relativePath, String content) {
        if (knowledgeDir == null) {
            throw new IllegalArgumentException("知识目录不能为空");
        }
        Path resolved = resolveBound(knowledgeDir, relativePath);
        try {
            Files.createDirectories(resolved.getParent());
            Files.writeString(resolved, content == null ? "" : content, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("知识文档保存失败: " + resolved, e);
        }
        return open(knowledgeDir);
    }

    /**
     * 解析相对路径并做越界校验，非法路径抛出IllegalArgumentException
     * @param knowledgeDir
     * @param relativePath
     * @return
     */
    private static Path resolveBound(Path knowledgeDir, String relativePath) {
        if (relativePath == null || relativePath.isBlank()) {
            throw new IllegalArgumentException("相对路径不能为空");
        }
        Path base = knowledgeDir.toAbsolutePath().normalize();
        Path resolved = base.resolve(relativePath).normalize();
        if (!resolved.startsWith(base)) {
            throw new IllegalArgumentException("路径越出知识目录边界: " + relativePath);
        }
        return resolved;
    }
}