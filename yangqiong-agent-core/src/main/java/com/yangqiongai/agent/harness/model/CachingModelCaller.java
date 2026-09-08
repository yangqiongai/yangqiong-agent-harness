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
import java.util.Collections;
import java.util.List;
import java.util.Map;

import com.yangqiongai.agent.harness.core.message.AgentMessage;
import com.yangqiongai.agent.harness.core.model.AgentChatResponse;
import com.yangqiongai.agent.harness.core.model.AgentGenerateOptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * 缓存模型调用器
 * <p>
 * 包装{@link ModelCaller}，在调用前查询缓存，命中时直接返回缓存结果；
 * 未命中时调用底层模型并将结果写入缓存。流式调用缓存完整响应分片列表。
 * </p>
 * @author yangqiong
 */
public class CachingModelCaller extends ModelCaller {

    private static final Logger log = LoggerFactory.getLogger(CachingModelCaller.class);

    /**
     * 底层模型调用器
     */
    private final ModelCaller delegate;

    /**
     * 模型缓存
     */
    private final ModelCache cache;

    /**
     * 响应解析器（复用实例）
     */
    private final ModelResponseParser parser = new ModelResponseParser();

    public CachingModelCaller(ModelCaller delegate, ModelCache cache) {
        // CachingModelCaller将所有call/stream转发到delegate，不直接使用super的model字段
        super(null);
        this.delegate = delegate;
        this.cache = cache;
    }

    /**
     * 设置生成选项时同步传播给delegate，确保底层调用使用一致选项
     * @param generateOptions
     */
    @Override
    public void setGenerateOptions(AgentGenerateOptions generateOptions) {
        super.setGenerateOptions(generateOptions);
        if (delegate != null) {
            delegate.setGenerateOptions(generateOptions);
        }
    }

    @Override
    public Mono<AgentChatResponse> call(List<AgentMessage> messages, List<Map<String, Object>> toolSchemas) {
        List<AgentChatResponse> cached = cache.get(messages, toolSchemas, getGenerateOptions());
        if (cached != null && !cached.isEmpty()) {
            log.debug("[Cache] 命中模型调用缓存: messages={}", messages != null ? messages.size() : 0);
            AgentChatResponse merged = parser.mergeResponses(cached);
            return Mono.just(merged);
        }
        return delegate.call(messages, toolSchemas)
                .doOnNext(response -> {
                    List<AgentChatResponse> single = new ArrayList<>();
                    single.add(response);
                    cache.put(messages, toolSchemas, getGenerateOptions(), single);
                });
    }

    @Override
    public Flux<AgentChatResponse> stream(List<AgentMessage> messages, List<Map<String, Object>> toolSchemas) {
        List<AgentChatResponse> cached = cache.get(messages, toolSchemas, getGenerateOptions());
        if (cached != null && !cached.isEmpty()) {
            log.debug("[Cache] 命中模型流式调用缓存: messages={}", messages != null ? messages.size() : 0);
            return Flux.fromIterable(cached);
        }
        List<AgentChatResponse> accumulated = Collections.synchronizedList(new ArrayList<>());
        return delegate.stream(messages, toolSchemas)
                .doOnNext(accumulated::add)
                .doOnComplete(() -> {
                    if (!accumulated.isEmpty()) {
                        cache.put(messages, toolSchemas, getGenerateOptions(), accumulated);
                    }
                });
    }
}
