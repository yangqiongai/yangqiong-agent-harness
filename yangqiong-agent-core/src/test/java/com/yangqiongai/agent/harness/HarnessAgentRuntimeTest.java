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
package com.yangqiongai.agent.harness;

import com.yangqiongai.agent.harness.engine.EngineConfig;
import com.yangqiongai.agent.harness.engine.ReActEngine;
import com.yangqiongai.agent.harness.mcp.McpToolkit;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Agent运行时测试
 * @author yangqiong
 */
class HarnessAgentRuntimeTest {

    @Test
    void shouldCloseMcpToolkitOnClose() {
        ReActEngine engine = mock(ReActEngine.class);
        EngineConfig config = mock(EngineConfig.class);
        McpToolkit toolkit = mock(McpToolkit.class);
        when(toolkit.close()).thenReturn(Mono.empty());
        HarnessAgentRuntime runtime = new HarnessAgentRuntime(engine, config, toolkit);

        StepVerifier.create(runtime.close()).verifyComplete();

        verify(toolkit).close();
    }

    @Test
    void shouldCompleteCloseWithoutToolkit() {
        ReActEngine engine = mock(ReActEngine.class);
        EngineConfig config = mock(EngineConfig.class);
        HarnessAgentRuntime runtime = new HarnessAgentRuntime(engine, config);

        StepVerifier.create(runtime.close()).verifyComplete();
    }

    @Test
    void shouldCloseIdempotently() {
        ReActEngine engine = mock(ReActEngine.class);
        EngineConfig config = mock(EngineConfig.class);
        McpToolkit toolkit = mock(McpToolkit.class);
        when(toolkit.close()).thenReturn(Mono.empty());
        HarnessAgentRuntime runtime = new HarnessAgentRuntime(engine, config, toolkit);

        StepVerifier.create(runtime.close()).verifyComplete();
        StepVerifier.create(runtime.close()).verifyComplete();

        verify(toolkit, times(2)).close();
    }
}
