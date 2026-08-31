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

import java.util.Objects;

/**
 * 结构化输出重试策略
 * <p>
 * 配置结构化输出校验失败后的自动修复重试：最大重试次数与错误注入模板。
 * 校验失败时引擎把"校验错误 + 要求修正"作为下一条USER消息继续迭代。
 * 未显式配置时使用默认策略（最多重试2次）。
 * </p>
 * @author yangqiong
 */
public final class StructuredOutputRetryPolicy {

    /**
     * 默认最大重试次数
     */
    public static final int DEFAULT_MAX_RETRIES = 2;

    /**
     * 默认错误注入模板，%s占位符替换为校验错误描述
     */
    public static final String DEFAULT_ERROR_TEMPLATE =
            "你的上一次输出不符合结构化要求，错误: %s。请严格按照JSON Schema格式重新输出。";

    /**
     * 最大重试次数
     */
    private final int maxRetries;

    /**
     * 错误注入模板，含%s占位符
     */
    private final String errorInjectionTemplate;

    /**
     * 全参构造
     * @param maxRetries
     * @param errorInjectionTemplate
     */
    public StructuredOutputRetryPolicy(int maxRetries, String errorInjectionTemplate) {
        this.maxRetries = maxRetries > 0 ? maxRetries : DEFAULT_MAX_RETRIES;
        this.errorInjectionTemplate = errorInjectionTemplate != null && !errorInjectionTemplate.isBlank()
                ? errorInjectionTemplate : DEFAULT_ERROR_TEMPLATE;
    }

    /**
     * 创建默认策略
     * @return
     */
    public static StructuredOutputRetryPolicy defaultPolicy() {
        return new StructuredOutputRetryPolicy(DEFAULT_MAX_RETRIES, DEFAULT_ERROR_TEMPLATE);
    }

    /**
     * 获取最大重试次数
     * @return
     */
    public int getMaxRetries() {
        return maxRetries;
    }

    /**
     * 获取错误注入模板
     * @return
     */
    public String getErrorInjectionTemplate() {
        return errorInjectionTemplate;
    }

    /**
     * 按校验错误格式化纠正提示
     * @param validationError
     * @return
     */
    public String formatError(String validationError) {
        return String.format(errorInjectionTemplate,
                validationError != null ? validationError : "输出格式不合法");
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        StructuredOutputRetryPolicy that = (StructuredOutputRetryPolicy) o;
        return maxRetries == that.maxRetries
                && Objects.equals(errorInjectionTemplate, that.errorInjectionTemplate);
    }

    @Override
    public int hashCode() {
        return Objects.hash(maxRetries, errorInjectionTemplate);
    }

    @Override
    public String toString() {
        return "StructuredOutputRetryPolicy{maxRetries=" + maxRetries
                + ", errorInjectionTemplate='" + errorInjectionTemplate + "'}";
    }
}