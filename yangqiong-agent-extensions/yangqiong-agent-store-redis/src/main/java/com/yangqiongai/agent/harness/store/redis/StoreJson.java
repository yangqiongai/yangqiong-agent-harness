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
package com.yangqiongai.agent.harness.store.redis;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.yangqiongai.agent.harness.durable.AgentRunRecord;
import com.yangqiongai.agent.harness.durable.AgentRunRecord.StateTransition;
import com.yangqiongai.agent.harness.durable.AgentRunState;
import com.yangqiongai.agent.harness.durable.ApprovalRecord;
import com.yangqiongai.agent.harness.durable.ApprovalRecord.ApprovalState;
import com.yangqiongai.agent.harness.durable.serialization.HarnessObjectMapper;

/**
 * 运行与审批记录编解码
 * <p>
 * harness 的 AgentRunRecord、ApprovalRecord 未声明 Jackson 创建注解，
 * 无法经由默认映射器反序列化，需在此手工完成对象与 JSON 互转。
 * </p>
 * @author yangqiong
 */
final class StoreJson {

    /**
     * 共享映射器
     */
    private static final ObjectMapper MAPPER = HarnessObjectMapper.get();

    private StoreJson() {
    }

    /**
     * 运行记录序列化为JSON
     * @param record
     * @return
     */
    static String toRun(AgentRunRecord record) {
        ObjectNode node = MAPPER.createObjectNode();
        node.put("runId", record.getRunId());
        node.put("scopeId", record.getScopeId());
        node.put("sessionId", record.getSessionId());
        node.put("userId", record.getUserId());
        node.put("agentName", record.getAgentName());
        node.put("createdAt", record.getCreatedAt());
        node.put("updatedAt", record.getUpdatedAt());
        node.put("version", record.getVersion());
        node.put("state", record.getState().name());
        if (record.getError() != null) {
            node.put("error", record.getError());
        }
        ArrayNode transitions = node.putArray("transitions");
        for (StateTransition t : record.getTransitions()) {
            ObjectNode tn = transitions.addObject();
            tn.put("state", t.getState().name());
            tn.put("timestamp", t.getTimestamp());
            if (t.getReason() != null) {
                tn.put("reason", t.getReason());
            }
        }
        return write(node);
    }

    /**
     * JSON反序列化为运行记录
     * @param json
     * @return
     */
    static AgentRunRecord fromRun(String json) {
        try {
            JsonNode node = MAPPER.readTree(json);
            AgentRunRecord record = new AgentRunRecord(
                    text(node, "runId"), text(node, "scopeId"), text(node, "sessionId"),
                    text(node, "userId"), text(node, "agentName"));
            setField(record, "updatedAt", node.path("updatedAt").asLong());
            setField(record, "version", node.path("version").asLong());
            setField(record, "state", AgentRunState.valueOf(node.path("state").asText()));
            String error = optText(node, "error");
            if (error != null) {
                setField(record, "error", error);
            }
            List<StateTransition> transitions = new ArrayList<>();
            for (JsonNode t : node.path("transitions")) {
                transitions.add(newTransition(AgentRunState.valueOf(t.path("state").asText()),
                        t.path("timestamp").asLong(), optText(t, "reason")));
            }
            setField(record, "transitions", transitions);
            return record;
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("反序列化AgentRunRecord失败", e);
        }
    }

    /**
     * 审批记录序列化为JSON
     * @param record
     * @return
     */
    static String toApproval(ApprovalRecord record) {
        ObjectNode node = MAPPER.createObjectNode();
        node.put("approvalId", record.getApprovalId());
        node.put("runId", record.getRunId());
        node.put("toolCallId", record.getToolCallId());
        node.put("toolName", record.getToolName());
        node.put("scopeId", record.getScopeId());
        node.put("approverId", record.getApproverId());
        node.put("state", record.getState().name());
        if (record.getReason() != null) {
            node.put("reason", record.getReason());
        }
        node.put("createdAt", record.getCreatedAt());
        return write(node);
    }

    /**
     * JSON反序列化为审批记录
     * @param json
     * @return
     */
    static ApprovalRecord fromApproval(String json) {
        try {
            JsonNode node = MAPPER.readTree(json);
            return new ApprovalRecord(
                    text(node, "approvalId"), text(node, "runId"), text(node, "toolCallId"),
                    text(node, "toolName"), text(node, "scopeId"), text(node, "approverId"),
                    ApprovalState.valueOf(node.path("state").asText()), optText(node, "reason"),
                    node.path("createdAt").asLong());
        } catch (Exception e) {
            throw new IllegalStateException("反序列化ApprovalRecord失败", e);
        }
    }

    /**
     * 序列化对象节点
     * @param node
     * @return
     */
    private static String write(ObjectNode node) {
        try {
            return MAPPER.writeValueAsString(node);
        } catch (Exception e) {
            throw new IllegalStateException("序列化记录失败", e);
        }
    }

    /**
     * 读取文本字段，缺少或为 null 时返回 null
     * @param node
     * @param name
     * @return
     */
    private static String text(JsonNode node, String name) {
        JsonNode field = node.get(name);
        return field == null || field.isNull() ? null : field.asText();
    }

    /**
     * 读取可选文本字段，缺少或为 null 时返回 null
     * @param node
     * @param name
     * @return
     */
    private static String optText(JsonNode node, String name) {
        JsonNode field = node.get(name);
        return field == null || field.isNull() ? null : field.asText();
    }

    /**
     * 反射设置字段值
     * @param target
     * @param name
     * @param value
     */
    private static void setField(Object target, String name, Object value) {
        try {
            Field field = target.getClass().getDeclaredField(name);
            field.setAccessible(true);
            field.set(target, value);
        } catch (Exception e) {
            throw new IllegalStateException("反射设置字段失败: " + name, e);
        }
    }

    /**
     * 反射构造状态迁移记录
     * @param state
     * @param timestamp
     * @param reason
     * @return
     */
    private static StateTransition newTransition(AgentRunState state, long timestamp, String reason) {
        try {
            Constructor<StateTransition> constructor =
                    StateTransition.class.getDeclaredConstructor(AgentRunState.class, long.class, String.class);
            constructor.setAccessible(true);
            return constructor.newInstance(state, timestamp, reason);
        } catch (Exception e) {
            throw new IllegalStateException("构造状态迁移记录失败", e);
        }
    }
}