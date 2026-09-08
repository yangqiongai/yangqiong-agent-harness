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
package com.yangqiongai.agent.harness.starter.autoconfigure;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import com.yangqiongai.agent.harness.model.embedding.EmbeddingModel;
import com.yangqiongai.agent.harness.model.embedding.HashingEmbeddingModel;
import com.yangqiongai.agent.harness.rag.RetrievedChunk;
import com.yangqiongai.agent.harness.rag.Retriever;
import com.yangqiongai.agent.harness.store.vector.PersistentVectorStore;

/**
 * 向量存储自动装配测试
 * @author yangqiong
 */
class AgentVectorAutoConfigurationTest {

    /**
     * 上下文运行器，装配向量存储自动配置类
     */
    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(AgentVectorAutoConfiguration.class));

    /**
     * 向量存储自动装配默认关闭
     */
    @Test
    void shouldSkipVectorByDefault() {
        contextRunner.run(context -> {
            assertThat(context).doesNotHaveBean(PersistentVectorStore.class);
            assertThat(context).doesNotHaveBean(Retriever.class);
        });
    }

    /**
     * 启用后装配Lucene向量存储与语义检索器，写入内容可召回
     */
    @Test
    void shouldAssembleLuceneVectorStoreWhenEnabled(@TempDir Path dataDir) {
        contextRunner
                .withPropertyValues(
                        "ai.harness.vector.enabled=true",
                        "ai.harness.vector.store=lucene",
                        "ai.harness.vector.config.dataDir=" + dataDir.toAbsolutePath())
                .run(context -> {
                    assertThat(context).hasSingleBean(PersistentVectorStore.class);
                    assertThat(context).hasSingleBean(Retriever.class);
                    context.getBean(PersistentVectorStore.class).vectorRetriever()
                            .addContent("泱穹智能体框架支持向量检索", Map.of("source", "doc-1"));
                    List<RetrievedChunk> hits = context.getBean(Retriever.class)
                            .retrieve("向量检索", 5, null);
                    assertThat(hits).isNotEmpty();
                    assertThat(hits.get(0).content()).contains("向量检索");
                });
    }

    /**
     * 容器内嵌入模型Bean优先于缺省哈希嵌入
     */
    @Test
    void shouldPreferEmbeddingModelBean(@TempDir Path dataDir) {
        contextRunner
                .withBean(EmbeddingModel.class, () -> new HashingEmbeddingModel(16))
                .withPropertyValues(
                        "ai.harness.vector.enabled=true",
                        "ai.harness.vector.store=lucene",
                        "ai.harness.vector.config.dataDir=" + dataDir.toAbsolutePath())
                .run(context -> {
                    assertThat(context).hasSingleBean(PersistentVectorStore.class);
                    assertThat(context.getBean(EmbeddingModel.class).dimension()).isEqualTo(16);
                });
    }
}
