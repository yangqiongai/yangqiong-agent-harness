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
package com.yangqiongai.agent.harness.example.springboot;

import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.yangqiongai.agent.harness.core.AgentRuntime;
import com.yangqiongai.agent.harness.engine.AgentRuntimeContext;
import com.yangqiongai.agent.harness.core.message.MessageFactory;

import reactor.core.publisher.Mono;

/**
 * Agent对话接口
 * @author yangqiong
 */
@RestController
@RequestMapping("/api/agent")
public class AgentController {

    /**
     * 由Starter自动装配的Agent运行时
     */
    @Autowired
    private AgentRuntime agentRuntime;

    /**
     * 发起一次Agent对话
     * @param request
     * @return
     */
    @PostMapping("/chat")
    public Mono<Map<String, Object>> chat(@RequestBody ChatRequest request) {
        return agentRuntime.call(
                List.of(MessageFactory.createUserMessage(request.message())),
                AgentRuntimeContext.empty()
        ).map(reply -> Map.of("reply", reply.getTextContent()));
    }

    /**
     * 对话请求体
     * @param message
     */
    public record ChatRequest(String message) {
    }
}
