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
package com.yangqiongai.agent.harness.core.message;

import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Agent Token用量统计
 * @author yangqiong
 */
public final class AgentChatUsage {

    /**
     * 提示Token数
     */
    private final int promptTokens;

    /**
     * 完成Token数
     */
    private final int completionTokens;

    /**
     * 总Token数
     */
    private final int totalTokens;

    @JsonCreator
    public AgentChatUsage(@JsonProperty("promptTokens") int promptTokens,
                          @JsonProperty("completionTokens") int completionTokens,
                          @JsonProperty("totalTokens") int totalTokens) {
        this.promptTokens = promptTokens;
        this.completionTokens = completionTokens;
        this.totalTokens = totalTokens;
    }

    /**
     * 累加两份用量统计，任一为null时返回另一份
     * @param a
     * @param b
     * @return
     */
    public static AgentChatUsage merge(AgentChatUsage a, AgentChatUsage b) {
        if (a == null) {
            return b;
        }
        if (b == null) {
            return a;
        }
        return new AgentChatUsage(
                a.promptTokens + b.promptTokens,
                a.completionTokens + b.completionTokens,
                a.totalTokens + b.totalTokens);
    }

    /**
     * 获取提示Token数
     * @return
     */
    public int getPromptTokens() {
        return promptTokens;
    }

    /**
     * 获取完成Token数
     * @return
     */
    public int getCompletionTokens() {
        return completionTokens;
    }

    /**
     * 获取总Token数
     * @return
     */
    public int getTotalTokens() {
        return totalTokens;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        AgentChatUsage that = (AgentChatUsage) o;
        return promptTokens == that.promptTokens
                && completionTokens == that.completionTokens
                && totalTokens == that.totalTokens;
    }

    @Override
    public int hashCode() {
        return Objects.hash(promptTokens, completionTokens, totalTokens);
    }

    @Override
    public String toString() {
        return "AgentChatUsage{promptTokens=" + promptTokens
                + ", completionTokens=" + completionTokens
                + ", totalTokens=" + totalTokens + "}";
    }
}
