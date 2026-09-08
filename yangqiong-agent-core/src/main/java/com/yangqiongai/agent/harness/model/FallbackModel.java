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
package com.yangqiongai.agent.harness.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import com.yangqiongai.agent.harness.core.message.AgentMessage;
import com.yangqiongai.agent.harness.core.model.AgentChatResponse;
import com.yangqiongai.agent.harness.core.model.AgentGenerateOptions;
import com.yangqiongai.agent.harness.core.model.AgentModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;

/**
 * 回退模型
 * <p>
 * 包装多个AgentModel，按优先级依次尝试调用。主模型失败时自动切换到回退模型，
 * 保障Agent在模型不可用时仍能降级运行。
 * </p>
 * @author yangqiong
 */
public class FallbackModel implements AgentModel {

    private static final Logger log = LoggerFactory.getLogger(FallbackModel.class);

    /**
     * 模型列表（按优先级排序，第一个为主模型）
     */
    private final List<AgentModel> models;

    public FallbackModel(List<AgentModel> models) {
        if (models == null || models.isEmpty()) {
            throw new IllegalArgumentException("回退模型列表不能为空");
        }
        this.models = new ArrayList<>(models);
    }

    /**
     * 创建回退模型构建器
     * @return
     */
    public static Builder builder() {
        return new Builder();
    }

    @Override
    public AgentChatResponse generate(List<AgentMessage> messages, List<Map<String, Object>> tools,
                                        AgentGenerateOptions options) {
        Exception lastError = null;
        for (int i = 0; i < models.size(); i++) {
            AgentModel model = models.get(i);
            try {
                return model.generate(messages, tools, options);
            } catch (Exception e) {
                log.warn("[Fallback] 模型{}调用失败({}/{}): {}",
                        i == 0 ? "主模型" : "回退模型" + i, i + 1, models.size(), e.getMessage());
                lastError = e;
            }
        }
        throw new RuntimeException("所有模型均调用失败", lastError);
    }

    @Override
    public Flux<AgentChatResponse> stream(List<AgentMessage> messages, List<Map<String, Object>> tools,
                                            AgentGenerateOptions options) {
        return streamWithFallback(messages, tools, options, 0);
    }

    /**
     * 递归尝试流式调用，当前模型失败时切换到下一个
     * <p>
     * 与{@link RetryableModelCaller}一致以首片已发射为守卫：模型已向下游发出
     * 部分分片后失败时不可降级重试，否则回退模型从头生成会造成内容重复拼接，
     * 此时只能向下游传播错误。
     * </p>
     * @param messages
     * @param tools
     * @param options
     * @param index
     * @return
     */
    private Flux<AgentChatResponse> streamWithFallback(List<AgentMessage> messages,
                                                         List<Map<String, Object>> tools,
                                                         AgentGenerateOptions options, int index) {
        if (index >= models.size()) {
            return Flux.error(new RuntimeException("所有模型均调用失败"));
        }
        int modelIndex = index;
        AtomicBoolean firstReceived = new AtomicBoolean(false);
        return models.get(index).stream(messages, tools, options)
                .doOnNext(chunk -> firstReceived.set(true))
                .onErrorResume(error -> {
                    log.warn("[Fallback] 模型{}流式调用失败({}/{}): {}",
                            modelIndex == 0 ? "主模型" : "回退模型" + modelIndex,
                            modelIndex + 1, models.size(), error.getMessage());
                    if (firstReceived.get()) {
                        // 首片已发射，降级重试会导致内容重复，只能传播错误
                        log.warn("[Fallback] 已向下游发射部分分片，放弃降级重试");
                        return Flux.error(error);
                    }
                    return streamWithFallback(messages, tools, options, modelIndex + 1);
                });
    }

    /**
     * 回退模型构建器
     * @author yangqiong
     */
    public static class Builder {

        /**
         * 模型列表
         */
        private final List<AgentModel> modelList = new ArrayList<>();

        /**
         * 添加主模型
         * @param model
         * @return
         */
        public Builder primary(AgentModel model) {
            modelList.add(0, model);
            return this;
        }

        /**
         * 添加回退模型
         * @param model
         * @return
         */
        public Builder fallback(AgentModel model) {
            modelList.add(model);
            return this;
        }

        public FallbackModel build() {
            return new FallbackModel(modelList);
        }
    }
}
