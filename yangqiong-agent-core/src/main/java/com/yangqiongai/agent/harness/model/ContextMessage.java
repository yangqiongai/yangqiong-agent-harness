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

/**
 * 上下文注入消息
 * @author yangqiong
 */
public class ContextMessage {

    /**
     * 消息来源：system_prompt-系统提示词，history-入参历史，tool_result-工具结果，memory_inject-记忆注入
     */
    public static final String SOURCE_SYSTEM_PROMPT = "system_prompt";

    /**
     * 消息来源：入参历史
     */
    public static final String SOURCE_HISTORY = "history";

    /**
     * 消息来源：工具结果
     */
    public static final String SOURCE_TOOL_RESULT = "tool_result";

    /**
     * 消息来源：记忆注入
     */
    public static final String SOURCE_MEMORY_INJECT = "memory_inject";

    /**
     * 消息角色
     */
    private final String role;

    /**
     * 消息文本内容
     */
    private final String content;

    /**
     * 消息来源
     */
    private final String source;

    /**
     * 内容是否被截断
     */
    private final boolean truncated;

    public ContextMessage(String role, String content, String source, boolean truncated) {
        this.role = role;
        this.content = content;
        this.source = source;
        this.truncated = truncated;
    }

    public String getRole() {
        return role;
    }

    public String getContent() {
        return content;
    }

    public String getSource() {
        return source;
    }

    public boolean isTruncated() {
        return truncated;
    }
}
