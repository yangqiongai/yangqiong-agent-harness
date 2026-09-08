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
package com.yangqiongai.agent.harness.local.runtime;

import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import com.yangqiongai.agent.harness.config.AgentApprovalMode;

/**
 * 本地一键装配配置
 * <p>
 * 以单一根目录为轴的装配参数集合：rootDir 承载 harness.db 与 workspace，
 * 模型端点可显式指定（baseUrl 以 /v1 结尾按OpenAI兼容协议，否则按Ollama原生协议），
 * 留空时由发现器自动探测并取第一个端点与第一个模型；
 * scopeId 与 sessionId 供检查点恢复按复合键定位最近快照。
 * </p>
 * @author yangqiong
 */
public class LocalHarnessConfig {

    /**
     * 默认存储根目录名
     */
    private static final String DEFAULT_DIR_NAME = ".yangqiong-agent";

    /**
     * 存储根目录，库文件为 {rootDir}/harness.db
     */
    private final Path rootDir;

    /**
     * 工作区目录，为空时默认 {rootDir}/workspace
     */
    private final Path workspaceDir;

    /**
     * 显式指定的模型服务基础地址，为空时走自动发现
     */
    private final String endpointBaseUrl;

    /**
     * 显式指定的模型名称，为空时取发现端点的第一个模型
     */
    private final String modelName;

    /**
     * 显式指定模型服务的鉴权密钥，远端OpenAI兼容服务需要，本地服务可留空
     */
    private final String endpointApiKey;

    /**
     * 追加探测的OpenAI兼容端点地址列表
     */
    private final List<String> extraEndpointBaseUrls;

    /**
     * 是否注册本地Shell工具
     */
    private final boolean shellEnabled;

    /**
     * Shell命令执行超时时间
     */
    private final Duration shellTimeout;

    /**
     * Shell命令是否需要人工审批后才执行
     */
    private final boolean shellRequireApproval;

    /**
     * ReAct循环最大迭代次数
     */
    private final int maxIters;

    /**
     * 系统提示词，为空时不设置
     */
    private final String systemPrompt;

    /**
     * 检查点恢复的作用域标识
     */
    private final String scopeId;

    /**
     * 检查点恢复的会话标识
     */
    private final String sessionId;

    /**
     * 审批模式（MANUAL人工、AUTO自动AI审批、FULL_ACCESS全部放行、CUSTOM沿用原细粒度配置）
     */
    private final AgentApprovalMode approvalMode;

    /**
     * AUTO审批模式下的审批策略描述，约束审批模型判定口径
     */
    private final String aiApprovalGuidance;

    /**
     * 是否启用工作区目录双向同步（AGENTS.md读入、技能发现、会话自动落盘）
     */
    private final boolean workspaceSyncEnabled;

    /**
     * 是否启用 MEMORY.md 跨会话记忆简报读入
     */
    private final boolean memoryFileEnabled;

    /**
     * 是否启用 knowledge/ 目录RAG检索
     */
    private final boolean knowledgeRagEnabled;

    /**
     * 是否启用定时任务调度
     */
    private final boolean cronEnabled;

    /**
     * 定时任务存储文件，为空时默认 {rootDir}/cron/jobs.json
     */
    private final Path cronStoreFile;

    /**
     * 是否启用复盘记忆沉淀
     */
    private final boolean memoryConsolidateEnabled;

    /**
     * 是否启用技能沉淀
     */
    private final boolean skillDistillEnabled;

    /**
     * 是否启用知识库与记忆自维护
     */
    private final boolean knowledgeMaintainEnabled;

    /**
     * 是否启用监控告警与自愈
     */
    private final boolean healthWatchdogEnabled;

    /**
     * 以构建器参数固化配置
     * @param builder
     */
    private LocalHarnessConfig(Builder builder) {
        this.rootDir = builder.rootDir;
        this.workspaceDir = builder.workspaceDir;
        this.endpointBaseUrl = blankToNull(builder.endpointBaseUrl);
        this.modelName = blankToNull(builder.modelName);
        this.endpointApiKey = blankToNull(builder.endpointApiKey);
        this.extraEndpointBaseUrls = List.copyOf(builder.extraEndpointBaseUrls);
        this.shellEnabled = builder.shellEnabled;
        this.shellTimeout = builder.shellTimeout;
        this.shellRequireApproval = builder.shellRequireApproval;
        this.maxIters = builder.maxIters;
        this.systemPrompt = blankToNull(builder.systemPrompt);
        this.scopeId = builder.scopeId;
        this.sessionId = builder.sessionId;
        this.approvalMode = builder.approvalMode != null ? builder.approvalMode : AgentApprovalMode.CUSTOM;
        this.aiApprovalGuidance = blankToNull(builder.aiApprovalGuidance);
        this.workspaceSyncEnabled = builder.workspaceSyncEnabled;
        this.memoryFileEnabled = builder.memoryFileEnabled;
        this.knowledgeRagEnabled = builder.knowledgeRagEnabled;
        this.cronEnabled = builder.cronEnabled;
        this.cronStoreFile = builder.cronStoreFile;
        this.memoryConsolidateEnabled = builder.memoryConsolidateEnabled;
        this.skillDistillEnabled = builder.skillDistillEnabled;
        this.knowledgeMaintainEnabled = builder.knowledgeMaintainEnabled;
        this.healthWatchdogEnabled = builder.healthWatchdogEnabled;
    }

