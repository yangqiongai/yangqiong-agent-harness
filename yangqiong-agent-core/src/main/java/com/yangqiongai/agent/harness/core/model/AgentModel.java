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
package com.yangqiongai.agent.harness.core.model;

import com.yangqiongai.agent.harness.core.message.AgentMessage;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.Map;

/**
 * Agent模型
 * @author yangqiong
 */
public interface AgentModel {

    /**
     * 同步生成模型响应，内部使用默认超时阻塞流式结果
     * @param messages
     * @param tools
     * @param options
     * @return
     */
    AgentChatResponse generate(List<AgentMessage> messages, List<Map<String, Object>> tools, AgentGenerateOptions options);

    /**
     * 流式生成模型响应
     * @param messages
     * @param tools
     * @param options
     * @return
     */
    Flux<AgentChatResponse> stream(List<AgentMessage> messages, List<Map<String, Object>> tools, AgentGenerateOptions options);

    /**
     * 获取模型名称，用于可观测事件标识，默认返回实现类简单名
     * @return
     */
    default String modelName() {
        return getClass().getSimpleName();
    }
}
