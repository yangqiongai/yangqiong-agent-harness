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
package com.yangqiongai.agent.harness.permission;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * 权限审计出口有界性测试
 * @author yangqiong
 */
class AuditSinkBoundedTest {

    private AuditRecord record(String toolCallId) {
        return new AuditRecord(System.currentTimeMillis(), "scope-1", "run-1",
                toolCallId, "TOOL_PERMISSION_DECISION", "ALLOW", "web_search");
    }

    @Test
    void shouldKeepOnlyRecentRecordsWhenExceedingLimit() {
        AuditSink.InMemory sink = new AuditSink.InMemory();

        for (int i = 1; i <= 5001; i++) {
            sink.append(record("call-" + i));
        }

        List<AuditRecord> records = sink.getRecords();
        assertThat(records).hasSize(5000);
        // 最旧一条已淘汰，保留的是第2条至第5001条
        assertThat(records.get(0).getToolCallId()).isEqualTo("call-2");
        assertThat(records.get(records.size() - 1).getToolCallId()).isEqualTo("call-5001");
    }

    @Test
    void shouldIgnoreNullRecord() {
        AuditSink.InMemory sink = new AuditSink.InMemory();

        sink.append(null);

        assertThat(sink.getRecords()).isEmpty();
    }
}
