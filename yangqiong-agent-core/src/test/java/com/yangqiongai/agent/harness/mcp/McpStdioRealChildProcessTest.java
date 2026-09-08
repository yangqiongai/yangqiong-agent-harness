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

import java.io.File;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * MCP STDIO真实子进程传输验证，使用本机node启动临时MCP假服务器，覆盖握手与工具调用
 * @author yangqiong
 */
class McpStdioRealChildProcessTest {

    /**
     * 验证与node子进程的真实stdio握手与调用链路
     */
    @Test
    void realSubprocessHandshakeAndCall() {
        String node = findNode();
        Assumptions.assumeTrue(node != null, "未找到node，跳过真实子进程测试");
        DefaultMcpClient client = DefaultMcpClient.create(
                McpServerConfig.builder("fake", McpTransportType.STDIO)
                        .command(node).args(List.of("-e", fakeServerJs()))
                        .initTimeout(Duration.ofSeconds(10)).requestTimeout(Duration.ofSeconds(10))
                        .build());
        try {
            client.initialize().block();
            assertThat(client.getNegotiatedProtocolVersion()).isEqualTo("2025-06-18");
            List<McpToolDescriptor> tools = client.listTools().block();
            assertThat(tools).extracting(McpToolDescriptor::getName).contains("echo");
            McpToolCallResult result = client.callTool("echo", Map.of("message", "hi")).block();
            assertThat(result).isNotNull();
            assertThat(result.extractText()).isEqualTo("ok");
        } finally {
            client.close().block();
        }
    }

    private String fakeServerJs() {
        return "const rl=require('readline').createInterface({input:process.stdin});"
                + "rl.on('line',l=>{let m;try{m=JSON.parse(l)}catch(e){return}let o={jsonrpc:'2.0',id:m.id};"
                + "if(m.method==='initialize')o.result={protocolVersion:'2025-06-18',capabilities:{},serverInfo:{name:'fake',version:'1'}};"
                + "else if(m.method==='tools/list')o.result={tools:[{name:'echo',description:'e',inputSchema:{type:'object'}}]};"
                + "else if(m.method==='tools/call')o.result={content:[{type:'text',text:'ok'}]};else o.result={};"
                + "process.stdout.write(JSON.stringify(o)+'\\n');});";
    }

    private String findNode() {
        for (String path : List.of(
                "D:\\nodejs\\node\\node.exe",
                "C:\\Program Files\\nodejs\\node.exe",
                "C:\\Program Files (x86)\\nodejs\\node.exe")) {
            if (new File(path).exists()) {
                return path;
            }
        }
        return null;
    }
}