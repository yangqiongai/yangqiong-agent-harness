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
package com.yangqiongai.agent.harness.local.store;

import java.sql.Connection;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

import com.yangqiongai.agent.harness.core.memory.SessionMemory;
import com.yangqiongai.agent.harness.core.memory.SessionSummary;
import com.yangqiongai.agent.harness.core.message.AgentMessage;
import com.yangqiongai.agent.harness.durable.serialization.AgentMessageSerializer;

/**
 * SQLite会话记忆
 * <p>
 * 覆写带scopeId的默认方法实现复合键隔离：摘要表按 (scope_id, session_id) 唯一键
 * 冲突时更新（upsert），情节表按复合键追加记录并按自增ID倒序取最近情节，
 * 还原为时间正序返回。
 * </p>
 * @author yangqiong
 */
public class SqliteSessionMemory implements SessionMemory {

    /**
     * 情节默认最大返回数
     */
    private static final int DEFAULT_EPISODE_LIMIT = 100;

    /**
     * 摘要按复合键upsert
     */
    private static final String SQL_UPSERT_SUMMARY = """
            INSERT INTO harness_session_summary (scope_id, session_id, summary, summarized_message_count)
            VALUES (?, ?, ?, ?)
            ON CONFLICT (scope_id, session_id) DO UPDATE SET
                summary = excluded.summary,
                summarized_message_count = excluded.summarized_message_count,
                update_time = CURRENT_TIMESTAMP""";

    /**
     * 按复合键查询摘要
     */
    private static final String SQL_FIND_SUMMARY = """
            SELECT summary, summarized_message_count FROM harness_session_summary
            WHERE scope_id = ? AND session_id = ?""";

    /**
     * 插入情节消息
     */
    private static final String SQL_INSERT_EPISODE = """
            INSERT INTO harness_session_episode (scope_id, session_id, message)
            VALUES (?, ?, ?)""";

    /**
     * 按复合键倒序取最近情节
     */
    private static final String SQL_LIST_EPISODES = """
            SELECT message FROM harness_session_episode
            WHERE scope_id = ? AND session_id = ?
            ORDER BY id DESC
            LIMIT ?""";

    /**
     * 按复合键清理摘要
     */
    private static final String SQL_CLEAR_SUMMARY = """
            DELETE FROM harness_session_summary
            WHERE scope_id = ? AND session_id = ?""";

    /**
     * 按复合键清理情节
     */
    private static final String SQL_CLEAR_EPISODES = """
            DELETE FROM harness_session_episode
            WHERE scope_id = ? AND session_id = ?""";

    /**
     * 共享数据库连接
     */
    private final Connection connection;

    /**
     * 构造器
     * @param connection
     */
    public SqliteSessionMemory(Connection connection) {
        this.connection = Objects.requireNonNull(connection, "connection不能为空");
    }

    /**
     * 保存会话运行摘要及已摘要到的消息数
     * @param sessionId
     * @param summary
     * @param summarizedMessageCount
     * @return
     */
    @Override
    public void saveSummary(String sessionId, String summary, int summarizedMessageCount) {
        saveSummary(null, sessionId, summary, summarizedMessageCount);
    }

    /**
     * 按租户作用域保存会话摘要
     * @param scopeId
     * @param sessionId
     * @param summary
     * @param summarizedMessageCount
     */
    @Override
    public void saveSummary(String scopeId, String sessionId, String summary, int summarizedMessageCount) {
        if (sessionId == null) {
            return;
        }
        synchronized (connection) {
            SqliteSupport.update(connection, SQL_UPSERT_SUMMARY, statement -> {
                statement.setString(1, normalizeScope(scopeId));
                statement.setString(2, sessionId);
                statement.setString(3, summary);
                statement.setInt(4, summarizedMessageCount);
            });
        }
    }

    /**
     * 加载会话运行摘要
     * @param sessionId
     * @return
     */
    @Override
    public SessionSummary loadSummary(String sessionId) {
        return loadSummary(null, sessionId);
    }

    /**
     * 按租户作用域加载会话摘要
     * @param scopeId
     * @param sessionId
     * @return
     */
    @Override
    public SessionSummary loadSummary(String scopeId, String sessionId) {
        if (sessionId == null) {
            return null;
        }
        synchronized (connection) {
            return SqliteSupport.queryFirst(connection, SQL_FIND_SUMMARY, statement -> {
                statement.setString(1, normalizeScope(scopeId));
                statement.setString(2, sessionId);
            }, resultSet -> new SessionSummary(
                    resultSet.getString("summary"),
                    resultSet.getInt("summarized_message_count")));
        }
    }

    /**
     * 保存一条关键情节消息
     * @param sessionId
     * @param episode
     * @return
     */
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
        synchronized (connection) {
            SqliteSupport.update(connection, SQL_INSERT_EPISODE, statement -> {
                statement.setString(1, normalizeScope(scopeId));
                statement.setString(2, sessionId);
                statement.setString(3, AgentMessageSerializer.toJson(episode));
            });
        }
    }

    /**
     * 列出会话最近的情节消息
     * @param sessionId
     * @param limit
     * @return
     */
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
            return List.of();
        }
        int topK = limit > 0 ? limit : DEFAULT_EPISODE_LIMIT;
        synchronized (connection) {
            List<String> jsonList = SqliteSupport.query(connection, SQL_LIST_EPISODES, statement -> {
                statement.setString(1, normalizeScope(scopeId));
                statement.setString(2, sessionId);
                statement.setInt(3, topK);
            }, resultSet -> resultSet.getString("message"));
            List<AgentMessage> result = new ArrayList<>();
            for (String json : jsonList) {
                result.add(AgentMessageSerializer.fromJson(json));
            }
            // 倒序取出的最近情节需还原为时间正序返回
            Collections.reverse(result);
            return result;
        }
    }

    /**
     * 清理会话记忆
     * @param sessionId
     * @return
     */
    @Override
    public void clear(String sessionId) {
        clear(null, sessionId);
    }

    /**
     * 按租户作用域清理会话记忆
     * @param scopeId
     * @param sessionId
     */
    @Override
    public void clear(String scopeId, String sessionId) {
        if (sessionId == null) {
            return;
        }
        synchronized (connection) {
            String scope = normalizeScope(scopeId);
            SqliteSupport.update(connection, SQL_CLEAR_SUMMARY, statement -> {
                statement.setString(1, scope);
                statement.setString(2, sessionId);
            });
            SqliteSupport.update(connection, SQL_CLEAR_EPISODES, statement -> {
                statement.setString(1, scope);
                statement.setString(2, sessionId);
            });
        }
    }

    /**
     * 空scope归一为空串以适配非空列，与复合键语义一致
     * @param scopeId
     * @return
     */
    private String normalizeScope(String scopeId) {
        return scopeId != null ? scopeId : "";
    }
}
