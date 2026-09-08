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

import com.yangqiongai.agent.harness.durable.AgentCheckpoint;

/**
 * 检查点序列化器
 * <p>
 * AgentCheckpoint 与 JSON 互转，供共享检查点存储落库复用。
 * </p>
 * @author yangqiong
 */
public final class AgentCheckpointSerializer {

    private AgentCheckpointSerializer() {
    }

    /**
     * 序列化检查点为JSON
     * @param checkpoint
     * @return
     */
    public static String toJson(AgentCheckpoint checkpoint) {
        try {
            return HarnessObjectMapper.get().writeValueAsString(checkpoint);
        } catch (Exception e) {
            throw new IllegalStateException("序列化AgentCheckpoint失败", e);
        }
    }

    /**
     * 反序列化JSON为检查点
     * @param json
     * @return
     */
    public static AgentCheckpoint fromJson(String json) {
        try {
            return HarnessObjectMapper.get().readValue(json, AgentCheckpoint.class);
        } catch (Exception e) {
            throw new IllegalStateException("反序列化AgentCheckpoint失败", e);
        }
    }
}
