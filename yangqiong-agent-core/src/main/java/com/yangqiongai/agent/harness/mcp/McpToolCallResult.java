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
package com.yangqiongai.agent.harness.mcp;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * MCP工具调用结果
 * @author yangqiong
 */
public final class McpToolCallResult {

    /**
     * 原始内容块列表，每项含type/text等字段
     */
    private final List<Map<String, Object>> content;

    /**
     * 是否为工具执行错误
     */
    private final boolean error;

    public McpToolCallResult(List<Map<String, Object>> content, boolean error) {
        this.content = content != null ? List.copyOf(content) : List.of();
        this.error = error;
    }

    /**
     * 获取原始内容块列表
     * @return
     */
    public List<Map<String, Object>> getContent() {
        return content;
    }

    /**
     * 是否为工具执行错误
     * @return
     */
    public boolean isError() {
        return error;
    }

    /**
     * 提取全部文本内容拼接（非文本块跳过）
     * @return
     */
    public String extractText() {
        if (content.isEmpty()) {
            return "";
        }
        List<String> texts = new ArrayList<>(content.size());
        for (Map<String, Object> item : content) {
            Object type = item.get("type");
            Object text = item.get("text");
            if ("text".equals(type) && text != null) {
                texts.add(String.valueOf(text));
            }
        }
        return String.join("\n", texts);
    }
}
