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
package com.yangqiong.agent.harness.config;

/**
 * Token预算策略
 * <p>
 * 基于provider真实usage回填的累计用量做预算硬控：
 * 超过告警阈值发TOKEN_BUDGET_WARN事件，超过硬上限按动作处置（WARN/ABORT）。
 * </p>
 * @author yangqiong
 */
public class TokenBudgetPolicy {

    /**
     * 告警阈值Token数（0表示不告警）
     */
    private final long warnThresholdTokens;

    /**
     * 硬上限Token数（0表示不设硬控）
     */
    private final long hardLimitTokens;

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

    public TokenBudgetPolicy(long warnThresholdTokens, long hardLimitTokens, ExceedAction exceedAction) {
        this.warnThresholdTokens = warnThresholdTokens;
        this.hardLimitTokens = hardLimitTokens;
        this.exceedAction = exceedAction != null ? exceedAction : ExceedAction.WARN;
    }

    /**
     * 创建仅告警策略
     * @param warnThresholdTokens
     * @return
     */
    public static TokenBudgetPolicy warnOnly(long warnThresholdTokens) {
        return new TokenBudgetPolicy(warnThresholdTokens, 0, ExceedAction.WARN);
    }

    /**
     * 创建硬控策略
     * @param warnThresholdTokens
     * @param hardLimitTokens
     * @return
     */
    public static TokenBudgetPolicy hardLimit(long warnThresholdTokens, long hardLimitTokens) {
        return new TokenBudgetPolicy(warnThresholdTokens, hardLimitTokens, ExceedAction.ABORT);
    }

    /**
     * 判断用量是否超过告警阈值
     * @param usedTokens
     * @return
     */
    public boolean isWarnExceeded(long usedTokens) {
        return warnThresholdTokens > 0 && usedTokens > warnThresholdTokens;
    }

    /**
     * 判断用量是否超过硬上限
     * @param usedTokens
     * @return
     */
    public boolean isHardExceeded(long usedTokens) {
        return hardLimitTokens > 0 && usedTokens > hardLimitTokens;
    }

    public long getWarnThresholdTokens() {
        return warnThresholdTokens;
    }

    public long getHardLimitTokens() {
        return hardLimitTokens;
    }

    public ExceedAction getExceedAction() {
        return exceedAction;
    }
}
