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

import java.util.Objects;
import java.util.Optional;

import com.yangqiongai.agent.harness.core.event.AgentEvent;
import com.yangqiongai.agent.harness.durable.AgentCheckpoint;
import com.yangqiongai.agent.harness.engine.AgentRuntimeContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;

/**
 * 本地智能体恢复
 * <p>
 * resumeLatest 装配的产物：聚合恢复所需的本地会话与最近检查点，
 * resume() 以检查点自带的 scopeId 与 sessionId 构建上下文并调用运行时的
 * resumeFromCheckpoint 完成续跑，无检查点时返回空事件流且不抛异常。
 * </p>
 * @author yangqiong
 */
public class LocalAgentResume implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(LocalAgentResume.class);

    /**
     * 恢复所用的本地会话
     */
    private final LocalAgentSession session;

    /**
     * 最近检查点，无检查点时为null
     */
    private final AgentCheckpoint checkpoint;

    /**
     * 以会话与检查点构建恢复入口
     * @param session
     * @param checkpoint 可为null，表示无检查点可恢复
     */
    public LocalAgentResume(LocalAgentSession session, AgentCheckpoint checkpoint) {
        this.session = Objects.requireNonNull(session, "session不能为空");
        this.checkpoint = checkpoint;
    }

    /**
     * 获取恢复所用的本地会话
     * @return
     */
    public LocalAgentSession session() {
        return session;
    }

    /**
     * 是否存在可恢复的检查点
     * @return
     */
    public boolean hasCheckpoint() {
        return checkpoint != null;
    }

    /**
     * 获取最近检查点
     * @return
     */
    public Optional<AgentCheckpoint> checkpoint() {
        return Optional.ofNullable(checkpoint);
    }

    /**
     * 从最近检查点续跑，无检查点时返回空事件流
     * @return
     */
    public Flux<AgentEvent> resume() {
        if (checkpoint == null) {
            log.warn("无检查点可恢复: scopeId={}, sessionId={}",
                    session.config().scopeId(), session.config().sessionId());
            return Flux.empty();
        }
        AgentRuntimeContext context = AgentRuntimeContext.builder()
                .scopeId(checkpoint.getScopeId())
                .sessionId(checkpoint.getSessionId())
                .build();
        return session.runtime().resumeFromCheckpoint(context);
    }

    /**
     * 关闭恢复入口，级联关闭底层会话与共享存储
     * @return
     */
    @Override
    public void close() {
        session.close();
    }
}
