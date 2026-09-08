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
package com.yangqiongai.agent.harness.model.routing;

import java.util.List;

import com.yangqiongai.agent.harness.core.message.AgentMessage;

/**
 * 模型路由
 * @author yangqiong
 */
public interface ModelRouter {

    /**
     * 按任务类型与输入消息从候选中选择最优模型
     * @param taskType
     * @param inputMessages
     * @param candidates
     * @return
     */
    RouteDecision select(String taskType, List<AgentMessage> inputMessages, List<RouteCandidate> candidates);
}
