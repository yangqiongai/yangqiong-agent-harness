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

import java.nio.charset.CharacterCodingException;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

/**
 * 默认文档解析器（仅纯文本/UTF-8）
 * <p>
 * 作为引擎侧默认实现，仅支持UTF-8编码的纯文本文件
 * （txt/md/csv/json/xml等），二进制或非法编码返回error。
 * 平台侧可注入复用ai-rag的Tika解析器覆盖PDF/Word等复杂格式。
 * </p>
 * @author yangqiong
 */
public class TextDocumentParser implements DocumentParser {

    /**
     * 最大解析字节数，防止超大文件耗尽内存
     */
    private static final int MAX_BYTES = 2 * 1024 * 1024;

    /**
     * 支持解析的纯文本文件扩展名
     */
    private static final String[] TEXT_EXTENSIONS = {
            "txt", "md", "markdown", "csv", "json", "xml", "html", "htm", "log", "yaml", "yml", "properties"
    };

    /**
     * 解析文档内容，仅支持UTF-8纯文本
     * @param content
     * @param filename
     * @return
     */
    @Override
    public String parse(byte[] content, String filename) {
        if (content == null) {
            throw new IllegalArgumentException("文件内容为空");
        }
        if (content.length > MAX_BYTES) {
            throw new IllegalArgumentException("文件超过解析大小限制: " + content.length + " bytes");
        }
        String name = filename != null ? filename.toLowerCase(Locale.ROOT) : "";
        if (!isTextExtension(name)) {
            throw new IllegalArgumentException("不支持的文件格式: " + (filename != null ? filename : "未知")
                    + "，仅支持纯文本文件");
        }
        return decodeUtf8(content);
    }

    /**
     * 校验是否为纯文本扩展名
     * @param filename
     * @return
     */
    private boolean isTextExtension(String filename) {
        for (String ext : TEXT_EXTENSIONS) {
            if (filename.endsWith("." + ext)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 严格按UTF-8解码，非法字节抛出异常
     * @param content
     * @return
     */
    private String decodeUtf8(byte[] content) {
        CharsetDecoder decoder = StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT);
        try {
            return decoder.decode(java.nio.ByteBuffer.wrap(content)).toString();
        } catch (CharacterCodingException e) {
            throw new IllegalArgumentException("文件不是合法UTF-8文本，无法解析: " + e.getMessage());
        }
    }
}