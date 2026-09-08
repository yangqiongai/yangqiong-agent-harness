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

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;

import com.yangqiongai.agent.harness.core.AgentRuntime;
import com.yangqiongai.agent.harness.core.interruption.AgentInterruptControl;
import com.yangqiongai.agent.harness.core.interruption.AgentInterruptSource;
import com.yangqiongai.agent.harness.core.message.AgentMessage;
import com.yangqiongai.agent.harness.core.message.AgentMessageRole;
import com.yangqiongai.agent.harness.engine.AgentRuntimeContext;

/**
 * Agent运行时中断控制单元测试
 * @author yangqiong
 */
public class HarnessAgentRuntimeInterruptTest {

    /**
     * 调用interrupt触发USER来源中断，传递用户主动中断消息
     */
    @Test
    void interrupt_触发中断并记录来源与消息() {
        AtomicReference<AgentInterruptSource> recordedSource = new AtomicReference<>();
        AtomicReference<AgentMessage> recordedMessage = new AtomicReference<>();
        AgentInterruptControl control = (source, message) -> {
            recordedSource.set(source);
            recordedMessage.set(message);
        };
        AgentRuntimeContext context = AgentRuntimeContext.builder()
                .interruptControl(control)
                .build();
        // 构造纯代理运行时，仅验证中断控制接线，不触发引擎执行
        AgentRuntime runtime = new HarnessAgentRuntime(null, null);

        runtime.interrupt(context);

        assertThat(recordedSource.get()).isEqualTo(AgentInterruptSource.USER);
        assertThat(recordedMessage.get()).isNotNull();
        assertThat(recordedMessage.get().getRole()).isEqualTo(AgentMessageRole.USER);
    }
}