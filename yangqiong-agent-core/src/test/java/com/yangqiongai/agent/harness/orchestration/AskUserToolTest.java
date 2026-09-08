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
package com.yangqiongai.agent.harness.orchestration;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import com.yangqiongai.agent.harness.core.message.AgentToolResultBlock;
import com.yangqiongai.agent.harness.core.tool.AgentToolCallParam;
import com.yangqiongai.agent.harness.subagent.orchestration.AskUserTool;
import org.junit.jupiter.api.Test;

import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

/**
 * 向用户提问工具测试
 * @author yangqiong
 */
class AskUserToolTest {

    @Test
    void normalQuestionReturnsClarificationBlock() {
        AskUserTool tool = new AskUserTool();
        AgentToolCallParam param = new AgentToolCallParam(
                Map.of("question", "请告诉我项目预算上限？"));

        Mono<AgentToolResultBlock> result = tool.callAsync(param);

        StepVerifier.create(result)
                .assertNext(block -> {
                    assertThat(block.isError()).isFalse();
                    assertThat(block.isClarificationRequest()).isTrue();
                    assertThat(block.getTextContent()).isEqualTo("请告诉我项目预算上限？");
                })
                .verifyComplete();
    }

    @Test
    void missingQuestionReturnsError() {
        AskUserTool tool = new AskUserTool();
        AgentToolCallParam param = new AgentToolCallParam(Map.of("other", "x"));

        StepVerifier.create(tool.callAsync(param))
                .assertNext(block -> {
                    assertThat(block.isError()).isTrue();
                    assertThat(block.getTextContent()).contains("question");
                })
                .verifyComplete();
    }

    @Test
    void blankQuestionReturnsError() {
        AskUserTool tool = new AskUserTool();
        AgentToolCallParam param = new AgentToolCallParam(Map.of("question", "  "));

        StepVerifier.create(tool.callAsync(param))
                .assertNext(block -> {
                    assertThat(block.isError()).isTrue();
                    assertThat(block.getTextContent()).contains("question");
                })
                .verifyComplete();
    }

    @Test
    void nullInputReturnsError() {
        AskUserTool tool = new AskUserTool();

        StepVerifier.create(tool.callAsync(null))
                .assertNext(block -> {
                    assertThat(block.isError()).isTrue();
                    assertThat(block.getTextContent()).contains("参数");
                })
                .verifyComplete();
    }
}