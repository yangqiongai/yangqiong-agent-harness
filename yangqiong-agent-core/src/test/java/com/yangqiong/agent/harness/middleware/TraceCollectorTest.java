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
package com.yangqiong.agent.harness.middleware;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * 追踪Span与收集器测试
 * @author yangqiong
 */
class TraceCollectorTest {

    @Test
    void shouldCreateRootSpan() {
        TraceCollector collector = new TraceCollector();
        TraceSpan root = collector.startRoot("session-1", "agent", "agent");
        assertThat(root.getName()).isEqualTo("agent");
        assertThat(root.getType()).isEqualTo("agent");
        assertThat(root.getParent()).isNull();
    }

    @Test
    void shouldCreateChildSpan() {
        TraceCollector collector = new TraceCollector();
        collector.startRoot("session-1", "agent", "agent");
        TraceSpan child = collector.startChild("session-1", "reasoning", "reasoning");
        assertThat(child.getParent()).isNotNull();
        assertThat(child.getParent().getChildren()).contains(child);
    }

    @Test
    void shouldEndSpanAndReturnToParent() {
        TraceCollector collector = new TraceCollector();
        TraceSpan root = collector.startRoot("session-1", "agent", "agent");
        collector.startChild("session-1", "reasoning", "reasoning");
        collector.endCurrent("session-1");
        // 结束子span后，currentSpan应回退到root
        TraceSpan nextChild = collector.startChild("session-1", "acting", "acting");
        assertThat(nextChild.getParent()).isEqualTo(root);
    }

    @Test
    void shouldExportOtlpJson() {
        TraceCollector collector = new TraceCollector();
        collector.startRoot("session-1", "agent", "agent");
        collector.putAttribute("session-1", "testKey", "testValue");
        collector.startChild("session-1", "reasoning", "reasoning");
        collector.endCurrent("session-1");
        collector.endCurrent("session-1");
        String json = collector.exportOtlp("session-1");
        assertThat(json).contains("\"name\":\"agent\"");
        assertThat(json).contains("\"children\":[");
        assertThat(json).contains("\"name\":\"reasoning\"");
        assertThat(json).contains("\"testKey\":\"testValue\"");
    }

    @Test
    void shouldClearSession() {
        TraceCollector collector = new TraceCollector();
        collector.startRoot("session-1", "agent", "agent");
        collector.clear("session-1");
        assertThat(collector.getRootSpan("session-1")).isNull();
    }

    @Test
    void shouldRecordElapsedMs() throws InterruptedException {
        TraceCollector collector = new TraceCollector();
        TraceSpan span = collector.startRoot("session-1", "agent", "agent");
        Thread.sleep(10);
        collector.endCurrent("session-1");
        assertThat(span.getElapsedMs()).isGreaterThanOrEqualTo(8);
    }

    @Test
    void shouldIsolateBySessionId() {
        TraceCollector collector = new TraceCollector();
        collector.startRoot("session-1", "agent1", "agent");
        collector.startRoot("session-2", "agent2", "agent");
        assertThat(collector.getRootSpan("session-1").getName()).isEqualTo("agent1");
        assertThat(collector.getRootSpan("session-2").getName()).isEqualTo("agent2");
    }
}
