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

/**
 * MCP传输类型
 * @author yangqiong
 */
public enum McpTransportType {

    /**
     * 流式HTTP传输（规范推荐，单POST请求-响应，响应可为JSON或SSE封装）
     */
    STREAMABLE_HTTP,

    /**
     * 旧版SSE传输（双向：GET建立事件流接收响应，POST发送请求；规范已标记弃用）
     */
    SSE,

    /**
     * 标准输入输出传输（子进程行分隔JSON-RPC）
     */
    STDIO
}
