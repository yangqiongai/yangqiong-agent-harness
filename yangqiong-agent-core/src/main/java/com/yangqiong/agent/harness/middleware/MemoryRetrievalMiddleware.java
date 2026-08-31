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
package com.yangqiong.agent.harness.middleware;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import com.yangqiong.agent.harness.engine.AgentRuntimeContext;
import com.yangqiong.agent.harness.core.event.AgentEvent;
import com.yangqiong.agent.harness.core.memory.AgentLongTermMemory;
import com.yangqiong.agent.harness.core.message.AgentMessage;
import com.yangqiong.agent.harness.core.message.AgentMessageRole;
import com.yangqiong.agent.harness.core.middleware.AgentMiddleware;
import com.yangqiong.agent.harness.core.message.MessageFactory;
import com.yangqiong.agent.harness.guardrail.GuardrailFence;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;

/**
 * 长期记忆检索注入中间件
 * <p>
 * 在每轮首次推理前自动从L3长期记忆检索相关条目并注入L1上下文，
 * 补齐L3→L1自动流（区别于LLM主动调用memory_search工具）。
 * 仅当输入最后一条消息为USER角色时触发，避免多轮工具迭代中重复注入造成噪声与Token浪费。
 * 检索失败或无命中时静默透传，不影响推理主流程。
 * </p>
 * @author yangqiong
 */
public class MemoryRetrievalMiddleware implements AgentMiddleware {

    private static final Logger log = LoggerFactory.getLogger(MemoryRetrievalMiddleware.class);

    /**
     * 默认检索条目数
     */
    private static final int DEFAULT_TOP_K = 3;

    /**
     * 默认注入模板前缀
     */
    private static final String DEFAULT_TEMPLATE = "相关长期记忆：\n";

    /**
     * 长期记忆存储
     */
    private final AgentLongTermMemory memory;

    /**
     * 检索条目上限
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

    public MemoryRetrievalMiddleware(AgentLongTermMemory memory) {
        this(memory, DEFAULT_TOP_K, true);
    }

    public MemoryRetrievalMiddleware(AgentLongTermMemory memory, int topK) {
        this(memory, topK, true);
    }

    public MemoryRetrievalMiddleware(AgentLongTermMemory memory, int topK, boolean enabled) {
        this(memory, topK, DEFAULT_TEMPLATE, enabled);
    }

    /**
     * 完整构造器
     * @param memory
     * @param topK
     * @param injectionTemplate
     * @param enabled
     */
    public MemoryRetrievalMiddleware(AgentLongTermMemory memory, int topK, String injectionTemplate, boolean enabled) {
        this.memory = memory;
        this.topK = topK > 0 ? topK : DEFAULT_TOP_K;
        this.injectionTemplate = injectionTemplate != null ? injectionTemplate : DEFAULT_TEMPLATE;
        this.enabled = enabled;
    }

    /**
     * 推理前注入相关长期记忆
     * @param context
     * @param input
     * @param next
     * @return
     */
    @Override
    public Flux<AgentEvent> onReasoning(AgentRuntimeContext context, List<AgentMessage> input,
                                         Function<List<AgentMessage>, Flux<AgentEvent>> next) {
        if (!enabled || memory == null || input == null || input.isEmpty()) {
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
        String userId = context != null ? context.getUserId() : null;
        String scopeId = context != null ? context.getScopeId() : null;
        List<String> hits;
        try {
            hits = memory.search(scopeId, userId, query, topK);
        } catch (Exception e) {
            log.warn("长期记忆检索失败: scopeId={}, userId={}", scopeId, userId, e);
            return next.apply(input);
        }
        if (hits == null || hits.isEmpty()) {
            return next.apply(input);
        }
        AgentMessage memoryMsg = buildMemoryMessage(hits);
        List<AgentMessage> injected = new ArrayList<>(input.size() + 1);
        injected.add(memoryMsg);
        injected.addAll(input);
        return next.apply(injected);
    }

    /**
     * 构造长期记忆注入消息（检索内容加围栏，标记为不可信数据防间接注入）
     * @param hits
     * @return
     */
    private AgentMessage buildMemoryMessage(List<String> hits) {
        StringBuilder sb = new StringBuilder(injectionTemplate);
        for (int i = 0; i < hits.size(); i++) {
            sb.append("[").append(i + 1).append("] ")
                    .append(GuardrailFence.fence(hits.get(i)))
                    .append("\n");
        }
        return MessageFactory.createSystemMessage(sb.toString());
    }
}
