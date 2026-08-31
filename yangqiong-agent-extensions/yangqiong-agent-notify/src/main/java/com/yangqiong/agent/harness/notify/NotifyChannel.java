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

/**
 * 通知渠道
 * <p>
 * 通知的投递出口抽象：按渠道类型标识自身，将通知消息按渠道协议组装并发送，
 * 发送成败统一收敛为{@link NotifyResult}，实现应自行隔离异常而不向上抛出。
 * </p>
 * @author yangqiong
 */
public interface NotifyChannel {

    /**
     * 通用Webhook渠道类型标识
     */
    String TYPE_WEBHOOK = "webhook";

    /**
     * 飞书渠道类型标识
     */
    String TYPE_FEISHU = "feishu";

    /**
     * 钉钉渠道类型标识
     */
    String TYPE_DINGTALK = "dingtalk";

    /**
     * 邮件渠道类型标识
     */
    String TYPE_EMAIL = "email";

    /**
     * 渠道类型标识，与{@link ChannelTarget}的channelType匹配
     * @return
     */
    String type();

    /**
     * 按目标配置发送通知
     * @param message
     * @param target
     * @return
     */
    NotifyResult send(NotifyMessage message, ChannelTarget target);
}
