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

import java.util.Map;

/**
 * 检索结果块
 * @author yangqiong
 * @param content 检索内容
 * @param score 匹配分值
 * @param source 来源标识
 * @param metadata 扩展元数据
 */
public record RetrievedChunk(
        String content,
        double score,
        String source,
        Map<String, Object> metadata
) {

    /**
     * 创建检索结果块
     * @param content
     * @param score
     * @param source
     * @return
     */
    public static RetrievedChunk of(String content, double score, String source) {
        return new RetrievedChunk(content, score, source, Map.of());
    }

    /**
     * 创建检索结果块
     * @param content
     * @param score
     * @param source
     * @param metadata
     * @return
     */
    public static RetrievedChunk of(String content, double score, String source, Map<String, Object> metadata) {
        return new RetrievedChunk(content, score, source, metadata);
    }
}