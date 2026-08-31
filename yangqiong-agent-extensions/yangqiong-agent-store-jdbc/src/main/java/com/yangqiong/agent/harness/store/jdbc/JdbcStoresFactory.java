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

import javax.sql.DataSource;

import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.session.SqlSessionManager;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.MybatisSqlSessionFactoryBuilder;

/**
 * 共享存储MyBatis-Plus装配工厂
 * <p>
 * 非Spring手动装配：无需Spring容器，直接用数据源构建 SqlSessionFactory，
 * 注册 Mapper 接口后通过线程安全的 SqlSessionManager 获取代理 Mapper，
 * 供各存储实现复用，全程零Spring依赖。
 * </p>
 * @author yangqiong
 */
public final class JdbcStoresFactory {

    /**
     * SqlSession管理器（线程安全，可复用）
     */
    private final SqlSessionManager sqlSessionManager;

    /**
     * 构造器：以数据源构建SqlSessionFactory并注册全部Mapper
     * @param dataSource
     * @param mapperTypes
     */
    public JdbcStoresFactory(DataSource dataSource, Class<?>... mapperTypes) {
        if (dataSource == null) {
            throw new IllegalArgumentException("数据源不能为空");
        }
        MybatisConfiguration configuration = new MybatisConfiguration();
        configuration.setEnvironment(new Environment("harness", new JdbcTransactionFactory(), dataSource));
        configuration.setMapUnderscoreToCamelCase(true);
        for (Class<?> mapperType : mapperTypes) {
            configuration.addMapper(mapperType);
        }
        SqlSessionFactory sessionFactory = new MybatisSqlSessionFactoryBuilder().build(configuration);
        this.sqlSessionManager = SqlSessionManager.newInstance(sessionFactory);
    }

    /**
     * 获取Mapper代理实例
     * @param mapperType
     * @return
     */
    public <T> T getMapper(Class<T> mapperType) {
        return sqlSessionManager.getMapper(mapperType);
    }
}
