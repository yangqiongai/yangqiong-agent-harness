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
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * MCP JSON-RPC报文构建与解析
 * @author yangqiong
 */
public final class McpJsonRpc {

    /**
     * 共享JSON映射器
     */
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private McpJsonRpc() {
    }

    /**
     * 获取共享JSON映射器
     * @return
     */
    public static ObjectMapper mapper() {
        return MAPPER;
    }

    /**
     * 构建JSON-RPC请求报文
     * @param id
     * @param method
     * @param params
     * @return
     */
    public static ObjectNode request(long id, String method, ObjectNode params) {
        ObjectNode node = MAPPER.createObjectNode();
        node.put("jsonrpc", "2.0");
        node.put("id", id);
        node.put("method", method);
        if (params != null) {
            node.set("params", params);
        }
        return node;
    }

    /**
     * 构建JSON-RPC通知报文（无id，无需响应）
     * @param method
     * @return
     */
    public static ObjectNode notification(String method) {
        return notification(method, null);
    }

    /**
     * 构建带参数的JSON-RPC通知报文（无id，无需响应）
     * @param method
     * @param params
     * @return
     */
    public static ObjectNode notification(String method, ObjectNode params) {
        ObjectNode node = MAPPER.createObjectNode();
        node.put("jsonrpc", "2.0");
        node.put("method", method);
        if (params != null) {
            node.set("params", params);
        }
        return node;
    }

    /**
     * 判断报文是否为JSON-RPC响应（含id且有result或error字段）
     * @param node
     * @return
     */
    public static boolean isResponse(JsonNode node) {
        return node != null && node.has("id")
                && (node.has("result") || node.has("error"))
                && !node.has("method");
    }

    /**
     * 从响应报文提取result，error响应转为异常
     * @param response
     * @param method
     * @return
     */
    public static JsonNode extractResult(JsonNode response, String method) {
        JsonNode error = response.get("error");
        if (error != null && !error.isNull()) {
            int code = error.path("code").asInt(-32603);
            String message = error.path("message").asText("MCP调用失败");
            throw new McpRpcException(code, method, method + "调用失败：" + message + "（code=" + code + "）");
        }
        JsonNode result = response.get("result");
        return result != null ? result : MAPPER.createObjectNode();
    }

    /**
     * SSE事件累积解析器，逐行喂入，事件块结束时产出原始事件
     * @author yangqiong
     */
    public static final class SseEventAccumulator {

        private final List<String> dataLines = new ArrayList<>();

        private String eventName;

        /**
         * 喂入一行SSE文本，若事件块结束则返回原始事件
         * @param line
         * @return
         */
        public Optional<SseEvent> feed(String line) {
            if (line == null) {
                return Optional.empty();
            }
            if (line.endsWith("\r")) {
                line = line.substring(0, line.length() - 1);
            }
            if (line.isEmpty()) {
                return flush();
            }
            if (line.startsWith(":")) {
                return Optional.empty();
            }
            int colonIndex = line.indexOf(':');
            String field = colonIndex < 0 ? line : line.substring(0, colonIndex);
            String value = colonIndex < 0 ? "" : line.substring(colonIndex + 1);
            if (value.startsWith(" ")) {
                value = value.substring(1);
            }
            switch (field) {
                case "data" -> dataLines.add(value);
                case "event" -> eventName = value;
                default -> {
                    // id/retry等字段忽略
                }
            }
            return Optional.empty();
        }

        private Optional<SseEvent> flush() {
            if (dataLines.isEmpty()) {
                return Optional.empty();
            }
            String payload = String.join("\n", dataLines);
            String event = eventName;
            dataLines.clear();
            eventName = null;
            return Optional.of(new SseEvent(event, payload));
        }
    }

    /**
     * SSE原始事件
     * @param event 事件名称，可能为null
     * @param data 数据载荷文本
     * @author yangqiong
     */
    public record SseEvent(String event, String data) {

        /**
         * 将数据载荷解析为JSON节点，失败返回空
         * @return
         */
        public Optional<JsonNode> parseJson() {
            try {
                return Optional.of(MAPPER.readTree(data));
            } catch (Exception e) {
                return Optional.empty();
            }
        }
    }
}
