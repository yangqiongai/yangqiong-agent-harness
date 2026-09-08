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
package com.yangqiongai.agent.harness.store.jdbc.mapper;

import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.yangqiongai.agent.harness.store.jdbc.entity.RunLockEntity;

/**
 * 运行锁映射
 * @author yangqiong
 */
public interface RunLockMapper extends BaseMapper<RunLockEntity> {

    /**
     * 条件接管：仅当原持有者为本节点或原锁已过期时更新成功
     * @param runId
     * @param owner
     * @param expiresAt
     * @param now
     * @return
     */
    @Update("UPDATE harness_run_lock SET owner_node_id = #{owner}, expires_at = #{expiresAt} "
            + "WHERE run_id = #{runId} AND (owner_node_id = #{owner} OR expires_at < #{now})")
    int updateTakeover(@Param("runId") String runId, @Param("owner") String owner,
                       @Param("expiresAt") long expiresAt, @Param("now") long now);

    /**
     * 插入锁记录，主键冲突时抛异常
     * @param runId
     * @param owner
     * @param expiresAt
     * @return
     */
    @Insert("INSERT INTO harness_run_lock (run_id, owner_node_id, expires_at) "
            + "VALUES (#{runId}, #{owner}, #{expiresAt})")
    int insertLock(@Param("runId") String runId, @Param("owner") String owner,
                   @Param("expiresAt") long expiresAt);

    /**
     * 持有者续期
     * @param runId
     * @param owner
     * @param expiresAt
     * @param now
     * @return
     */
    @Update("UPDATE harness_run_lock SET expires_at = #{expiresAt} "
            + "WHERE run_id = #{runId} AND owner_node_id = #{owner} AND expires_at > #{now}")
    int renewLock(@Param("runId") String runId, @Param("owner") String owner,
                  @Param("expiresAt") long expiresAt, @Param("now") long now);

    /**
     * 仅持有者可删除锁
     * @param runId
     * @param owner
     * @return
     */
    @Delete("DELETE FROM harness_run_lock WHERE run_id = #{runId} AND owner_node_id = #{owner}")
    int deleteByOwner(@Param("runId") String runId, @Param("owner") String owner);

    /**
     * 查询未过期的持有者
     * @param runId
     * @param now
     * @return
     */
    @Select("SELECT owner_node_id FROM harness_run_lock WHERE run_id = #{runId} AND expires_at > #{now}")
    String selectOwner(@Param("runId") String runId, @Param("now") long now);
}
