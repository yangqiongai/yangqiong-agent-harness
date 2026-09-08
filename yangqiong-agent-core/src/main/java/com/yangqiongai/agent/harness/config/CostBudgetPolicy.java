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
 * 成本预算策略
 * @author yangqiong
 */
public class CostBudgetPolicy {

    /**
     * 告警阈值美元（0表示不告警）
     */
    private final double warnUsd;

    /**
     * 硬上限美元（0表示不设硬控）
     */
    private final double hardLimitUsd;

    /**
     * 超硬上限动作
     */
    private final ExceedAction exceedAction;

    /**
     * 超限动作
     * @author yangqiong
     */
    public enum ExceedAction {

        /**
         * 仅告警不中断
         */
        WARN,

        /**
         * 中止Agent执行
         */
        ABORT
    }

    public CostBudgetPolicy(double warnUsd, double hardLimitUsd, ExceedAction exceedAction) {
        this.warnUsd = warnUsd;
        this.hardLimitUsd = hardLimitUsd;
        this.exceedAction = exceedAction != null ? exceedAction : ExceedAction.WARN;
    }

    /**
     * 创建仅告警策略
     * @param warnUsd
     * @return
     */
    public static CostBudgetPolicy warnOnly(double warnUsd) {
        return new CostBudgetPolicy(warnUsd, 0.0, ExceedAction.WARN);
    }

    /**
     * 创建硬控策略
     * @param warnUsd
     * @param hardLimitUsd
     * @return
     */
    public static CostBudgetPolicy hardLimit(double warnUsd, double hardLimitUsd) {
        return new CostBudgetPolicy(warnUsd, hardLimitUsd, ExceedAction.ABORT);
    }

    /**
     * 判断成本是否超过告警阈值
     * @param currentCostUsd
     * @return
     */
    public boolean isWarnExceeded(double currentCostUsd) {
        return warnUsd > 0 && currentCostUsd > warnUsd;
    }

    /**
     * 判断成本是否超过硬上限
     * @param currentCostUsd
     * @return
     */
    public boolean isHardExceeded(double currentCostUsd) {
        return hardLimitUsd > 0 && currentCostUsd > hardLimitUsd;
    }

    public double getWarnUsd() {
        return warnUsd;
    }

    public double getHardLimitUsd() {
        return hardLimitUsd;
    }

    public ExceedAction getExceedAction() {
        return exceedAction;
    }
}