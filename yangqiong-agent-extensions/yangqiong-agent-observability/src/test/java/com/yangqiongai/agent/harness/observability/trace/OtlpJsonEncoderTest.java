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

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.yangqiongai.agent.harness.core.trace.SpanInfo;

/**
 * OTLP JSON编码器测试
 * @author yangqiong
 */
class OtlpJsonEncoderTest {

    /**
     * W3C标准长度的traceId/spanId样例
     */
    private static final String TRACE_ID = "0123456789abcdef0123456789abcdef";

    /**
     * 超长spanId样例（UUID去横线32位）
     */
    private static final String LONG_SPAN_ID = "0123456789abcdef0123456789abcdef";

    /**
     * 验证报文结构：resource/scope包裹、ID规整、时间戳回推、属性类型映射
     */
    @Test
    void encodeShouldProduceOtlpCompliantJson() {
        Map<String, Object> attributes = new java.util.LinkedHashMap<>();
        attributes.put("status", "completed");
        attributes.put("iteration", 3);
        attributes.put("success", true);
        attributes.put("ratio", 1.5);
        SpanInfo span = new SpanInfo(TRACE_ID, LONG_SPAN_ID, null, "agent_run", 100L, attributes);
        OtlpBatchBuffer.TimedSpan timed = new OtlpBatchBuffer.TimedSpan(span, 1_700_000_000_000L);

        String json = OtlpJsonEncoder.encode(List.of(timed), "demo-agent");

        assertThat(json).startsWith("{\"resourceSpans\":[{\"resource\":{\"attributes\":[");
        assertThat(json).contains("\"key\":\"service.name\",\"value\":{\"stringValue\":\"demo-agent\"}");
        assertThat(json).contains("\"traceId\":\"" + TRACE_ID + "\"");
        assertThat(json).contains("\"spanId\":\"0123456789abcdef\"");
        assertThat(json).doesNotContain("parentSpanId");
        assertThat(json).contains("\"name\":\"agent_run\"");
        assertThat(json).contains("\"kind\":\"SPAN_KIND_INTERNAL\"");
        assertThat(json).contains("\"startTimeUnixNano\":\"1699999999900000000\"");
        assertThat(json).contains("\"endTimeUnixNano\":\"1700000000000000000\"");
        assertThat(json).contains("\"key\":\"status\",\"value\":{\"stringValue\":\"completed\"}");
        assertThat(json).contains("\"key\":\"iteration\",\"value\":{\"intValue\":\"3\"}");
        assertThat(json).contains("\"key\":\"success\",\"value\":{\"boolValue\":true}");
        assertThat(json).contains("\"key\":\"ratio\",\"value\":{\"doubleValue\":1.5}");
        assertThat(json).endsWith("]}]}]}");
    }

    /**
     * 验证父Span与多个Span的列表编码
     */
    @Test
    void encodeShouldKeepParentSpanIdAndJoinMultipleSpans() {
        SpanInfo root = new SpanInfo(TRACE_ID, LONG_SPAN_ID, null, "agent_run", 100L, Map.of());
        SpanInfo child = new SpanInfo(TRACE_ID, LONG_SPAN_ID, LONG_SPAN_ID, "reasoning", 40L, Map.of());
        List<OtlpBatchBuffer.TimedSpan> spans = List.of(
                new OtlpBatchBuffer.TimedSpan(root, 1_700_000_000_000L),
                new OtlpBatchBuffer.TimedSpan(child, 1_700_000_000_050L));

        String json = OtlpJsonEncoder.encode(spans, "svc");

        assertThat(json).contains("\"parentSpanId\":\"0123456789abcdef\"");
        assertThat(json).contains("\"name\":\"reasoning\"");
        int nameCount = json.split("\"name\":", -1).length - 1;
        assertThat(nameCount).isEqualTo(3);
    }

    /**
     * 验证空集合输出合法的空报文
     */
    @Test
    void encodeShouldHandleEmptyBatch() {
        String json = OtlpJsonEncoder.encode(List.of(), "svc");

        assertThat(json).contains("\"spans\":[]");
    }
}
