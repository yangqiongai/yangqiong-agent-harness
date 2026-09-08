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
package com.yangqiongai.agent.harness.tool;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.yangqiongai.agent.harness.engine.EngineContext;
import com.yangqiongai.agent.harness.engine.MiddlewareChain;
import com.yangqiongai.agent.harness.engine.DurableExecutionTracker;
import com.yangqiongai.agent.harness.core.message.MessageFactory;
import com.yangqiongai.agent.harness.permission.PermissionEngine;
import com.yangqiongai.agent.harness.permission.ToolPolicyGate;
import com.yangqiongai.agent.harness.engine.AgentRuntimeContext;
import com.yangqiongai.agent.harness.config.AgentPermissionDecision;
import com.yangqiongai.agent.harness.core.HarnessAgentRuntimeBuilder;
import com.yangqiongai.agent.harness.core.message.AgentMessage;
import com.yangqiongai.agent.harness.core.message.AgentTextBlock;
import com.yangqiongai.agent.harness.core.message.AgentToolResultBlock;
import com.yangqiongai.agent.harness.core.message.AgentToolUseBlock;
import com.yangqiongai.agent.harness.core.tool.AgentTool;
import com.yangqiongai.agent.harness.core.tool.AgentToolCallParam;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * 工具执行器
 * @author yangqiong
 */
public class ToolExecutor {

    private static final Logger log = LoggerFactory.getLogger(ToolExecutor.class);

    /**
     * 工具箱
     */
    private final HarnessToolkit toolkit;

    /**
     * 中间件链
     */
    private final MiddlewareChain middlewareChain;

    /**
     * 权限引擎
     */
    private final PermissionEngine permissionEngine;

    /**
     * 工具白名单（仅允许调用的工具名称集合，null或空时不限制）
     */
    private final Set<String> allowedTools;

    /**
     * 工具黑名单（禁止调用的工具名称集合，null或空时不限制）
     */
    private final Set<String> deniedTools;

    /**
     * 统一权限策略门（可选，非null时收敛权限判定唯一出口并留审计）
     */
    private volatile ToolPolicyGate policyGate;

    /**
     * 工具执行记录存储（可选，非null时同幂等键重复调用复用首次结果）
     */
    private volatile ToolExecutionStore ledger;

    public ToolExecutor(HarnessToolkit toolkit) {
        this(toolkit, null, null, null, null);
    }

    public ToolExecutor(HarnessToolkit toolkit, MiddlewareChain middlewareChain,
                         PermissionEngine permissionEngine) {
        this(toolkit, middlewareChain, permissionEngine, null, null);
    }

    public ToolExecutor(HarnessToolkit toolkit, MiddlewareChain middlewareChain,
                         PermissionEngine permissionEngine,
                         Set<String> allowedTools, Set<String> deniedTools) {
        this.toolkit = toolkit;
        this.middlewareChain = middlewareChain;
        this.permissionEngine = permissionEngine;
        this.allowedTools = allowedTools;
        this.deniedTools = deniedTools;
    }

    /**
     * 注入统一权限策略门
     * @param policyGate
     * @return
     */
    public ToolExecutor policyGate(ToolPolicyGate policyGate) {
        this.policyGate = policyGate;
        return this;
    }

    /**
     * 注入工具执行记录存储（分布式幂等去重）
     * @param ledger
     * @return
     */
    public ToolExecutor ledger(ToolExecutionStore ledger) {
        this.ledger = ledger;
        return this;
    }

    /**
     * 执行单个工具调用
     * @param toolUse
     * @param context
     * @param engineContext
     * @return
     */
    public Mono<AgentMessage> executeTool(AgentToolUseBlock toolUse, AgentRuntimeContext context,
                                           EngineContext engineContext) {
        return executeToolWithRetry(toolUse, context, engineContext, 0);
    }

