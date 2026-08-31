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
package com.yangqiong.agent.harness.model;

import java.time.Duration;

/**
 * 模型重试配置
 * @author yangqiong
 */
public class ModelRetryConfig {

    /**
     * 是否启用重试
     */
    private final boolean enabled;

    /**
     * 最大重试次数（不含首次调用）
     */
    private final int maxRetries;

    /**
     * 初始退避时长
     */
    private final Duration initialBackoff;

    /**
     * 退避乘数
     */
    private final double backoffMultiplier;

    /**
     * 最大退避时长
     */
    private final Duration maxBackoff;

    public ModelRetryConfig(boolean enabled, int maxRetries, Duration initialBackoff,
                            double backoffMultiplier, Duration maxBackoff) {
        this.enabled = enabled;
        this.maxRetries = maxRetries;
        this.initialBackoff = initialBackoff;
        this.backoffMultiplier = backoffMultiplier;
        this.maxBackoff = maxBackoff;
    }

    /**
     * 创建默认配置构建器
     * @return
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * 创建启用重试的默认配置
     * @return
     */
    public static ModelRetryConfig defaultEnabled() {
        return builder().build();
    }

    /**
     * 创建禁用重试的配置
     * @return
     */
    public static ModelRetryConfig disabled() {
        return new ModelRetryConfig(false, 0, Duration.ZERO, 1.0, Duration.ZERO);
    }

    public boolean isEnabled() {
        return enabled;
    }

    public int getMaxRetries() {
        return maxRetries;
    }

    public Duration getInitialBackoff() {
        return initialBackoff;
    }

    public double getBackoffMultiplier() {
        return backoffMultiplier;
    }

    public Duration getMaxBackoff() {
        return maxBackoff;
    }

    /**
     * 模型重试配置构建器
     * @author yangqiong
     */
    public static class Builder {

        /**
         * 是否启用
         */
        private boolean enabled = true;

        /**
         * 最大重试次数
         */
        private int maxRetries = 3;

        /**
         * 初始退避
         */
        private Duration initialBackoff = Duration.ofSeconds(1);

        /**
         * 退避乘数
         */
        private double backoffMultiplier = 2.0;

        /**
         * 最大退避
         */
        private Duration maxBackoff = Duration.ofSeconds(10);

        public Builder enabled(boolean enabled) {
            this.enabled = enabled;
            return this;
        }

        public Builder maxRetries(int maxRetries) {
            this.maxRetries = maxRetries;
            return this;
        }

        public Builder initialBackoff(Duration initialBackoff) {
            this.initialBackoff = initialBackoff;
            return this;
        }

        public Builder backoffMultiplier(double backoffMultiplier) {
            this.backoffMultiplier = backoffMultiplier;
            return this;
        }

        public Builder maxBackoff(Duration maxBackoff) {
            this.maxBackoff = maxBackoff;
            return this;
        }

        public ModelRetryConfig build() {
            return new ModelRetryConfig(enabled, maxRetries, initialBackoff, backoffMultiplier, maxBackoff);
        }
    }
}
