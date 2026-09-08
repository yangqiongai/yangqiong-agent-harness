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
package com.yangqiongai.agent.harness.config;

/**
 * Agent消息压缩配置
 * @author yangqiong
 */
public final class AgentCompactionConfig {

    private final int triggerMessages;

    private final int keepMessages;

    private AgentCompactionConfig(int triggerMessages, int keepMessages) {
        this.triggerMessages = triggerMessages;
        this.keepMessages = keepMessages;
    }

    /**
     * 创建默认压缩配置
     * @return
     */
    public static AgentCompactionConfig defaults() {
        return new AgentCompactionConfig(40, 12);
    }

    public static Builder builder() {
        return new Builder();
    }

    /**
     * 获取触发压缩的消息数
     * @return
     */
    public int getTriggerMessages() {
        return triggerMessages;
    }

    /**
     * 获取保留的消息数
     * @return
     */
    public int getKeepMessages() {
        return keepMessages;
    }

    @Override
    public String toString() {
        return "AgentCompactionConfig{triggerMessages=" + triggerMessages + ", keepMessages=" + keepMessages + "}";
    }

    /**
     * 压缩配置构建器
     * @author yangqiong
     */
    public static class Builder {

        private int triggerMessages;

        private int keepMessages;

        public Builder triggerMessages(int triggerMessages) {
            this.triggerMessages = triggerMessages;
            return this;
        }

        public Builder keepMessages(int keepMessages) {
            this.keepMessages = keepMessages;
            return this;
        }

        public AgentCompactionConfig build() {
            return new AgentCompactionConfig(triggerMessages, keepMessages);
        }
    }
}