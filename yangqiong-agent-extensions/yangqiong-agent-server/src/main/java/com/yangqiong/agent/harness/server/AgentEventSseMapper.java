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

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.yangqiong.agent.harness.core.event.AgentEvent;
import com.yangqiong.agent.harness.core.event.AgentEventType;
import com.yangqiong.agent.harness.core.event.RequireUserConfirmEvent;
import com.yangqiong.agent.harness.core.message.AgentToolUseBlock;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.springframework.http.codec.ServerSentEvent;

/**
 * Agent事件SSE映射
 * <p>
 * 将内核 AgentEvent 平铺为面向客户端的SSE事件：event 字段为事件类型，
 * data 为JSON负载；审批暂停事件额外透出待确认工具清单，供前端发起resume回调。
 * </p>
 * @author yangqiong
 */
public class AgentEventSseMapper {

    /**
     * JSON映射器
     */
    private final ObjectMapper objectMapper;

    /**
     * 构建SSE映射器
     */
    public AgentEventSseMapper() {
        this.objectMapper = new ObjectMapper();
    }

    /**
     * 将事件转换为SSE事件
     * @param event
     * @return
     */
    public ServerSentEvent<String> toSse(AgentEvent event) {
        Map<String, Object> surface = surface(event);
        return ServerSentEvent.<String>builder()
                .event(event.getType().name())
                .data(writeJson(surface))
                .build();
    }

    /**
     * 将异常转换为SSE错误事件
     * @param error
     * @return
     */
    public ServerSentEvent<String> errorSse(Throwable error) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("type", AgentEventType.ERROR.name());
        payload.put("message", error == null ? "unknown error" : error.getMessage());
        return ServerSentEvent.<String>builder()
                .event(AgentEventType.ERROR.name())
                .data(writeJson(payload))
                .build();
    }

    /**
     * 平铺事件为面向客户端的表面负载
     * @param event
     * @return
     */
    Map<String, Object> surface(AgentEvent event) {
        Map<String, Object> surface = new LinkedHashMap<>();
        surface.put("type", event.getType().name());
        Object payload = event.getPayload();
        if (payload == null) {
            return surface;
        }
        if (event.getType() == AgentEventType.REQUIRE_USER_CONFIRM
                && event instanceof RequireUserConfirmEvent confirm) {
            surface.put("pendingConfirmations", pendingConfirmations(confirm));
            return surface;
        }
        surface.put("payload", serialize(payload));
        return surface;
    }

    /**
     * 提取待人工确认的工具调用清单
     * @param confirm
     * @return
     */
    private List<Map<String, Object>> pendingConfirmations(RequireUserConfirmEvent confirm) {
        return confirm.getPendingToolCalls().stream()
                .map(this::toolCallSurface)
                .toList();
    }

    /**
     * 平铺单个待确认工具调用
     * @param call
     * @return
     */
    private Map<String, Object> toolCallSurface(AgentToolUseBlock call) {
        Map<String, Object> surface = new LinkedHashMap<>();
        surface.put("toolCallId", call.getToolUseId());
        surface.put("toolName", call.getToolName());
        return surface;
    }

    /**
     * 序列化事件载荷为JSON对象，序列化失败时退化为原始字符串
     * @param payload
     * @return
     */
    private Object serialize(Object payload) {
        try {
            return objectMapper.readTree(writeJson(payload));
        } catch (JsonProcessingException e) {
            return String.valueOf(payload);
        }
    }

    /**
     * 将对象序列化为JSON字符串，失败时返回友好提示
     * @param value
     * @return
     */
    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            return "{\"type\":\"ERROR\",\"message\":\"事件序列化失败\"}";
        }
    }
}