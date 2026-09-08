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
package com.yangqiongai.agent.harness.spi;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import com.yangqiongai.agent.harness.core.message.AgentContentBlock;
import com.yangqiongai.agent.harness.core.message.AgentMessage;
import com.yangqiongai.agent.harness.core.message.AgentTextBlock;
import com.yangqiongai.agent.harness.core.model.AgentChatResponse;
import com.yangqiongai.agent.harness.core.model.AgentGenerateOptions;
import com.yangqiongai.agent.harness.core.model.AgentModel;
import reactor.core.publisher.Flux;

/**
 * SPI测试桩模型
 * @author yangqiong
 */
public class TestAgentModel implements AgentModel {

    @Override
    public AgentChatResponse generate(List<AgentMessage> messages, List<Map<String, Object>> tools,
                                        AgentGenerateOptions options) {
        return createResponse();
    }

    @Override
    public Flux<AgentChatResponse> stream(List<AgentMessage> messages, List<Map<String, Object>> tools,
                                            AgentGenerateOptions options) {
        return Flux.just(createResponse());
    }

    private AgentChatResponse createResponse() {
        List<AgentContentBlock> blocks = Collections.singletonList(
                AgentTextBlock.builder().text("spi模型返回").build());
        return new AgentChatResponse(blocks, null);
    }
}