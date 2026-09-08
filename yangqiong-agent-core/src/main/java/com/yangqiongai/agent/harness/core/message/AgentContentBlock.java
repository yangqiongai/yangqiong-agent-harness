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
package com.yangqiongai.agent.harness.core.message;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

/**
 * Agent内容块标记接口
 * <p>
 * 声明多态类型信息，支持内容块序列化到JSON后按类型还原。
 * </p>
 * @author yangqiong
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "type")
@JsonSubTypes({
        @JsonSubTypes.Type(value = AgentTextBlock.class, name = "textBlock"),
        @JsonSubTypes.Type(value = AgentThinkingBlock.class, name = "thinkingBlock"),
        @JsonSubTypes.Type(value = AgentImageBlock.class, name = "imageBlock"),
        @JsonSubTypes.Type(value = AgentToolUseBlock.class, name = "toolUseBlock"),
        @JsonSubTypes.Type(value = AgentToolResultBlock.class, name = "toolResultBlock")
})
public interface AgentContentBlock {
}
