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

import java.util.List;
import java.util.Map;

import com.yangqiongai.agent.harness.core.message.AgentToolResultBlock;
import com.yangqiongai.agent.harness.core.tool.AgentToolCallParam;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

/**
 * Web检索工具测试
 * @author yangqiong
 */
class WebSearchToolTest {

    @Test
    void normalHitContainsFenceAndSource() {
        WebSearchTool tool = new WebSearchTool((query, topK) -> List.of(
                new WebSearchResult("Java百科", "https://example.com/java",
                        "Java是面向对象的编程语言", 0.95),
                new WebSearchResult("Spring指南", "https://example.com/spring",
                        "Spring框架用于依赖注入", 0.7)));
        AgentToolCallParam param = new AgentToolCallParam(Map.of("query", "Java", "topK", 3));

        StepVerifier.create(tool.callAsync(param))
                .assertNext(block -> {
                    assertThat(block.isError()).isFalse();
                    String text = block.getTextContent();
                    assertThat(text).contains("<untrusted_content>");
                    assertThat(text).contains("Java是面向对象的编程语言");
                    assertThat(text).contains("[来源: Java百科](https://example.com/java)");
                    assertThat(text).contains("[来源: Spring指南]");
                })
                .verifyComplete();
    }

    @Test
    void providerNotConfiguredReturnsError() {
        WebSearchTool tool = new WebSearchTool(null);
        AgentToolCallParam param = new AgentToolCallParam(Map.of("query", "Java"));

        StepVerifier.create(tool.callAsync(param))
                .assertNext(block -> {
                    assertThat(block.isError()).isTrue();
                    assertThat(block.getTextContent()).contains("未配置WebSearchProvider");
                })
                .verifyComplete();
    }

    @Test
    void providerExceptionReturnsError() {
        WebSearchTool tool = new WebSearchTool((query, topK) -> {
            throw new IllegalStateException("搜索服务异常");
        });
        AgentToolCallParam param = new AgentToolCallParam(Map.of("query", "Java"));

        StepVerifier.create(tool.callAsync(param))
                .assertNext(block -> {
                    assertThat(block.isError()).isTrue();
                    assertThat(block.getTextContent()).contains("联网搜索失败");
                })
                .verifyComplete();
    }

    @Test
    void missingQueryReturnsError() {
        WebSearchTool tool = new WebSearchTool((query, topK) -> List.of());
        AgentToolCallParam param = new AgentToolCallParam(Map.of("other", "x"));

        StepVerifier.create(tool.callAsync(param))
                .assertNext(block -> {
                    assertThat(block.isError()).isTrue();
                    assertThat(block.getTextContent()).contains("query");
                })
                .verifyComplete();
    }

    @Test
    void longResultsAreTruncated() {
        WebSearchTool tool = new WebSearchTool((query, topK) ->
                List.of(new WebSearchResult("大结果", "https://example.com/big", "x".repeat(20000), 1.0)));
        AgentToolCallParam param = new AgentToolCallParam(Map.of("query", "Java"));

        Mono<AgentToolResultBlock> result = tool.callAsync(param);

        StepVerifier.create(result)
                .assertNext(block -> {
                    assertThat(block.isError()).isFalse();
                    assertThat(block.getTextContent()).contains("已截断");
                })
                .verifyComplete();
    }
}
