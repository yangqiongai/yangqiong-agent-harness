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
package com.yangqiongai.agent.harness.store.jdbc;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.yangqiongai.agent.harness.core.memory.SessionSummary;
import com.yangqiongai.agent.harness.core.message.AgentMessage;
import com.yangqiongai.agent.harness.core.message.AgentMessageRole;
import com.yangqiongai.agent.harness.core.message.AgentTextBlock;
import com.yangqiongai.agent.harness.store.jdbc.mapper.SessionEpisodeMapper;
import com.yangqiongai.agent.harness.store.jdbc.mapper.SessionSummaryMapper;

/**
 * 会话记忆JDBC实现测试
 * @author yangqiong
 */
class JdbcSessionMemoryTest {

    /**
     * 会话记忆
     */
    private JdbcSessionMemory memory;

    @BeforeEach
    void setUp() {
        JdbcStoresFactory factory = JdbcTestSupport.newFactory();
        memory = new JdbcSessionMemory(factory.getMapper(SessionSummaryMapper.class),
                factory.getMapper(SessionEpisodeMapper.class));
    }

    /**
     * 保存并加载摘要
     */
    @Test
    void saveAndLoadSummary() {
        memory.saveSummary("scope1", "session1", "已完成首轮对话", 10);

        SessionSummary summary = memory.loadSummary("scope1", "session1");
        assertThat(summary).isNotNull();
        assertThat(summary.getSummary()).isEqualTo("已完成首轮对话");
        assertThat(summary.getSummarizedMessageCount()).isEqualTo(10);
    }

    /**
     * 更新摘要以覆盖旧值
     */
    @Test
    void saveSummaryOverwrite() {
        memory.saveSummary("scope1", "session1", "第一版摘要", 5);
        memory.saveSummary("scope1", "session1", "第二版摘要", 8);

        SessionSummary summary = memory.loadSummary("scope1", "session1");
        assertThat(summary.getSummary()).isEqualTo("第二版摘要");
        assertThat(summary.getSummarizedMessageCount()).isEqualTo(8);
    }

    /**
     * 摘要复合键隔离
     */
    @Test
    void summaryScopeIsolation() {
        memory.saveSummary("scope1", "session1", "租户A摘要", 1);

        assertThat(memory.loadSummary("scope1", "session1")).isNotNull();
        assertThat(memory.loadSummary("scope2", "session1")).isNull();
        assertThat(memory.loadSummary(null, "session1")).isNull();
    }

    /**
     * 无参数默认方法委托至空scope复合键
     */
    @Test
    void singleArgDelegatesToNullScope() {
        memory.saveSummary("session1", "单参数摘要", 3);

        SessionSummary summary = memory.loadSummary("session1");
        assertThat(summary.getSummary()).isEqualTo("单参数摘要");
        assertThat(memory.loadSummary(null, "session1")).isNotNull();
    }

    /**
     * 保存并列出最近情节
     */
    @Test
    void saveAndListEpisodes() {
        memory.saveEpisode("scope1", "session1", message("情节一"));
        memory.saveEpisode("scope1", "session1", message("情节二"));
        memory.saveEpisode("scope1", "session1", message("情节三"));

        List<AgentMessage> episodes = memory.listEpisodes("scope1", "session1", 2);
        assertThat(episodes).hasSize(2);
        assertThat(episodes.get(0).getTextContent()).isEqualTo("情节二");
        assertThat(episodes.get(1).getTextContent()).isEqualTo("情节三");
    }

    /**
     * 情节按会话隔离
     */
    @Test
    void episodeScopeIsolation() {
        memory.saveEpisode("scope1", "session1", message("租户A"));

        assertThat(memory.listEpisodes("scope1", "session1", 10)).hasSize(1);
        assertThat(memory.listEpisodes("scope2", "session1", 10)).isEmpty();
    }

    /**
     * 清理会话记忆同时清空摘要与情节
     */
    @Test
    void clearSessionMemory() {
        memory.saveSummary("scope1", "session1", "摘要", 1);
        memory.saveEpisode("scope1", "session1", message("情节"));

        memory.clear("scope1", "session1");

        assertThat(memory.loadSummary("scope1", "session1")).isNull();
        assertThat(memory.listEpisodes("scope1", "session1", 10)).isEmpty();
    }

    /**
     * 构造文本消息
     * @param text
     * @return
     */
    private AgentMessage message(String text) {
        return AgentMessage.builder()
                .role(AgentMessageRole.USER)
                .content(List.of(AgentTextBlock.builder().text(text).build()))
                .build();
    }
}