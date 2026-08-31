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
package com.yangqiong.agent.harness.server;

import java.nio.file.Path;

import com.yangqiong.agent.harness.config.AgentApprovalMode;
import com.yangqiong.agent.harness.local.runtime.LocalHarnessConfig;

/**
 * 服务端装配配置
 * <p>
 * 封装服务化部署所需的基础模型端点、审批模式与默认作用域/会话，按请求的
 * scopeId/sessionId 派生每次会话的 LocalHarnessConfig，供会话管理器装配。
 * </p>
 * @author yangqiong
 */
public record ServerConfig(
        Path rootDir,
        String endpointBaseUrl,
        String modelName,
        String endpointApiKey,
        boolean shellEnabled,
        boolean shellRequireApproval,
        int maxIters,
        String systemPrompt,
        AgentApprovalMode approvalMode,
        String defaultScopeId,
        String defaultSessionId
) {

    /**
     * 默认最大推理迭代次数
     */
    private static final int DEFAULT_MAX_ITERS = 8;

    /**
     * 默认作用域标识
     */
    private static final String DEFAULT_SCOPE_ID = "server";

    /**
     * 默认会话标识
     */
    private static final String DEFAULT_SESSION_ID = "default-session";

    /**
     * 构建器
     * @return
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * 按给定作用域与会话派生本地装配配置
     * @param scopeId
     * @param sessionId
     * @return
     */
    public LocalHarnessConfig toLocalConfig(String scopeId, String sessionId) {
        LocalHarnessConfig.Builder builder = com.yangqiong.agent.harness.local.runtime.LocalAgentHarness.builder()
                .rootDir(rootDir)
                .shellEnabled(shellEnabled)
                .shellRequireApproval(shellRequireApproval)
                .maxIters(maxIters)
                .scopeId(scopeId)
                .sessionId(sessionId);
        if (systemPrompt != null) {
            builder.systemPrompt(systemPrompt);
        }
        if (endpointBaseUrl != null) {
            builder.endpointBaseUrl(endpointBaseUrl);
        }
        if (modelName != null) {
            builder.modelName(modelName);
        }
        if (endpointApiKey != null) {
            builder.endpointApiKey(endpointApiKey);
        }
        if (approvalMode != null) {
            builder.approvalMode(approvalMode);
        }
        return builder.build();
    }

    /**
     * 服务端装配配置构建器
     * @author yangqiong
     */
    public static class Builder {

        private Path rootDir;
        private String endpointBaseUrl;
        private String modelName;
        private String endpointApiKey;
        private boolean shellEnabled;
        private boolean shellRequireApproval;
        private int maxIters = DEFAULT_MAX_ITERS;
        private String systemPrompt;
        private AgentApprovalMode approvalMode = AgentApprovalMode.MANUAL;
        private String defaultScopeId = DEFAULT_SCOPE_ID;
        private String defaultSessionId = DEFAULT_SESSION_ID;

        /**
         * 继续时在根目录必填，通过 rootDir 设置
         */
        public Builder() {
        }

        /**
         * 设置根目录（必填）
         * @param rootDir
         * @return
         */
        public Builder rootDir(Path rootDir) {
            this.rootDir = rootDir;
            return this;
        }

        /**
         * 设置模型端点基础地址
         * @param endpointBaseUrl
         * @return
         */
        public Builder endpointBaseUrl(String endpointBaseUrl) {
            this.endpointBaseUrl = endpointBaseUrl;
            return this;
        }

        /**
         * 设置模型名称
         * @param modelName
         * @return
         */
        public Builder modelName(String modelName) {
            this.modelName = modelName;
            return this;
        }

        /**
         * 设置远端模型鉴权密钥
         * @param endpointApiKey
         * @return
         */
        public Builder endpointApiKey(String endpointApiKey) {
            this.endpointApiKey = endpointApiKey;
            return this;
        }

        /**
         * 是否启用Shell工具
         * @param shellEnabled
         * @return
         */
        public Builder shellEnabled(boolean shellEnabled) {
            this.shellEnabled = shellEnabled;
            return this;
        }

        /**
         * 是否要求Shell调用人工审批
         * @param shellRequireApproval
         * @return
         */
        public Builder shellRequireApproval(boolean shellRequireApproval) {
            this.shellRequireApproval = shellRequireApproval;
            return this;
        }

        /**
         * 设置最大推理迭代次数
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
         * 设置统一审批模式
         * @param approvalMode
         * @return
         */
        public Builder approvalMode(AgentApprovalMode approvalMode) {
            this.approvalMode = approvalMode;
            return this;
        }

        /**
         * 设置默认作用域标识
         * @param defaultScopeId
         * @return
         */
        public Builder defaultScopeId(String defaultScopeId) {
            this.defaultScopeId = defaultScopeId;
            return this;
        }

        /**
         * 设置默认会话标识
         * @param defaultSessionId
         * @return
         */
        public Builder defaultSessionId(String defaultSessionId) {
            this.defaultSessionId = defaultSessionId;
            return this;
        }

        /**
         * 构建服务端装配配置
         * @return
         */
        public ServerConfig build() {
            return new ServerConfig(rootDir, endpointBaseUrl, modelName, endpointApiKey,
                    shellEnabled, shellRequireApproval, maxIters, systemPrompt,
                    approvalMode, defaultScopeId, defaultSessionId);
        }
    }
}