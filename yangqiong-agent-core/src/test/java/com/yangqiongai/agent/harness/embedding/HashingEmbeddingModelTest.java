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
package com.yangqiongai.agent.harness.embedding;

import static org.assertj.core.api.Assertions.assertThat;

import com.yangqiongai.agent.harness.model.embedding.EmbeddingModel;
import com.yangqiongai.agent.harness.model.embedding.HashingEmbeddingModel;
import org.junit.jupiter.api.Test;

/**
 * 哈希嵌入模型测试
 * @author yangqiong
 */
class HashingEmbeddingModelTest {

    @Test
    void deterministicVector() {
        HashingEmbeddingModel model = new HashingEmbeddingModel();
        assertThat(model.embed("语义记忆测试文本")).isEqualTo(model.embed("语义记忆测试文本"));
    }

    @Test
    void correctDimension() {
        HashingEmbeddingModel model = new HashingEmbeddingModel();
        assertThat(model.dimension()).isEqualTo(128);
        assertThat(model.embed("任意文本")).hasSize(128);
    }

    @Test
    void similarTextsHaveHighCosine() {
        HashingEmbeddingModel model = new HashingEmbeddingModel();
        float[] a = model.embed("今天北京的天气如何");
        float[] b = model.embed("今天北京的天气怎么样");
        float[] c = model.embed("如何制作一份美味的蛋糕");

        double similar = EmbeddingModel.cosine(a, b);
        double unrelated = EmbeddingModel.cosine(a, c);

        assertThat(similar).isGreaterThan(unrelated);
        assertThat(similar).isGreaterThan(0.5);
    }

    @Test
    void blankTextReturnsZeroVector() {
        HashingEmbeddingModel model = new HashingEmbeddingModel();
        assertThat(model.embed(null)).containsOnly(0.0f);
        assertThat(model.embed("   ")).containsOnly(0.0f);
    }
}
