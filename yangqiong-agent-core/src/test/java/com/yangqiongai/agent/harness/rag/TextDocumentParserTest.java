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
package com.yangqiongai.agent.harness.rag;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

/**
 * 默认文档解析器测试
 * @author yangqiong
 */
class TextDocumentParserTest {

    private final TextDocumentParser parser = new TextDocumentParser();

    @Test
    void shouldParseUtf8TextFile() {
        String content = parser.parse("hello harness".getBytes(StandardCharsets.UTF_8), "notes.txt");
        assertThat(content).isEqualTo("hello harness");
    }

    @Test
    void shouldParseMarkdownAndCsv() {
        assertThat(parser.parse("# 标题".getBytes(StandardCharsets.UTF_8), "readme.md"))
                .isEqualTo("# 标题");
        assertThat(parser.parse("a,b\n1,2".getBytes(StandardCharsets.UTF_8), "data.csv"))
                .isEqualTo("a,b\n1,2");
    }

    @Test
    void shouldRejectUnsupportedFormat() {
        byte[] pdfHeader = new byte[]{(byte) 0x25, (byte) 0x50, (byte) 0x44, (byte) 0x46};
        assertThatThrownBy(() -> parser.parse(pdfHeader, "doc.pdf"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("不支持的文件格式");
    }

    @Test
    void shouldRejectInvalidUtf8() {
        byte[] invalid = new byte[]{(byte) 0xC3, (byte) 0x28};
        assertThatThrownBy(() -> parser.parse(invalid, "bad.txt"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("UTF-8");
    }

    @Test
    void shouldRejectNullContent() {
        assertThatThrownBy(() -> parser.parse(null, "a.txt"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("为空");
    }
}