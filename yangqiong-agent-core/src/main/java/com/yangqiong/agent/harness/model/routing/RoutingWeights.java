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
package com.yangqiong.agent.harness.model.routing;

/**
 * 路由权重
 * @author yangqiong
 */
public final class RoutingWeights {

    /**
     * 默认权重实例
     */
    public static final RoutingWeights DEFAULT = new RoutingWeights(0.3d, 0.3d, 0.2d, 0.2d);

    /**
     * 成本权重
     */
    private final double costWeight;

    /**
     * 延迟权重
     */
    private final double latencyWeight;

    /**
     * 上下文匹配权重
     */
    private final double contextFitWeight;

    /**
     * 健康权重
     */
    private final double healthWeight;

    private RoutingWeights(double costWeight, double latencyWeight, double contextFitWeight, double healthWeight) {
        this.costWeight = costWeight;
        this.latencyWeight = latencyWeight;
        this.contextFitWeight = contextFitWeight;
        this.healthWeight = healthWeight;
    }

    /**
     * 创建路由权重构建器
     * @return
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * 获取成本权重
     * @return
     */
    public double getCostWeight() {
        return costWeight;
    }

    /**
     * 获取延迟权重
     * @return
     */
    public double getLatencyWeight() {
        return latencyWeight;
    }

    /**
     * 获取上下文匹配权重
     * @return
     */
    public double getContextFitWeight() {
        return contextFitWeight;
    }

    /**
     * 获取健康权重
     * @return
     */
    public double getHealthWeight() {
        return healthWeight;
    }

    @Override
    public String toString() {
        return "RoutingWeights{costWeight=" + costWeight + ", latencyWeight=" + latencyWeight
                + ", contextFitWeight=" + contextFitWeight + ", healthWeight=" + healthWeight + "}";
    }

    /**
     * 路由权重构建器
     * @author yangqiong
     */
    public static class Builder {

        /**
         * 成本权重
         */
        private double costWeight = 0.3d;

        /**
         * 延迟权重
         */
        private double latencyWeight = 0.3d;

        /**
         * 上下文匹配权重
         */
        private double contextFitWeight = 0.2d;

        /**
         * 健康权重
         */
        private double healthWeight = 0.2d;

        public Builder costWeight(double costWeight) {
            this.costWeight = costWeight;
            return this;
        }

        public Builder latencyWeight(double latencyWeight) {
            this.latencyWeight = latencyWeight;
            return this;
        }

        public Builder contextFitWeight(double contextFitWeight) {
            this.contextFitWeight = contextFitWeight;
            return this;
        }

        public Builder healthWeight(double healthWeight) {
            this.healthWeight = healthWeight;
            return this;
        }

        public RoutingWeights build() {
            return new RoutingWeights(costWeight, latencyWeight, contextFitWeight, healthWeight);
        }
    }
}
