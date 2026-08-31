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
package com.yangqiong.agent.harness.rag;

/**
 * 文档解析器 SPI
 * <p>
 * 定义引擎侧文档解析契约，供Agent文件工具读取上传文件内容。
 * 引擎提供默认{@link TextDocumentParser}（仅纯文本/UTF-8），
 * 平台侧可注入复用ai-rag的Tika解析器（PDF/Word/表格等）。
 * </p>
 * @author yangqiong
 */
public interface DocumentParser {

    /**
     * 解析文档内容为文本
     * @param content 文件字节内容
     * @param filename 文件名（用于识别格式）
     * @return 解析出的纯文本内容，无法解析时抛出异常
     */
    String parse(byte[] content, String filename);
}