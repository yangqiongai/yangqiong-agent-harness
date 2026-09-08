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

import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.zip.CRC32C;

/**
 * 哈希嵌入模型
 * <p>
 * 零依赖的确定性嵌入实现：基于字符n-gram做特征哈希，使用CRC32C生成稳定的
 * 哈希值并派生向量下标与符号，共享n-gram越多的文本余弦相似度越高。
 * 作为默认的轻量实现，供向量记忆与语义缓存在无外部向量服务时开箱使用。
 * </p>
 * @author yangqiong
 */
public class HashingEmbeddingModel implements EmbeddingModel {

    /**
     * 默认嵌入维度
     */
    public static final int DEFAULT_DIMENSION = 128;

    /**
     * 嵌入向量维度
     */
    private final int dim;

    public HashingEmbeddingModel() {
        this(DEFAULT_DIMENSION);
    }

    /**
     * 按指定维度构建哈希嵌入模型
     * @param dim
     */
    public HashingEmbeddingModel(int dim) {
        if (dim <= 0) {
            throw new IllegalArgumentException("嵌入维度必须为正数: " + dim);
        }
        this.dim = dim;
    }

    @Override
    public int dimension() {
        return dim;
    }

    @Override
    public float[] embed(String text) {
        float[] vector = new float[dim];
        if (text == null || text.isBlank()) {
            return vector;
        }
        String normalized = text.toLowerCase(Locale.ROOT);
        int length = normalized.length();
        for (int i = 0; i < length; i++) {
            // 以2~4字符n-gram作为特征，共享子串的文本获得相近的向量
            for (int size = 2; size <= 4 && i + size <= length; size++) {
                addFeature(vector, normalized.substring(i, i + size));
            }
        }
        if (length < 2) {
            addFeature(vector, normalized);
        }
        return normalize(vector);
    }

    /**
     * 将特征以双哈希方式累加进向量，降低不同特征碰撞抵消的概率
     * @param vector
     * @param token
     */
    private void addFeature(float[] vector, String token) {
        long h1 = hash(token, 1);
        long h2 = hash(token, 2);
        vector[(int) Math.floorMod(h1, dim)] += (h1 & 0x80000000L) == 0 ? 1.0f : -1.0f;
        int idx2 = (int) Math.floorMod(h2, dim);
        vector[idx2] += (h2 & 0x80000000L) == 0 ? 1.0f : -1.0f;
    }

    /**
     * 计算特征的确定性CRC32C哈希值
     * @param token
     * @param seed
     * @return
     */
    private long hash(String token, int seed) {
        CRC32C crc = new CRC32C();
        crc.update(token.getBytes(StandardCharsets.UTF_8));
        crc.update(seed);
        return crc.getValue();
    }

    /**
     * 向量归一化为单位长度，全零向量保持不变
     * @param vector
     * @return
     */
    private float[] normalize(float[] vector) {
        double norm = 0.0d;
        for (float v : vector) {
            norm += v * v;
        }
        if (norm == 0.0d) {
            return vector;
        }
        double scale = Math.sqrt(norm);
        for (int i = 0; i < vector.length; i++) {
            vector[i] = (float) (vector[i] / scale);
        }
        return vector;
    }
}
