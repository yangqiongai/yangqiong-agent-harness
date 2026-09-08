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
package com.yangqiongai.agent.harness.model.embedding;

/**
 * 文本嵌入模型
 * <p>
 * 将文本映射为固定维度的稠密向量，用于语义检索、记忆存储与语义缓存。
 * 外部可实现为基于本地哈希的轻量实现，或接入远端向量化服务。
 * </p>
 * @author yangqiong
 */
public interface EmbeddingModel {

    /**
     * 计算文本嵌入向量，输入为null或空文本时返回全零向量
     * @param text
     * @return
     */
    float[] embed(String text);

    /**
     * 获取嵌入向量维度
     * @return
     */
    int dimension();

    /**
     * 计算两个向量间的余弦相似度，任一向量为全零时返回0
     * @param a
     * @param b
     * @return
     */
    static double cosine(float[] a, float[] b) {
        if (a == null || b == null || a.length == 0 || b.length == 0 || a.length != b.length) {
            return 0.0d;
        }
        double dot = 0.0d;
        double normA = 0.0d;
        double normB = 0.0d;
        for (int i = 0; i < a.length; i++) {
            dot += a[i] * b[i];
            normA += a[i] * a[i];
            normB += b[i] * b[i];
        }
        if (normA == 0.0d || normB == 0.0d) {
            return 0.0d;
        }
        return dot / (Math.sqrt(normA) * Math.sqrt(normB));
    }
}
