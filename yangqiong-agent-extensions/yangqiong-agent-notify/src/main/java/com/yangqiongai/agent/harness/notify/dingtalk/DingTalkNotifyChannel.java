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
package com.yangqiongai.agent.harness.notify.dingtalk;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

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
 * 钉钉通知渠道
 * <p>
 * 按钉钉自定义机器人协议以文本消息推送，配置项url为机器人webhook地址，可选secret开启加签，
 * 响应中errcode为0视为投递成功。
 * </p>
 * @author yangqiong
 */
public class DingTalkNotifyChannel implements NotifyChannel {

    /**
     * 机器人webhook地址配置key
     */
    private static final String KEY_URL = "url";

    /**
     * 加签密钥配置key
     */
    private static final String KEY_SECRET = "secret";

    /**
     * JSON映射器
     */
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Override
    public String type() {
        return TYPE_DINGTALK;
    }

    @Override
    public NotifyResult send(NotifyMessage message, ChannelTarget target) {
        String url = target.getProperty(KEY_URL, null);
        if (url == null || url.isBlank()) {
            return NotifyResult.failure(type(), "缺少钉钉机器人webhook地址配置(url)");
        }
        try {
            String targetUrl = appendSign(url, target.getProperty(KEY_SECRET, null));
            String json = buildPayload(message);
            HttpJsonPoster.Response response = HttpJsonPoster.post(targetUrl, json);
            return evaluate(response);
        } catch (Exception e) {
            return NotifyResult.failure(type(), e.getMessage());
        }
    }

    /**
     * 配置了加签密钥时按钉钉协议追加时间戳与签名参数
     * @param url
     * @param secret
     * @return
     */
    private String appendSign(String url, String secret) throws Exception {
        if (secret == null || secret.isBlank()) {
            return url;
        }
        long timestamp = System.currentTimeMillis();
        String stringToSign = timestamp + "\n" + secret;
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        byte[] signData = mac.doFinal(stringToSign.getBytes(StandardCharsets.UTF_8));
        String sign = URLEncoder.encode(Base64.getEncoder().encodeToString(signData), StandardCharsets.UTF_8);
        String separator = url.contains("?") ? "&" : "?";
        return url + separator + "timestamp=" + timestamp + "&sign=" + sign;
    }

    /**
     * 组装钉钉文本消息载荷
     * @param message
     * @return
     */
    private String buildPayload(NotifyMessage message) throws JsonProcessingException {
        ObjectNode root = MAPPER.createObjectNode();
        root.put("msgtype", "text");
        ObjectNode text = root.putObject("text");
        text.put("content", message.getTitle() + "\n" + message.getContent());
        return MAPPER.writeValueAsString(root);
    }

    /**
     * 依据响应判定投递结果
     * @param response
     * @return
     */
    private NotifyResult evaluate(HttpJsonPoster.Response response) {
        if (!response.is2xx()) {
            return NotifyResult.failure(type(), "钉钉返回非2xx状态码: " + response.status());
        }
        try {
            JsonNode body = MAPPER.readTree(response.body());
            int code = body.path("errcode").asInt(-1);
            if (code == 0) {
                return NotifyResult.success(type());
            }
            return NotifyResult.failure(type(), "钉钉返回错误码: " + code + ", 消息: " + body.path("errmsg").asText());
        } catch (JsonProcessingException e) {
            return NotifyResult.failure(type(), "钉钉响应解析失败: " + e.getMessage());
        }
    }
}
