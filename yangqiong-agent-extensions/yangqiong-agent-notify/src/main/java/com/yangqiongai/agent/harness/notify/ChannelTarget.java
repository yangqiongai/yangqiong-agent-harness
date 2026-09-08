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
package com.yangqiongai.agent.harness.notify;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * 渠道目标
 * <p>
 * 描述一次通知投递的目标：渠道类型与渠道专属配置（如 webhook 地址、邮件服务器与收件人等）。
 * </p>
 * @author yangqiong
 */
public class ChannelTarget {

    /**
     * 渠道类型
     */
    private final String channelType;

    /**
     * 渠道配置键值
     */
    private final Map<String, String> properties;

    /**
     * 构造
     * @param channelType
     * @param properties
     */
    public ChannelTarget(String channelType, Map<String, String> properties) {
        this.channelType = Objects.requireNonNull(channelType, "渠道类型不能为空");
        this.properties = properties != null ? Map.copyOf(properties) : Map.of();
    }

    /**
     * 渠道类型
     * @return
     */
    public String getChannelType() {
        return channelType;
    }

    /**
     * 渠道配置键值
     * @return
     */
    public Map<String, String> getProperties() {
        return properties;
    }

    /**
     * 读取单个配置项，缺失时返回默认值
     * @param key
     * @param defaultValue
     * @return
     */
    public String getProperty(String key, String defaultValue) {
        return properties.getOrDefault(key, defaultValue);
    }

    /**
     * 创建构建器
     * @return
     */
    public static Builder builder() {
        return Builder.builder();
    }

    /**
     * 构建器
     * @author yangqiong
     */
    public static class Builder {

        /**
         * 渠道类型
         */
        private String channelType;

        /**
         * 渠道配置键值
         */
        private final Map<String, String> properties = new LinkedHashMap<>();

        private Builder() {
        }

        /**
         * 创建构建器
         * @return
         */
        public static Builder builder() {
            return new Builder();
        }

        /**
         * 设置渠道类型
         * @param channelType
         * @return
         */
        public Builder channelType(String channelType) {
            this.channelType = channelType;
            return this;
        }

        /**
         * 追加配置项
         * @param key
         * @param value
         * @return
         */
        public Builder property(String key, String value) {
            properties.put(key, value);
            return this;
        }

        /**
         * 构建渠道目标
         * @return
         */
        public ChannelTarget build() {
            return new ChannelTarget(channelType, properties);
        }
    }
}
