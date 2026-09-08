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

import java.nio.file.Paths;
import java.util.Map;

/**
 * Lucene本地向量索引提供方
 * <p>
 * 随 store-vector 内置注册，零外部依赖的默认实现，配置键 dataDir 指定落盘目录。
 * </p>
 * @author yangqiong
 */
public class LuceneVectorIndexProvider implements VectorIndexProvider {

    @Override
    public String store() {
        return "lucene";
    }

    @Override
    public VectorIndex create(Map<String, String> config) {
        String dataDir = config.get("dataDir");
        if (dataDir == null || dataDir.isBlank()) {
            throw new IllegalArgumentException("lucene向量索引缺少配置: dataDir");
        }
        return new LuceneVectorIndex(Paths.get(dataDir));
    }
}
