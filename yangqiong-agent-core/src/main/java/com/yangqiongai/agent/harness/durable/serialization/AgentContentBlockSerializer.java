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
import com.yangqiongai.agent.harness.core.message.AgentContentBlock;

/**
 * 内容块序列化器
 * <p>
 * 多态内容块与 JSON 互转，供工具执行记录/检查点等共享存储复用。
 * </p>
 * @author yangqiong
 */
public final class AgentContentBlockSerializer {

    private AgentContentBlockSerializer() {
    }

    /**
     * 序列化内容块为JSON
     * @param block
     * @return
     */
    public static String toJson(AgentContentBlock block) {
        try {
            return HarnessObjectMapper.get().writerFor(AgentContentBlock.class).writeValueAsString(block);
        } catch (Exception e) {
            throw new IllegalStateException("序列化AgentContentBlock失败", e);
        }
    }

    /**
     * 反序列化JSON为内容块
     * @param json
     * @return
     */
    public static AgentContentBlock fromJson(String json) {
        try {
            return HarnessObjectMapper.get().readValue(json, AgentContentBlock.class);
        } catch (Exception e) {
            throw new IllegalStateException("反序列化AgentContentBlock失败", e);
        }
    }

    /**
     * 序列化内容块列表为JSON
     * @param blocks
     * @return
     */
    public static String listToJson(List<AgentContentBlock> blocks) {
        try {
            return HarnessObjectMapper.get()
                    .writerFor(new TypeReference<List<AgentContentBlock>>() {
                    })
                    .writeValueAsString(blocks);
        } catch (Exception e) {
            throw new IllegalStateException("序列化AgentContentBlock列表失败", e);
        }
    }

    /**
     * 反序列化JSON为内容块列表
     * @param json
     * @return
     */
    public static List<AgentContentBlock> listFromJson(String json) {
        try {
            return HarnessObjectMapper.get().readValue(json, new TypeReference<List<AgentContentBlock>>() {
            });
        } catch (Exception e) {
            throw new IllegalStateException("反序列化AgentContentBlock列表失败", e);
        }
    }
}
