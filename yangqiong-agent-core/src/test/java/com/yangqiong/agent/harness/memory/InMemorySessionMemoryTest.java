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
package com.yangqiong.agent.harness.memory;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Collections;
import java.util.List;

import com.yangqiong.agent.harness.core.memory.SessionSummary;
import com.yangqiong.agent.harness.core.message.AgentMessage;
import com.yangqiong.agent.harness.core.message.AgentMessageRole;
import com.yangqiong.agent.harness.core.message.AgentTextBlock;
import org.junit.jupiter.api.Test;

/**
 * 会话级短期记忆内存实现测试
 * @author yangqiong
 */
class InMemorySessionMemoryTest {

    private AgentMessage episode(String text) {
        return AgentMessage.builder()
                .role(AgentMessageRole.USER)
                .content(Collections.singletonList(AgentTextBlock.builder().text(text).build()))
                .build();
    }

    @Test
    void saveAndLoadSummary() {
        InMemorySessionMemory memory = new InMemorySessionMemory();
        memory.saveSummary("s1", "摘要内容", 5);
        SessionSummary summary = memory.loadSummary("s1");
        assertThat(summary).isNotNull();
        assertThat(summary.getSummary()).isEqualTo("摘要内容");
        assertThat(summary.getSummarizedMessageCount()).isEqualTo(5);
    }

    @Test
    void loadSummaryReturnNullWhenAbsent() {
        InMemorySessionMemory memory = new InMemorySessionMemory();
        assertThat(memory.loadSummary("absent")).isNull();
    }

    @Test
    void saveSummaryOverwritesPrevious() {
        InMemorySessionMemory memory = new InMemorySessionMemory();
        memory.saveSummary("s1", "旧摘要", 3);
        memory.saveSummary("s1", "新摘要", 8);
        SessionSummary summary = memory.loadSummary("s1");
        assertThat(summary.getSummary()).isEqualTo("新摘要");
        assertThat(summary.getSummarizedMessageCount()).isEqualTo(8);
    }

    @Test
    void saveAndListEpisodesInRecentOrder() {
        InMemorySessionMemory memory = new InMemorySessionMemory();
        memory.saveEpisode("s1", episode("情节1"));
        memory.saveEpisode("s1", episode("情节2"));
        memory.saveEpisode("s1", episode("情节3"));

        List<AgentMessage> recent = memory.listEpisodes("s1", 2);
        assertThat(recent).hasSize(2);
        assertThat(recent.get(0).getTextContent()).isEqualTo("情节2");
        assertThat(recent.get(1).getTextContent()).isEqualTo("情节3");
    }

    @Test
    void listEpisodesReturnsAllWhenLimitExceedsSize() {
        InMemorySessionMemory memory = new InMemorySessionMemory();
        memory.saveEpisode("s1", episode("情节1"));
        List<AgentMessage> all = memory.listEpisodes("s1", 10);
        assertThat(all).hasSize(1);
    }

    @Test
    void listEpisodesReturnEmptyWhenAbsent() {
        InMemorySessionMemory memory = new InMemorySessionMemory();
        assertThat(memory.listEpisodes("absent", 5)).isEmpty();
    }

    @Test
    void clearRemovesSummaryAndEpisodes() {
        InMemorySessionMemory memory = new InMemorySessionMemory();
        memory.saveSummary("s1", "摘要", 3);
        memory.saveEpisode("s1", episode("情节1"));
        memory.clear("s1");
        assertThat(memory.loadSummary("s1")).isNull();
        assertThat(memory.listEpisodes("s1", 5)).isEmpty();
    }

    @Test
    void isolatedBySessionId() {
        InMemorySessionMemory memory = new InMemorySessionMemory();
        memory.saveSummary("s1", "会话1摘要", 2);
        memory.saveSummary("s2", "会话2摘要", 4);
        assertThat(memory.loadSummary("s1").getSummary()).isEqualTo("会话1摘要");
        assertThat(memory.loadSummary("s2").getSummary()).isEqualTo("会话2摘要");
    }

    @Test
    void nullSessionIdIsNoop() {
        InMemorySessionMemory memory = new InMemorySessionMemory();
        memory.saveSummary(null, "x", 1);
        memory.saveEpisode(null, episode("x"));
        assertThat(memory.loadSummary(null)).isNull();
        assertThat(memory.listEpisodes(null, 5)).isEmpty();
    }
}
