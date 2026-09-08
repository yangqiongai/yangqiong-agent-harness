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
package com.yangqiongai.agent.harness.durable.serialization;

import java.util.List;

import com.fasterxml.jackson.core.type.TypeReference;
import com.yangqiongai.agent.harness.core.message.AgentMessage;

/**
 * 消息序列化器
 * <p>
 * AgentMessage 与 JSON 互转，供检查点/会话摘要等共享存储落库复用。
 * </p>
 * @author yangqiong
 */
public final class AgentMessageSerializer {

    private AgentMessageSerializer() {
    }

    /**
     * 序列化消息为JSON
     * @param message
     * @return
     */
    public static String toJson(AgentMessage message) {
        try {
            return HarnessObjectMapper.get().writeValueAsString(message);
        } catch (Exception e) {
            throw new IllegalStateException("序列化AgentMessage失败", e);
        }
    }

    /**
     * 反序列化JSON为消息
     * @param json
     * @return
     */
    public static AgentMessage fromJson(String json) {
        try {
            return HarnessObjectMapper.get().readValue(json, AgentMessage.class);
        } catch (Exception e) {
            throw new IllegalStateException("反序列化AgentMessage失败", e);
        }
    }

    /**
     * 序列化消息列表为JSON
     * @param messages
     * @return
     */
    public static String listToJson(List<AgentMessage> messages) {
        try {
            return HarnessObjectMapper.get().writeValueAsString(messages);
        } catch (Exception e) {
            throw new IllegalStateException("序列化AgentMessage列表失败", e);
        }
    }

    /**
     * 反序列化JSON为消息列表
     * @param json
     * @return
     */
    public static List<AgentMessage> listFromJson(String json) {
        try {
            return HarnessObjectMapper.get().readValue(json, new TypeReference<List<AgentMessage>>() {
            });
        } catch (Exception e) {
            throw new IllegalStateException("反序列化AgentMessage列表失败", e);
        }
    }
}
