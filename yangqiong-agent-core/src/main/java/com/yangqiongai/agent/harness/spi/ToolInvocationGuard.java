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
package com.yangqiongai.agent.harness.spi;

/**
 * 工具调用守卫SPI
 * <p>
 * 挂接引擎工具调用链前后两钩子：before可拦截拒绝（如最小权限画像、出口白名单），
 * after做事后审计与异常检测（如动作审计哈希链）。实现方抛出的异常一律不阻断主流程。
 * 通过{@code HarnessRuntimeBuilder.guard(...)}注册。
 * </p>
 * @author yangqiong
 */
public interface ToolInvocationGuard {

    /**
     * 工具调用前钩子
     * @param invocation
     * @return
     */
    Decision before(ToolInvocation invocation);

    /**
     * 工具调用后钩子（含拒绝与失败结果，实现方自行吞异常）
     * @param outcome
     */
    void after(ToolInvocationOutcome outcome);

    /**
     * 排序权重（越小越先执行），默认0
     * @return
     */
    default int order() {
        return 0;
    }

    /**
     * 前置判定结果
     */
    final class Decision {

        /**
         * 是否拒绝
         */
        private final boolean denied;

        /**
         * 拒绝原因（放行时为null）
         */
        private final String reason;

        private Decision(boolean denied, String reason) {
            this.denied = denied;
            this.reason = reason;
        }

        /**
         * 放行
         * @return
         */
        public static Decision allow() {
            return new Decision(false, null);
        }

        /**
         * 拒绝
         * @param reason
         * @return
         */
        public static Decision deny(String reason) {
            return new Decision(true, reason);
        }

        public boolean isDenied() {
            return denied;
        }

        public String getReason() {
            return reason;
        }
    }
}
