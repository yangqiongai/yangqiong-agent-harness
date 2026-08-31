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
package com.yangqiong.agent.harness.subagent;

import java.util.Collections;
import java.util.List;

import com.yangqiong.agent.harness.subagent.orchestration.SubagentDeclaration;
import com.yangqiong.agent.harness.subagent.orchestration.SubagentEventEmitPolicy;
import com.yangqiong.agent.harness.subagent.orchestration.SubagentResult;
import com.yangqiong.agent.harness.core.AdvancedAgentRuntimeBuilder;
import com.yangqiong.agent.harness.core.AgentRuntime;
import com.yangqiong.agent.harness.engine.AgentRuntimeContext;
import com.yangqiong.agent.harness.core.AgentRuntimeFactory;
import com.yangqiong.agent.harness.core.event.AgentEvent;
import com.yangqiong.agent.harness.core.event.AgentEventType;
import com.yangqiong.agent.harness.core.message.AgentMessage;
import com.yangqiong.agent.harness.core.message.AgentMessageRole;
import com.yangqiong.agent.harness.core.message.AgentTextBlock;
import com.yangqiong.agent.harness.core.middleware.AgentMiddleware;
import com.yangqiong.agent.harness.core.model.AgentModelFactory;
import com.yangqiong.agent.harness.core.skill.AgentSkillBox;
import com.yangqiong.agent.harness.core.tool.AgentToolkit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * 子代理执行器
 * <p>
 * 通过SubagentContext继承父级能力配置，按SubagentDeclaration筛选工具/技能/MCP/中间件，
 * 角色字段（name/systemPrompt/model/maxIters）与执行范式（agentLoop）按声明覆盖，
 * 声明未指定范式时子代理使用默认ReActEngine。
 * 子代理会话使用命名空间隔离：parentSessionId + ">subagent_" + depth + "_" + name
 * </p>
 * @author yangqiong
 */
public class SubagentSpawner {

    private static final Logger log = LoggerFactory.getLogger(SubagentSpawner.class);

    /**
     * 会话命名空间分隔符
     */
    private static final String NAMESPACE_SEPARATOR = ">subagent_";

    /**
     * 运行时工厂
     */
    private final AgentRuntimeFactory runtimeFactory;

    /**
     * 模型工厂（可选，子代理声明指定modelCode时使用）
     */
    private final AgentModelFactory modelFactory;

    /**
     * 父级能力快照（build时捕获）
     */
    private final SubagentContext capabilityContext;

    /**
     * 能力筛选策略
     */
    private final SubagentCapabilityPolicy capabilityPolicy;

    public SubagentSpawner(AgentRuntimeFactory runtimeFactory, AgentModelFactory modelFactory,
                             SubagentContext capabilityContext, SubagentCapabilityPolicy capabilityPolicy) {
        this.runtimeFactory = runtimeFactory;
        this.modelFactory = modelFactory;
        this.capabilityContext = capabilityContext;
        this.capabilityPolicy = capabilityPolicy;
    }

    /**
     * 生成并执行子代理（纯响应式，无阻塞调用）
     * <p>
     * 继承父上下文的userId与中断控制器，sessionId使用命名空间隔离。
     * 能力配置从SubagentContext快照继承，按SubagentDeclaration筛选。
     * </p>
     * @param declaration
     * @param input
     * @param parentCtx
     * @return
     */
    public Mono<SubagentResult> spawn(SubagentDeclaration declaration, String input,
                                        AgentRuntimeContext parentCtx) {
        if (declaration == null) {
            return Mono.just(SubagentResult.failure("unknown", "子代理声明为空", 0));
        }
        if (runtimeFactory == null) {
            log.warn("AgentRuntimeFactory未注入，无法生成子代理: name={}", declaration.getName());
            return Mono.just(SubagentResult.failure(declaration.getName(), "运行时工厂未注入", 0));
        }
        long startTime = System.currentTimeMillis();
        return Mono.defer(() -> {
            AgentRuntime childAgent = buildRuntime(declaration);
            // 会话命名空间隔离：parentSessionId + ">subagent_" + depth + "_" + name
            AgentRuntimeContext childCtx = buildChildContext(parentCtx, declaration.getName());
            AgentMessage userMsg = AgentMessage.builder()
                    .name("user")
                    .role(AgentMessageRole.USER)
                    .content(Collections.singletonList(AgentTextBlock.builder()
                            .text(input != null ? input : "")
                            .build()))
                    .build();
            // 打印子代理完整输入输出（带名称），便于观察编排模式内部沟通细节
            log.debug("子代理[{}] 输入: {}", declaration.getName(), input != null ? input : "");
            return childAgent.call(List.of(userMsg), childCtx)
                    .map(result -> {
                        String output = result != null && result.getTextContent() != null
                                ? result.getTextContent() : "";
                        log.debug("子代理[{}] 输出: {}", declaration.getName(), output);
                        return SubagentResult.success(declaration.getName(), output,
                                System.currentTimeMillis() - startTime);
                    })
                    .defaultIfEmpty(SubagentResult.success(declaration.getName(), "",
                            System.currentTimeMillis() - startTime))
                    .onErrorResume(e -> {
                        log.error("子代理执行失败: name={}", declaration.getName(), e);
                        return Mono.just(SubagentResult.failure(declaration.getName(),
                                e.getMessage() != null ? e.getMessage() : "子代理执行异常",
                                System.currentTimeMillis() - startTime));
                    });
        });
    }

