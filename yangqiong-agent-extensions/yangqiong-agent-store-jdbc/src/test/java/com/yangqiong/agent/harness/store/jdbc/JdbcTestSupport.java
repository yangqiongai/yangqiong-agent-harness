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

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.Statement;

import javax.sql.DataSource;

import org.h2.jdbcx.JdbcDataSource;

import com.yangqiong.agent.harness.store.jdbc.mapper.ApprovalMapper;
import com.yangqiong.agent.harness.store.jdbc.mapper.CheckpointMapper;
import com.yangqiong.agent.harness.store.jdbc.mapper.LongTermMemoryMapper;
import com.yangqiong.agent.harness.store.jdbc.mapper.RunLockMapper;
import com.yangqiong.agent.harness.store.jdbc.mapper.RunMapper;
import com.yangqiong.agent.harness.store.jdbc.mapper.SessionEpisodeMapper;
import com.yangqiong.agent.harness.store.jdbc.mapper.SessionSummaryMapper;
import com.yangqiong.agent.harness.store.jdbc.mapper.ToolExecutionMapper;

/**
 * JDBC存储测试数据源辅助
 * <p>
 * 构造H2内存MySQL兼容模式数据源并执行schema.sql初始化表结构，
 * 兼容性预处理剥离MySQL专有语法以适配H2。
 * </p>
 * @author yangqiong
 */
final class JdbcTestSupport {

    /**
     * 内存库命名序号，保证每个数据源独占独立H2实例
     */
    private static final java.util.concurrent.atomic.AtomicInteger SEQ = new java.util.concurrent.atomic.AtomicInteger();

    private JdbcTestSupport() {
    }

    /**
     * 创建初始化完成的H2数据源
     * @return
     */
    static DataSource newDataSource() {
        JdbcDataSource ds = new JdbcDataSource();
        ds.setURL("jdbc:h2:mem:test" + SEQ.incrementAndGet() + ";MODE=MySQL;DB_CLOSE_DELAY=-1");
        ds.setUser("sa");
        ds.setPassword("");
        initSchema(ds);
        return ds;
    }

    /**
     * 创建装配全部Mapper的工厂
     * @return
     */
    static JdbcStoresFactory newFactory() {
        return new JdbcStoresFactory(newDataSource(),
                RunMapper.class, CheckpointMapper.class, ApprovalMapper.class,
                SessionSummaryMapper.class, SessionEpisodeMapper.class,
                LongTermMemoryMapper.class, RunLockMapper.class, ToolExecutionMapper.class);
    }

    /**
     * 执行建表脚本
     * @param dataSource
     */
    private static void initSchema(DataSource dataSource) {
        try (InputStream in = JdbcTestSupport.class.getResourceAsStream("/schema.sql")) {
            String sql = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            String clean = cleanForH2(sql);
            try (Connection conn = dataSource.getConnection();
                 Statement st = conn.createStatement()) {
                for (String stmt : clean.split(";")) {
                    String s = stmt.trim();
                    if (!s.isEmpty()) {
                        st.execute(s);
                    }
                }
            }
        } catch (Exception e) {
            throw new IllegalStateException("初始化H2建表失败", e);
        }
    }

    /**
     * 剥离MySQL专有语法使其兼容H2
     * @param sql
     * @return
     */
    private static String cleanForH2(String sql) {
        String s = sql.replaceAll("(?i)\\s+ON\\s+UPDATE\\s+CURRENT_TIMESTAMP", "");
        s = s.replaceAll("(?i)\\s+ENGINE\\s*=\\s*InnoDB", "");
        s = s.replaceAll("(?i)\\s+DEFAULT\\s+CHARSET\\s*=\\s*utf8mb4", "");
        s = s.replaceAll("(?i)\\s+COMMENT\\s*'[^']*'", "");
        s = s.replaceAll("(?i)\\s*--[^\\r\\n]*", "");
        return s;
    }
}