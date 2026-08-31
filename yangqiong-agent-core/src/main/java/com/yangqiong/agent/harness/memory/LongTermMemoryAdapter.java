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
package com.yangqiong.agent.harness.memory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.yangqiong.agent.harness.engine.AgentRuntimeContext;
import com.yangqiong.agent.harness.core.memory.AgentLongTermMemory;
import com.yangqiong.agent.harness.core.message.AgentMessage;
import com.yangqiong.agent.harness.core.message.AgentMessageRole;
import com.yangqiong.agent.harness.core.message.AgentTextBlock;
import com.yangqiong.agent.harness.core.message.AgentToolResultBlock;
import com.yangqiong.agent.harness.core.middleware.AgentMiddleware;
import com.yangqiong.agent.harness.core.tool.AgentTool;
import com.yangqiong.agent.harness.core.tool.AgentToolCallParam;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

/**
 * 长期记忆适配器
 * <p>
 * 提供 memory_search/memory_store/memory_delete 三个内置工具，
 * 并通过 onMessage 钩子自动持久化助手消息到长期记忆。
 * </p>
 * @author yangqiong
 */
public class LongTermMemoryAdapter implements AgentMiddleware {

    private static final Logger log = LoggerFactory.getLogger(LongTermMemoryAdapter.class);

    /**
     * 长期记忆存储
     */
    private final AgentLongTermMemory memory;

    /**
     * 是否禁用记忆工具
     */
    private final boolean toolsDisabled;

    /**
     * 是否禁用记忆钩子
     */
    private final boolean hooksDisabled;

    public LongTermMemoryAdapter(AgentLongTermMemory memory) {
        this(memory, false, false);
    }

    public LongTermMemoryAdapter(AgentLongTermMemory memory, boolean toolsDisabled, boolean hooksDisabled) {
        this.memory = memory;
        this.toolsDisabled = toolsDisabled;
        this.hooksDisabled = hooksDisabled;
    }

    /**
     * 获取记忆工具列表
     * @return
     */
    public List<AgentTool> getMemoryTools() {
        if (toolsDisabled || memory == null) {
            return Collections.emptyList();
        }
        List<AgentTool> tools = new ArrayList<>(3);
        tools.add(new MemorySearchTool(memory));
        tools.add(new MemoryStoreTool(memory));
        tools.add(new MemoryDeleteTool(memory));
        return tools;
    }

    /**
     * 消息钩子：自动持久化助手文本消息
     * @param message
     * @param context
     * @return
     */
    @Override
    public AgentMessage onMessage(AgentMessage message, AgentRuntimeContext context) {
        if (hooksDisabled || memory == null || message == null) {
            return message;
        }
        if (message.getRole() != AgentMessageRole.ASSISTANT) {
            return message;
        }
        String text = message.getTextContent();
        if (text == null || text.isBlank()) {
            return message;
        }
        try {
            Map<String, Object> metadata = new LinkedHashMap<>();
            metadata.put("sessionId", context.getSessionId());
            metadata.put("agentName", message.getName());
            memory.store(context.getScopeId(), context.getUserId(), context.getSessionId(), text, metadata);
        } catch (Exception e) {
            log.warn("自动持久化记忆失败: session={}", context.getSessionId(), e);
        }
        return message;
    }

    /**
     * 程序化记录记忆
     * @param content
     * @param metadata
     * @return
     */
    public Mono<Void> record(String content, Map<String, Object> metadata) {
        if (content == null || content.isBlank()) {
            return Mono.empty();
        }
        return Mono.fromRunnable(() -> memory.store(null, null, content, metadata))
                .onErrorResume(e -> {
                    log.warn("程序化记录记忆失败", e);
                    return Mono.<Void>empty();
                })
                .then();
    }

    /**
     * 程序化检索记忆
     * @param query
     * @param topK
     * @return
     */
    public Mono<List<String>> search(String query, int topK) {
        if (query == null || query.isBlank()) {
            return Mono.just(Collections.emptyList());
        }
        return Mono.fromCallable(() -> memory.search(null, query, topK))
                .onErrorResume(e -> {
                    log.warn("程序化检索记忆失败", e);
                    return Mono.just(Collections.emptyList());
                });
    }

    /**
     * 记忆搜索工具
     * @author yangqiong
     */
    private static class MemorySearchTool implements AgentTool {

        private final AgentLongTermMemory memory;

        MemorySearchTool(AgentLongTermMemory memory) {
            this.memory = memory;
        }

        @Override
        public String getName() {
            return "memory_search";
        }

        @Override
        public String getDescription() {
            return "检索长期记忆中与查询相关的记忆条目";
        }

        @Override
        public Map<String, Object> getParameters() {
            Map<String, Object> params = new LinkedHashMap<>();
            params.put("type", "object");
            Map<String, Object> properties = new LinkedHashMap<>();
            Map<String, Object> queryProp = new LinkedHashMap<>();
            queryProp.put("type", "string");
            queryProp.put("description", "检索关键词");
            properties.put("query", queryProp);
            Map<String, Object> limitProp = new LinkedHashMap<>();
            limitProp.put("type", "integer");
            limitProp.put("description", "返回条目上限，默认5");
            properties.put("limit", limitProp);
            params.put("properties", properties);
            params.put("required", List.of("query"));
            return params;
        }

