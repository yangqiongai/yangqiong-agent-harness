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
package com.yangqiongai.agent.harness.local.knowledge;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Set;

import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.Parser;
import org.apache.tika.sax.BodyContentHandler;
import org.xml.sax.SAXException;

/**
 * 文档文本抽取
 * <p>
 * 纯文本类文档（默认 .md/.markdown/.txt）直接按 UTF-8 读取；其余支持格式
 * （默认 Office/PDF/网页/数据等）经 Tika 自动识别解析为纯文本，解析失败抛出
 * UncheckedIOException。扩展名白名单与抽取上限均可经 TextExtractionConfig 配置。
 * </p>
 * @author yangqiong
 */
public final class DocumentTextExtractor {

    /**
     * 默认抽取配置，与历史默认值保持一致
     */
    private static final TextExtractionConfig DEFAULT_CONFIG = TextExtractionConfig.builder().build();

    /**
     * 全局生效的抽取配置，可经 configure 覆盖
     */
    private static volatile TextExtractionConfig config = DEFAULT_CONFIG;

    /**
     * 文档解析器（按内容自动识别格式）
     */
    private static final Parser PARSER = new AutoDetectParser();

    private DocumentTextExtractor() {
    }

    /**
     * 按全局配置抽取文档文本为UTF-8字符串
     * @param file
     * @return
     */
    public static String extract(Path file) {
        return extract(file, config);
    }

    /**
     * 按指定抽取配置抽取文档文本，配置为空时使用全局配置
     * @param file
     * @param cfg
     * @return
     */
    public static String extract(Path file, TextExtractionConfig cfg) {
        TextExtractionConfig effective = cfg != null ? cfg : config;
        String name = file.getFileName().toString().toLowerCase(Locale.ROOT);
        if (isPlainText(name, effective)) {
            return readPlainText(file, effective);
        }
        return parseBinary(file, effective);
    }

    /**
     * 覆盖全局抽取配置，传入null恢复默认值
     * @param cfg
     */
    public static void configure(TextExtractionConfig cfg) {
        config = cfg != null ? cfg : DEFAULT_CONFIG;
    }

    /**
     * 判断是否为支持的文档，按全局配置的扩展名白名单判定
     * @param file
     * @return
     */
    public static boolean isSupported(Path file) {
        return isSupported(file, config);
    }

