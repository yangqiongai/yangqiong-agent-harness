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
package com.yangqiongai.agent.harness.engine;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

import com.yangqiongai.agent.harness.core.interruption.AgentInterruptControl;

/**
 * Agent运行时上下文
 * <p>
 * 可变上下文：sessionId/userId 不可变，attributes 为可变映射，
 * 供中间件在执行过程中写入 SkillBox、TaskStepRecorder 等运行时对象。
 * </p>
 * @author yangqiong
 */
public class AgentRuntimeContext {

    /**
     * 作用域标识（引擎只做感知不做管理，平台适配层从作用域上下文映射注入）
     */
    private final String scopeId;

    private final String sessionId;

    private final String userId;

    private final AgentInterruptControl interruptControl;

    private final Map<String, Object> attributes;

    private AgentRuntimeContext(String scopeId, String sessionId, String userId,
                                AgentInterruptControl interruptControl,
                                Map<String, Object> attributes) {
        this.scopeId = scopeId;
        this.sessionId = sessionId;
        this.userId = userId;
        this.interruptControl = interruptControl;
        this.attributes = attributes != null ? new ConcurrentHashMap<>(attributes) : new ConcurrentHashMap<>();
    }

    /**
     * 创建空上下文
     * @return
     */
    public static AgentRuntimeContext empty() {
        return new AgentRuntimeContext(null, null, null, null, new HashMap<>());
    }

    /**
     * 获取作用域标识
     * @return
     */
    public String getScopeId() {
        return scopeId;
    }

    public static Builder builder() {
        return new Builder();
    }

    /**
     * 获取会话ID
     * @return
     */
    public String getSessionId() {
        return sessionId;
    }

    /**
     * 获取用户ID
     * @return
     */
    public String getUserId() {
        return userId;
    }

    /**
     * 获取中断控制器
     * @return
     */
    public AgentInterruptControl getInterruptControl() {
        return interruptControl;
    }

    /**
     * 获取扩展属性
     * @return
     */
    public Map<String, Object> getAttributes() {
        return attributes;
    }

    /**
     * 写入扩展属性
     * @param key
     * @param value
     */
    public void put(String key, Object value) {
        attributes.put(key, value);
    }

    /**
     * 读取扩展属性
     * @param key
     * @return
     */
    public Object get(String key) {
        return attributes.get(key);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        AgentRuntimeContext that = (AgentRuntimeContext) o;
        return Objects.equals(scopeId, that.scopeId)
                && Objects.equals(sessionId, that.sessionId)
                && Objects.equals(userId, that.userId)
                && Objects.equals(interruptControl, that.interruptControl)
                && Objects.equals(attributes, that.attributes);
    }

    @Override
    public int hashCode() {
        return Objects.hash(scopeId, sessionId, userId, interruptControl, attributes);
    }

    @Override
    public String toString() {
        return "AgentRuntimeContext{scopeId='" + scopeId + "', sessionId='" + sessionId + "', userId='" + userId + "', attributeSize=" + attributes.size() + "}";
    }

    /**
     * 运行时上下文构建器
     * @author yangqiong
     */
    public static class Builder {

        private String scopeId;

        private String sessionId;

        private String userId;

        private AgentInterruptControl interruptControl;

        private Map<String, Object> attributes = new HashMap<>();

        public Builder scopeId(String scopeId) {
            this.scopeId = scopeId;
            return this;
        }

        public Builder sessionId(String sessionId) {
            this.sessionId = sessionId;
            return this;
        }

        public Builder userId(String userId) {
            this.userId = userId;
            return this;
        }

        public Builder interruptControl(AgentInterruptControl interruptControl) {
            this.interruptControl = interruptControl;
            return this;
        }

        public Builder attributes(Map<String, Object> attributes) {
            this.attributes = attributes != null ? new HashMap<>(attributes) : new HashMap<>();
            return this;
        }

        public AgentRuntimeContext build() {
            return new AgentRuntimeContext(scopeId, sessionId, userId, interruptControl, attributes);
        }
    }
}