    /**
     * 生成并执行子代理，按事件传播策略返回事件流
     * <p>
     * FULL模式：返回子代理全部事件流，每个事件带parentAgentPath标记冒泡到主代理
     * SUMMARY_ONLY/NONE：将最终结果作为单个COMPLETED事件返回
     * </p>
     * @param declaration
     * @param input
     * @param parentCtx
     * @param eventEmitPolicy
     * @return
     */
    public Flux<AgentEvent> spawnStream(SubagentDeclaration declaration, String input,
                                          AgentRuntimeContext parentCtx,
                                          SubagentEventEmitPolicy eventEmitPolicy) {
        if (eventEmitPolicy == SubagentEventEmitPolicy.FULL) {
            return spawnFull(declaration, input, parentCtx);
        }
        // SUMMARY_ONLY 或 NONE：仅返回最终结果事件
        return spawn(declaration, input, parentCtx)
                .flatMapMany(result -> Flux.just(AgentEvent.of(AgentEventType.COMPLETED,
                        result.isSuccess() ? result.output() : result.error(),
                        buildParentAgentPath(parentCtx, declaration.getName()))));
    }

    /**
     * FULL模式：返回子代理全部事件流，带parentAgentPath标记
     * @param declaration
     * @param input
     * @param parentCtx
     * @return
     */
    private Flux<AgentEvent> spawnFull(SubagentDeclaration declaration, String input,
                                          AgentRuntimeContext parentCtx) {
        if (declaration == null || runtimeFactory == null) {
            return Flux.empty();
        }
        return Flux.defer(() -> {
            AgentRuntime childAgent = buildRuntime(declaration);
            AgentRuntimeContext childCtx = buildChildContext(parentCtx, declaration.getName());
            AgentMessage userMsg = AgentMessage.builder()
                    .name("user")
                    .role(AgentMessageRole.USER)
                    .content(Collections.singletonList(AgentTextBlock.builder()
                            .text(input != null ? input : "")
                            .build()))
                    .build();
            String parentAgentPath = buildParentAgentPath(parentCtx, declaration.getName());
            // 订阅子代理事件流，为每个事件附加parentAgentPath
            return childAgent.stream(List.of(userMsg), childCtx)
                    .map(event -> AgentEvent.of(event.getType(), event.getPayload(), parentAgentPath));
        });
    }

    /**
     * 按声明构建子代理运行时
     * <p>
     * 继承父级能力配置，按SubagentDeclaration筛选工具/技能/MCP/中间件，
     * 角色字段（name/systemPrompt/model/maxIters）与执行范式（agentLoop）按声明覆盖，
     * 声明未指定范式时子代理使用默认ReActEngine。
     * 供spawn及群聊场景注册到消息中枢复用。
     * </p>
     * @param declaration 子代理声明
     * @return 构建完成的子代理运行时
     */
    public AgentRuntime buildRuntime(SubagentDeclaration declaration) {
        AdvancedAgentRuntimeBuilder advancedBuilder = runtimeFactory.createBuilder();
        if (advancedBuilder instanceof RuntimeCapabilityAccessor harnessBuilder) {
            applyCapability(harnessBuilder, declaration);
            if (declaration.getAgentLoop() != null) {
                harnessBuilder.agentLoop(declaration.getAgentLoop());
            }
        }
        advancedBuilder.name(declaration.getName());
        String sysPrompt = declaration.getSystemPrompt() != null && !declaration.getSystemPrompt().isBlank()
                ? declaration.getSystemPrompt()
                : "你是" + declaration.getDescription() + "。请根据输入完成你的专业任务，输出结果。默认使用中文回答。";
        advancedBuilder.systemPrompt(sysPrompt);
        if (declaration.getModelCode() != null && modelFactory != null) {
            advancedBuilder.model(modelFactory.getModel(declaration.getModelCode(), null));
        }
        advancedBuilder.maxIters(declaration.getMaxIterations() != null ? declaration.getMaxIterations() : 10);
        return advancedBuilder.build();
    }

