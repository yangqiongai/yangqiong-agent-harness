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
package com.yangqiongai.agent.harness.store.vector;

import java.util.Map;

/**
 * 向量检索命中
 * @param id 记录id
 * @param score 相似度分值（降序排列）
 * @param content 内容文本
 * @param metadata 元数据（null时归一为空映射）
 * @author yangqiong
 */
public record VectorSearchHit(String id, float score, String content, Map<String, Object> metadata) {

    public VectorSearchHit {
        metadata = metadata == null ? Map.of() : metadata;
    }
}
