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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Objects;

import com.yangqiongai.agent.harness.core.memory.AgentLongTermMemory;
import com.yangqiongai.agent.harness.core.memory.SessionMemory;
import com.yangqiongai.agent.harness.durable.AgentRunStore;
import com.yangqiongai.agent.harness.durable.ApprovalStore;
import com.yangqiongai.agent.harness.durable.CheckpointStore;
import com.yangqiongai.agent.harness.durable.DistributedStores;
import com.yangqiongai.agent.harness.durable.RunLockStore;
import com.yangqiongai.agent.harness.tool.ToolExecutionStore;

/**
 * SQLite本地分布式存储
 * <p>
 * 以单文件SQLite库承载全部共享存储：库文件为 {rootDir}/harness.db，
 * 连接URL携带 WAL 日志模式与5秒忙等待，单例Connection被全部子存储复用，
 * 读写均通过 synchronized(connection) 串行化保证线程安全，close 时释放连接。
 * </p>
 * @author yangqiong
 */
public class LocalDistributedStores implements DistributedStores, AutoCloseable {

    /**
     * 根目录下的数据库文件名
     */
    private static final String DB_FILE_NAME = "harness.db";

    /**
     * 连接URL参数：WAL日志模式与5秒忙等待
     */
    private static final String URL_OPTIONS = "?journal_mode=WAL&busy_timeout=5000";

    /**
     * 默认本地存储根目录名
     */
    private static final String DEFAULT_DIR_NAME = ".yangqiong-agent";

    /**
     * 共享数据库连接（全部子存储复用同一实例）
     */
    private final Connection connection;

    /**
     * 数据库文件路径
     */
    private final Path dbFile;

    /**
     * 运行记录存储
     */
    private final SqliteAgentRunStore runStore;

    /**
     * 检查点存储
     */
    private final SqliteCheckpointStore checkpointStore;

    /**
     * 审批存储
     */
    private final SqliteApprovalStore approvalStore;

    /**
     * 会话记忆存储
     */
    private final SqliteSessionMemory sessionMemory;

    /**
     * 长期记忆存储
     */
    private final SqliteLongTermMemory longTermMemory;

    /**
     * 工具执行记录存储
     */
    private final SqliteToolExecutionStore toolExecutionStore;

    /**
     * 运行锁存储
     */
    private final SqliteRunLockStore runLockStore;

    /**
     * 以指定根目录装配本地存储，库文件为 {rootDir}/harness.db
     * @param rootDir
     */
    public LocalDistributedStores(Path rootDir) {
        Objects.requireNonNull(rootDir, "rootDir不能为空");
        Connection opened = null;
        try {
            Files.createDirectories(rootDir);
            Path dbFilePath = rootDir.toAbsolutePath().resolve(DB_FILE_NAME);
            opened = DriverManager.getConnection("jdbc:sqlite:" + dbFilePath + URL_OPTIONS);
            LocalStoreSchema.initialize(opened);
        } catch (IOException | SQLException e) {
            closeQuietly(opened);
            throw new IllegalStateException("初始化本地存储失败: " + rootDir, e);
        }
        this.connection = opened;
        this.dbFile = rootDir.toAbsolutePath().resolve(DB_FILE_NAME);
        this.runStore = new SqliteAgentRunStore(connection);
        this.checkpointStore = new SqliteCheckpointStore(connection);
        this.approvalStore = new SqliteApprovalStore(connection);
        this.sessionMemory = new SqliteSessionMemory(connection);
        this.longTermMemory = new SqliteLongTermMemory(connection);
        this.toolExecutionStore = new SqliteToolExecutionStore(connection);
        this.runLockStore = new SqliteRunLockStore(connection);
    }

    /**
     * 以默认目录（用户目录/.yangqiong-agent）创建本地存储
     * @return
     */
    public static LocalDistributedStores create() {
        return create(Path.of(System.getProperty("user.home"), DEFAULT_DIR_NAME));
    }

    /**
     * 以指定根目录创建本地存储
     * @param rootDir
     * @return
     */
    public static LocalDistributedStores create(Path rootDir) {
        return new LocalDistributedStores(rootDir);
    }

    /**
     * 运行记录存储
     * @return
     */
    @Override
    public AgentRunStore runStore() {
        return runStore;
    }

    /**
     * 检查点存储
     * @return
     */
    @Override
    public CheckpointStore checkpointStore() {
        return checkpointStore;
    }

    /**
     * 审批存储
     * @return
     */
    @Override
    public ApprovalStore approvalStore() {
        return approvalStore;
    }

    /**
     * 会话记忆存储
     * @return
     */
    @Override
    public SessionMemory sessionMemory() {
        return sessionMemory;
    }

    /**
     * 长期记忆存储
     * @return
     */
    @Override
    public AgentLongTermMemory longTermMemory() {
        return longTermMemory;
    }

    /**
     * 工具执行记录存储
     * @return
     */
    @Override
    public ToolExecutionStore toolExecutionStore() {
        return toolExecutionStore;
    }

    /**
     * 运行锁存储
     * @return
     */
    @Override
    public RunLockStore runLockStore() {
        return runLockStore;
    }

    /**
     * 连接可用性探测，可正常执行查询返回true
     * @return
     */
    public boolean ping() {
        synchronized (connection) {
            Integer value = SqliteSupport.queryFirst(connection, "SELECT 1", null,
                    resultSet -> resultSet.getInt(1));
            return value != null;
        }
    }

    /**
     * 采集各业务表记录数形成健康快照
     * @return
     */
    public LocalStoreHealth health() {
        synchronized (connection) {
            return new LocalStoreHealth(true, dbFile.toString(),
                    countRows("harness_run"),
                    countRows("harness_checkpoint"),
                    countRows("harness_approval"),
                    countRows("harness_long_term_memory"));
        }
    }

    /**
     * 统计指定表记录数
     * @param table
     * @return
     */
    private long countRows(String table) {
        Long value = SqliteSupport.queryFirst(connection, "SELECT COUNT(*) FROM " + table, null,
                resultSet -> resultSet.getLong(1));
        return value == null ? 0L : value;
    }

    /**
     * 本地存储健康快照
     * @author yangqiong
     */
    public record LocalStoreHealth(boolean ok, String dbFile, long runCount,
                                   long checkpointCount, long approvalCount, long memoryCount) {
    }

    /**
     * 关闭共享数据库连接
     * @return
     */
    @Override
    public void close() {
        synchronized (connection) {
            closeQuietly(connection);
        }
    }

    /**
     * 静默关闭连接，初始化失败与正常关闭时兜底释放资源
     * @param connection
     * @return
     */
    private static void closeQuietly(Connection connection) {
        if (connection == null) {
            return;
        }
        try {
            connection.close();
        } catch (SQLException e) {
            // 关闭失败仅吞掉异常，不影响主流程
        }
    }
}
