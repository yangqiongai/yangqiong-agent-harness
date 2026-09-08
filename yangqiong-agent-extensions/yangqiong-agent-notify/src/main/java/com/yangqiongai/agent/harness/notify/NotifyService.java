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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

import com.yangqiongai.agent.harness.notify.dingtalk.DingTalkNotifyChannel;
import com.yangqiongai.agent.harness.notify.email.EmailNotifyChannel;
import com.yangqiongai.agent.harness.notify.feishu.FeishuNotifyChannel;
import com.yangqiongai.agent.harness.notify.webhook.WebhookNotifyChannel;

/**
 * 通知服务
 * <p>
 * 渠道注册中心与聚合发送入口：按配置中的目标逐一匹配已注册渠道投递，
 * 单个渠道失败不影响其他渠道，全部结果聚合返回；内置飞书/钉钉/Webhook/邮件四类渠道。
 * </p>
 * @author yangqiong
 */
public class NotifyService {

    /**
     * 渠道类型到渠道实现的注册表
     */
    private final Map<String, NotifyChannel> channels = new ConcurrentHashMap<>();

    /**
     * 注册渠道，同类型覆盖
     * @param channel
     * @return
     */
    public NotifyService register(NotifyChannel channel) {
        Objects.requireNonNull(channel, "通知渠道不能为空");
        channels.put(channel.type(), channel);
        return this;
    }

    /**
     * 批量注册渠道
     * @param channels
     * @return
     */
    public NotifyService registerAll(List<NotifyChannel> channels) {
        if (channels != null) {
            channels.forEach(this::register);
        }
        return this;
    }

    /**
     * 是否已注册指定类型渠道
     * @param type
     * @return
     */
    public boolean hasChannel(String type) {
        return type != null && channels.containsKey(type);
    }

    /**
     * 按配置聚合发送通知，逐目标返回投递结果
     * @param message
     * @param config
     * @return
     */
    public List<NotifyResult> notify(NotifyMessage message, NotifyConfig config) {
        Objects.requireNonNull(message, "通知消息不能为空");
        if (config == null || config.isEmpty()) {
            return List.of();
        }
        List<NotifyResult> results = new ArrayList<>();
        for (ChannelTarget target : config.getTargets()) {
            NotifyChannel channel = channels.get(target.getChannelType());
            if (channel == null) {
                results.add(NotifyResult.failure(target.getChannelType(), "未注册渠道: " + target.getChannelType()));
                continue;
            }
            results.add(channel.send(message, target));
        }
        return List.copyOf(results);
    }

    /**
     * 内置四类渠道的默认通知服务
     * @return
     */
    public static NotifyService defaults() {
        return new NotifyService().registerAll(List.of(
                new FeishuNotifyChannel(),
                new DingTalkNotifyChannel(),
                new WebhookNotifyChannel(),
                new EmailNotifyChannel()));
    }
}
