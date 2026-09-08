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
package com.yangqiongai.agent.harness.observability.trace;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.yangqiongai.agent.harness.core.trace.SpanInfo;

/**
 * OTLP批量缓冲导出器测试
 * <p>
 * 注入内存HttpTransport替代真实网络，覆盖分批、端点规整、溢出淘汰、失败容错路径。
 * </p>
 * @author yangqiong
 */
class OtlpBatchBufferTest {

    /**
     * 捕获导出请求的内存传输通道
     */
    private static final class CapturingTransport implements OtlpBatchBuffer.HttpTransport {

        /**
         * 已捕获的请求体列表
         */
        private final List<String> bodies = new ArrayList<>();

        /**
         * 导出是否模拟失败
         */
        private volatile boolean failNext;

        @Override
        public boolean post(String url, String contentType, byte[] body, int timeoutMs) {
            if (failNext) {
                failNext = false;
                return false;
            }
            bodies.add(new String(body, java.nio.charset.StandardCharsets.UTF_8));
            return true;
        }
    }

    /**
     * 构建测试Span
     * @param name
     * @return
     */
    private static SpanInfo span(String name) {
        return new SpanInfo("0123456789abcdef0123456789abcdef",
                "0123456789abcdef0123456789abcdef", null, name, 10L, Map.of());
    }

    /**
     * 验证端点自动补全/v1/traces并按批量分批导出
     */
    @Test
    void flushShouldSplitBatchesAndNormalizeEndpoint() {
        CapturingTransport transport = new CapturingTransport();
        OtlpBatchBuffer buffer = new OtlpBatchBuffer("http://collector:4318", "svc",
                2, 0, 1000, 16, transport);

        buffer.offer(span("a"));
        buffer.offer(span("b"));
        buffer.offer(span("c"));
        buffer.flush();
        buffer.close();

        assertThat(transport.bodies).hasSize(2);
        assertThat(transport.bodies.get(0)).contains("\"name\":\"a\"").contains("\"name\":\"b\"");
        assertThat(transport.bodies.get(1)).contains("\"name\":\"c\"");
        assertThat(buffer.pendingCount()).isZero();
    }

    /**
     * 验证close触发收尾导出且幂等
     */
    @Test
    void closeShouldFlushRemainingOnce() {
        CapturingTransport transport = new CapturingTransport();
        OtlpBatchBuffer buffer = new OtlpBatchBuffer("http://collector:4318/v1/traces", "svc",
                64, 0, 1000, 16, transport);

        buffer.offer(span("a"));
        buffer.close();
        buffer.close();

        assertThat(transport.bodies).hasSize(1);
        assertThat(transport.bodies.get(0)).contains("\"name\":\"a\"");
    }

    /**
     * 验证缓冲满时淘汰最旧Span并计数
     */
    @Test
    void offerShouldEvictOldestWhenBufferFull() {
        CapturingTransport transport = new CapturingTransport();
        OtlpBatchBuffer buffer = new OtlpBatchBuffer("http://collector:4318", "svc",
                8, 0, 1000, 2, transport);

        buffer.offer(span("a"));
        buffer.offer(span("b"));
        buffer.offer(span("c"));
        buffer.flush();

        assertThat(buffer.droppedCount()).isEqualTo(1);
        assertThat(buffer.pendingCount()).isZero();
        assertThat(transport.bodies.get(0)).contains("\"name\":\"b\"").contains("\"name\":\"c\"");
        assertThat(transport.bodies.get(0)).doesNotContain("\"name\":\"a\"");
    }

    /**
     * 验证导出失败不外抛异常且丢弃该批
     */
    @Test
    void flushShouldSwallowTransportFailure() {
        CapturingTransport transport = new CapturingTransport();
        transport.failNext = true;
        OtlpBatchBuffer buffer = new OtlpBatchBuffer("http://collector:4318", "svc",
                64, 0, 1000, 16, transport);

        buffer.offer(span("a"));
        buffer.flush();
        buffer.offer(span("b"));
        buffer.flush();

        assertThat(transport.bodies).hasSize(1);
        assertThat(transport.bodies.get(0)).contains("\"name\":\"b\"");
    }
}