    /**
     * 带重试的工具调用执行
     * @param toolUse
     * @param context
     * @param engineContext
     * @param attempt
     * @return
     */
    private Mono<AgentMessage> executeToolWithRetry(AgentToolUseBlock toolUse, AgentRuntimeContext context,
                                                     EngineContext engineContext, int attempt) {
        String toolName = toolUse.getToolName();

        // 解析工具实例，供权限引擎使用工具声明的只读属性精确判定
        AgentTool tool = findTool(toolName, engineContext);
        if (tool == null) {
            AgentToolResultBlock errorResult = AgentToolResultBlock.error(
                    toolUse.getToolUseId(), "工具不存在: " + toolName);
            return Mono.just(MessageFactory.createToolMessage(errorResult));
        }

        // 权限判定：统一策略门优先（收敛唯一出口并留审计，携带入参供AI审批等精细判定），未注入时回退既有三段判定
        if (policyGate != null) {
            AgentPermissionDecision decision = policyGate.evaluate(toolName, toolUse.getToolUseId(),
                    context != null ? context.getScopeId() : null, currentRunId(context), toolUse.getInput());
            if (decision == AgentPermissionDecision.DENY) {
                AgentToolResultBlock errorResult = AgentToolResultBlock.error(
                        toolUse.getToolUseId(), "权限拒绝: " + toolName);
                return Mono.just(MessageFactory.createToolMessage(errorResult));
            }
            if (decision == AgentPermissionDecision.ASK) {
                AgentToolResultBlock pendingResult = AgentToolResultBlock.error(
                        toolUse.getToolUseId(), "等待人工确认: " + toolName);
                return Mono.just(MessageFactory.createToolMessage(pendingResult));
            }
        } else {
            if (permissionEngine != null) {
                AgentPermissionDecision decision = permissionEngine.evaluate(toolName, tool);
                if (decision == AgentPermissionDecision.DENY) {
                    AgentToolResultBlock errorResult = AgentToolResultBlock.error(
                            toolUse.getToolUseId(), "权限拒绝: " + toolName);
                    return Mono.just(MessageFactory.createToolMessage(errorResult));
                }
                if (decision == AgentPermissionDecision.ASK) {
                    AgentToolResultBlock pendingResult = AgentToolResultBlock.error(
                            toolUse.getToolUseId(), "等待人工确认: " + toolName);
                    return Mono.just(MessageFactory.createToolMessage(pendingResult));
                }
            }

            if (deniedTools != null && deniedTools.contains(toolName)) {
                AgentToolResultBlock errorResult = AgentToolResultBlock.error(
                        toolUse.getToolUseId(), "工具被黑名单禁止: " + toolName);
                return Mono.just(MessageFactory.createToolMessage(errorResult));
            }
            if (allowedTools != null && !allowedTools.isEmpty() && !allowedTools.contains(toolName)) {
                AgentToolResultBlock errorResult = AgentToolResultBlock.error(
                        toolUse.getToolUseId(), "工具不在白名单中: " + toolName);
                return Mono.just(MessageFactory.createToolMessage(errorResult));
            }
        }

        Map<String, Object> input = toolUse.getInput();
        if (middlewareChain != null) {
            input = middlewareChain.applyOnToolCall(toolName, input, context);
        }

        // 执行前校验参数是否符合工具Schema定义
        String validationError = ToolInputValidator.validate(tool.getParameters(), input);
        if (validationError != null) {
            AgentToolResultBlock errorResult = AgentToolResultBlock.error(
                    toolUse.getToolUseId(),
                    "参数校验失败: " + toolName + " -> " + validationError);
            return Mono.just(MessageFactory.createToolMessage(errorResult));
        }

        AgentToolCallParam param = new AgentToolCallParam(input,
                context != null ? context.getScopeId() : null,
                context != null ? context.getUserId() : null);

        // 幂等去重：同幂等键已完成时直接复用首次结果（恢复场景副作用零重复）
        String idempotencyKey = tool.getIdempotencyKey(param);
        if (ledger != null && idempotencyKey != null && ledger.isCompleted(idempotencyKey)) {
            AgentToolResultBlock cached = ledger.getResult(idempotencyKey);
            if (cached != null) {
                return Mono.just(MessageFactory.createToolMessage(
                        cached.withToolUseId(toolUse.getToolUseId())));
            }
        }

        // 工具调用超时控制，防止单个卡住的工具阻塞整个ReAct循环
        Mono<AgentToolResultBlock> resultMono = tool.callAsync(param);
        Duration toolTimeout = engineContext != null ? engineContext.getToolCallTimeout() : null;
        if (toolTimeout != null && !toolTimeout.isZero() && !toolTimeout.isNegative()) {
            resultMono = resultMono.timeout(toolTimeout);
        }
        return resultMono
                .map(result -> middlewareChain != null
                        ? middlewareChain.applyOnToolResult(toolName, result, context)
                        : result)
                .map(result -> truncateResultIfNeeded(result, engineContext))
                .map(result -> {
                    if (ledger != null && idempotencyKey != null) {
                        ledger.record(idempotencyKey, result);
                    }
                    return result;
                })
                .map(result -> result.withToolUseId(toolUse.getToolUseId()))
                .map(MessageFactory::createToolMessage)
                .onErrorResume(e -> handleToolFailure(toolUse, tool, context, engineContext, attempt, e));
    }

    /**
     * 读取运行上下文中的运行ID（供策略门审计关联）
     * @param context
     * @return
     */
    private String currentRunId(AgentRuntimeContext context) {
        Object runId = context != null
                ? context.get(DurableExecutionTracker.ATTR_RUN_ID) : null;
        return runId != null ? runId.toString() : null;
    }

