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

/**
 * 通知消息
 * <p>
 * 描述一条待发送的通知：标题与正文，由渠道按自身协议组装为对外载荷。
 * </p>
 * @author yangqiong
 */
public class NotifyMessage {

    /**
     * 通知标题
     */
    private final String title;

    /**
     * 通知正文
     */
    private final String content;

    /**
     * 构造
     * @param title
     * @param content
     */
    public NotifyMessage(String title, String content) {
        this.title = title != null ? title : "";
        this.content = content != null ? content : "";
    }

    /**
     * 通知标题
     * @return
     */
    public String getTitle() {
        return title;
    }

    /**
     * 通知正文
     * @return
     */
    public String getContent() {
        return content;
    }

    /**
     * 构建器
     * @author yangqiong
     */
    public static class Builder {

        /**
         * 通知标题
         */
        private String title;

        /**
         * 通知正文
         */
        private String content;

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
         * 设置通知标题
         * @param title
         * @return
         */
        public Builder title(String title) {
            this.title = title;
            return this;
        }

        /**
         * 设置通知正文
         * @param content
         * @return
         */
        public Builder content(String content) {
            this.content = content;
            return this;
        }

        /**
         * 构建通知消息
         * @return
         */
        public NotifyMessage build() {
            return new NotifyMessage(title, content);
        }
    }
}
