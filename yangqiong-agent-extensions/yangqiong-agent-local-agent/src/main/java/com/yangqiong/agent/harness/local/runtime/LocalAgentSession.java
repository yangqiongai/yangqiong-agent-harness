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
package com.yangqiong.agent.harness.local.runtime;

import java.util.Objects;

import com.yangqiong.agent.harness.core.AgentRuntime;
import com.yangqiong.agent.harness.cron.CronScheduler;
import com.yangqiong.agent.harness.local.store.LocalDistributedStores;
import com.yangqiong.agent.harness.local.workspace.HealthWatchdog;
import com.yangqiong.agent.harness.local.workspace.KnowledgeMaintainer;
import com.yangqiong.agent.harness.local.workspace.LocalWorkspace;
import com.yangqiong.agent.harness.local.workspace.MemoryConsolidator;
import com.yangqiong.agent.harness.local.workspace.SessionTranscriptStore;
import com.yangqiong.agent.harness.local.workspace.SkillDistiller;

/**
 * 本地智能体会话
 * <p>
 * 一键装配的产物聚合：持有可运行的 AgentRuntime、SQLite 共享存储、本地工作区、
 * 会话JSONL转写存储与装配配置，关闭时先停定时调度器再释放底层数据库连接。
 * </p>
 * @author yangqiong
 */
public class LocalAgentSession implements AutoCloseable {

    /**
     * 可运行的Agent运行时
     */
    private final AgentRuntime runtime;

    /**
     * SQLite本地共享存储
     */
    private final LocalDistributedStores stores;

    /**
     * 本地工作区
     */
    private final LocalWorkspace workspace;

    /**
     * 会话JSONL转写存储
     */
    private final SessionTranscriptStore transcript;

    /**
     * 产生本会话的装配配置
     */
    private final LocalHarnessConfig config;

    /**
     * 定时任务调度器，未启用时为空
     */
    private final CronScheduler cronScheduler;

    /**
     * 复盘记忆沉淀，未启用时为空
     */
    private final MemoryConsolidator memoryConsolidator;

    /**
     * 知识库与记忆自维护，未启用时为空
     */
    private final KnowledgeMaintainer knowledgeMaintainer;

    /**
     * 监控告警与自愈，未启用时为空
     */
    private final HealthWatchdog healthWatchdog;

    /**
     * 技能沉淀中间件，未启用时为空
     */
    private final SkillDistiller skillDistiller;

    /**
     * 装配产物聚合
     * @param runtime
     * @param stores
     * @param workspace
     * @param transcript
     * @param config
     * @param cronScheduler
     * @param memoryConsolidator
     * @param knowledgeMaintainer
     * @param healthWatchdog
     * @param skillDistiller
     */
    public LocalAgentSession(AgentRuntime runtime, LocalDistributedStores stores,
                             LocalWorkspace workspace, SessionTranscriptStore transcript,
                             LocalHarnessConfig config, CronScheduler cronScheduler,
                             MemoryConsolidator memoryConsolidator,
                             KnowledgeMaintainer knowledgeMaintainer,
                             HealthWatchdog healthWatchdog,
                             SkillDistiller skillDistiller) {
        this.runtime = Objects.requireNonNull(runtime, "runtime不能为空");
        this.stores = Objects.requireNonNull(stores, "stores不能为空");
        this.workspace = Objects.requireNonNull(workspace, "workspace不能为空");
        this.transcript = Objects.requireNonNull(transcript, "transcript不能为空");
        this.config = Objects.requireNonNull(config, "config不能为空");
        this.cronScheduler = cronScheduler;
        this.memoryConsolidator = memoryConsolidator;
        this.knowledgeMaintainer = knowledgeMaintainer;
        this.healthWatchdog = healthWatchdog;
        this.skillDistiller = skillDistiller;
    }

    /**
     * 获取Agent运行时
     * @return
     */
    public AgentRuntime runtime() {
        return runtime;
    }

    /**
     * 获取SQLite本地共享存储
     * @return
     */
    public LocalDistributedStores stores() {
        return stores;
    }

    /**
     * 获取本地工作区
     * @return
     */
    public LocalWorkspace workspace() {
        return workspace;
    }

    /**
     * 获取会话JSONL转写存储
     * @return
     */
    public SessionTranscriptStore transcript() {
        return transcript;
    }

    /**
     * 获取装配配置
     * @return
     */
    public LocalHarnessConfig config() {
        return config;
    }

    /**
     * 获取定时任务调度器，未启用时返回null
     * @return
     */
    public CronScheduler cronScheduler() {
        return cronScheduler;
    }

    /**
     * 获取复盘记忆沉淀，未启用时返回null
     * @return
     */
    public MemoryConsolidator memoryConsolidator() {
        return memoryConsolidator;
    }

    /**
     * 获取知识库与记忆自维护，未启用时返回null
     * @return
     */
    public KnowledgeMaintainer knowledgeMaintainer() {
        return knowledgeMaintainer;
    }

    /**
     * 获取监控告警与自愈，未启用时返回null
     * @return
     */
    public HealthWatchdog healthWatchdog() {
        return healthWatchdog;
    }

    /**
     * 获取技能沉淀中间件，未启用时返回null
     * @return
     */
    public SkillDistiller skillDistiller() {
        return skillDistiller;
    }

    /**
     * 关闭会话，先停止定时调度器再释放SQLite共享存储的数据库连接
     * @return
     */
    @Override
    public void close() {
        if (healthWatchdog != null) {
            healthWatchdog.stop();
        }
        if (cronScheduler != null) {
            cronScheduler.close();
        }
        stores.close();
    }
}
