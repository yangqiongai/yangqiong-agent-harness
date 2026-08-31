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
package com.yangqiong.agent.harness.core.memory;

import java.util.List;

import com.yangqiong.agent.harness.core.message.AgentMessage;
import com.yangqiong.agent.harness.middleware.SummaryCompactionMiddleware;

/**
 * Agent会话级短期记忆
 * <p>
 * L2缓存抽象，按sessionId隔离，缓存会话运行摘要与关键情节，
 * 供{@link SummaryCompactionMiddleware}等组件做增量摘要，
 * 也为外部接入Redis/DB提供统一扩展点。
 * </p>
 * @author yangqiong
 */
public interface SessionMemory {

    /**
     * 保存会话运行摘要及已摘要到的消息数
     * @param sessionId
     * @param summary
     * @param summarizedMessageCount 已摘要到的消息序号（不含）
     * @return
     */
    void saveSummary(String sessionId, String summary, int summarizedMessageCount);

    /**
     * 加载会话运行摘要
     * @param sessionId
     * @return 无摘要时返回null
     */
    SessionSummary loadSummary(String sessionId);

    /**
     * 保存一条关键情节消息
     * @param sessionId
     * @param episode
     * @return
     */
    void saveEpisode(String sessionId, AgentMessage episode);

    /**
     * 列出会话最近的情节消息
     * @param sessionId
     * @param limit 最大返回数
     * @return
     */
    List<AgentMessage> listEpisodes(String sessionId, int limit);

    /**
     * 清理会话记忆
     * @param sessionId
     * @return
     */
    void clear(String sessionId);

    /**
     * 按租户作用域保存会话摘要（复合键隔离，推荐实现覆写）
     * @param scopeId
     * @param sessionId
     * @param summary
     * @param summarizedMessageCount
     */
    default void saveSummary(String scopeId, String sessionId, String summary, int summarizedMessageCount) {
        saveSummary(sessionId, summary, summarizedMessageCount);
    }

    /**
     * 按租户作用域加载会话摘要（复合键隔离，推荐实现覆写）
     * @param scopeId
     * @param sessionId
     * @return
     */
    default SessionSummary loadSummary(String scopeId, String sessionId) {
        return loadSummary(sessionId);
    }

    /**
     * 按租户作用域清理会话记忆（复合键隔离，推荐实现覆写）
     * @param scopeId
     * @param sessionId
     */
    default void clear(String scopeId, String sessionId) {
        clear(sessionId);
    }
}
