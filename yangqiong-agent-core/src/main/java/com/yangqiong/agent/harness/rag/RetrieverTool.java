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

import com.yangqiong.agent.harness.core.message.AgentTextBlock;
import com.yangqiong.agent.harness.core.message.AgentToolResultBlock;
import com.yangqiong.agent.harness.core.tool.AgentTool;
import com.yangqiong.agent.harness.core.tool.AgentToolCallParam;
import com.yangqiong.agent.harness.guardrail.GuardrailFence;
import reactor.core.publisher.Mono;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * RAG检索工具
 * <p>
 * 实现AgentTool接口，供LLM按名称检索文档知识库。
 * 结果文本逐条加GuardrailFence围栏并附来源标注，未知source返回error。
 * </p>
 * @author yangqiong
 */
public class RetrieverTool implements AgentTool {

    /**
     * 工具名称
     */
    private static final String TOOL_NAME = "rag_search";

    /**
     * 默认检索源名称
     */
    private static final String DEFAULT_SOURCE = "default";

    /**
     * 默认返回条数
     */
    private static final int DEFAULT_TOP_K = 3;

    /**
     * 检索器注册表
     */
    private final RetrieverRegistry registry;

    public RetrieverTool(RetrieverRegistry registry) {
        this.registry = registry;
    }

    /**
     * 获取工具名称
     * @return
     */
    @Override
    public String getName() {
        return TOOL_NAME;
    }

    /**
     * 获取工具描述
     * @return
     */
    @Override
    public String getDescription() {
        return "从已注册的文档知识库中检索与查询相关的文本片段。"
                + "支持指定来源和返回条数，结果自带围栏标注与来源信息。";
    }

    /**
     * 获取工具参数定义
     * @return
     */
    @Override
    public Map<String, Object> getParameters() {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");

        Map<String, Object> properties = new LinkedHashMap<>();

        Map<String, Object> queryProp = new LinkedHashMap<>();
        queryProp.put("type", "string");
        queryProp.put("description", "检索查询文本");
        properties.put("query", queryProp);

        Map<String, Object> topKProp = new LinkedHashMap<>();
        topKProp.put("type", "integer");
        topKProp.put("default", DEFAULT_TOP_K);
        topKProp.put("description", "返回结果条数上限");
        properties.put("topK", topKProp);

        Map<String, Object> sourceProp = new LinkedHashMap<>();
        sourceProp.put("type", "string");
        sourceProp.put("description", "检索来源名称，未指定时使用默认来源");
        properties.put("source", sourceProp);

        schema.put("properties", properties);
        schema.put("required", List.of("query"));
        return schema;
    }

    /**
     * 执行检索
     * @param param
     * @return
     */
    @Override
    public Mono<AgentToolResultBlock> callAsync(AgentToolCallParam param) {
        Map<String, Object> input = param != null ? param.getInput() : null;
        if (input == null || input.isEmpty()) {
            return Mono.just(AgentToolResultBlock.error("工具参数为空"));
        }
        Object queryObj = input.get("query");
        if (queryObj == null || queryObj.toString().isBlank()) {
            return Mono.just(AgentToolResultBlock.error("query 参数缺失"));
        }
        String query = queryObj.toString();
        int topK = parseTopK(input.get("topK"));
        String sourceName = input.get("source") != null ? input.get("source").toString() : DEFAULT_SOURCE;
        Retriever retriever = registry.get(sourceName);
        if (retriever == null) {
            return Mono.just(AgentToolResultBlock.error("未知检索源: " + sourceName));
        }
        List<RetrievedChunk> chunks;
        try {
            chunks = retriever.retrieve(query, topK, null);
        } catch (Exception e) {
            return Mono.just(AgentToolResultBlock.error("检索失败: " + e.getMessage()));
        }
        if (chunks == null || chunks.isEmpty()) {
            return Mono.just(AgentToolResultBlock.error("未检索到相关内容"));
        }
        return Mono.just(AgentToolResultBlock.of(List.of(
                AgentTextBlock.builder().text(formatChunks(chunks)).build())));
    }

    /**
     * 格式化检索结果：逐条加围栏并附来源标注
     * @param chunks
     * @return
     */
    private String formatChunks(List<RetrievedChunk> chunks) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < chunks.size(); i++) {
            RetrievedChunk chunk = chunks.get(i);
            if (i > 0) {
                sb.append("\n\n");
            }
            sb.append(GuardrailFence.fence(chunk.content()))
                    .append("\n")
                    .append(formatSource(chunk));
        }
        return sb.toString();
    }

    /**
     * 格式化来源标注：[来源: title](url)
     * @param chunk
     * @return
     */
    private String formatSource(RetrievedChunk chunk) {
        String title = metadataValue(chunk, "title", chunk.source());
        String url = metadataValue(chunk, "url", null);
        if (url == null || url.isBlank()) {
            return "[来源: " + title + "]";
        }
        return "[来源: " + title + "](" + url + ")";
    }

    /**
     * 读取chunk元数据，缺失时返回默认值
     * @param chunk
     * @param key
     * @param defaultValue
     * @return
     */
    private String metadataValue(RetrievedChunk chunk, String key, String defaultValue) {
        if (chunk.metadata() != null) {
            Object value = chunk.metadata().get(key);
            if (value != null && !value.toString().isBlank()) {
                return value.toString();
            }
        }
        return defaultValue;
    }

    /**
     * 解析topK参数，非法时回退默认值
     * @param value
     * @return
     */
    private int parseTopK(Object value) {
        if (value == null) {
            return DEFAULT_TOP_K;
        }
        try {
            int k = Integer.parseInt(value.toString().trim());
            return k > 0 ? k : DEFAULT_TOP_K;
        } catch (NumberFormatException e) {
            return DEFAULT_TOP_K;
        }
    }

    /**
     * 是否只读
     * @return
     */
    @Override
    public boolean isReadOnly() {
        return true;
    }
}