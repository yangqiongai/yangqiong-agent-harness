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

import java.util.ArrayList;
import java.util.List;

/**
 * 护栏规则外部配置
 * <p>
 * 供YAML/JSON规则文件反序列化的传输模型，{@link GuardrailRuleRegistry}加载后转为运行时规则。
 * </p>
 * @author yangqiong
 */
public class GuardrailRuleConfig {

    /**
     * 规则列表
     */
    private List<RuleEntry> rules = new ArrayList<>();

    /**
     * 获取规则列表
     * @return
     */
    public List<RuleEntry> getRules() {
        return rules;
    }

    /**
     * 设置规则列表
     * @param rules
     */
    public void setRules(List<RuleEntry> rules) {
        this.rules = rules != null ? rules : new ArrayList<>();
    }

    /**
     * 规则配置项
     * @author yangqiong
     */
    public static class RuleEntry {

        /**
         * 规则名称（唯一，同名覆盖）
         */
        private String name;

        /**
         * 命中正则
         */
        private String regex;

        /**
         * 命中动作：BLOCK/MASK/WARN
         */
        private String action;

        /**
         * 作用入口集合（INPUT/TOOL_PARAM/TOOL_RESULT/OUTPUT/RETRIEVAL），缺省全部
         */
        private List<String> targets;

        /**
         * 是否启用，默认true
         */
        private boolean enabled = true;

        /**
         * 规则描述
         */
        private String description;

        /**
         * 获取规则名称
         * @return
         */
        public String getName() {
            return name;
        }

        /**
         * 设置规则名称
         * @param name
         */
        public void setName(String name) {
            this.name = name;
        }

        /**
         * 获取命中正则
         * @return
         */
        public String getRegex() {
            return regex;
        }

        /**
         * 设置命中正则
         * @param regex
         */
        public void setRegex(String regex) {
            this.regex = regex;
        }

        /**
         * 获取命中动作
         * @return
         */
        public String getAction() {
            return action;
        }

        /**
         * 设置命中动作
         * @param action
         */
        public void setAction(String action) {
            this.action = action;
        }

        /**
         * 获取作用入口集合
         * @return
         */
        public List<String> getTargets() {
            return targets;
        }

        /**
         * 设置作用入口集合
         * @param targets
         */
        public void setTargets(List<String> targets) {
            this.targets = targets;
        }

        /**
         * 是否启用
         * @return
         */
        public boolean getEnabled() {
            return enabled;
        }

        /**
         * 设置是否启用
         * @param enabled
         */
        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        /**
         * 获取规则描述
         * @return
         */
        public String getDescription() {
            return description;
        }

        /**
         * 设置规则描述
         * @param description
         */
        public void setDescription(String description) {
            this.description = description;
        }
    }
}