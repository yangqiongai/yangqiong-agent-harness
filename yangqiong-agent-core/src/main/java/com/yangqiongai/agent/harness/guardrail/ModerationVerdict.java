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
package com.yangqiongai.agent.harness.guardrail;

import java.util.Objects;

/**
 * 内容审查裁决
 * <p>
 * 由{@link ContentModerationPolicy}返回，指示内容审查结果：
 * PASS-放行、BLOCK-中止、MASK-脱敏。
 * </p>
 * @author yangqiong
 */
public final class ModerationVerdict {

    /**
     * 裁决动作
     */
    private final Action action;

    /**
     * 裁决理由
     */
    private final String reason;

    private ModerationVerdict(Action action, String reason) {
        this.action = action;
        this.reason = reason;
    }

    /**
     * 创建放行裁决
     * @return
     */
    public static ModerationVerdict pass() {
        return new ModerationVerdict(Action.PASS, null);
    }

    /**
     * 创建中止裁决
     * @param reason
     * @return
     */
    public static ModerationVerdict block(String reason) {
        return new ModerationVerdict(Action.BLOCK, reason != null ? reason : "内容命中安全审查策略");
    }

    /**
     * 创建脱敏裁决
     * @param reason
     * @return
     */
    public static ModerationVerdict mask(String reason) {
        return new ModerationVerdict(Action.MASK, reason != null ? reason : "内容需脱敏处理");
    }

    /**
     * 获取裁决动作
     * @return
     */
    public Action getAction() {
        return action;
    }

    /**
     * 获取裁决理由
     * @return
     */
    public String getReason() {
        return reason;
    }

    /**
     * 是否为放行
     * @return
     */
    public boolean isPass() {
        return action == Action.PASS;
    }

    /**
     * 是否拦截
     * @return
     */
    public boolean isBlock() {
        return action == Action.BLOCK;
    }

    /**
     * 是否脱敏
     * @return
     */
    public boolean isMask() {
        return action == Action.MASK;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ModerationVerdict that = (ModerationVerdict) o;
        return action == that.action && Objects.equals(reason, that.reason);
    }

    @Override
    public int hashCode() {
        return Objects.hash(action, reason);
    }

    @Override
    public String toString() {
        return "ModerationVerdict{action=" + action + ", reason='" + reason + "'}";
    }

    /**
     * 裁决动作枚举
     */
    public enum Action {
        /** 放行 */
        PASS,
        /** 中止并返回拒绝消息 */
        BLOCK,
        /** 脱敏处理 */
        MASK
    }
}