    /**
     * 创建配置构建器
     * @return
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * 获取存储根目录
     * @return
     */
    public Path rootDir() {
        return rootDir;
    }

    /**
     * 获取工作区目录，未配置时返回null
     * @return
     */
    public Path workspaceDir() {
        return workspaceDir;
    }

    /**
     * 获取显式指定的模型服务基础地址，未配置时返回null
     * @return
     */
    public String endpointBaseUrl() {
        return endpointBaseUrl;
    }

    /**
     * 获取显式指定的模型名称，未配置时返回null
     * @return
     */
    public String modelName() {
        return modelName;
    }

    /**
     * 获取显式指定模型服务的鉴权密钥，未配置时返回null
     * @return
     */
    public String endpointApiKey() {
        return endpointApiKey;
    }

    /**
     * 获取追加探测的OpenAI兼容端点地址列表
     * @return
     */
    public List<String> extraEndpointBaseUrls() {
        return extraEndpointBaseUrls;
    }

    /**
     * 是否注册本地Shell工具
     * @return
     */
    public boolean shellEnabled() {
        return shellEnabled;
    }

    /**
     * 获取Shell命令执行超时时间
     * @return
     */
    public Duration shellTimeout() {
        return shellTimeout;
    }

    /**
     * Shell命令是否需要人工审批后才执行
     * @return
     */
    public boolean shellRequireApproval() {
        return shellRequireApproval;
    }

    /**
     * 获取ReAct循环最大迭代次数
     * @return
     */
    public int maxIters() {
        return maxIters;
    }

    /**
     * 获取系统提示词，未配置时返回null
     * @return
     */
    public String systemPrompt() {
        return systemPrompt;
    }

    /**
     * 获取检查点恢复的作用域标识
     * @return
     */
    public String scopeId() {
        return scopeId;
    }

    /**
     * 获取检查点恢复的会话标识
     * @return
     */
    public String sessionId() {
        return sessionId;
    }

    /**
     * 获取审批模式
     * @return
     */
    public AgentApprovalMode approvalMode() {
        return approvalMode;
    }

    /**
     * 获取AUTO审批模式下的审批策略描述，未配置时返回null
     * @return
     */
    public String aiApprovalGuidance() {
        return aiApprovalGuidance;
    }

    /**
     * 是否启用工作区目录双向同步
     * @return
     */
    public boolean workspaceSyncEnabled() {
        return workspaceSyncEnabled;
    }

    /**
     * 是否启用 MEMORY.md 跨会话记忆简报读入
     * @return
     */
    public boolean memoryFileEnabled() {
        return memoryFileEnabled;
    }

    /**
     * 是否启用 knowledge/ 目录RAG检索
     * @return
     */
    public boolean knowledgeRagEnabled() {
        return knowledgeRagEnabled;
    }

    /**
     * 是否启用定时任务调度
     * @return
     */
    public boolean cronEnabled() {
        return cronEnabled;
    }

    /**
     * 获取定时任务存储文件，未配置时返回null
     * @return
     */
    public Path cronStoreFile() {
        return cronStoreFile;
    }

    /**
     * 是否启用复盘记忆沉淀
     * @return
     */
    public boolean memoryConsolidateEnabled() {
        return memoryConsolidateEnabled;
    }

    /**
     * 是否启用技能沉淀
     * @return
     */
    public boolean skillDistillEnabled() {
        return skillDistillEnabled;
    }

    /**
     * 是否启用知识库与记忆自维护
     * @return
     */
    public boolean knowledgeMaintainEnabled() {
        return knowledgeMaintainEnabled;
    }

    /**
     * 是否启用监控告警与自愈
     * @return
     */
    public boolean healthWatchdogEnabled() {
        return healthWatchdogEnabled;
    }

    /**
     * 将空白字符串统一归一为null
     * @param value
     * @return
     */
    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    /**
     * 本地装配配置构建器
     * @author yangqiong
     */
    public static class Builder {

        /**
         * 存储根目录，默认用户目录/.yangqiong-agent
         */
        private Path rootDir = Path.of(System.getProperty("user.home"), DEFAULT_DIR_NAME);

