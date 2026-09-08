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
package com.yangqiongai.agent.harness.rag;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import com.yangqiongai.agent.harness.core.message.AgentToolResultBlock;
import com.yangqiongai.agent.harness.core.tool.AgentToolCallParam;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

/**
 * RAG检索工具测试
 * @author yangqiong
 */
class RetrieverToolTest {

    private RetrieverRegistry registryWithWiki() {
        RetrieverRegistry registry = new RetrieverRegistry();
        registry.register("wiki", (query, topK, filters) -> List.of(
                new RetrievedChunk("Java是面向对象的编程语言", 0.95, "wiki",
                        Map.of("title", "Java百科", "url", "https://example.com/java")),
                new RetrievedChunk("Spring框架用于依赖注入", 0.7, "wiki",
                        Map.of("title", "Spring指南"))));
        return registry;
    }

    @Test
    void normalHitContainsFenceAndSource() {
        RetrieverTool tool = new RetrieverTool(registryWithWiki());
        AgentToolCallParam param = new AgentToolCallParam(
                Map.of("query", "Java", "topK", 3, "source", "wiki"));

        Mono<AgentToolResultBlock> result = tool.callAsync(param);

        StepVerifier.create(result)
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
    void unknownSourceReturnsError() {
        RetrieverTool tool = new RetrieverTool(registryWithWiki());
        AgentToolCallParam param = new AgentToolCallParam(Map.of("query", "Java", "source", "unknown"));

        StepVerifier.create(tool.callAsync(param))
                .assertNext(block -> {
                    assertThat(block.isError()).isTrue();
                    assertThat(block.getTextContent()).contains("未知检索源");
                })
                .verifyComplete();
    }

    @Test
    void missingQueryReturnsError() {
        RetrieverTool tool = new RetrieverTool(registryWithWiki());
        AgentToolCallParam param = new AgentToolCallParam(Map.of("source", "wiki"));

        StepVerifier.create(tool.callAsync(param))
                .assertNext(block -> {
                    assertThat(block.isError()).isTrue();
                    assertThat(block.getTextContent()).contains("query");
                })
                .verifyComplete();
    }

    @Test
    void retrieverExceptionReturnsError() {
        RetrieverRegistry registry = new RetrieverRegistry();
        registry.register("wiki", (query, topK, filters) -> {
            throw new IllegalStateException("检索器异常");
        });
        RetrieverTool tool = new RetrieverTool(registry);
        AgentToolCallParam param = new AgentToolCallParam(Map.of("query", "Java", "source", "wiki"));

        StepVerifier.create(tool.callAsync(param))
                .assertNext(block -> {
                    assertThat(block.isError()).isTrue();
                    assertThat(block.getTextContent()).contains("检索失败");
                })
                .verifyComplete();
    }

    @Test
    void nullInputReturnsError() {
        RetrieverTool tool = new RetrieverTool(registryWithWiki());

        StepVerifier.create(tool.callAsync(null))
                .assertNext(block -> {
                    assertThat(block.isError()).isTrue();
                })
                .verifyComplete();
    }
}