    /**
     * 工具结果超出字符上限时截断并附加截断标记，防止大结果撑爆上下文窗口
     * @param result
     * @param engineContext
     * @return
     */
    private AgentToolResultBlock truncateResultIfNeeded(AgentToolResultBlock result, EngineContext engineContext) {
        if (engineContext == null) {
            return result;
        }
        int maxChars = engineContext.getMaxToolResultChars();
        if (maxChars <= 0) {
            return result;
        }
        String text = result.getTextContent();
        if (text.length() <= maxChars) {
            return result;
        }
        String truncated = text.substring(0, maxChars)
                + "\n[结果已截断，原始长度: " + text.length() + " 字符]";
        if (result.isError()) {
            return AgentToolResultBlock.error(truncated);
        }
        return AgentToolResultBlock.of(List.of(AgentTextBlock.builder().text(truncated).build()));
    }

    /**
     * 根据失败策略处理工具调用失败
     * <p>
     * 带副作用工具（非只读）默认禁自动重试，防止重试放大副作用；
     * 只读工具按RETRY策略在上限内重试。
     * </p>
     * @param toolUse
     * @param tool
     * @param context
     * @param engineContext
     * @param attempt
     * @param error
     * @return
     */
    private Mono<AgentMessage> handleToolFailure(AgentToolUseBlock toolUse, AgentTool tool,
                                                  AgentRuntimeContext context, EngineContext engineContext,
                                                  int attempt, Throwable error) {
        String toolName = toolUse.getToolName();
        int maxRetries = engineContext != null ? engineContext.getMaxToolRetries() : 0;
        HarnessAgentRuntimeBuilder.ToolFailureStrategy strategy =
                engineContext != null ? engineContext.getToolFailureStrategy() : null;
        boolean sideEffect = tool == null || !tool.isReadOnly();

        // RETRY策略：只读工具未达上限则重试，副作用工具禁自动重试
        if (strategy == HarnessAgentRuntimeBuilder.ToolFailureStrategy.RETRY
                && attempt < maxRetries && !sideEffect) {
            log.warn("工具调用失败，准备重试: tool={}, attempt={}/{}", toolName, attempt + 1, maxRetries, error);
            return executeToolWithRetry(toolUse, context, engineContext, attempt + 1);
        }
        if (strategy == HarnessAgentRuntimeBuilder.ToolFailureStrategy.RETRY && sideEffect && attempt < maxRetries) {
            log.warn("副作用工具失败跳过自动重试（幂等键可由工具执行记录兜底）: tool={}, risk={}",
                    toolName, tool != null ? tool.getRiskLevel() : "unknown");
        }

        // ABORT策略：向上抛错由ReActEngine onErrorResume捕获
        if (strategy == HarnessAgentRuntimeBuilder.ToolFailureStrategy.ABORT) {
            log.error("工具调用失败，中止Agent: tool={}", toolName, error);
            return Mono.error(error);
        }

        // 默认与SKIP策略：记录错误结果继续
        log.error("工具调用失败: tool={}", toolName, error);
        AgentToolResultBlock errorResult = AgentToolResultBlock.error(
                toolUse.getToolUseId(),
                error.getMessage() != null ? error.getMessage() : "工具调用异常");
        if (middlewareChain != null) {
            errorResult = middlewareChain.applyOnToolResult(toolName, errorResult, context);
        }
        return Mono.just(MessageFactory.createToolMessage(errorResult));
    }

    /**
     * 按名称查找工具，优先查引擎上下文（含动态工具），其次查静态工具箱
     * @param toolName
     * @param engineContext
     * @return
     */
    private AgentTool findTool(String toolName, EngineContext engineContext) {
        if (engineContext != null) {
            AgentTool tool = engineContext.findTool(toolName);
            if (tool != null) {
                return tool;
            }
        }
        return toolkit != null ? toolkit.find(toolName) : null;
    }

    /**
     * 批量执行工具调用，按配置的并发数并行执行
     * @param toolCalls
     * @param context
     * @param engineContext
     * @return
     */
    public Mono<List<AgentMessage>> executeTools(List<AgentToolUseBlock> toolCalls, AgentRuntimeContext context,
                                                   EngineContext engineContext) {
        if (toolCalls == null || toolCalls.isEmpty()) {
            return Mono.just(new ArrayList<>());
        }
        int concurrency = engineContext != null && engineContext.getMaxConcurrentToolCalls() > 0
                ? engineContext.getMaxConcurrentToolCalls() : 1;
        return Flux.fromIterable(toolCalls)
                .flatMap(toolUse -> executeTool(toolUse, context, engineContext), concurrency)
                .collectList();
    }
}
