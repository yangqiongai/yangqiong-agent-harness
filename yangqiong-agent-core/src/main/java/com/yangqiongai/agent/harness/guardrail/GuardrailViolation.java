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

/**
 * 护栏命中
 * <p>
 * 记录一次护栏命中的规则、入口与命中片段，供拦截文案与审计使用。
 * </p>
 * @author yangqiong
 */
public class GuardrailViolation {

    /**
     * 命中规则
     */
    private final GuardrailRule rule;

    /**
     * 作用入口
     */
    private final GuardrailTarget target;

    /**
     * 命中片段（脱敏后的前缀样本，避免日志泄露全文）
     */
    private final String matchedSnippet;

    public GuardrailViolation(GuardrailRule rule, GuardrailTarget target, String matchedSnippet) {
        this.rule = rule;
        this.target = target;
        this.matchedSnippet = matchedSnippet;
    }

    public GuardrailRule getRule() {
        return rule;
    }

    public GuardrailTarget getTarget() {
        return target;
    }

    public String getMatchedSnippet() {
        return matchedSnippet;
    }

    /**
     * 生成违规描述
     * @return
     */
    public String describe() {
        return "护栏命中: rule=" + rule.getName() + ", target=" + target
                + ", action=" + rule.getAction()
                + (matchedSnippet != null ? ", snippet=" + matchedSnippet : "");
    }
}
