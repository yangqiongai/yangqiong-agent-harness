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
package com.yangqiong.agent.harness.cron;

import java.util.List;
import java.util.Optional;

/**
 * 定时任务存储
 * <p>
 * 定时任务的权威读写口，负责任务的持久化与查询，调度器通过它读写任务状态。
 * </p>
 * @author yangqiong
 */
public interface CronJobStore {

    /**
     * 查询全部定时任务
     * @return
     */
    List<CronJob> list();

    /**
     * 按ID查询定时任务
     * @param id
     * @return
     */
    Optional<CronJob> get(String id);

    /**
     * 保存定时任务（新增或覆盖）
     * @param job
     */
    void save(CronJob job);

    /**
     * 按ID删除定时任务
     * @param id
     */
    void delete(String id);
}
