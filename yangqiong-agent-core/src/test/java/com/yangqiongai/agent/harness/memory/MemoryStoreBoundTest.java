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
package com.yangqiongai.agent.harness.memory;

import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.yangqiongai.agent.harness.core.message.AgentMessage;
import com.yangqiongai.agent.harness.core.message.AgentMessageRole;
import com.yangqiongai.agent.harness.core.message.AgentTextBlock;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 记忆内存存储容量上限淘汰
 * @author yangqiong
 */
public class MemoryStoreBoundTest {

    /**
     * 会话数超限时摘要与情节总量保持有界，新写入会话受保护
     */
    @Test
    void sessionMemory_会话数超限保持有界() {
        InMemorySessionMemory memory = new InMemorySessionMemory();
        for (int i = 0; i <= InMemorySessionMemory.MAX_SESSIONS; i++) {
            memory.saveSummary("tenant-a", "session-" + i, "摘要" + i, i);
        }
        int survived = 0;
        for (int i = 0; i <= InMemorySessionMemory.MAX_SESSIONS; i++) {
            if (memory.loadSummary("tenant-a", "session-" + i) != null) {
                survived++;
            }
        }
        assertThat(survived).isEqualTo(InMemorySessionMemory.MAX_SESSIONS);
        assertThat(memory.loadSummary("tenant-a", "session-" + InMemorySessionMemory.MAX_SESSIONS)).isNotNull();
    }

    /**
     * 单会话情节超限淘汰最旧条目
     */
    @Test
    void sessionMemory_单会话情节超限淘汰最旧() {
        InMemorySessionMemory memory = new InMemorySessionMemory();
        for (int i = 0; i <= InMemorySessionMemory.MAX_EPISODES_PER_SESSION; i++) {
            memory.saveEpisode("tenant-a", "session-0", episode("情节" + i));
        }
        List<AgentMessage> episodes = memory.listEpisodes("tenant-a", "session-0",
                InMemorySessionMemory.MAX_EPISODES_PER_SESSION + 1);
        assertThat(episodes).hasSize(InMemorySessionMemory.MAX_EPISODES_PER_SESSION);
        // 最旧情节"情节0"被淘汰
        assertThat(episodes.get(0).getTextContent()).isEqualTo("情节1");
    }

    /**
     * 长期记忆条目超限时淘汰创建时间最旧条目
     */
    @Test
    void longTermMemory_条目超限淘汰最旧() throws InterruptedException {
        InMemoryLongTermMemory memory = new InMemoryLongTermMemory();
        memory.store("tenant-a", "user-1", "session-0", "oldest-entry-marker", null);
        // 保证最早条目创建时间严格最小
        Thread.sleep(10L);
        for (int i = 0; i < InMemoryLongTermMemory.MAX_ENTRIES; i++) {
            memory.store("tenant-a", "user-1", "session-0", "bulk-entry-" + i, null);
        }
        assertThat(memory.search("tenant-a", "user-1", "oldest", 10)).isEmpty();
        assertThat(memory.search("tenant-a", "user-1", "bulk-entry-" + (InMemoryLongTermMemory.MAX_ENTRIES - 1), 10))
                .isNotEmpty();
    }

    /**
     * 构造情节消息
     * @param text
     * @return
     */
    private AgentMessage episode(String text) {
        return AgentMessage.builder()
                .name("assistant")
                .role(AgentMessageRole.ASSISTANT)
                .content(Collections.singletonList(AgentTextBlock.builder().text(text).build()))
                .build();
    }
}
