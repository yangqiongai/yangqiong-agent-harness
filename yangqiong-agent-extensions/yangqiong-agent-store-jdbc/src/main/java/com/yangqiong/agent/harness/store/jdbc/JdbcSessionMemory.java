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
package com.yangqiong.agent.harness.store.jdbc;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.yangqiong.agent.harness.core.memory.SessionMemory;
import com.yangqiong.agent.harness.core.memory.SessionSummary;
import com.yangqiong.agent.harness.core.message.AgentMessage;
import com.yangqiong.agent.harness.durable.serialization.AgentMessageSerializer;
import com.yangqiong.agent.harness.store.jdbc.entity.SessionEpisodeEntity;
import com.yangqiong.agent.harness.store.jdbc.entity.SessionSummaryEntity;
import com.yangqiong.agent.harness.store.jdbc.mapper.SessionEpisodeMapper;
import com.yangqiong.agent.harness.store.jdbc.mapper.SessionSummaryMapper;

/**
 * 会话记忆JDBC实现
 * <p>
 * 覆写带scopeId的默认方法实现复合键隔离：摘要表唯一键为(scope_id, session_id)，
 * 情节表按(scope_id, session_id)追加记录并按时间倒序取最近情节。
 * </p>
 * @author yangqiong
 */
public class JdbcSessionMemory implements SessionMemory {

    /**
     * 会话摘要映射器
     */
    private final SessionSummaryMapper summaryMapper;

    /**
     * 会话情节映射器
     */
    private final SessionEpisodeMapper episodeMapper;

    /**
     * 构造器
     * @param summaryMapper
     * @param episodeMapper
     */
    public JdbcSessionMemory(SessionSummaryMapper summaryMapper, SessionEpisodeMapper episodeMapper) {
        this.summaryMapper = summaryMapper;
        this.episodeMapper = episodeMapper;
    }

    @Override
    public void saveSummary(String sessionId, String summary, int summarizedMessageCount) {
        saveSummary(null, sessionId, summary, summarizedMessageCount);
    }

    @Override
    public void saveSummary(String scopeId, String sessionId, String summary, int summarizedMessageCount) {
        if (sessionId == null) {
            return;
        }
        summaryMapper.upsert(normalizeScope(scopeId), sessionId, summary, summarizedMessageCount);
    }

    @Override
    public SessionSummary loadSummary(String sessionId) {
        return loadSummary(null, sessionId);
    }

    @Override
    public SessionSummary loadSummary(String scopeId, String sessionId) {
        if (sessionId == null) {
            return null;
        }
        SessionSummaryEntity entity = summaryMapper.selectOne(Wrappers.<SessionSummaryEntity>lambdaQuery()
                .eq(SessionSummaryEntity::getScopeId, normalizeScope(scopeId))
                .eq(SessionSummaryEntity::getSessionId, sessionId));
        if (entity == null) {
            return null;
        }
        return new SessionSummary(entity.getSummary(), entity.getSummarizedMessageCount());
    }

    @Override
    public void saveEpisode(String sessionId, AgentMessage episode) {
        saveEpisode(null, sessionId, episode);
    }

    /**
     * 按租户作用域保存一条关键情节消息
     * @param scopeId
     * @param sessionId
     * @param episode
     */
    public void saveEpisode(String scopeId, String sessionId, AgentMessage episode) {
        if (sessionId == null || episode == null) {
            return;
        }
        SessionEpisodeEntity entity = new SessionEpisodeEntity();
        entity.setScopeId(normalizeScope(scopeId));
        entity.setSessionId(sessionId);
        entity.setMessage(AgentMessageSerializer.toJson(episode));
        episodeMapper.insert(entity);
    }

    @Override
    public List<AgentMessage> listEpisodes(String sessionId, int limit) {
        return listEpisodes(null, sessionId, limit);
    }

    /**
     * 按租户作用域列出会话最近的情节消息
     * @param scopeId
     * @param sessionId
     * @param limit
     * @return
     */
    public List<AgentMessage> listEpisodes(String scopeId, String sessionId, int limit) {
        if (sessionId == null) {
            return Collections.emptyList();
        }
        int topK = limit > 0 ? limit : 100;
        List<SessionEpisodeEntity> entities = episodeMapper.listLatest(normalizeScope(scopeId), sessionId, topK);
        List<AgentMessage> result = new ArrayList<>();
        for (SessionEpisodeEntity entity : entities) {
            result.add(AgentMessageSerializer.fromJson(entity.getMessage()));
        }
        // 倒序取出的最近情节需还原为时间正序返回
        Collections.reverse(result);
        return result;
    }

    @Override
    public void clear(String sessionId) {
        clear(null, sessionId);
    }

    @Override
    public void clear(String scopeId, String sessionId) {
        if (sessionId == null) {
            return;
        }
        summaryMapper.delete(Wrappers.<SessionSummaryEntity>lambdaQuery()
                .eq(SessionSummaryEntity::getScopeId, normalizeScope(scopeId))
                .eq(SessionSummaryEntity::getSessionId, sessionId));
        episodeMapper.delete(Wrappers.<SessionEpisodeEntity>lambdaQuery()
                .eq(SessionEpisodeEntity::getScopeId, normalizeScope(scopeId))
                .eq(SessionEpisodeEntity::getSessionId, sessionId));
    }

    /**
     * 空scope归一为""以适配非空列，与复合键语义一致
     * @param scopeId
     * @return
     */
    private String normalizeScope(String scopeId) {
        return scopeId != null ? scopeId : "";
    }
}
