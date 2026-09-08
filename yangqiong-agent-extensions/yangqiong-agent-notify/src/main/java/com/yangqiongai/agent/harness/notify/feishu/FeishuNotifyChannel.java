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
package com.yangqiongai.agent.harness.notify.feishu;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.yangqiongai.agent.harness.notify.ChannelTarget;
import com.yangqiongai.agent.harness.notify.NotifyChannel;
import com.yangqiongai.agent.harness.notify.NotifyMessage;
import com.yangqiongai.agent.harness.notify.NotifyResult;
import com.yangqiongai.agent.harness.notify.internal.HttpJsonPoster;

/**
 * 飞书通知渠道
 * <p>
 * 按飞书自定义机器人协议以文本消息推送，配置项url为机器人webhook地址（含access_token），
 * 响应中code为0视为投递成功。
 * </p>
 * @author yangqiong
 */
public class FeishuNotifyChannel implements NotifyChannel {

    /**
     * 机器人webhook地址配置key
     */
    private static final String KEY_URL = "url";

    /**
     * JSON映射器
     */
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Override
    public String type() {
        return TYPE_FEISHU;
    }

    @Override
    public NotifyResult send(NotifyMessage message, ChannelTarget target) {
        String url = target.getProperty(KEY_URL, null);
        if (url == null || url.isBlank()) {
            return NotifyResult.failure(type(), "缺少飞书机器人webhook地址配置(url)");
        }
        try {
            String json = buildPayload(message);
            HttpJsonPoster.Response response = HttpJsonPoster.post(url, json);
            return evaluate(response);
        } catch (Exception e) {
            return NotifyResult.failure(type(), e.getMessage());
        }
    }

    /**
     * 组装飞书文本消息载荷
     * @param message
     * @return
     */
    private String buildPayload(NotifyMessage message) throws JsonProcessingException {
        ObjectNode root = MAPPER.createObjectNode();
        root.put("msg_type", "text");
        ObjectNode content = root.putObject("content");
        content.put("text", message.getTitle() + "\n" + message.getContent());
        return MAPPER.writeValueAsString(root);
    }

    /**
     * 依据响应判定投递结果
     * @param response
     * @return
     */
    private NotifyResult evaluate(HttpJsonPoster.Response response) {
        if (!response.is2xx()) {
            return NotifyResult.failure(type(), "飞书返回非2xx状态码: " + response.status());
        }
        try {
            JsonNode body = MAPPER.readTree(response.body());
            int code = body.path("code").asInt(-1);
            if (code == 0) {
                return NotifyResult.success(type());
            }
            return NotifyResult.failure(type(), "飞书返回错误码: " + code + ", 消息: " + body.path("msg").asText());
        } catch (JsonProcessingException e) {
            return NotifyResult.failure(type(), "飞书响应解析失败: " + e.getMessage());
        }
    }
}
