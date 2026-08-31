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
package com.yangqiong.agent.harness;

import java.util.List;

import com.yangqiong.agent.harness.core.AgentRuntime;
import com.yangqiong.agent.harness.core.event.AgentEvent;
import com.yangqiong.agent.harness.core.message.AgentMessage;
import com.yangqiong.agent.harness.core.model.AgentChatResponse;
import com.yangqiong.agent.harness.core.model.AgentGenerateOptions;
import com.yangqiong.agent.harness.core.model.AgentModel;
import com.yangqiong.agent.harness.engine.AgentLoop;
import com.yangqiong.agent.harness.engine.EngineContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 运行时定制器与自定义执行循环注入测试
 * @author yangqiong
 */
class RuntimeCustomizerAndAgentLoopTest {

    /**
     * 测试桩模型
     */
    private static final class StubModel implements AgentModel {

        @Override
        public AgentChatResponse generate(List<AgentMessage> messages, List<Map<String, Object>> tools, AgentGenerateOptions options) {
            return null;
        }

        @Override
        public Flux<AgentChatResponse> stream(List<AgentMessage> messages, List<Map<String, Object>> tools, AgentGenerateOptions options) {
            return Flux.empty();
        }
    }

    /**
     * 记录执行循环是否被调用的桩实现
     */
    private static final class RecordingLoop implements AgentLoop {

        private boolean invoked;

        @Override
        public Flux<AgentEvent> run(List<AgentMessage> inputs, EngineContext context) {
            invoked = true;
            return Flux.empty();
        }
    }

    /**
     * 验证addCustomizer在build前被回调并生效
     */
    @Test
    @DisplayName("addCustomizer在build装配前回调")
    void addCustomizer_shouldBeAppliedBeforeBuild() {
        RecordingLoop loop = new RecordingLoop();
        AgentRuntime runtime = new HarnessRuntimeBuilder()
                .model(new StubModel())
                .addCustomizer(builder -> builder.agentLoop(loop))
                .build();
        assertThat(runtime).isNotNull();
        runtime.stream(List.of(com.yangqiong.agent.harness.core.message.MessageFactory.createUserMessage("hi")),
                com.yangqiong.agent.harness.engine.AgentRuntimeContext.empty()).blockLast();
        assertThat(loop.invoked).isTrue();
    }
}
