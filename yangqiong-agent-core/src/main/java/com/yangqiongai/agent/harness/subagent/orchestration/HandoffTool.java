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
package com.yangqiongai.agent.harness.subagent.orchestration;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.yangqiongai.agent.harness.engine.AgentRuntimeContext;
import com.yangqiongai.agent.harness.core.message.AgentMessage;
import com.yangqiongai.agent.harness.core.message.AgentTextBlock;
import com.yangqiongai.agent.harness.core.message.AgentToolResultBlock;
import com.yangqiongai.agent.harness.core.tool.AgentTool;
import com.yangqiongai.agent.harness.core.tool.AgentToolCallParam;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

/**
 * Handoff移交工具
 * @author yangqiong
 */
public class HandoffTool implements AgentTool {

    /**
     * 日志
     */
    private static final Logger log = LoggerFactory.getLogger(HandoffTool.class);

    /**
     * Handoff目标注册表
     */
    private final HandoffRegistry registry;

    /**
     * 父级运行时上下文（运行时注入，用于传播scopeId/sessionId/userId）
     */
    private volatile AgentRuntimeContext parentCtx;

    public HandoffTool(HandoffRegistry registry) {
        this(registry, null);
    }

    public HandoffTool(HandoffRegistry registry, AgentRuntimeContext parentCtx) {
        this.registry = registry;
        this.parentCtx = parentCtx;
    }

    /**
     * 注入父级运行时上下文
     * @param parentCtx
     */
    public void setParentCtx(AgentRuntimeContext parentCtx) {
        this.parentCtx = parentCtx;
    }

    /**
     * 获取工具名称
     * @return
     */
    @Override
    public String getName() {
        return "handoff";
    }

    /**
     * 获取工具描述
     * @return
     */
    @Override
    public String getDescription() {
        return "将对话控制权移交给指定的命名Agent，携带移交上下文与原因，返回目标Agent的最终回复。";
    }

    /**
     * 获取工具参数定义
     * @return
     */
    @Override
    public Map<String, Object> getParameters() {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("type", "object");
        Map<String, Object> properties = new LinkedHashMap<>();
        Map<String, Object> targetProp = new LinkedHashMap<>();
        targetProp.put("type", "string");
        targetProp.put("description", "目标Agent名称，须已注册到HandoffRegistry");
        properties.put("target", targetProp);
        Map<String, Object> reasonProp = new LinkedHashMap<>();
        reasonProp.put("type", "string");
        reasonProp.put("description", "移交原因说明");
        properties.put("reason", reasonProp);
        Map<String, Object> contextProp = new LinkedHashMap<>();
        contextProp.put("type", "object");
        contextProp.put("description", "移交上下文，可为字符串或消息历史列表");
        properties.put("context", contextProp);
        params.put("properties", properties);
        params.put("required", List.of("target"));
        return params;
    }

    /**
     * 异步调用工具
     * @param param
     * @return
     */
    @Override
    public Mono<AgentToolResultBlock> callAsync(AgentToolCallParam param) {
        Map<String, Object> input = param != null ? param.getInput() : null;
        if (input == null) {
            return Mono.just(AgentToolResultBlock.error("handoff参数缺失"));
        }
        Object targetValue = input.get("target");
        String targetName = targetValue != null ? String.valueOf(targetValue) : "";
        if (targetName.isBlank()) {
            return Mono.just(AgentToolResultBlock.error("handoff目标缺失"));
        }
        HandoffTarget target = registry.get(targetName).orElse(null);
        if (target == null) {
            log.warn("未知handoff目标: {}", targetName);
            return Mono.just(AgentToolResultBlock.error("未知handoff目标: " + targetName));
        }
        String reason = input.get("reason") != null ? String.valueOf(input.get("reason")) : null;
        Object contextValue = input.get("context");
        List<AgentMessage> history = extractHistory(contextValue);
        String contextText = buildContextText(contextValue, reason);
        HandoffContextFilter filter = target.filter() != null
                ? target.filter() : HandoffContextFilter.defaultFilter();
        List<AgentMessage> filtered = filter.filter(history, contextText);
        AgentRuntimeContext mergedContext = mergeContext(param);
        return target.runtime().call(filtered, mergedContext)
                .map(message -> AgentToolResultBlock.of(List.of(
                        AgentTextBlock.builder().text(message.getTextContent()).build())))
                .onErrorResume(e -> {
                    log.error("handoff目标执行异常: target={}", targetName, e);
                    return Mono.just(AgentToolResultBlock.error(
                            e.getMessage() != null ? e.getMessage() : "handoff目标执行异常"));
                });
    }

    /**
     * 从上下文参数中提取消息历史
     * @param contextValue
     * @return
     */
    private List<AgentMessage> extractHistory(Object contextValue) {
        if (contextValue instanceof List<?> list) {
            List<AgentMessage> messages = new ArrayList<>();
            for (Object item : list) {
                if (item instanceof AgentMessage message) {
                    messages.add(message);
                }
            }
            return messages;
        }
        return new ArrayList<>();
    }

    /**
     * 构造上下文文本
     * @param contextValue
     * @param reason
     * @return
     */
    private String buildContextText(Object contextValue, String reason) {
        StringBuilder sb = new StringBuilder();
        if (contextValue instanceof String text && !text.isBlank()) {
            sb.append(text);
        }
        if (reason != null && !reason.isBlank()) {
            if (sb.length() > 0) {
                sb.append('\n');
            }
            sb.append(reason);
        }
        return sb.toString();
    }

    /**
     * 合并运行时上下文，传播scopeId/sessionId/userId
     * @param param
     * @return
     */
    private AgentRuntimeContext mergeContext(AgentToolCallParam param) {
        AgentRuntimeContext.Builder builder = AgentRuntimeContext.builder();
        if (param != null && param.getScopeId() != null) {
            builder.scopeId(param.getScopeId());
        } else if (parentCtx != null) {
            builder.scopeId(parentCtx.getScopeId());
        }
        if (parentCtx != null) {
            builder.sessionId(parentCtx.getSessionId());
        }
        if (param != null && param.getUserId() != null) {
            builder.userId(param.getUserId());
        } else if (parentCtx != null) {
            builder.userId(parentCtx.getUserId());
        }
        return builder.build();
    }
}
