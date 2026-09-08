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
package com.yangqiongai.agent.harness.mcp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import java.io.File;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * MCP stdio传输测试，经子进程回显服务验证按行JSON-RPC通信
 * @author yangqiong
 */
class McpStdioTransportTest {

    private McpStdioTransport transport;

    @AfterEach
    void tearDown() {
        if (transport != null) {
            transport.close().block(Duration.ofSeconds(5));
        }
    }

    @Test
    void callRoundTripsThroughSubprocess() {
        transport = newTransport();

        JsonNode result = transport.call("initialize",
                McpJsonRpc.mapper().createObjectNode()).block(Duration.ofSeconds(15));

        assertThat(result.path("method").asText()).isEqualTo("initialize");
    }

    @Test
    void sequentialCallsGetIndependentResults() {
        transport = newTransport();

        JsonNode first = transport.call("ping", null).block(Duration.ofSeconds(15));
        JsonNode second = transport.call("tools/list", null).block(Duration.ofSeconds(15));

        assertThat(first.path("method").asText()).isEqualTo("ping");
        assertThat(second.path("method").asText()).isEqualTo("tools/list");
    }

    @Test
    void errorResponseMappedToRpcException() {
        transport = newTransport();

        assertThatThrownBy(() -> transport.call("fail", null).block(Duration.ofSeconds(15)))
                .isInstanceOf(McpRpcException.class)
                .hasMessageContaining("boom");
    }

    @Test
    void nonJsonLinesIgnored() {
        transport = newTransport();

        JsonNode result = transport.call("noisy", null).block(Duration.ofSeconds(15));

        assertThat(result.path("method").asText()).isEqualTo("noisy");
    }

    @Test
    void notifyDoesNotBreakFollowingRequests() {
        transport = newTransport();

        transport.notify("notifications/initialized").block(Duration.ofSeconds(15));

        JsonNode result = transport.call("ping", null).block(Duration.ofSeconds(15));
        assertThat(result.path("method").asText()).isEqualTo("ping");
    }

    @Test
    void closeFailsPendingRequests() throws Exception {
        transport = newTransport();
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Throwable> error = new AtomicReference<>();

        transport.call("hang", null).subscribe(ignored -> { }, e -> {
            error.set(e);
            latch.countDown();
        });

        transport.close().block(Duration.ofSeconds(5));

        assertThat(latch.await(5, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
        assertThat(error.get()).isInstanceOf(McpRpcException.class)
                .hasMessageContaining("传输已关闭");
    }

    private McpStdioTransport newTransport() {
        String javaHome = System.getProperty("java.home");
        boolean windows = System.getProperty("os.name", "").toLowerCase().contains("win");
        String javaBin = javaHome + File.separator + "bin" + File.separator + (windows ? "java.exe" : "java");
        return new McpStdioTransport(javaBin,
                List.of("-cp", System.getProperty("java.class.path"), McpStdioEchoServer.class.getName()),
                Map.of(), Duration.ofSeconds(30));
    }
}
