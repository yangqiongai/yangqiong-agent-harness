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
package com.yangqiongai.agent.harness.server;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.yangqiongai.agent.harness.local.runtime.LocalAgentHarness;
import com.yangqiongai.agent.harness.local.runtime.LocalAgentSession;

/**
 * 会话管理器
 * <p>
 * 按 (scopeId, sessionId) 复合键缓存已装配的本地会话，同一会话在对话流推送与
 * 审批暂停后的 resume 请求间复用同一运行时，保证暂停状态可在请求间延续。
 * </p>
 * @author yangqiong
 */
public class AgentSessionManager implements AutoCloseable {

    /**
     * 服务端装配配置
     */
    private final ServerConfig config;

    /**
     * 会话键映射
     */
    private final Map<String, LocalAgentSession> sessions = new ConcurrentHashMap<>();

    /**
     * 以服务端装配配置构建会话管理器
     * @param config
     */
    public AgentSessionManager(ServerConfig config) {
        this.config = config;
    }

    /**
     * 获取或惰性装配指定会话，同一复合键复用同一运行时
     * @param scopeId
     * @param sessionId
     * @return
     */
    public LocalAgentSession sessionFor(String scopeId, String sessionId) {
        return sessions.computeIfAbsent(sessionKey(scopeId, sessionId),
                key -> LocalAgentHarness.create(config.toLocalConfig(scopeId, sessionId)));
    }

    /**
     * 计算会话复合键
     * @param scopeId
     * @param sessionId
     * @return
     */
    static String sessionKey(String scopeId, String sessionId) {
        return (scopeId == null ? "" : scopeId) + "::" + (sessionId == null ? "" : sessionId);
    }

    /**
     * 获取当前会话总数
     * @return
     */
    public int size() {
        return sessions.size();
    }

    /**
     * 关闭全部缓存的会话，释放底层资源
     */
    @Override
    public void close() {
        sessions.values().forEach(LocalAgentSession::close);
        sessions.clear();
    }
}