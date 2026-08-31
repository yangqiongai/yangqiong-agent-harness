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
package com.yangqiong.agent.harness.starter.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Agent运行时配置
 * <p>
 * 前缀 ai.harness.runtime，用于构建{@link com.yangqiong.agent.harness.HarnessRuntimeBuilder}。
 * </p>
 * @author yangqiong
 */
@ConfigurationProperties(prefix = "ai.harness.runtime")
public class HarnessRuntimeProperties {

    /**
     * 是否启用Agent运行时自动装配
     */
    private boolean enabled = true;

    /**
     * Agent名称
     */
    private String name = "agent";

    /**
     * 系统提示词
     */
    private String systemPrompt = "你是一个智能助手";

    /**
     * 最大迭代次数
     */
    private int maxIters = 10;

    /**
     * 是否启用Web工具（未配置提供方时也注册，调用返回未配置提示）
     */
    private boolean webEnabled;

    /**
     * 是否启用向用户提问工具
     */
    private boolean askUserEnabled;

    /**
     * 是否启用计划模式
     */
    private boolean planModeEnabled;

    /**
     * 是否启用多子代理
     */
    private boolean subagentsEnabled;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getSystemPrompt() {
        return systemPrompt;
    }

    public void setSystemPrompt(String systemPrompt) {
        this.systemPrompt = systemPrompt;
    }

    public int getMaxIters() {
        return maxIters;
    }

    public void setMaxIters(int maxIters) {
        this.maxIters = maxIters;
    }

    public boolean isWebEnabled() {
        return webEnabled;
    }

    public void setWebEnabled(boolean webEnabled) {
        this.webEnabled = webEnabled;
    }

    public boolean isAskUserEnabled() {
        return askUserEnabled;
    }

    public void setAskUserEnabled(boolean askUserEnabled) {
        this.askUserEnabled = askUserEnabled;
    }

    public boolean isPlanModeEnabled() {
        return planModeEnabled;
    }

    public void setPlanModeEnabled(boolean planModeEnabled) {
        this.planModeEnabled = planModeEnabled;
    }

    public boolean isSubagentsEnabled() {
        return subagentsEnabled;
    }

    public void setSubagentsEnabled(boolean subagentsEnabled) {
        this.subagentsEnabled = subagentsEnabled;
    }
}