        /**
         * 工作区目录，为空时默认 {rootDir}/workspace
         */
        private Path workspaceDir;

        /**
         * 显式指定的模型服务基础地址
         */
        private String endpointBaseUrl;

        /**
         * 显式指定的模型名称
         */
        private String modelName;

        /**
         * 显式指定模型服务的鉴权密钥
         */
        private String endpointApiKey;

        /**
         * 追加探测的OpenAI兼容端点地址列表
         */
        private List<String> extraEndpointBaseUrls = new ArrayList<>();

        /**
         * 是否注册本地Shell工具
         */
        private boolean shellEnabled = true;

        /**
         * Shell命令执行超时时间
         */
        private Duration shellTimeout = Duration.ofSeconds(30);

        /**
         * Shell命令是否需要人工审批后才执行
         */
        private boolean shellRequireApproval = true;

        /**
         * ReAct循环最大迭代次数
         */
        private int maxIters = 8;

        /**
         * 系统提示词
         */
        private String systemPrompt;

        /**
         * 检查点恢复的作用域标识
         */
        private String scopeId = "local";

        /**
         * 检查点恢复的会话标识
         */
        private String sessionId = "default";

        /**
         * 审批模式
         */
        private AgentApprovalMode approvalMode;

        /**
         * AUTO审批模式下的审批策略描述
         */
        private String aiApprovalGuidance;

        /**
         * 是否启用工作区目录双向同步，默认开启
         */
        private boolean workspaceSyncEnabled = true;

        /**
         * 是否启用 MEMORY.md 跨会话记忆简报读入，默认开启
         */
        private boolean memoryFileEnabled = true;

        /**
         * 是否启用 knowledge/ 目录RAG检索，默认开启
         */
        private boolean knowledgeRagEnabled = true;

        /**
         * 是否启用定时任务调度，默认关闭
         */
        private boolean cronEnabled = false;

        /**
         * 定时任务存储文件，为空时默认 {rootDir}/cron/jobs.json
         */
        private Path cronStoreFile;

        /**
         * 是否启用复盘记忆沉淀，默认关闭
         */
        private boolean memoryConsolidateEnabled = false;

        /**
         * 是否启用技能沉淀，默认关闭
         */
        private boolean skillDistillEnabled = false;

        /**
         * 是否启用知识库与记忆自维护，默认关闭
         */
        private boolean knowledgeMaintainEnabled = false;

        /**
         * 是否启用监控告警与自愈，默认关闭
         */
        private boolean healthWatchdogEnabled = false;

        /**
         * 设置存储根目录
         * @param rootDir
         * @return
         */
        public Builder rootDir(Path rootDir) {
            this.rootDir = Objects.requireNonNull(rootDir, "rootDir不能为空");
            return this;
        }

        /**
         * 设置工作区目录，为空时默认 {rootDir}/workspace
         * @param workspaceDir
         * @return
         */
        public Builder workspaceDir(Path workspaceDir) {
            this.workspaceDir = workspaceDir;
            return this;
        }

        /**
         * 设置显式指定的模型服务基础地址
         * @param endpointBaseUrl
         * @return
         */
        public Builder endpointBaseUrl(String endpointBaseUrl) {
            this.endpointBaseUrl = endpointBaseUrl;
            return this;
        }

        /**
         * 设置显式指定的模型名称
         * @param modelName
         * @return
         */
        public Builder modelName(String modelName) {
            this.modelName = modelName;
            return this;
        }

        /**
         * 设置显式指定模型服务的鉴权密钥
         * @param endpointApiKey
         * @return
         */
        public Builder endpointApiKey(String endpointApiKey) {
            this.endpointApiKey = endpointApiKey;
            return this;
        }

        /**
         * 设置追加探测的OpenAI兼容端点地址列表
         * @param extraEndpointBaseUrls
         * @return
         */
        public Builder extraEndpointBaseUrls(List<String> extraEndpointBaseUrls) {
            this.extraEndpointBaseUrls = extraEndpointBaseUrls != null
                    ? new ArrayList<>(extraEndpointBaseUrls) : new ArrayList<>();
            return this;
        }

        /**
         * 追加单个OpenAI兼容端点地址
         * @param extraEndpointBaseUrl
         * @return
         */
        public Builder addExtraEndpointBaseUrl(String extraEndpointBaseUrl) {
            if (extraEndpointBaseUrl != null && !extraEndpointBaseUrl.isBlank()) {
                this.extraEndpointBaseUrls.add(extraEndpointBaseUrl);
            }
            return this;
        }

        /**
         * 设置是否注册本地Shell工具
         * @param shellEnabled
         * @return
         */
        public Builder shellEnabled(boolean shellEnabled) {
            this.shellEnabled = shellEnabled;
            return this;
        }

