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
 * MCP远程调用异常
 * @author yangqiong
 */
public class McpRpcException extends RuntimeException {

    /**
     * JSON-RPC错误码
     */
    private final int code;

    /**
     * 触发异常的方法名
     */
    private final String method;

    public McpRpcException(int code, String method, String message) {
        super(message);
        this.code = code;
        this.method = method;
    }

    /**
     * 获取JSON-RPC错误码
     * @return
     */
    public int getCode() {
        return code;
    }

    /**
     * 获取触发异常的方法名
     * @return
     */
    public String getMethod() {
        return method;
    }
}
