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

import java.util.List;
import java.util.Map;

/**
 * 检索器
 * <p>
 * 根据查询条件从文档索引中检索相关结果块，支持 topK 截断和过滤器。
 * </p>
 * @author yangqiong
 */
public interface Retriever {

    /**
     * 执行检索
     * @param query 查询文本
     * @param topK 返回结果上限
     * @param filters 过滤条件，按 key/value 匹配 metadata
     * @return
     */
    List<RetrievedChunk> retrieve(String query, int topK, Map<String, Object> filters);
}