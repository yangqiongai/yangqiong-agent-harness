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
package com.yangqiongai.agent.harness.notify.internal;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

/**
 * HTTP JSON 投递器
 * <p>
 * 基于 JDK HttpClient 的轻量 JSON POST 封装，统一超时与异常收敛，
 * 供 Webhook/飞书/钉钉等渠道复用。
 * </p>
 * @author yangqiong
 */
public final class HttpJsonPoster {

    /**
     * 连接超时
     */
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(10);

    /**
     * 请求超时
     */
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(15);

    /**
     * 共享客户端
     */
    private static final HttpClient CLIENT = HttpClient.newBuilder()
            .connectTimeout(CONNECT_TIMEOUT)
            .build();

    private HttpJsonPoster() {
    }

    /**
     * 以JSON体POST到指定地址，返回状态码与响应体
     * @param url
     * @param json
     * @return
     */
    public static Response post(String url, String json) {
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                    .timeout(REQUEST_TIMEOUT)
                    .header("Content-Type", "application/json; charset=utf-8")
                    .POST(HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8))
                    .build();
            HttpResponse<String> response = CLIENT.send(request,
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            return new Response(response.statusCode(), response.body());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new HttpPostException("通知请求被中断: " + url, e);
        } catch (IOException e) {
            throw new HttpPostException("通知请求失败: " + url, e);
        }
    }

    /**
     * HTTP响应
     * @param status
     * @param body
     */
    public record Response(int status, String body) {

        /**
         * 是否为2xx成功状态
         * @return
         */
        public boolean is2xx() {
            return status >= 200 && status < 300;
        }
    }

    /**
     * 通知HTTP异常
     * <p>
     * 请求发送阶段的失败统一包装，由渠道捕获转换为失败结果。
     * </p>
     * @author yangqiong
     */
    public static class HttpPostException extends RuntimeException {

        /**
         * 构造
         * @param message
         * @param cause
         */
        public HttpPostException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
