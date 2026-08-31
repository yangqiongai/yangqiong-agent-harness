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

import java.util.List;
import java.util.Map;

import com.yangqiong.agent.harness.core.message.AgentTextBlock;
import com.yangqiong.agent.harness.core.message.AgentToolResultBlock;
import com.yangqiong.agent.harness.core.tool.AgentTool;
import com.yangqiong.agent.harness.core.tool.AgentToolCallParam;
import reactor.core.publisher.Mono;

/**
 * SPI工具提供者测试实现
 * @author yangqiong
 */
public class TestToolProvider implements AgentToolProvider {

    @Override
    public List<AgentTool> provideTools() {
        return List.of(new AgentTool() {

            @Override
            public String getName() {
                return "spi_test_tool";
            }

            @Override
            public String getDescription() {
                return "SPI自动发现测试工具";
            }

            @Override
            public Map<String, Object> getParameters() {
                return Map.of("type", "object", "properties", Map.of());
            }

            @Override
            public Mono<AgentToolResultBlock> callAsync(AgentToolCallParam param) {
                return Mono.just(AgentToolResultBlock.of(List.of(
                        AgentTextBlock.builder().text("spi tool result").build())));
            }
        });
    }
}