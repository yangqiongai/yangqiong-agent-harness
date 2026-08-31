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

import com.yangqiong.agent.harness.core.message.AgentChatUsage;

/**
 * 成本追踪器
 * @author yangqiong
 */
public class CostTracker {

    private double totalCostUsd;

    public CostTracker() {
        this.totalCostUsd = 0.0;
    }

    /**
     * 按模型定价累计成本
     * @param usage
     * @param pricing
     * @return
     */
    public synchronized double accumulate(AgentChatUsage usage, ModelPricing pricing) {
        double promptCost = (usage.getPromptTokens() / 1000.0) * pricing.promptUsdPer1k();
        double completionCost = (usage.getCompletionTokens() / 1000.0) * pricing.completionUsdPer1k();
        totalCostUsd += promptCost + completionCost;
        return totalCostUsd;
    }

    /**
     * 获取当前累计总成本
     * @return
     */
    public synchronized double getTotalCostUsd() {
        return totalCostUsd;
    }
}