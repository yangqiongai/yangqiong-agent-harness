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
package com.yangqiong.agent.harness.spi;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import com.yangqiong.agent.harness.HarnessRuntimeBuilder;
import com.yangqiong.agent.harness.core.AgentRuntime;
import com.yangqiong.agent.harness.core.event.AgentEventType;
import com.yangqiong.agent.harness.core.message.AgentContentBlock;
import com.yangqiong.agent.harness.core.message.AgentMessage;
import com.yangqiong.agent.harness.core.message.AgentTextBlock;
import com.yangqiong.agent.harness.core.model.AgentChatResponse;
import com.yangqiong.agent.harness.core.model.AgentGenerateOptions;
import com.yangqiong.agent.harness.core.model.AgentModel;
import com.yangqiong.agent.harness.engine.AgentRuntimeContext;
import com.yangqiong.agent.harness.event.EventBus;
import com.yangqiong.agent.harness.core.message.MessageFactory;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;

/**
 * 扩展点运行时接线测试
 * <p>
 * 验证P1模型调用装饰链与P2事件监听器注册中心在真实运行时下的接线：
 * 自定义调用层被包裹进链并能收到模型请求，运行事件自动广播给监听器。
 * </p>
 * @author yangqiong
 */
class HarnessExtensionPointsIntegrationTest {

    /**
     * P1：自定义模型调用层包裹进链并承接真实模型请求
     */
    @Test
    void customModelCallerLayerIsAppliedAndInvoked() {
        AtomicInteger buildCount = new AtomicInteger();

        AgentRuntime runtime = new HarnessRuntimeBuilder()
                .name("p1-agent")
                .model(new BaseStubModel())
                .addModelCallerLayer(m -> {
                    buildCount.incrementAndGet();
                    return new CountingLayer(m);
                })
                .build();

        runOnce(runtime);

        assertThat(buildCount.get()).as("构建时应插入自定义调用层").isEqualTo(1);
        assertThat(CountingLayer.totalCalls.get())
                .as("运行时应通过自定义调用层发起模型请求").isGreaterThan(0);
    }

    /**
     * P2：运行事件通过显式EventBus自动广播给监听器
     */
    @Test
    void runtimeEventsBroadcastToRegisteredListener() {
        AtomicInteger received = new AtomicInteger();
        EventBus bus = new EventBus();
        bus.register(event -> {
            if (event.getType() == AgentEventType.AGENT_RESULT) {
                received.incrementAndGet();
            }
        });

        AgentRuntime runtime = new HarnessRuntimeBuilder()
                .name("p2-agent")
                .model(new BaseStubModel())
                .eventBus(bus)
                .build();

        runOnce(runtime);

        assertThat(received.get()).as("运行产生的AGENT_RESULT事件应广播给监听器").isGreaterThan(0);
    }

    /**
     * P2：addEventListener懒创建EventBus并完成运行时广播接线
     */
    @Test
    void addEventListenerLazilyCreatesBusAndWiresBroadcast() {
        AtomicInteger received = new AtomicInteger();
        AgentRuntime runtime = new HarnessRuntimeBuilder()
                .name("p2-lazy-agent")
                .model(new BaseStubModel())
                .addEventListener(event -> received.incrementAndGet())
                .build();

        runOnce(runtime);

        assertThat(received.get()).as("懒创建EventBus后运行事件应广播").isGreaterThan(0);
    }

    /**
     * 执行单轮运行并阻塞等待事件流结束
     * @param runtime
     */
    private void runOnce(AgentRuntime runtime) {
        AgentRuntimeContext context = AgentRuntimeContext.builder().build();
        List<AgentMessage> inputs = List.of(MessageFactory.createUserMessage("你好，请简单回复"));
        runtime.stream(inputs, context).collectList().block(Duration.ofSeconds(20));
    }

    /**
     * 计数组装层
     */
    private static final class CountingLayer implements AgentModel {

        /**
         * 全局层调用计数
         */
        private static final AtomicInteger totalCalls = new AtomicInteger();

        /**
         * 被装饰的内层模型
         */
        private final AgentModel delegate;

        CountingLayer(AgentModel delegate) {
            this.delegate = delegate;
        }

        @Override
        public AgentChatResponse generate(List<AgentMessage> messages, List<Map<String, Object>> tools,
                                          AgentGenerateOptions options) {
            totalCalls.incrementAndGet();
            return delegate.generate(messages, tools, options);
        }

        @Override
        public Flux<AgentChatResponse> stream(List<AgentMessage> messages, List<Map<String, Object>> tools,
                                              AgentGenerateOptions options) {
            totalCalls.incrementAndGet();
            return delegate.stream(messages, tools, options);
        }
    }

    /**
     * 基础桩模型：接收文本并返回简单回复
     */
    private static final class BaseStubModel implements AgentModel {

        @Override
        public AgentChatResponse generate(List<AgentMessage> messages, List<Map<String, Object>> tools,
                                          AgentGenerateOptions options) {
            return textResponse();
        }

        @Override
        public Flux<AgentChatResponse> stream(List<AgentMessage> messages, List<Map<String, Object>> tools,
                                              AgentGenerateOptions options) {
            return Flux.just(textResponse());
        }

        private AgentChatResponse textResponse() {
            List<AgentContentBlock> content = Collections.singletonList(
                    AgentTextBlock.builder().text("收到").build());
            return new AgentChatResponse(content, null);
        }
    }
}