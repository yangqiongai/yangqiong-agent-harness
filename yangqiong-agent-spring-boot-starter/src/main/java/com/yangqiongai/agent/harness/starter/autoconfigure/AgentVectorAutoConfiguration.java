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

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

import com.yangqiongai.agent.harness.model.embedding.EmbeddingModel;
import com.yangqiongai.agent.harness.model.embedding.HashingEmbeddingModel;
import com.yangqiongai.agent.harness.rag.Retriever;
import com.yangqiongai.agent.harness.starter.config.HarnessVectorProperties;
import com.yangqiongai.agent.harness.store.vector.PersistentVectorStore;
import com.yangqiongai.agent.harness.store.vector.VectorIndexes;

/**
 * 向量存储自动装配
 * <p>
 * 启用 ai.harness.vector 后按 store 标识经 ServiceLoader 装配向量索引，
 * 产出{@link PersistentVectorStore}与语义检索器；类路径无 store-vector 模块时整体跳过。
 * 嵌入模型优先注入容器内Bean，缺省降级为零依赖的哈希嵌入实现。
 * </p>
 * @author yangqiong
 */
@AutoConfiguration
@ConditionalOnClass(PersistentVectorStore.class)
@ConditionalOnProperty(prefix = "ai.harness.vector", name = "enabled", havingValue = "true")
@EnableConfigurationProperties(HarnessVectorProperties.class)
public class AgentVectorAutoConfiguration {

    /**
     * 向量存储Bean，按配置创建向量索引并与嵌入模型绑定，上下文关闭时释放底层资源
     * @param properties
     * @param embeddingModelProvider
     * @return
     */
    @Bean(destroyMethod = "close")
    @ConditionalOnMissingBean
    public PersistentVectorStore persistentVectorStore(HarnessVectorProperties properties,
                                                       ObjectProvider<EmbeddingModel> embeddingModelProvider) {
        EmbeddingModel embeddingModel = embeddingModelProvider
                .getIfAvailable(HashingEmbeddingModel::new);
        return PersistentVectorStore.open(
                VectorIndexes.create(properties.getStore(), properties.getConfig()), embeddingModel);
    }

    /**
     * 语义向量检索器Bean，跨集合检索；使用方自定义Retriever后本装配自动失效
     * @param vectorStore
     * @return
     */
    @Bean
    @ConditionalOnMissingBean(Retriever.class)
    public Retriever vectorRetriever(PersistentVectorStore vectorStore) {
        return vectorStore.retriever();
    }
}
