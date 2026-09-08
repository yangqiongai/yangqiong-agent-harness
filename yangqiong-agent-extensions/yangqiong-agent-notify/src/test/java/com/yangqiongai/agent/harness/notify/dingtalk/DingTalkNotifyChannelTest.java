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
 * 钉钉通知渠道测试
 * @author yangqiong
 */
class DingTalkNotifyChannelTest {

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
     * 最近一次请求query
     */
    private final AtomicReference<String> lastQuery = new AtomicReference<>();

    /**
     * 启动本地HTTP服务模拟钉钉端点
     * @throws IOException
     */
    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            lastBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            lastQuery.set(exchange.getRequestURI().getQuery());
            boolean fail = exchange.getRequestURI().getPath().contains("fail");
            String body = fail ? "{\"errcode\":310000,\"errmsg\":\"签名不匹配\"}"
                    : "{\"errcode\":0,\"errmsg\":\"ok\"}";
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
        DingTalkNotifyChannel channel = new DingTalkNotifyChannel();

        NotifyResult result = channel.send(message(),
                ChannelTarget.builder().channelType("dingtalk").build());

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getErrorMessage()).contains("url");
    }

    /**
     * 响应errcode为0视为投递成功
     */
    @Test
    void succeedsWhenErrCodeZero() {
        DingTalkNotifyChannel channel = new DingTalkNotifyChannel();

        NotifyResult result = channel.send(message(), target(baseUrl + "/hook"));

        assertThat(result.isSuccess()).isTrue();
    }

    /**
     * 响应errcode非0视为投递失败
     */
    @Test
    void failsWhenErrCodeNonZero() {
        DingTalkNotifyChannel channel = new DingTalkNotifyChannel();

        NotifyResult result = channel.send(message(), target(baseUrl + "/fail"));

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getErrorMessage()).contains("310000");
    }

    /**
     * 未配置密钥时请求地址不追加签名参数
     */
    @Test
    void noSecretKeepsUrlUnchanged() {
        DingTalkNotifyChannel channel = new DingTalkNotifyChannel();

        channel.send(message(), target(baseUrl + "/hook"));

        assertThat(lastQuery.get()).isNullOrEmpty();
    }

    /**
     * 配置密钥时请求地址追加时间戳与签名
     */
    @Test
    void secretAppendsTimestampAndSign() {
        DingTalkNotifyChannel channel = new DingTalkNotifyChannel();

        channel.send(message(), ChannelTarget.builder().channelType("dingtalk")
                .property("url", baseUrl + "/hook")
                .property("secret", "SECxxx")
                .build());

        assertThat(lastQuery.get()).contains("timestamp=").contains("sign=");
    }

    /**
     * 请求体符合钉钉文本消息协议
     * @throws Exception
     */
    @Test
    void requestPayloadIsTextMessage() throws Exception {
        DingTalkNotifyChannel channel = new DingTalkNotifyChannel();

        channel.send(message(), target(baseUrl + "/hook"));

        JsonNode payload = new ObjectMapper().readTree(lastBody.get());
        assertThat(payload.path("msgtype").asText()).isEqualTo("text");
        assertThat(payload.path("text").path("content").asText()).contains("标题").contains("内容");
    }

    private NotifyMessage message() {
        return NotifyMessage.Builder.builder().title("标题").content("内容").build();
    }

    private ChannelTarget target(String url) {
        return ChannelTarget.builder().channelType("dingtalk").property("url", url).build();
    }
}
