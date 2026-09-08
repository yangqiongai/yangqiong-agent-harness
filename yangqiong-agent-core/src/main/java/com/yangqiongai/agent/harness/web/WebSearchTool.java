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
package com.yangqiongai.agent.harness.web;

import com.yangqiongai.agent.harness.core.message.AgentTextBlock;
import com.yangqiongai.agent.harness.core.message.AgentToolResultBlock;
import com.yangqiongai.agent.harness.core.tool.AgentTool;
import com.yangqiongai.agent.harness.core.tool.AgentToolCallParam;
import com.yangqiongai.agent.harness.guardrail.GuardrailFence;
import reactor.core.publisher.Mono;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Web检索工具
 * <p>
 * 实现AgentTool接口，供LLM通过web_search工具联网检索。
 * 结果文本逐条加GuardrailFence围栏并附来源标注。
 * 未配置WebSearchProvider时返回错误提示。
 * </p>
 * @author yangqiong
 */
public class WebSearchTool implements AgentTool {

    private static final String TOOL_NAME = "web_search";

    private static final int DEFAULT_TOP_K = 3;

    private static final int MAX_RESULT_CHARS = 8000;

    private final WebSearchProvider provider;

    /**
     * 构造
     * @param provider Web检索提供方，null表示未配置
     */
    public WebSearchTool(WebSearchProvider provider) {
        this.provider = provider;
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
        return "从互联网检索与查询相关的网页信息。返回结果带围栏标注与来源链接。";
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
        queryProp.put("description", "搜索查询文本");
        properties.put("query", queryProp);

        Map<String, Object> topKProp = new LinkedHashMap<>();
        topKProp.put("type", "integer");
        topKProp.put("default", DEFAULT_TOP_K);
        topKProp.put("description", "返回结果条数上限");
        properties.put("topK", topKProp);

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
        if (provider == null) {
            return Mono.just(AgentToolResultBlock.error("未配置WebSearchProvider，联网搜索不可用"));
        }

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

        List<WebSearchResult> results;
        try {
            results = provider.search(query, topK);
        } catch (Exception e) {
            return Mono.just(AgentToolResultBlock.error("联网搜索失败: " + e.getMessage()));
        }

        if (results == null || results.isEmpty()) {
            return Mono.just(AgentToolResultBlock.error("未检索到相关内容"));
        }

        return Mono.just(AgentToolResultBlock.of(List.of(
                AgentTextBlock.builder().text(formatResults(results)).build())));
    }

    /**
     * 格式化检索结果：逐条加围栏并附来源标注，超出字符预算时截断
     * @param results
     * @return
     */
    private String formatResults(List<WebSearchResult> results) {
        StringBuilder sb = new StringBuilder();
        int budget = MAX_RESULT_CHARS;
        boolean truncated = false;
        for (int i = 0; i < results.size(); i++) {
            WebSearchResult result = results.get(i);
            String fenced = GuardrailFence.fence(result.snippet() != null ? result.snippet() : "");
            String source = "[来源: " + result.title() + "](" + result.url() + ")";
            String entry = fenced + "\n" + source;
            if (sb.length() + entry.length() <= budget) {
                if (i > 0) {
                    sb.append("\n\n");
                }
                sb.append(entry);
            } else {
                // 超出字符预算：截断本条内容后附加，并标记结果已截断
                truncated = true;
                int remaining = budget - sb.length();
                if (remaining > 0) {
                    if (i > 0) {
                        sb.append("\n\n");
                        remaining -= 2;
                    }
                    if (remaining > 0) {
                        sb.append(entry, 0, Math.min(entry.length(), remaining));
                    }
                }
                break;
            }
        }
        if (truncated) {
            sb.append("\n\n(结果已截断，完整结果共").append(results.size()).append("条)");
        }
        return sb.toString();
    }

    /**
     * 解析topK参数
     * @param value
     * @return
     */
    private int parseTopK(Object value) {
        if (value == null) {
            return DEFAULT_TOP_K;
        }
        try {
            int k = Integer.parseInt(value.toString().trim());
            return k > 0 ? Math.min(k, 10) : DEFAULT_TOP_K;
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