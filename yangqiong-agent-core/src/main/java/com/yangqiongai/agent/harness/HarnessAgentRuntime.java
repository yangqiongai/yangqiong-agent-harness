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
package com.yangqiongai.agent.harness;

import com.yangqiongai.agent.harness.engine.AgentLoop;
import com.yangqiongai.agent.harness.engine.EngineConfig;
import com.yangqiongai.agent.harness.engine.EngineContext;
import com.yangqiongai.agent.harness.engine.AgentRuntimeContext;
import com.yangqiongai.agent.harness.mcp.McpToolkit;
import com.yangqiongai.agent.harness.memory.HistoryMerger;
import com.yangqiongai.agent.harness.core.AgentRuntime;
import com.yangqiongai.agent.harness.core.event.AgentEvent;
import com.yangqiongai.agent.harness.core.event.ClarificationAnswer;
import com.yangqiongai.agent.harness.core.event.ConfirmResult;
import com.yangqiongai.agent.harness.core.message.AgentMessage;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * Agent运行时
 * @author yangqiong
 */
public class HarnessAgentRuntime implements AgentRuntime {

    /**
     * Agent执行循环（默认ReActEngine，可替换自定义范式）
     */
    private final AgentLoop engine;

    /**
     * 引擎配置（构建时固化）
     */
    private final EngineConfig config;

    /**
     * MCP工具集（可空，非空时close级联关闭全部MCP连接）
     */
    private final McpToolkit mcpToolkit;

    public HarnessAgentRuntime(AgentLoop engine, EngineConfig config) {
        this(engine, config, null);
    }

    public HarnessAgentRuntime(AgentLoop engine, EngineConfig config, McpToolkit mcpToolkit) {
        this.engine = engine;
        this.config = config;
        this.mcpToolkit = mcpToolkit;
    }

    /**
     * 同步调用Agent
     * @param inputs
     * @param context
     * @return
     */
    @Override
    public Mono<AgentMessage> call(List<AgentMessage> inputs, AgentRuntimeContext context) {
        List<AgentMessage> inputsWithHistory = HistoryMerger.merge(inputs, context);
        EngineContext ctx = config.toEngineContext(context);
        return engine.call(inputsWithHistory, ctx);
    }

    /**
     * 流式输出Agent事件
     * @param inputs
     * @param context
     * @return
     */
    @Override
    public Flux<AgentEvent> stream(List<AgentMessage> inputs, AgentRuntimeContext context) {
        List<AgentMessage> inputsWithHistory = HistoryMerger.merge(inputs, context);
        EngineContext ctx = config.toEngineContext(context);
        return engine.run(inputsWithHistory, ctx);
    }

    /**
     * 获取Agent名称
     * @return
     */
    @Override
    public String getName() {
        return config.getAgentName();
    }

    /**
     * 获取引擎配置（包级可见，供构建装配测试校验工具与中间件注册）
     * @return
     */
    EngineConfig getEngineConfig() {
        return config;
    }

    /**
     * 恢复因人工确认暂停的Agent执行
     * @param confirmResults
     * @param context
     * @return
     */
    @Override
    public Flux<AgentEvent> resume(List<ConfirmResult> confirmResults, AgentRuntimeContext context) {
        EngineContext ctx = config.toEngineContext(context);
        return engine.resume(confirmResults, ctx);
    }

    /**
     * 恢复因用户澄清暂停的Agent执行
     * @param answers
     * @param context
     * @return
     */
    @Override
    public Flux<AgentEvent> resumeWithClarification(List<ClarificationAnswer> answers, AgentRuntimeContext context) {
        EngineContext ctx = config.toEngineContext(context);
        return engine.resumeWithClarification(answers, ctx);
    }

    /**
     * 从持久化检查点续跑，委托引擎恢复最近落盘快照并续接会话
     * @param context
     * @return
     */
    @Override
    public Flux<AgentEvent> resumeFromCheckpoint(AgentRuntimeContext context) {
        EngineContext ctx = config.toEngineContext(context);
        return engine.restoreFromCheckpoint(ctx);
    }

    /**
     * 关闭运行时，级联关闭MCP连接等底层资源（幂等，重复调用安全）
     * @return
     */
    @Override
    public Mono<Void> close() {
        return mcpToolkit != null ? mcpToolkit.close() : Mono.empty();
    }
}
