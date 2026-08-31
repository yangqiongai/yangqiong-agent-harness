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
package com.yangqiong.agent.harness.mcp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * MCP传输层契约，负责JSON-RPC报文的收发与请求id关联
 * @author yangqiong
 */
public interface McpTransport {

    /**
     * 发送JSON-RPC请求并返回result节点，error响应转为McpRpcException
     * @param method
     * @param params
     * @return
     */
    Mono<JsonNode> call(String method, ObjectNode params);

    /**
     * 发送JSON-RPC通知（无需响应）
     * @param method
     * @return
     */
    Mono<Void> notify(String method);

    /**
     * 发送带参数的JSON-RPC通知，默认退化为无参数通知
     * @param method
     * @param params
     * @return
     */
    default Mono<Void> notify(String method, ObjectNode params) {
        return notify(method);
    }

    /**
     * 服务端主动推送的JSON-RPC通知流（工具/资源变更等，无id报文）
     * 未建立持久流的传输返回空流
     * @return
     */
    default Flux<JsonNode> notifications() {
        return Flux.empty();
    }

    /**
     * 关闭传输并释放底层连接或进程
     * @return
     */
    Mono<Void> close();
}