        @Override
        public Mono<AgentToolResultBlock> callAsync(AgentToolCallParam param) {
            String query = "";
            int limit = 5;
            if (param != null && param.getInput() != null) {
                Object q = param.getInput().get("query");
                if (q != null) {
                    query = String.valueOf(q);
                }
                Object l = param.getInput().get("limit");
                if (l instanceof Number n) {
                    limit = n.intValue();
                }
            }
            final String q = query;
            final int lim = limit;
            final String scope = param != null ? param.getScopeId() : null;
            final String user = param != null ? param.getUserId() : null;
            return Mono.fromCallable(() -> memory.search(scope, user, q, lim))
                    .map(results -> {
                        StringBuilder sb = new StringBuilder();
                        for (int i = 0; i < results.size(); i++) {
                            sb.append("[").append(i + 1).append("] ").append(results.get(i)).append("\n");
                        }
                        String text = sb.length() == 0 ? "未找到相关记忆" : sb.toString();
                        return AgentToolResultBlock.of(List.of(
                                AgentTextBlock.builder().text(text).build()));
                    })
                    .onErrorResume(e -> Mono.just(AgentToolResultBlock.error(
                            e.getMessage() != null ? e.getMessage() : "记忆检索异常")));
        }
    }

    /**
     * 记忆存储工具
     * @author yangqiong
     */
    private static class MemoryStoreTool implements AgentTool {

        private final AgentLongTermMemory memory;

        MemoryStoreTool(AgentLongTermMemory memory) {
            this.memory = memory;
        }

        @Override
        public String getName() {
            return "memory_store";
        }

        @Override
        public String getDescription() {
            return "将指定内容存储到长期记忆";
        }

        @Override
        public Map<String, Object> getParameters() {
            Map<String, Object> params = new LinkedHashMap<>();
            params.put("type", "object");
            Map<String, Object> properties = new LinkedHashMap<>();
            Map<String, Object> contentProp = new LinkedHashMap<>();
            contentProp.put("type", "string");
            contentProp.put("description", "要存储的内容");
            properties.put("content", contentProp);
            params.put("properties", properties);
            params.put("required", List.of("content"));
            return params;
        }

        @Override
        public Mono<AgentToolResultBlock> callAsync(AgentToolCallParam param) {
            String content = "";
            if (param != null && param.getInput() != null) {
                Object c = param.getInput().get("content");
                if (c != null) {
                    content = String.valueOf(c);
                }
            }
            final String text = content;
            final String scope = param != null ? param.getScopeId() : null;
            final String user = param != null ? param.getUserId() : null;
            return Mono.fromRunnable(() -> memory.store(scope, user, null, text, null))
                    .thenReturn(AgentToolResultBlock.of(List.of(
                            AgentTextBlock.builder().text("记忆已存储").build())))
                    .onErrorResume(e -> Mono.just(AgentToolResultBlock.error(
                            e.getMessage() != null ? e.getMessage() : "记忆存储异常")));
        }
    }

    /**
     * 记忆删除工具
     * @author yangqiong
     */
    private static class MemoryDeleteTool implements AgentTool {

        private final AgentLongTermMemory memory;

        MemoryDeleteTool(AgentLongTermMemory memory) {
            this.memory = memory;
        }

        @Override
        public String getName() {
            return "memory_delete";
        }

        @Override
        public String getDescription() {
            return "按记忆ID删除长期记忆条目";
        }

        @Override
        public Map<String, Object> getParameters() {
            Map<String, Object> params = new LinkedHashMap<>();
            params.put("type", "object");
            Map<String, Object> properties = new LinkedHashMap<>();
            Map<String, Object> idProp = new LinkedHashMap<>();
            idProp.put("type", "string");
            idProp.put("description", "记忆ID");
            properties.put("memoryId", idProp);
            params.put("properties", properties);
            params.put("required", List.of("memoryId"));
            return params;
        }

        @Override
        public Mono<AgentToolResultBlock> callAsync(AgentToolCallParam param) {
            String memoryId = "";
            if (param != null && param.getInput() != null) {
                Object id = param.getInput().get("memoryId");
                if (id != null) {
                    memoryId = String.valueOf(id);
                }
            }
            final String id = memoryId;
            final String scope = param != null ? param.getScopeId() : null;
            return Mono.fromRunnable(() -> memory.delete(scope, id))
                    .thenReturn(AgentToolResultBlock.of(List.of(
                            AgentTextBlock.builder().text("记忆已删除").build())))
                    .onErrorResume(e -> Mono.just(AgentToolResultBlock.error(
                            e.getMessage() != null ? e.getMessage() : "记忆删除异常")));
        }
    }
}
