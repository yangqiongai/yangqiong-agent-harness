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
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * SQLite语句执行辅助
 * <p>
 * 收敛预编译语句的资源管理与SQLException包装，供各子存储复用。
 * 本类不做加锁，调用方必须已持有连接锁 synchronized(connection)，
 * 以保证复合读写序列的原子性。
 * </p>
 * @author yangqiong
 */
final class SqliteSupport {

    private SqliteSupport() {
    }

    /**
     * 执行更新语句并返回影响行数
     * @param connection
     * @param sql
     * @param binder
     * @return
     */
    static int update(Connection connection, String sql, Binder binder) {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            if (binder != null) {
                binder.bind(statement);
            }
            return statement.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("执行SQLite更新失败: " + sql, e);
        }
    }

    /**
     * 执行查询语句并映射全部结果行
     * @param connection
     * @param sql
     * @param binder
     * @param mapper
     * @return
     */
    static <T> List<T> query(Connection connection, String sql, Binder binder, RowMapper<T> mapper) {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            if (binder != null) {
                binder.bind(statement);
            }
            try (ResultSet resultSet = statement.executeQuery()) {
                List<T> result = new ArrayList<>();
                while (resultSet.next()) {
                    result.add(mapper.map(resultSet));
                }
                return result;
            }
        } catch (SQLException e) {
            throw new IllegalStateException("执行SQLite查询失败: " + sql, e);
        }
    }

    /**
     * 执行查询语句并映射首个结果行，无结果时返回null
     * @param connection
     * @param sql
     * @param binder
     * @param mapper
     * @return
     */
    static <T> T queryFirst(Connection connection, String sql, Binder binder, RowMapper<T> mapper) {
        List<T> rows = query(connection, sql, binder, mapper);
        return rows.isEmpty() ? null : rows.get(0);
    }

    /**
     * 语句参数绑定函数
     * @author yangqiong
     */
    @FunctionalInterface
    interface Binder {

        /**
         * 绑定预编译语句参数
         * @param statement
         */
        void bind(PreparedStatement statement) throws SQLException;
    }

    /**
     * 结果行映射函数
     * @author yangqiong
     */
    @FunctionalInterface
    interface RowMapper<T> {

        /**
         * 映射单行结果
         * @param resultSet
         * @return
         */
        T map(ResultSet resultSet) throws SQLException;
    }
}