    /**
     * 构建子代理运行时上下文，继承父级scopeId保证租户隔离，sessionId使用命名空间隔离
     * @param parentCtx 父级上下文
     * @param subagentName 子代理名称
     * @return 隔离后的子代理上下文
     */
    private AgentRuntimeContext buildChildContext(AgentRuntimeContext parentCtx, String subagentName) {
        String parentSessionId = parentCtx != null ? parentCtx.getSessionId() : null;
        int depth = parseDepth(parentSessionId);
        String childSessionId = buildNamespacedSessionId(parentSessionId, depth + 1, subagentName);
        return AgentRuntimeContext.builder()
                .scopeId(parentCtx != null ? parentCtx.getScopeId() : null)
                .sessionId(childSessionId)
                .userId(parentCtx != null ? parentCtx.getUserId() : null)
                .interruptControl(parentCtx != null ? parentCtx.getInterruptControl() : null)
                .build();
    }

    /**
     * 构建命名空间隔离的sessionId：parentSessionId + ">subagent_" + depth + "_" + name
     * @param parentSessionId
     * @param depth
     * @param name
     * @return
     */
    private String buildNamespacedSessionId(String parentSessionId, int depth, String name) {
        String base = parentSessionId != null ? parentSessionId : "root";
        return base + NAMESPACE_SEPARATOR + depth + "_" + name;
    }

    /**
     * 构建parentAgentPath标记：parentSessionId + ">subagent_" + depth + "_" + name
     * @param parentCtx
     * @param name
     * @return
     */
    private String buildParentAgentPath(AgentRuntimeContext parentCtx, String name) {
        String parentSessionId = parentCtx != null ? parentCtx.getSessionId() : null;
        int depth = parseDepth(parentSessionId);
        return buildNamespacedSessionId(parentSessionId, depth + 1, name);
    }

    /**
     * 从sessionId解析当前嵌套深度
     * <p>
     * sessionId格式：root>subagent_1_xxx>subagent_2_yyy
     * 深度 = ">subagent_" 出现次数
     * </p>
     * @param sessionId
     * @return 当前深度（0表示主代理）
     */
    public static int parseDepth(String sessionId) {
        if (sessionId == null || sessionId.isEmpty()) {
            return 0;
        }
        int depth = 0;
        int idx = 0;
        while ((idx = sessionId.indexOf(NAMESPACE_SEPARATOR, idx)) != -1) {
            depth++;
            idx += NAMESPACE_SEPARATOR.length();
        }
        return depth;
    }

    /**
     * 应用能力继承：先应用父级能力快照，再按声明筛选工具/技能/MCP/中间件
     * @param builder 子代理构建器
     * @param declaration 子代理声明
     */
    private void applyCapability(RuntimeCapabilityAccessor builder, SubagentDeclaration declaration) {
        if (capabilityContext == null) {
            return;
        }
        capabilityContext.applyTo(builder);
        if (capabilityPolicy == null) {
            return;
        }
        AgentToolkit sourceToolkit = capabilityContext.getToolkit();
        if (sourceToolkit != null) {
            builder.toolkit(capabilityPolicy.filterToolkit(sourceToolkit, declaration));
        }
        AgentSkillBox sourceSkillBox = capabilityContext.getSkillBox();
        if (sourceSkillBox != null) {
            AgentSkillBox filteredSkillBox = capabilityPolicy.filterSkillBox(sourceSkillBox, declaration);
            builder.skillBox(filteredSkillBox);
        }
        List<AgentMiddleware> sourceMiddlewares = capabilityContext.getMiddlewares();
        if (sourceMiddlewares != null) {
            List<AgentMiddleware> filtered = capabilityPolicy.filterMiddlewares(sourceMiddlewares, declaration);
            for (AgentMiddleware mw : filtered) {
                builder.middleware(mw);
            }
        }
    }
}
