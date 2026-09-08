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

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import com.yangqiongai.agent.harness.notify.ChannelTarget;
import com.yangqiongai.agent.harness.notify.NotifyMessage;
import com.yangqiongai.agent.harness.notify.NotifyResult;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * 飞书通知渠道测试
 * @author yangqiong
 */
class FeishuNotifyChannelTest {

    /**
     * 本地HTTP服务
     */
    private HttpServer server;

    /**
     * 服务基地址
     */
    private String baseUrl;

    /**
     * 最近一次请求体
     */
    private final AtomicReference<String> lastBody = new AtomicReference<>();

    /**
     * 启动本地HTTP服务模拟飞书端点
     * @throws IOException
     */
    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            lastBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            boolean fail = exchange.getRequestURI().getPath().contains("fail");
            String body = fail ? "{\"code\":9499,\"msg\":\"频控\"}" : "{\"code\":0,\"msg\":\"success\"}";
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        server.start();
        baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
    }

    /**
     * 停止本地HTTP服务
     */
    @AfterEach
    void stopServer() {
        server.stop(0);
    }

    /**
     * 缺少webhook地址时返回失败结果
     */
    @Test
    void missingUrlReturnsFailure() {
        FeishuNotifyChannel channel = new FeishuNotifyChannel();

        NotifyResult result = channel.send(message(),
                ChannelTarget.builder().channelType("feishu").build());

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getErrorMessage()).contains("url");
    }

    /**
     * 响应code为0视为投递成功
     */
    @Test
    void succeedsWhenCodeZero() {
        FeishuNotifyChannel channel = new FeishuNotifyChannel();

        NotifyResult result = channel.send(message(), target(baseUrl + "/hook"));

        assertThat(result.isSuccess()).isTrue();
    }

    /**
     * 响应code非0视为投递失败
     */
    @Test
    void failsWhenCodeNonZero() {
        FeishuNotifyChannel channel = new FeishuNotifyChannel();

        NotifyResult result = channel.send(message(), target(baseUrl + "/fail"));

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getErrorMessage()).contains("9499");
    }

    /**
     * 请求体符合飞书文本消息协议
     * @throws Exception
     */
    @Test
    void requestPayloadIsTextMessage() throws Exception {
        FeishuNotifyChannel channel = new FeishuNotifyChannel();

        channel.send(message(), target(baseUrl + "/hook"));

        JsonNode payload = new ObjectMapper().readTree(lastBody.get());
        assertThat(payload.path("msg_type").asText()).isEqualTo("text");
        assertThat(payload.path("content").path("text").asText()).contains("标题").contains("内容");
    }

    private NotifyMessage message() {
        return NotifyMessage.Builder.builder().title("标题").content("内容").build();
    }

    private ChannelTarget target(String url) {
        return ChannelTarget.builder().channelType("feishu").property("url", url).build();
    }
}
