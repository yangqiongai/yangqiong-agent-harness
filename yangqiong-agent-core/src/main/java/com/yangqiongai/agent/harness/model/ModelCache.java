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
package com.yangqiongai.agent.harness.model;

import java.util.List;
import java.util.Map;

import com.yangqiongai.agent.harness.core.message.AgentMessage;
import com.yangqiongai.agent.harness.core.model.AgentChatResponse;
import com.yangqiongai.agent.harness.core.model.AgentGenerateOptions;

/**
 * 模型缓存
 * <p>
 * 缓存LLM调用结果，相同输入（消息+工具Schema+生成选项）命中缓存时直接返回，
 * 避免重复调用模型，降低成本与延迟。
 * </p>
 * @author yangqiong
 */
public interface ModelCache {

    /**
     * 查询缓存
     * @param messages
     * @param toolSchemas
     * @param options
     * @return 命中返回缓存响应列表，未命中返回null
     */
    List<AgentChatResponse> get(List<AgentMessage> messages, List<Map<String, Object>> toolSchemas,
                                 AgentGenerateOptions options);

    /**
     * 写入缓存
     * @param messages
     * @param toolSchemas
     * @param options
     * @param responses
     */
    void put(List<AgentMessage> messages, List<Map<String, Object>> toolSchemas,
             AgentGenerateOptions options, List<AgentChatResponse> responses);

    /**
     * 清空缓存
     */
    void clear();

    /**
     * 获取缓存条目数
     * @return
     */
    int size();
}
