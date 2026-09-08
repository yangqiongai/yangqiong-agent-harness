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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * 文档文本抽取测试
 * @author yangqiong
 */
class DocumentTextExtractorTest {

    @TempDir
    Path tempDir;

    @Test
    void shouldExtractPlainText() throws Exception {
        Path md = tempDir.resolve("guide.md");
        Files.writeString(md, "本地智能体使用指南");
        assertThat(DocumentTextExtractor.extract(md)).isEqualTo("本地智能体使用指南");

        Path txt = tempDir.resolve("faq.txt");
        Files.writeString(txt, "常见问题解答");
        assertThat(DocumentTextExtractor.extract(txt)).isEqualTo("常见问题解答");
    }

    @Test
    void shouldExtractDocxText() throws Exception {
        Path docx = tempDir.resolve("note.docx");
        Files.write(docx, DocFixtures.minimalDocx("Hello docx parsing"));
        assertThat(DocumentTextExtractor.extract(docx)).contains("docx");
    }

    @Test
    void shouldExtractPdfText() throws Exception {
        Path pdf = tempDir.resolve("report.pdf");
        Files.write(pdf, DocFixtures.minimalPdf());
        assertThat(DocumentTextExtractor.extract(pdf)).contains("Hello PDF");
    }

    @Test
    void shouldExtractHtmlTextWithoutTags() throws Exception {
        Path html = tempDir.resolve("page.html");
        Files.writeString(html, "<html><body><p>预算报告内容</p></body></html>");
        assertThat(DocumentTextExtractor.extract(html)).contains("预算报告内容").doesNotContain("<");
    }

    @Test
    void shouldRejectBinaryOverSizeLimit() throws Exception {
        Path docx = tempDir.resolve("big.docx");
        Files.write(docx, DocFixtures.minimalDocx("内容"));
        // 用极小上限验证超限拒绝，不依赖真实50MB大文件
        DocumentTextExtractor.TextExtractionConfig cfg = DocumentTextExtractor.TextExtractionConfig.builder()
                .maxBinaryBytes(16)
                .build();
        assertThatThrownBy(() -> DocumentTextExtractor.extract(docx, cfg))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void shouldOverrideLimitViaConfigure() throws Exception {
        Path md = tempDir.resolve("oversize.md");
        Files.writeString(md, "abcdef");
        try {
            DocumentTextExtractor.configure(DocumentTextExtractor.TextExtractionConfig.builder()
                    .maxPlainTextBytes(4)
                    .build());
            // 全局配置生效后，超出纯文本上限按空文本处理
            assertThat(DocumentTextExtractor.extract(md)).isEmpty();
        } finally {
            // 恢复默认配置，避免影响其它用例
            DocumentTextExtractor.configure(null);
        }
    }

    @Test
    void shouldRejectNonPositiveConfig() {
        assertThatThrownBy(() -> DocumentTextExtractor.TextExtractionConfig.builder()
                .maxBinaryBytes(0)
                .build()).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldHonorCustomSuffixConfig() throws Exception {
        Path log = tempDir.resolve("app.log");
        Files.writeString(log, "自定义扩展名日志");
        DocumentTextExtractor.TextExtractionConfig cfg = DocumentTextExtractor.TextExtractionConfig.builder()
                .plainTextSuffixes(Set.of(".md", ".markdown", ".txt", ".log"))
                .supportedSuffixes(Set.of(".md", ".txt", ".log"))
                .build();
        // 自定义纯文本白名单后，.log 按 UTF-8 直接读取
        assertThat(DocumentTextExtractor.extract(log, cfg)).isEqualTo("自定义扩展名日志");
        assertThat(DocumentTextExtractor.isSupported(log, cfg)).isTrue();
        assertThat(DocumentTextExtractor.isSupported(tempDir.resolve("a.pdf"), cfg)).isFalse();
    }

    @Test
    void shouldRejectEmptySuffixConfig() {
        assertThatThrownBy(() -> DocumentTextExtractor.TextExtractionConfig.builder()
                .supportedSuffixes(Set.of())
                .build()).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldRecognizeSupportedSuffixes() {
        assertThat(DocumentTextExtractor.isSupported(tempDir.resolve("a.md"))).isTrue();
        assertThat(DocumentTextExtractor.isSupported(tempDir.resolve("a.txt"))).isTrue();
        assertThat(DocumentTextExtractor.isSupported(tempDir.resolve("a.docx"))).isTrue();
        assertThat(DocumentTextExtractor.isSupported(tempDir.resolve("a.doc"))).isTrue();
        assertThat(DocumentTextExtractor.isSupported(tempDir.resolve("a.pdf"))).isTrue();
        assertThat(DocumentTextExtractor.isSupported(tempDir.resolve("a.xlsx"))).isTrue();
        assertThat(DocumentTextExtractor.isSupported(tempDir.resolve("a.xls"))).isTrue();
        assertThat(DocumentTextExtractor.isSupported(tempDir.resolve("a.pptx"))).isTrue();
        assertThat(DocumentTextExtractor.isSupported(tempDir.resolve("a.csv"))).isTrue();
        assertThat(DocumentTextExtractor.isSupported(tempDir.resolve("a.html"))).isTrue();
        assertThat(DocumentTextExtractor.isSupported(tempDir.resolve("a.json"))).isTrue();
        assertThat(DocumentTextExtractor.isSupported(tempDir.resolve("a.rtf"))).isTrue();
        assertThat(DocumentTextExtractor.isSupported(tempDir.resolve("a.bin"))).isFalse();
    }
}
