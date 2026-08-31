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
package com.yangqiong.agent.harness.core.model;


import com.yangqiong.agent.harness.core.message.AgentChatUsage;

/**
 * Token消耗指标
 * @author yangqiong
 */
public final class TokenMetrics {

    /**
     * 输入Token数
     */
    private final long inputTokens;

    /**
     * 输出Token数
     */
    private final long outputTokens;

    /**
     * 总Token数
     */
    private final long totalTokens;

    /**
     * 模型执行耗时（秒）
     */
    private final double time;

    public TokenMetrics(long inputTokens, long outputTokens, long totalTokens, double time) {
        this.inputTokens = inputTokens;
        this.outputTokens = outputTokens;
        this.totalTokens = totalTokens;
        this.time = time;
    }

    /**
     * 从AgentChatUsage构建TokenMetrics
     * @param chatUsage
     * @return
     */
    public static TokenMetrics fromChatUsage(AgentChatUsage chatUsage) {
        if (chatUsage == null) {
            return empty();
        }
        return new TokenMetrics(
                chatUsage.getPromptTokens(),
                chatUsage.getCompletionTokens(),
                chatUsage.getTotalTokens(),
                0
        );
    }

    /**
     * 创建空的TokenMetrics
     * @return
     */
    public static TokenMetrics empty() {
        return new TokenMetrics(0, 0, 0, 0);
    }

    public static Builder builder() {
        return new Builder();
    }

    public long getInputTokens() {
        return inputTokens;
    }

    public long getOutputTokens() {
        return outputTokens;
    }

    public long getTotalTokens() {
        return totalTokens;
    }

    public double getTime() {
        return time;
    }

    public static class Builder {

        /**
         * 输入Token数
         */
        private long inputTokens;

        /**
         * 输出Token数
         */
        private long outputTokens;

        /**
         * 总Token数
         */
        private long totalTokens;

        /**
         * 模型执行耗时（秒）
         */
        private double time;

        public Builder inputTokens(long inputTokens) {
            this.inputTokens = inputTokens;
            return this;
        }

        public Builder outputTokens(long outputTokens) {
            this.outputTokens = outputTokens;
            return this;
        }

        public Builder totalTokens(long totalTokens) {
            this.totalTokens = totalTokens;
            return this;
        }

        public Builder time(double time) {
            this.time = time;
            return this;
        }

        public TokenMetrics build() {
            return new TokenMetrics(inputTokens, outputTokens, totalTokens, time);
        }
    }
}
