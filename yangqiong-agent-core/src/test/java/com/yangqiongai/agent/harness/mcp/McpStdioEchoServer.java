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
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;

/**
 * MCP stdio测试回显服务，按行读取JSON-RPC请求并回送应答
 * @author yangqiong
 */
public final class McpStdioEchoServer {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private McpStdioEchoServer() {
    }

    /**
     * 测试服务入口，method为fail时回送错误应答，为hang时不应答，为noisy时先回送非JSON行
     * @param args
     * @throws Exception
     */
    public static void main(String[] args) throws Exception {
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(System.in, StandardCharsets.UTF_8));
                PrintWriter writer = new PrintWriter(System.out, true, StandardCharsets.UTF_8)) {
            String line = reader.readLine();
            while (line != null) {
                respond(line, writer);
                line = reader.readLine();
            }
        }
    }

    private static void respond(String line, PrintWriter writer) throws Exception {
        JsonNode request;
        try {
            request = MAPPER.readTree(line);
        } catch (Exception e) {
            return;
        }
        JsonNode id = request.get("id");
        if (id == null || id.isNull()) {
            return;
        }
        String method = request.path("method").asText("");
        if ("hang".equals(method)) {
            return;
        }
        if ("fail".equals(method)) {
            writer.println("{\"jsonrpc\":\"2.0\",\"id\":" + id
                    + ",\"error\":{\"code\":-32000,\"message\":\"boom\"}}");
            return;
        }
        if ("noisy".equals(method)) {
            writer.println("not-a-json-line");
        }
        writer.println("{\"jsonrpc\":\"2.0\",\"id\":" + id
                + ",\"result\":{\"method\":\"" + method + "\"}}");
    }
}
