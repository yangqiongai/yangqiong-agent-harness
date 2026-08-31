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
package com.yangqiong.agent.harness.durable.serialization;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

/**
 * 序列化统一对象映射器
 * <p>
 * 集中配置 Jackson 规则，供消息/检查点/内容块等共享存储场景复用，
 * 保证多态内容块与 Builder 反序列化行为全局一致。
 * </p>
 * @author yangqiong
 */
public final class HarnessObjectMapper {

    /**
     * 全局共享映射器（线程安全，可并发读写）
     */
    private static final ObjectMapper MAPPER = build();

    private HarnessObjectMapper() {
    }

    private static ObjectMapper build() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        mapper.configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false);
        return mapper;
    }

    /**
     * 获取全局映射器
     * @return
     */
    public static ObjectMapper get() {
        return MAPPER;
    }
}
