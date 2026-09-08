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
package com.yangqiongai.agent.harness.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import com.yangqiongai.agent.harness.core.tool.AgentToolCallParam;
import org.junit.jupiter.api.Test;
import reactor.test.StepVerifier;

/**
 * 网页抓取工具测试
 * @author yangqiong
 */
class WebFetchToolTest {

    @Test
    void normalFetchContainsFence() {
        WebFetchTool tool = new WebFetchTool((url, maxChars) -> "网页正文内容");
        AgentToolCallParam param = new AgentToolCallParam(Map.of("url", "https://example.com/page"));

        StepVerifier.create(tool.callAsync(param))
                .assertNext(block -> {
                    assertThat(block.isError()).isFalse();
                    String text = block.getTextContent();
                    assertThat(text).contains("<untrusted_content>");
                    assertThat(text).contains("网页正文内容");
                })
                .verifyComplete();
    }

    @Test
    void nonHttpProtocolRejected() {
        WebFetchTool tool = new WebFetchTool((url, maxChars) -> "不应被调用");
        AgentToolCallParam param = new AgentToolCallParam(Map.of("url", "file:///etc/passwd"));

        StepVerifier.create(tool.callAsync(param))
                .assertNext(block -> {
                    assertThat(block.isError()).isTrue();
                    assertThat(block.getTextContent()).contains("仅支持http和https协议");
                })
                .verifyComplete();
    }

    @Test
    void fetcherExceptionReturnsError() {
        WebFetchTool tool = new WebFetchTool((url, maxChars) -> {
            throw new IllegalStateException("抓取服务异常");
        });
        AgentToolCallParam param = new AgentToolCallParam(Map.of("url", "https://example.com/page"));

        StepVerifier.create(tool.callAsync(param))
                .assertNext(block -> {
                    assertThat(block.isError()).isTrue();
                    assertThat(block.getTextContent()).contains("网页抓取失败");
                })
                .verifyComplete();
    }

    @Test
    void fetcherNotConfiguredReturnsError() {
        WebFetchTool tool = new WebFetchTool(null);
        AgentToolCallParam param = new AgentToolCallParam(Map.of("url", "https://example.com/page"));

        StepVerifier.create(tool.callAsync(param))
                .assertNext(block -> {
                    assertThat(block.isError()).isTrue();
                    assertThat(block.getTextContent()).contains("未配置WebFetcher");
                })
                .verifyComplete();
    }

    @Test
    void missingUrlReturnsError() {
        WebFetchTool tool = new WebFetchTool((url, maxChars) -> "内容");
        AgentToolCallParam param = new AgentToolCallParam(Map.of("other", "x"));

        StepVerifier.create(tool.callAsync(param))
                .assertNext(block -> {
                    assertThat(block.isError()).isTrue();
                    assertThat(block.getTextContent()).contains("url");
                })
                .verifyComplete();
    }
}