        /**
         * 设置Shell命令执行超时时间
         * @param shellTimeout
         * @return
         */
        public Builder shellTimeout(Duration shellTimeout) {
            this.shellTimeout = shellTimeout;
            return this;
        }

        /**
         * 设置Shell命令是否需要人工审批后才执行
         * @param shellRequireApproval
         * @return
         */
        public Builder shellRequireApproval(boolean shellRequireApproval) {
            this.shellRequireApproval = shellRequireApproval;
            return this;
        }

        /**
         * 设置ReAct循环最大迭代次数
         * @param maxIters
         * @return
         */
        public Builder maxIters(int maxIters) {
            this.maxIters = maxIters;
            return this;
        }

        /**
         * 设置系统提示词
         * @param systemPrompt
         * @return
         */
        public Builder systemPrompt(String systemPrompt) {
            this.systemPrompt = systemPrompt;
            return this;
        }

        /**
         * 设置检查点恢复的作用域标识
         * @param scopeId
         * @return
         */
        public Builder scopeId(String scopeId) {
            this.scopeId = scopeId;
            return this;
        }

        /**
         * 设置检查点恢复的会话标识
         * @param sessionId
         * @return
         */
        public Builder sessionId(String sessionId) {
            this.sessionId = sessionId;
            return this;
        }

        /**
         * 设置审批模式
         * @param approvalMode
         * @return
         */
        public Builder approvalMode(AgentApprovalMode approvalMode) {
            this.approvalMode = approvalMode;
            return this;
        }

        /**
         * 设置AUTO审批模式下的审批策略描述
         * @param aiApprovalGuidance
         * @return
         */
        public Builder aiApprovalGuidance(String aiApprovalGuidance) {
            this.aiApprovalGuidance = aiApprovalGuidance;
            return this;
        }

        /**
         * 设置是否启用工作区目录双向同步
         * @param workspaceSyncEnabled
         * @return
         */
        public Builder workspaceSyncEnabled(boolean workspaceSyncEnabled) {
            this.workspaceSyncEnabled = workspaceSyncEnabled;
            return this;
        }

        /**
         * 设置是否启用 MEMORY.md 跨会话记忆简报读入
         * @param memoryFileEnabled
         * @return
         */
        public Builder memoryFileEnabled(boolean memoryFileEnabled) {
            this.memoryFileEnabled = memoryFileEnabled;
            return this;
        }

        /**
         * 设置是否启用 knowledge/ 目录RAG检索
         * @param knowledgeRagEnabled
         * @return
         */
        public Builder knowledgeRagEnabled(boolean knowledgeRagEnabled) {
            this.knowledgeRagEnabled = knowledgeRagEnabled;
            return this;
        }

        /**
         * 设置是否启用定时任务调度
         * @param cronEnabled
         * @return
         */
        public Builder cronEnabled(boolean cronEnabled) {
            this.cronEnabled = cronEnabled;
            return this;
        }

        /**
         * 设置定时任务存储文件
         * @param cronStoreFile
         * @return
         */
        public Builder cronStoreFile(Path cronStoreFile) {
            this.cronStoreFile = cronStoreFile;
            return this;
        }

        /**
         * 设置是否启用复盘记忆沉淀
         * @param memoryConsolidateEnabled
         * @return
         */
        public Builder memoryConsolidateEnabled(boolean memoryConsolidateEnabled) {
            this.memoryConsolidateEnabled = memoryConsolidateEnabled;
            return this;
        }

        /**
         * 设置是否启用技能沉淀
         * @param skillDistillEnabled
         * @return
         */
        public Builder skillDistillEnabled(boolean skillDistillEnabled) {
            this.skillDistillEnabled = skillDistillEnabled;
            return this;
        }

        /**
         * 设置是否启用知识库与记忆自维护
         * @param knowledgeMaintainEnabled
         * @return
         */
        public Builder knowledgeMaintainEnabled(boolean knowledgeMaintainEnabled) {
            this.knowledgeMaintainEnabled = knowledgeMaintainEnabled;
            return this;
        }

        /**
         * 设置是否启用监控告警与自愈
         * @param healthWatchdogEnabled
         * @return
         */
        public Builder healthWatchdogEnabled(boolean healthWatchdogEnabled) {
            this.healthWatchdogEnabled = healthWatchdogEnabled;
            return this;
        }

        /**
         * 构建本地装配配置
         * @return
         */
        public LocalHarnessConfig build() {
            if (maxIters <= 0) {
                throw new IllegalArgumentException("maxIters必须为正数: " + maxIters);
            }
            if (shellTimeout == null || shellTimeout.isZero() || shellTimeout.isNegative()) {
                throw new IllegalArgumentException("shellTimeout必须为正时长");
            }
            return new LocalHarnessConfig(this);
        }
    }
}
