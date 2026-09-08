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
package com.yangqiongai.agent.harness.tool;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 关键词工具筛选器
 * <p>
 * 将用户查询拆分为关键词，与工具名称和描述进行匹配，
 * 筛选出匹配度高的工具，至少保留5个工具作为兜底。
 * </p>
 * @author yangqiong
 */
public class KeywordToolFilter implements ToolFilter {

    /**
     * 最少保留工具数
     */
    private static final int MIN_TOOL_COUNT = 5;

    /**
     * 关键词最小长度
     */
    private static final int MIN_KEYWORD_LENGTH = 2;

    @Override
    public List<Map<String, Object>> filter(List<Map<String, Object>> toolSchemas, String userQuery) {
        if (toolSchemas == null || toolSchemas.isEmpty()) {
            return toolSchemas != null ? toolSchemas : List.of();
        }
        if (userQuery == null || userQuery.isBlank()) {
            return toolSchemas;
        }

        // 提取关键词
        List<String> keywords = extractKeywords(userQuery);
        if (keywords.isEmpty()) {
            return toolSchemas;
        }

        // 筛选匹配的工具
        List<Map<String, Object>> matched = new ArrayList<>();
        for (Map<String, Object> schema : toolSchemas) {
            String text = buildSearchText(schema);
            if (text != null && matchesAny(text, keywords)) {
                matched.add(schema);
            }
        }

        // 兜底：匹配结果不足5个时返回全量
        if (matched.size() < MIN_TOOL_COUNT) {
            return toolSchemas;
        }
        return matched;
    }

    /**
     * 从用户查询中提取关键词
     * @param query
     * @return
     */
    private List<String> extractKeywords(String query) {
        List<String> keywords = new ArrayList<>();
        // 按常见分隔符拆分
        String[] parts = query.split("[\\s,，、；;。.！!？?：:()（）\\[\\]【】]+");
        for (String part : parts) {
            String trimmed = part.trim();
            if (trimmed.length() >= MIN_KEYWORD_LENGTH) {
                keywords.add(trimmed.toLowerCase());
            }
        }
        return keywords;
    }

    /**
     * 构建用于匹配的搜索文本（工具名称 + 描述）
     * @param schema
     * @return
     */
    private String buildSearchText(Map<String, Object> schema) {
        if (schema == null) {
            return null;
        }
        Object name = schema.get("name");
        Object desc = schema.get("description");
        if(name == null){
            Object obj = schema.get("function");
            if(obj instanceof Map data){
                name = data.get("name");
                desc = data.get("description");
            }
        }
        StringBuilder sb = new StringBuilder();
        if (name != null) {
            sb.append(name).append(" ");
        }
        if (desc != null) {
            sb.append(desc);
        }
        return sb.toString().toLowerCase();
    }

    /**
     * 判断文本是否匹配任意关键词
     * @param text
     * @param keywords
     * @return
     */
    private boolean matchesAny(String text, List<String> keywords) {
        for (String keyword : keywords) {
            if (text.contains(keyword)) {
                return true;
            }
        }
        return false;
    }
}