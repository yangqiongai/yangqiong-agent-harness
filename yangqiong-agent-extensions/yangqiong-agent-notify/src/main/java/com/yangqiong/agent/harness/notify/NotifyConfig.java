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
package com.yangqiong.agent.harness.notify;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * 通知配置
 * <p>
 * 一次通知的投递目标集合，通知服务按目标逐一匹配渠道并发送。
 * </p>
 * @author yangqiong
 */
public class NotifyConfig {

    /**
     * 投递目标列表
     */
    private final List<ChannelTarget> targets;

    /**
     * 构造
     * @param targets
     */
    public NotifyConfig(List<ChannelTarget> targets) {
        this.targets = targets != null ? List.copyOf(targets) : List.of();
    }

    /**
     * 投递目标列表
     * @return
     */
    public List<ChannelTarget> getTargets() {
        return targets;
    }

    /**
     * 是否配置了投递目标
     * @return
     */
    public boolean isEmpty() {
        return targets.isEmpty();
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
         * 投递目标列表
         */
        private final List<ChannelTarget> targets = new ArrayList<>();

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
         * 追加投递目标
         * @param target
         * @return
         */
        public Builder target(ChannelTarget target) {
            targets.add(Objects.requireNonNull(target, "渠道目标不能为空"));
            return this;
        }

        /**
         * 构建通知配置
         * @return
         */
        public NotifyConfig build() {
            return new NotifyConfig(targets);
        }
    }
}
