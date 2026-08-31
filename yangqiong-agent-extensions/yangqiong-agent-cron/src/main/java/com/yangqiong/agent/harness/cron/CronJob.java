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

/**
 * 定时任务
 * <p>
 * 描述一条可持久化的自动化任务：按cron表达式周期性触发的执行指令，
 * 附带运行计数与最近/下次触发时间（毫秒时间戳），enabled用于暂停与恢复。
 * </p>
 * @author yangqiong
 */
public class CronJob {

    /**
     * 任务ID
     */
    private String id;

    /**
     * 任务名称
     */
    private String name;

    /**
     * cron表达式（5段或6段）
     */
    private String cronExpression;

    /**
     * 触发时交给Agent执行的指令
     */
    private String instruction;

    /**
     * 租户作用域标识，执行时注入运行时上下文
     */
    private String scopeId;

    /**
     * 会话标识，执行时注入运行时上下文
     */
    private String sessionId;

    /**
     * 是否启用，false表示暂停
     */
    private boolean enabled = true;

    /**
     * 已触发次数
     */
    private long runCount;

    /**
     * 最近触发时间（毫秒时间戳）
     */
    private long lastRunAtMillis;

    /**
     * 下次触发时间（毫秒时间戳）
     */
    private long nextRunAtMillis;

    /**
     * 创建时间（毫秒时间戳）
     */
    private long createdAtMillis;

    /**
     * 最近更新时间（毫秒时间戳）
     */
    private long updatedAtMillis;

    /**
     * 获取任务ID
     * @return
     */
    public String getId() {
        return id;
    }

    /**
     * 设置任务ID
     * @param id
     */
    public void setId(String id) {
        this.id = id;
    }

    /**
     * 获取任务名称
     * @return
     */
    public String getName() {
        return name;
    }

    /**
     * 设置任务名称
     * @param name
     */
    public void setName(String name) {
        this.name = name;
    }

    /**
     * 获取cron表达式
     * @return
     */
    public String getCronExpression() {
        return cronExpression;
    }

    /**
     * 设置cron表达式
     * @param cronExpression
     */
    public void setCronExpression(String cronExpression) {
        this.cronExpression = cronExpression;
    }

    /**
     * 获取触发指令
     * @return
     */
    public String getInstruction() {
        return instruction;
    }

    /**
     * 设置触发指令
     * @param instruction
     */
    public void setInstruction(String instruction) {
        this.instruction = instruction;
    }

    /**
     * 获取租户作用域标识
     * @return
     */
    public String getScopeId() {
        return scopeId;
    }

    /**
     * 设置租户作用域标识
     * @param scopeId
     */
    public void setScopeId(String scopeId) {
        this.scopeId = scopeId;
    }

    /**
     * 获取会话标识
     * @return
     */
    public String getSessionId() {
        return sessionId;
    }

    /**
     * 设置会话标识
     * @param sessionId
     */
    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    /**
     * 是否启用
     * @return
     */
    public boolean isEnabled() {
        return enabled;
    }

    /**
     * 设置是否启用
     * @param enabled
     */
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    /**
     * 获取已触发次数
     * @return
     */
    public long getRunCount() {
        return runCount;
    }

    /**
     * 设置已触发次数
     * @param runCount
     */
    public void setRunCount(long runCount) {
        this.runCount = runCount;
    }

    /**
     * 获取最近触发时间
     * @return
     */
    public long getLastRunAtMillis() {
        return lastRunAtMillis;
    }

    /**
     * 设置最近触发时间
     * @param lastRunAtMillis
     */
    public void setLastRunAtMillis(long lastRunAtMillis) {
        this.lastRunAtMillis = lastRunAtMillis;
    }

    /**
     * 获取下次触发时间
     * @return
     */
    public long getNextRunAtMillis() {
        return nextRunAtMillis;
    }

    /**
     * 设置下次触发时间
     * @param nextRunAtMillis
     */
    public void setNextRunAtMillis(long nextRunAtMillis) {
        this.nextRunAtMillis = nextRunAtMillis;
    }

    /**
     * 获取创建时间
     * @return
     */
    public long getCreatedAtMillis() {
        return createdAtMillis;
    }

    /**
     * 设置创建时间
     * @param createdAtMillis
     */
    public void setCreatedAtMillis(long createdAtMillis) {
        this.createdAtMillis = createdAtMillis;
    }

    /**
     * 获取最近更新时间
     * @return
     */
    public long getUpdatedAtMillis() {
        return updatedAtMillis;
    }

    /**
     * 设置最近更新时间
     * @param updatedAtMillis
     */
    public void setUpdatedAtMillis(long updatedAtMillis) {
        this.updatedAtMillis = updatedAtMillis;
    }

    /**
     * 创建任务构建器
     * @return
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * 定时任务构建器
     * @author yangqiong
     */
    public static class Builder {

        /**
         * 任务ID
         */
        private String id;

        /**
         * 任务名称
         */
        private String name;

        /**
         * cron表达式
         */
        private String cronExpression;

        /**
         * 触发指令
         */
        private String instruction;

        /**
         * 租户作用域标识
         */
        private String scopeId;

        /**
         * 会话标识
         */
        private String sessionId;

        /**
         * 是否启用
         */
        private boolean enabled = true;

        /**
         * 设置任务ID
         * @param id
         * @return
         */
        public Builder id(String id) {
            this.id = id;
            return this;
        }

        /**
         * 设置任务名称
         * @param name
         * @return
         */
        public Builder name(String name) {
            this.name = name;
            return this;
        }

        /**
         * 设置cron表达式
         * @param cronExpression
         * @return
         */
        public Builder cronExpression(String cronExpression) {
            this.cronExpression = cronExpression;
            return this;
        }

        /**
         * 设置触发指令
         * @param instruction
         * @return
         */
        public Builder instruction(String instruction) {
            this.instruction = instruction;
            return this;
        }

        /**
         * 设置租户作用域标识
         * @param scopeId
         * @return
         */
        public Builder scopeId(String scopeId) {
            this.scopeId = scopeId;
            return this;
        }

        /**
         * 设置会话标识
         * @param sessionId
         * @return
         */
        public Builder sessionId(String sessionId) {
            this.sessionId = sessionId;
            return this;
        }

        /**
         * 设置是否启用
         * @param enabled
         * @return
         */
        public Builder enabled(boolean enabled) {
            this.enabled = enabled;
            return this;
        }

        /**
         * 构建定时任务
         * @return
         */
        public CronJob build() {
            CronJob job = new CronJob();
            job.setId(id);
            job.setName(name);
            job.setCronExpression(cronExpression);
            job.setInstruction(instruction);
            job.setScopeId(scopeId);
            job.setSessionId(sessionId);
            job.setEnabled(enabled);
            return job;
        }
    }
}
