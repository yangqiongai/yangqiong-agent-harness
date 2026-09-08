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
package com.yangqiongai.agent.harness.store.jdbc;

import java.util.Collections;
import java.util.List;

import com.yangqiongai.agent.harness.core.message.AgentContentBlock;
import com.yangqiongai.agent.harness.core.message.AgentToolUseBlock;
import com.yangqiongai.agent.harness.durable.serialization.AgentContentBlockSerializer;
import com.yangqiongai.agent.harness.durable.serialization.AgentMessageSerializer;
import com.yangqiongai.agent.harness.durable.serialization.HarnessObjectMapper;

/**
 * 检查点字段序列化辅助
 * <p>
 * 复用harness序列化器完成消息、待执行工具调用与已完成ID集合的JSON互转。
 * </p>
 * @author yangqiong
 */
final class AgentMessageSerializerUtil {

    /**
     * JSON映射器
     */
    private static final com.fasterxml.jackson.databind.ObjectMapper MAPPER = HarnessObjectMapper.get();

    private AgentMessageSerializerUtil() {
    }

    /**
     * 消息列表转JSON
     * @param messages
     * @return
     */
    static String messagesToJson(List<com.yangqiongai.agent.harness.core.message.AgentMessage> messages) {
        return AgentMessageSerializer.listToJson(messages);
    }

    /**
     * JSON转消息列表
     * @param json
     * @return
     */
    static List<com.yangqiongai.agent.harness.core.message.AgentMessage> messagesFromJson(String json) {
        return json == null || json.isBlank()
                ? Collections.emptyList() : AgentMessageSerializer.listFromJson(json);
    }

    /**
     * 待执行工具调用列表转JSON
     * @param blocks
     * @return
     */
    static String toolUseToJson(List<AgentToolUseBlock> blocks) {
        if (blocks == null) {
            blocks = Collections.emptyList();
        }
        return AgentContentBlockSerializer.listToJson(blocks.stream().map(b -> (AgentContentBlock) b).toList());
    }

    /**
     * JSON转待执行工具调用列表
     * @param json
     * @return
     */
    static List<AgentToolUseBlock> toolUseFromJson(String json) {
        if (json == null || json.isBlank()) {
            return Collections.emptyList();
        }
        List<AgentContentBlock> blocks = AgentContentBlockSerializer.listFromJson(json);
        return blocks.stream().map(b -> (AgentToolUseBlock) b).toList();
    }

    /**
     * 字符串集合转JSON
     * @param values
     * @return
     */
    static String stringListToJson(List<String> values) {
        if (values == null) {
            values = Collections.emptyList();
        }
        try {
            return MAPPER.writeValueAsString(values);
        } catch (Exception e) {
            throw new IllegalStateException("序列化字符串集合失败", e);
        }
    }

    /**
     * JSON转字符串集合
     * @param json
     * @return
     */
    static List<String> stringListFromJson(String json) {
        if (json == null || json.isBlank()) {
            return Collections.emptyList();
        }
        try {
            List<String> values = MAPPER.readValue(json, MAPPER.getTypeFactory()
                    .constructCollectionType(List.class, String.class));
            return values != null ? values : Collections.emptyList();
        } catch (Exception e) {
            throw new IllegalStateException("反序列化字符串集合失败", e);
        }
    }
}