    /**
     * 判断是否为支持的文档，按指定配置的扩展名白名单判定
     * @param file
     * @param cfg
     * @return
     */
    public static boolean isSupported(Path file, TextExtractionConfig cfg) {
        TextExtractionConfig effective = cfg != null ? cfg : config;
        String name = file.getFileName().toString().toLowerCase(Locale.ROOT);
        for (String suffix : effective.supportedSuffixes()) {
            if (name.endsWith(suffix)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 判断是否为纯文本扩展名
     * @param name
     * @param cfg
     * @return
     */
    private static boolean isPlainText(String name, TextExtractionConfig cfg) {
        for (String suffix : cfg.plainTextSuffixes()) {
            if (name.endsWith(suffix)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 读取纯文本文件，超上限返回空串
     * @param file
     * @param cfg
     * @return
     */
    private static String readPlainText(Path file, TextExtractionConfig cfg) {
        try {
            if (Files.size(file) > cfg.maxPlainTextBytes()) {
                return "";
            }
            return new String(Files.readAllBytes(file), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("纯文本文档读取失败: " + file, e);
        }
    }

    /**
     * 解析二进制文档（docx/doc/pdf等），文件超过大小上限直接拒绝
     * @param file
     * @param cfg
     * @return
     */
    private static String parseBinary(Path file, TextExtractionConfig cfg) {
        try {
            if (Files.size(file) > cfg.maxBinaryBytes()) {
                throw new IllegalStateException("二进制文档超过大小上限(" + cfg.maxBinaryBytes() + "字节): " + file);
            }
        } catch (IOException e) {
            throw new UncheckedIOException("二进制文档读取失败: " + file, e);
        }
        try (InputStream in = Files.newInputStream(file)) {
            BodyContentHandler handler = new BodyContentHandler(cfg.maxTikaChars());
            Metadata metadata = new Metadata();
            PARSER.parse(in, handler, metadata, new ParseContext());
            return handler.toString();
        } catch (IOException e) {
            throw new UncheckedIOException("二进制文档读取失败: " + file, e);
        } catch (SAXException | TikaException e) {
            throw new UncheckedIOException("二进制文档解析失败: " + file, new IOException(e));
        }
    }

    /**
     * 文档文本抽取配置
     * <p>
     * 纯文本/支持文档的扩展名白名单与三个抽取上限均可自定义，默认值与历史常量保持一致：
     * 纯文本 .md/.markdown/.txt，支持格式含 Office/PDF/网页/数据等，
     * 纯文本2MB、Tika输出10MB字符、二进制50MB。
     * </p>
     * @author yangqiong
     */
    public static final class TextExtractionConfig {

        /**
         * 纯文本扩展名集合
         */
        private final Set<String> plainTextSuffixes;

        /**
         * 支持的文档扩展名集合（Tika 可解析的常见格式）
         */
        private final Set<String> supportedSuffixes;

        /**
         * 纯文本单文件读取上限（约2MB）
         */
        private final long maxPlainTextBytes;

        /**
         * Tika 抽取文本字符上限（约10MB）
         */
        private final int maxTikaChars;

        /**
         * 二进制文档大小上限（约50MB）
         */
        private final long maxBinaryBytes;

        private TextExtractionConfig(Builder builder) {
            this.plainTextSuffixes = Set.copyOf(builder.plainTextSuffixes);
            this.supportedSuffixes = Set.copyOf(builder.supportedSuffixes);
            this.maxPlainTextBytes = builder.maxPlainTextBytes;
            this.maxTikaChars = builder.maxTikaChars;
            this.maxBinaryBytes = builder.maxBinaryBytes;
        }

        /**
         * 获取纯文本扩展名集合
         * @return
         */
        public Set<String> plainTextSuffixes() {
            return plainTextSuffixes;
        }

        /**
         * 获取支持的文档扩展名集合
         * @return
         */
        public Set<String> supportedSuffixes() {
            return supportedSuffixes;
        }

        /**
         * 获取纯文本单文件读取上限
         * @return
         */
        public long maxPlainTextBytes() {
            return maxPlainTextBytes;
        }

        /**
         * 获取Tika抽取文本字符上限
         * @return
         */
        public int maxTikaChars() {
            return maxTikaChars;
        }

        /**
         * 获取二进制文档大小上限
         * @return
         */
        public long maxBinaryBytes() {
            return maxBinaryBytes;
        }

        /**
         * 创建配置构建器
         * @return
         */
        public static Builder builder() {
            return new Builder();
        }

        /**
         * 文档文本抽取配置构建器
         * @author yangqiong
         */
        public static final class Builder {

            /**
             * 纯文本扩展名集合
             */
            private Set<String> plainTextSuffixes = Set.of(".md", ".markdown", ".txt");

            /**
             * 支持的文档扩展名集合
             */
            private Set<String> supportedSuffixes = Set.of(".md", ".markdown", ".txt",
                    ".docx", ".doc", ".pdf", ".xlsx", ".xls", ".pptx", ".ppt", ".csv",
                    ".ods", ".odt", ".odp", ".html", ".htm", ".xml", ".json", ".yaml", ".yml", ".rtf");

            /**
             * 纯文本单文件读取上限
             */
            private long maxPlainTextBytes = 2 * 1024 * 1024L;

            /**
             * Tika 抽取文本字符上限
             */
            private int maxTikaChars = 10 * 1024 * 1024;

            /**
             * 二进制文档大小上限
             */
            private long maxBinaryBytes = 50 * 1024 * 1024L;

            /**
             * 设置纯文本扩展名集合
             * @param plainTextSuffixes
             * @return
             */
            public Builder plainTextSuffixes(Set<String> plainTextSuffixes) {
                this.plainTextSuffixes = plainTextSuffixes;
                return this;
            }

            /**
             * 设置支持的文档扩展名集合
             * @param supportedSuffixes
             * @return
             */
            public Builder supportedSuffixes(Set<String> supportedSuffixes) {
                this.supportedSuffixes = supportedSuffixes;
                return this;
            }

            /**
             * 设置纯文本单文件读取上限
             * @param maxPlainTextBytes
             * @return
             */
            public Builder maxPlainTextBytes(long maxPlainTextBytes) {
                this.maxPlainTextBytes = maxPlainTextBytes;
                return this;
            }

            /**
             * 设置Tika抽取文本字符上限
             * @param maxTikaChars
             * @return
             */
            public Builder maxTikaChars(int maxTikaChars) {
                this.maxTikaChars = maxTikaChars;
                return this;
            }

            /**
             * 设置二进制文档大小上限
             * @param maxBinaryBytes
             * @return
             */
            public Builder maxBinaryBytes(long maxBinaryBytes) {
                this.maxBinaryBytes = maxBinaryBytes;
                return this;
            }

            /**
             * 构建配置，扩展名集合不能为空，三个上限必须为正数
             * @return
             */
            public TextExtractionConfig build() {
                if (plainTextSuffixes == null || plainTextSuffixes.isEmpty()) {
                    throw new IllegalArgumentException("plainTextSuffixes不能为空");
                }
                if (supportedSuffixes == null || supportedSuffixes.isEmpty()) {
                    throw new IllegalArgumentException("supportedSuffixes不能为空");
                }
                if (maxPlainTextBytes <= 0) {
                    throw new IllegalArgumentException("maxPlainTextBytes必须为正数: " + maxPlainTextBytes);
                }
                if (maxTikaChars <= 0) {
                    throw new IllegalArgumentException("maxTikaChars必须为正数: " + maxTikaChars);
                }
                if (maxBinaryBytes <= 0) {
                    throw new IllegalArgumentException("maxBinaryBytes必须为正数: " + maxBinaryBytes);
                }
                return new TextExtractionConfig(this);
            }
        }
    }
}
