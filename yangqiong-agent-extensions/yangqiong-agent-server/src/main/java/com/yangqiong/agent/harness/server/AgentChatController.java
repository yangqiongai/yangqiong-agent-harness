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

import java.util.List;
import java.util.Map;

import com.yangqiong.agent.harness.core.event.ConfirmResult;
import com.yangqiong.agent.harness.core.message.MessageFactory;
import com.yangqiong.agent.harness.engine.AgentRuntimeContext;

import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import reactor.core.publisher.Flux;

/**
 * 智能体对话接口
 * <p>
 * POST /api/chat 以SSE推送对话事件流；当工具调用触发人工审批时，引擎暂停并推送
 * REQUIRE_USER_CONFIRM 事件，客户端可再以 POST /api/chat/resume 携带审批决策
 * HTTP回调恢复执行，实现审批暂停与恢复的服务化闭环。
 * </p>
 * @author yangqiong
 */
@RestController
@RequestMapping("/api")
public class AgentChatController {

    /**
     * 会话管理器
     */
    private final AgentSessionManager sessionManager;

    /**
     * 服务端装配配置
     */
    private final ServerConfig config;

    /**
     * SSE事件映射器
     */
    private final AgentEventSseMapper sseMapper;

    /**
     * 以会话管理器与装配配置构建对话接口
     * @param sessionManager
     * @param config
     * @param sseMapper
     */
    public AgentChatController(AgentSessionManager sessionManager, ServerConfig config,
                               AgentEventSseMapper sseMapper) {
        this.sessionManager = sessionManager;
        this.config = config;
        this.sseMapper = sseMapper;
    }

    /**
     * 服务化对话入口，SSE推送事件流
     * @param request
     * @return
     */
    @PostMapping(value = "/chat", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<String>> chat(@RequestBody ChatRequest request) {
        String sessionId = orDefault(request.sessionId(), config.defaultSessionId());
        String scopeId = orDefault(request.scopeId(), config.defaultScopeId());
        AgentRuntimeContext context = context(scopeId, sessionId, request.userId());
        return sessionManager.sessionFor(scopeId, sessionId)
                .runtime()
                .stream(List.of(MessageFactory.createUserMessage(request.message())), context)
                .map(sseMapper::toSse)
                .onErrorResume(error -> Flux.just(sseMapper.errorSse(error)));
    }

    /**
     * 审批暂停后的恢复入口，HTTP回调续跑事件流
     * @param request
     * @return
     */
    @PostMapping(value = "/chat/resume", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<String>> resume(@RequestBody ResumeRequest request) {
        String sessionId = orDefault(request.sessionId(), config.defaultSessionId());
        String scopeId = orDefault(request.scopeId(), config.defaultScopeId());
        AgentRuntimeContext context = context(scopeId, sessionId, request.userId());
        List<ConfirmDecision> decisions = request.confirmations() == null
                ? List.of() : request.confirmations();
        List<ConfirmResult> confirmResults = decisions.stream()
                .map(ConfirmDecision::toCore)
                .toList();
        return sessionManager.sessionFor(scopeId, sessionId)
                .runtime()
                .resume(confirmResults, context)
                .map(sseMapper::toSse)
                .onErrorResume(error -> Flux.just(sseMapper.errorSse(error)));
    }

    /**
     * 健康检查
     * @return
     */
    @GetMapping(value = "/health", produces = MediaType.APPLICATION_JSON_VALUE)
    public Map<String, Object> health() {
        return Map.of("status", "ok", "sessions", sessionManager.size());
    }

    /**
     * 构建运行时上下文
     * @param scopeId
     * @param sessionId
     * @param userId
     * @return
     */
    private AgentRuntimeContext context(String scopeId, String sessionId, String userId) {
        return AgentRuntimeContext.builder()
                .scopeId(scopeId)
                .sessionId(sessionId)
                .userId(userId)
                .build();
    }

    /**
     * 取缺省值
     * @param value
     * @param defaultValue
     * @return
     */
    private String orDefault(String value, String defaultValue) {
        return (value == null || value.isBlank()) ? defaultValue : value;
    }
}