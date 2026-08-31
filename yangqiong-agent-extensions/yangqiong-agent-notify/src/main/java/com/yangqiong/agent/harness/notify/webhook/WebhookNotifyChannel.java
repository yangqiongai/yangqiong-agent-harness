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
package com.yangqiong.agent.harness.notify.webhook;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.yangqiong.agent.harness.notify.ChannelTarget;
import com.yangqiong.agent.harness.notify.NotifyChannel;
import com.yangqiong.agent.harness.notify.NotifyMessage;
import com.yangqiong.agent.harness.notify.NotifyResult;
import com.yangqiong.agent.harness.notify.internal.HttpJsonPoster;

/**
 * Webhook通知渠道
 * <p>
 * 将标题与正文以{@code {"title":...,"content":...}}的JSON体POST到通用Webhook地址，
 * 配置项url为必填，2xx响应视为投递成功。
 * </p>
 * @author yangqiong
 */
public class WebhookNotifyChannel implements NotifyChannel {

    /**
     * webhook地址配置key
     */
    private static final String KEY_URL = "url";

    /**
     * JSON映射器
     */
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Override
    public String type() {
        return TYPE_WEBHOOK;
    }

    @Override
    public NotifyResult send(NotifyMessage message, ChannelTarget target) {
        String url = target.getProperty(KEY_URL, null);
        if (url == null || url.isBlank()) {
            return NotifyResult.failure(type(), "缺少webhook地址配置(url)");
        }
        try {
            String json = buildPayload(message);
            HttpJsonPoster.Response response = HttpJsonPoster.post(url, json);
            if (!response.is2xx()) {
                return NotifyResult.failure(type(), "webhook返回非2xx状态码: " + response.status());
            }
            return NotifyResult.success(type());
        } catch (Exception e) {
            return NotifyResult.failure(type(), e.getMessage());
        }
    }

    /**
     * 组装通知JSON载荷
     * @param message
     * @return
     */
    private String buildPayload(NotifyMessage message) throws JsonProcessingException {
        ObjectNode node = MAPPER.createObjectNode();
        node.put("title", message.getTitle());
        node.put("content", message.getContent());
        return MAPPER.writeValueAsString(node);
    }
}
