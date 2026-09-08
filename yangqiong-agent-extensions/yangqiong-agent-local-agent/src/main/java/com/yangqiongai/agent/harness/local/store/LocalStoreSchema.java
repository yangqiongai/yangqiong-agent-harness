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
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.Objects;

/**
 * SQLite本地存储模式
 * <p>
 * SQLite方言幂等建表脚本：覆盖运行记录、检查点、审批、会话摘要、会话情节、
 * 长期记忆、工具执行与运行锁全部表，语句可重复执行，
 * 建表完成后写入 PRAGMA user_version 标记库表结构版本。
 * </p>
 * @author yangqiong
 */
public final class LocalStoreSchema {

    /**
     * 当前库表结构版本
     */
    public static final int USER_VERSION = 1;

    /**
     * 幂等建表与索引语句（SQLite方言）
     */
    private static final List<String> SCHEMA_STATEMENTS = List.of(
            """
            CREATE TABLE IF NOT EXISTS harness_run (
                run_id      TEXT NOT NULL,
                scope_id    TEXT NOT NULL,
                session_id  TEXT NOT NULL,
                user_id     TEXT,
                agent_name  TEXT,
                created_at  INTEGER NOT NULL,
                updated_at  INTEGER NOT NULL,
                version     INTEGER NOT NULL DEFAULT 0,
                state       TEXT NOT NULL,
                error       TEXT,
                transitions TEXT,
                create_user TEXT,
                create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                update_user TEXT,
                update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                PRIMARY KEY (run_id)
            )
            """,
            """
            CREATE INDEX IF NOT EXISTS idx_harness_run_session
                ON harness_run (scope_id, session_id, created_at)
            """,
            """
            CREATE TABLE IF NOT EXISTS harness_checkpoint (
                run_id                 TEXT NOT NULL,
                scope_id               TEXT NOT NULL,
                session_id             TEXT NOT NULL,
                version                INTEGER NOT NULL,
                iteration              INTEGER NOT NULL,
                messages               TEXT,
                pending_tool_calls     TEXT,
                completed_tool_use_ids TEXT,
                checkpoint_time        INTEGER NOT NULL,
                create_user            TEXT,
                create_time            DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                update_user            TEXT,
                update_time            DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                PRIMARY KEY (run_id, version)
            )
            """,
            """
            CREATE INDEX IF NOT EXISTS idx_harness_checkpoint_session
                ON harness_checkpoint (scope_id, session_id, version)
            """,
            """
            CREATE TABLE IF NOT EXISTS harness_approval (
                approval_id  TEXT NOT NULL,
                run_id       TEXT NOT NULL,
                tool_call_id TEXT NOT NULL,
                tool_name    TEXT,
                scope_id     TEXT NOT NULL,
                approver_id  TEXT,
                state        TEXT NOT NULL,
                reason       TEXT,
                created_at   INTEGER NOT NULL,
                create_user  TEXT,
                create_time  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                update_user  TEXT,
                update_time  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                PRIMARY KEY (approval_id)
            )
            """,
            """
            CREATE UNIQUE INDEX IF NOT EXISTS uk_harness_approval_tool
                ON harness_approval (tool_call_id)
            """,
            """
            CREATE INDEX IF NOT EXISTS idx_harness_approval_scope
                ON harness_approval (scope_id, state)
            """,
            """
            CREATE INDEX IF NOT EXISTS idx_harness_approval_run
                ON harness_approval (run_id)
            """,
            """
            CREATE TABLE IF NOT EXISTS harness_session_summary (
                id                       INTEGER PRIMARY KEY AUTOINCREMENT,
                scope_id                 TEXT NOT NULL,
                session_id               TEXT NOT NULL,
                summary                  TEXT,
                summarized_message_count INTEGER NOT NULL DEFAULT 0,
                create_user              TEXT,
                create_time              DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                update_user              TEXT,
                update_time              DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
            )
            """,
            """
            CREATE UNIQUE INDEX IF NOT EXISTS uk_harness_session_summary
                ON harness_session_summary (scope_id, session_id)
            """,
            """
            CREATE TABLE IF NOT EXISTS harness_session_episode (
                id          INTEGER PRIMARY KEY AUTOINCREMENT,
                scope_id    TEXT NOT NULL,
                session_id  TEXT NOT NULL,
                message     TEXT,
                create_user TEXT,
                create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                update_user TEXT,
                update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
            )
            """,
            """
            CREATE INDEX IF NOT EXISTS idx_harness_episode_session
                ON harness_session_episode (scope_id, session_id, id)
            """,
            """
            CREATE TABLE IF NOT EXISTS harness_long_term_memory (
                id          TEXT NOT NULL,
                scope_id    TEXT NOT NULL,
                user_id     TEXT NOT NULL,
                session_id  TEXT,
                content     TEXT,
                metadata    TEXT,
                created_at  INTEGER NOT NULL,
                create_user TEXT,
                create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                update_user TEXT,
                update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                PRIMARY KEY (id)
            )
            """,
            """
            CREATE INDEX IF NOT EXISTS idx_harness_memory_user
                ON harness_long_term_memory (scope_id, user_id)
            """,
            """
            CREATE TABLE IF NOT EXISTS harness_tool_execution (
                idempotency_key TEXT NOT NULL,
                result          TEXT,
                create_user     TEXT,
                create_time     DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                update_user     TEXT,
                update_time     DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                PRIMARY KEY (idempotency_key)
            )
            """,
            """
            CREATE TABLE IF NOT EXISTS harness_run_lock (
                run_id         TEXT NOT NULL,
                owner_node_id  TEXT NOT NULL,
                expires_at     INTEGER NOT NULL,
                create_user    TEXT,
                create_time    DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                update_user    TEXT,
                update_time    DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                PRIMARY KEY (run_id)
            )
            """
    );

    private LocalStoreSchema() {
    }

    /**
     * 初始化库表结构，全部语句幂等可重复执行
     * @param connection
     */
    public static void initialize(Connection connection) throws SQLException {
        Objects.requireNonNull(connection, "connection不能为空");
        try (Statement statement = connection.createStatement()) {
            for (String sql : SCHEMA_STATEMENTS) {
                statement.execute(sql);
            }
        }
        // 结构版本只升不降，避免旧版本进程回写覆盖更高结构标记
        if (currentUserVersion(connection) < USER_VERSION) {
            try (Statement statement = connection.createStatement()) {
                statement.execute("PRAGMA user_version = " + USER_VERSION);
            }
        }
    }

    /**
     * 读取当前库表结构版本
     * @param connection
     * @return
     */
    public static int currentUserVersion(Connection connection) throws SQLException {
        Objects.requireNonNull(connection, "connection不能为空");
        try (Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery("PRAGMA user_version")) {
            return resultSet.next() ? resultSet.getInt(1) : 0;
        }
    }
}
