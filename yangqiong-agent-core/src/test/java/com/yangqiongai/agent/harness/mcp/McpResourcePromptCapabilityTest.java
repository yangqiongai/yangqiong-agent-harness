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
package com.yangqiongai.agent.harness.mcp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;
import reactor.test.StepVerifier;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * MCP资源/提示词/能力协商/服务端通知测试
 * @author yangqiong
 */
class McpResourcePromptCapabilityTest {

    /**
     * 验证资源发现、资源读取与资源模板发现
     * @return
     */
    @Test
    void discoverAndReadResources() {
        FakeTransport transport = new FakeTransport();
        ObjectNode resourceList = json();
        resourceList.putArray("resources").addObject()
                .put("uri", "file:///readme.md")
                .put("name", "readme")
                .put("description", "项目说明")
                .put("mimeType", "text/markdown");
        transport.respond("resources/list", resourceList);
        ObjectNode templateList = json();
        templateList.putArray("resourceTemplates").addObject()
                .put("uriTemplate", "file:///{path}")
                .put("name", "任意文件");
        transport.respond("resources/templates/list", templateList);
        ObjectNode resourceRead = json();
        resourceRead.putArray("contents").addObject()
                .put("uri", "file:///readme.md")
                .put("mimeType", "text/markdown")
                .put("text", "# 标题");
        transport.respond("resources/read", resourceRead);

        DefaultMcpClient client = client(transport);

        StepVerifier.create(client.listResources())
                .assertNext(list -> {
                    assertThat(list).hasSize(1);
                    assertThat(list.get(0).uri()).isEqualTo("file:///readme.md");
                })
                .verifyComplete();

        StepVerifier.create(client.listResourceTemplates())
                .assertNext(list -> assertThat(list).hasSize(1))
                .verifyComplete();

        StepVerifier.create(client.readResource("file:///readme.md"))
                .assertNext(content -> {
                    assertThat(content.text()).isEqualTo("# 标题");
                    assertThat(content.mimeType()).isEqualTo("text/markdown");
                })
                .verifyComplete();
    }

    /**
     * 验证提示词发现与展开
     * @return
     */
    @Test
    void discoverAndGetPrompts() {
        FakeTransport transport = new FakeTransport();
        ObjectNode promptList = json();
        promptList.putArray("prompts").addObject()
                .put("name", "greet")
                .put("description", "问候")
                .putArray("arguments").addObject()
                    .put("name", "who")
                    .put("required", true);
        transport.respond("prompts/list", promptList);
        ObjectNode promptGet = json();
        promptGet.put("description", "问候");
        promptGet.putArray("messages").addObject()
                .put("role", "user")
                .putObject("content")
                    .put("type", "text")
                    .put("text", "你好，{who}");
        transport.respond("prompts/get", promptGet);

        DefaultMcpClient client = client(transport);

        StepVerifier.create(client.listPrompts())
                .assertNext(list -> {
                    assertThat(list).hasSize(1);
                    assertThat(list.get(0).name()).isEqualTo("greet");
                    assertThat(list.get(0).arguments().get(0).required()).isTrue();
                })
                .verifyComplete();

        StepVerifier.create(client.getPrompt("greet", Map.of("who", "世界")))
                .assertNext(result -> {
                    assertThat(result.messages()).hasSize(1);
                    assertThat(result.messages().get(0).role()).isEqualTo("user");
                })
                .verifyComplete();
    }

    /**
     * 验证握手能力协商、协议版本与服务端能力记录
     * @return
     */
    @Test
    void negotiateCapabilitiesAndVersion() {
        FakeTransport transport = new FakeTransport();
        ObjectNode init = json();
        init.put("protocolVersion", "2024-11-05");
        init.putObject("capabilities")
                .put("resources", true)
                .put("prompts", true)
                .put("tools", true);
        transport.respond("initialize", init);

        McpServerConfig config = McpServerConfig.builder("srv", McpTransportType.STREAMABLE_HTTP)
                .endpoint("http://localhost")
                .clientCapabilities(McpClientCapabilities.all())
                .build();
        DefaultMcpClient client = new DefaultMcpClient(config, transport);

        StepVerifier.create(client.initialize()).verifyComplete();

        assertThat(client.getNegotiatedProtocolVersion()).isEqualTo("2024-11-05");
        assertThat(transport.sentInitializeCapabilities().has("roots")).isTrue();
        assertThat(transport.sentInitializeCapabilities().has("sampling")).isTrue();
        assertThat(client.getServerCapabilities()).isNotNull();
        assertThat(client.getServerCapabilities().supports("resources")).isTrue();
        assertThat(client.getServerCapabilities().supports("prompts")).isTrue();
        assertThat(transport.notificationsSent()).contains("notifications/initialized");
    }

    /**
     * 验证服务端主动通知可被订阅转发
     * @return
     */
    @Test
    void forwardServerNotifications() {
        FakeTransport transport = new FakeTransport();
        DefaultMcpClient client = client(transport);
        ObjectNode change = json();
        change.putObject("params").putArray("changedUris").add("file:///readme.md");
        change.put("method", "notifications/resources/updated");

        StepVerifier.create(client.notifications())
                .then(() -> transport.emit(change))
                .expectNextMatches(node -> "notifications/resources/updated".equals(node.path("method").asText()))
                .thenCancel()
                .verify();
    }

    private static DefaultMcpClient client(FakeTransport transport) {
        McpServerConfig config = McpServerConfig.builder("srv", McpTransportType.STREAMABLE_HTTP)
                .endpoint("http://localhost")
                .build();
        return new DefaultMcpClient(config, transport);
    }

    private static ObjectNode json() {
        return McpJsonRpc.mapper().createObjectNode();
    }

    /**
     * 脚本化假传输
     * @author yangqiong
     */
    private static final class FakeTransport implements McpTransport {

        private final Map<String, JsonNode> responses = new HashMap<>();

        private final Sinks.Many<JsonNode> notifs = Sinks.many().multicast().onBackpressureBuffer();

        private final List<String> notificationsSent = new java.util.ArrayList<>();

        private ObjectNode initializeParams;

        @Override
        public Mono<JsonNode> call(String method, ObjectNode params) {
            if ("initialize".equals(method)) {
                this.initializeParams = params;
            }
            JsonNode response = responses.get(method);
            return response != null ? Mono.just(response) : Mono.empty();
        }

        @Override
        public Mono<Void> notify(String method) {
            notificationsSent.add(method);
            return Mono.empty();
        }

        @Override
        public Flux<JsonNode> notifications() {
            return notifs.asFlux();
        }

        @Override
        public Mono<Void> close() {
            return Mono.empty();
        }

        void respond(String method, JsonNode result) {
            responses.put(method, result);
        }

        void emit(JsonNode notification) {
            notifs.tryEmitNext(notification);
        }

        JsonNode sentInitializeCapabilities() {
            return initializeParams != null ? initializeParams.get("capabilities") : null;
        }

        List<String> notificationsSent() {
            return notificationsSent;
        }
    }
}