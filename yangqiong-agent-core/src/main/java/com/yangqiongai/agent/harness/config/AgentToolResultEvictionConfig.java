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
package com.yangqiongai.agent.harness.config;

/**
 * Agent工具结果驱逐配置
 * @author yangqiong
 */
public final class AgentToolResultEvictionConfig {

    private final int maxResultChars;

    private final int previewChars;

    private AgentToolResultEvictionConfig(int maxResultChars, int previewChars) {
        this.maxResultChars = maxResultChars;
        this.previewChars = previewChars;
    }

    public static Builder builder() {
        return new Builder();
    }

    /**
     * 获取结果最大字符数
     * @return
     */
    public int getMaxResultChars() {
        return maxResultChars;
    }

    /**
     * 获取预览字符数
     * @return
     */
    public int getPreviewChars() {
        return previewChars;
    }

    @Override
    public String toString() {
        return "AgentToolResultEvictionConfig{maxResultChars=" + maxResultChars
                + ", previewChars=" + previewChars + "}";
    }

    /**
     * 工具结果驱逐配置构建器
     * @author yangqiong
     */
    public static class Builder {

        private int maxResultChars;

        private int previewChars;

        public Builder maxResultChars(int maxResultChars) {
            this.maxResultChars = maxResultChars;
            return this;
        }

        public Builder previewChars(int previewChars) {
            this.previewChars = previewChars;
            return this;
        }

        public AgentToolResultEvictionConfig build() {
            return new AgentToolResultEvictionConfig(maxResultChars, previewChars);
        }
    }
}