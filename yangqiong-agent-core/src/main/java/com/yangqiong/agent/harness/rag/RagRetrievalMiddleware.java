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

import com.yangqiong.agent.harness.core.event.AgentEvent;
import com.yangqiong.agent.harness.core.message.AgentMessage;
import com.yangqiong.agent.harness.core.message.AgentMessageRole;
import com.yangqiong.agent.harness.core.middleware.AgentMiddleware;
import com.yangqiong.agent.harness.engine.AgentRuntimeContext;
import com.yangqiong.agent.harness.guardrail.GuardrailFence;
import com.yangqiong.agent.harness.core.message.MessageFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * RAG检索中间件
 * <p>
 * 镜像MemoryRetrievalMiddleware：仅当输入最后一条消息为USER角色时，
 * 检索并注入围栏system消息。默认关闭，检索失败时静默透传。
 * </p>
 * @author yangqiong
 */
public class RagRetrievalMiddleware implements AgentMiddleware {

    private static final Logger log = LoggerFactory.getLogger(RagRetrievalMiddleware.class);

    /**
     * 默认检索条数
     */
    private static final int DEFAULT_TOP_K = 3;

    /**
     * 默认注入模板前缀
     */
    private static final String DEFAULT_TEMPLATE = "相关检索资料：\n";

    /**
     * 检索器注册表
     */
    private final RetrieverRegistry registry;

    /**
     * 使用的检索源名称
     */
    private final String source;

    /**
     * 检索条数
     */
    private final int topK;

    /**
     * 注入模板前缀
     */
    private final String injectionTemplate;

    /**
     * 是否启用
     */
    private final boolean enabled;

    public RagRetrievalMiddleware(RetrieverRegistry registry, String source) {
        this(registry, source, DEFAULT_TOP_K, false);
    }

    public RagRetrievalMiddleware(RetrieverRegistry registry, String source, int topK) {
        this(registry, source, topK, false);
    }

    /**
     * 完整构造器
     * @param registry
     * @param source
     * @param topK
     * @param enabled
     */
    public RagRetrievalMiddleware(RetrieverRegistry registry, String source, int topK, boolean enabled) {
        this(registry, source, topK, DEFAULT_TEMPLATE, enabled);
    }

    /**
     * 完整构造器
     * @param registry
     * @param source
     * @param topK
     * @param injectionTemplate
     * @param enabled
     */
    public RagRetrievalMiddleware(RetrieverRegistry registry, String source, int topK,
                                  String injectionTemplate, boolean enabled) {
        this.registry = registry;
        this.source = source;
        this.topK = topK > 0 ? topK : DEFAULT_TOP_K;
        this.injectionTemplate = injectionTemplate != null ? injectionTemplate : DEFAULT_TEMPLATE;
        this.enabled = enabled;
    }

    /**
     * 推理前注入相关检索资料
     * @param context
     * @param input
     * @param next
     * @return
     */
    @Override
    public Flux<AgentEvent> onReasoning(AgentRuntimeContext context, List<AgentMessage> input,
                                        Function<List<AgentMessage>, Flux<AgentEvent>> next) {
        if (!enabled || registry == null || input == null || input.isEmpty()) {
            return next.apply(input);
        }
        AgentMessage last = input.get(input.size() - 1);
        if (last == null || last.getRole() != AgentMessageRole.USER) {
            return next.apply(input);
        }
        String query = last.getTextContent();
        if (query == null || query.isBlank()) {
            return next.apply(input);
        }
        Retriever retriever = registry.get(source);
        if (retriever == null) {
            return next.apply(input);
        }
        List<RetrievedChunk> hits;
        try {
            hits = retriever.retrieve(query, topK, null);
        } catch (Exception e) {
            log.warn("RAG检索失败: source={}", source, e);
            return next.apply(input);
        }
        if (hits == null || hits.isEmpty()) {
            return next.apply(input);
        }
        AgentMessage ragMsg = buildRagMessage(hits);
        List<AgentMessage> injected = new ArrayList<>(input.size() + 1);
        injected.add(ragMsg);
        injected.addAll(input);
        return next.apply(injected);
    }

    /**
     * 构造检索注入消息（检索内容加围栏，标记为不可信数据防间接注入）
     * @param hits
     * @return
     */
    private AgentMessage buildRagMessage(List<RetrievedChunk> hits) {
        StringBuilder sb = new StringBuilder(injectionTemplate);
        for (int i = 0; i < hits.size(); i++) {
            sb.append("[").append(i + 1).append("] ")
                    .append(GuardrailFence.fence(hits.get(i).content()))
                    .append("\n");
        }
        return MessageFactory.createSystemMessage(sb.toString());
    }
}