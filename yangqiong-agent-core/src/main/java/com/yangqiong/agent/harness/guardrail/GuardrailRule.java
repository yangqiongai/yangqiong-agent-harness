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
package com.yangqiong.agent.harness.guardrail;

import java.util.Objects;
import java.util.regex.Pattern;

/**
 * 护栏规则
 * <p>
 * 一条可配置的护栏规则：命名正则 + 动作 + 作用入口集合，
 * 规则经{@link GuardrailRuleRegistry}统一管理与匹配。
 * </p>
 * @author yangqiong
 */
public class GuardrailRule {

    /**
     * 规则名称（唯一，用于覆盖与移除）
     */
    private final String name;

    /**
     * 命中正则（建议大小写不敏感）
     */
    private final Pattern pattern;

    /**
     * 命中动作
     */
    private final GuardrailAction action;

    /**
     * 作用入口集合
     */
    private final GuardrailTarget[] targets;

    /**
     * 是否启用
     */
    private volatile boolean enabled;

    /**
     * 规则描述
     */
    private final String description;

    /**
     * 护栏动作
     * @author yangqiong
     */
    public enum GuardrailAction {

        /**
         * 拦截（输入/参数阻断，结果转为错误占位）
         */
        BLOCK,

        /**
         * 脱敏（命中片段替换为占位符后放行）
         */
        MASK,

        /**
         * 告警放行
         */
        WARN
    }

    public GuardrailRule(String name, Pattern pattern, GuardrailAction action,
                         GuardrailTarget[] targets, String description) {
        this.name = Objects.requireNonNull(name, "规则名称不能为空");
        this.pattern = Objects.requireNonNull(pattern, "规则正则不能为空");
        this.action = action != null ? action : GuardrailAction.BLOCK;
        this.targets = targets != null && targets.length > 0
                ? targets.clone() : GuardrailTarget.values();
        this.description = description;
        this.enabled = true;
    }

    /**
     * 创建规则（作用于全部入口）
     * @param name
     * @param regex
     * @param action
     * @return
     */
    public static GuardrailRule of(String name, String regex, GuardrailAction action) {
        return new GuardrailRule(name, Pattern.compile(regex, Pattern.CASE_INSENSITIVE),
                action, null, null);
    }

    /**
     * 创建规则（指定作用入口）
     * @param name
     * @param regex
     * @param action
     * @param targets
     * @return
     */
    public static GuardrailRule of(String name, String regex, GuardrailAction action,
                                   GuardrailTarget... targets) {
        return new GuardrailRule(name, Pattern.compile(regex, Pattern.CASE_INSENSITIVE),
                action, targets, null);
    }

    /**
     * 判断规则是否作用于指定入口
     * @param target
     * @return
     */
    public boolean appliesTo(GuardrailTarget target) {
        for (GuardrailTarget t : targets) {
            if (t == target) {
                return true;
            }
        }
        return false;
    }

    /**
     * 判断内容是否命中规则
     * @param content
     * @return
     */
    public boolean matches(String content) {
        return enabled && content != null && pattern.matcher(content).find();
    }

    public String getName() {
        return name;
    }

    public Pattern getPattern() {
        return pattern;
    }

    public GuardrailAction getAction() {
        return action;
    }

    public GuardrailTarget[] getTargets() {
        return targets.clone();
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getDescription() {
        return description;
    }
}
