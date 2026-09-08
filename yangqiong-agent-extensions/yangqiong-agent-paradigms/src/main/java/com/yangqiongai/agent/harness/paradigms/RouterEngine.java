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
package com.yangqiongai.agent.harness.paradigms;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

import com.yangqiongai.agent.harness.core.event.AgentEvent;
import com.yangqiongai.agent.harness.core.event.AgentEventType;
import com.yangqiongai.agent.harness.core.message.AgentContentBlock;
import com.yangqiongai.agent.harness.core.message.AgentMessage;
import com.yangqiongai.agent.harness.core.message.AgentMessageRole;
import com.yangqiongai.agent.harness.core.message.AgentTextBlock;
import com.yangqiongai.agent.harness.core.message.MessageFactory;
import com.yangqiongai.agent.harness.core.model.AgentChatResponse;
import com.yangqiongai.agent.harness.engine.AbstractAgentLoop;
import com.yangqiongai.agent.harness.engine.EngineContext;
import com.yangqiongai.agent.harness.engine.ReActEngine;

import reactor.core.publisher.Flux;

/**
 * 范式路由引擎
 * <p>
 * 面向执行范式的元层路由：先按工具可用性做启发式预筛缩小候选集，
 * 再经一次轻量模型分类轮依据任务特征选定单一最佳范式并转发执行；
 * 分类输出无法解析时回退兜底范式（有工具为ReAct，无工具为SelfAsk）。
 * 支持以forcedParadigm强制指定范式跳过分类轮，确定性场景由调用方直接定夺。
 * 选中引擎以独立run()转发，事件流中包含其完整生命周期与结果事件，
 * 路由决策以ENGINE_ROUTED事件承载，便于下游观测与断言。
 * 继承 AbstractAgentLoop 复用生命周期/中间件/预算基础设施。
 * </p>
 * @author yangqiong
 */
public class RouterEngine extends AbstractAgentLoop {

    /**
     * 可路由的范式类型
     */
    public enum ParadigmType {

        /**
         * ReAct基座：思考-行动-观察循环
         */
        REACT,

        /**
         * 先规划后执行
         */
        PLAN_EXECUTE,

        /**
         * 一次性规划按依赖并行执行后统一推理
         */
        REWOO,

        /**
         * 自评估失败重试
         */
        REFLEXION,

        /**
         * 复合问题拆解逐个作答
         */
        SELF_ASK,

        /**
         * 草稿批评修订循环
         */
        SELF_REFINE
    }

    /**
     * 路由决策
     */
    public static final class RoutingDecision {

        /**
         * 选中的范式类型
         */
        private final ParadigmType paradigm;

        /**
         * 一句话选择理由
         */
        private final String reason;

        /**
         * 按选中结果与理由构建
         * @param paradigm
         * @param reason
         */
        public RoutingDecision(ParadigmType paradigm, String reason) {
            this.paradigm = paradigm;
            this.reason = reason;
        }

        public ParadigmType getParadigm() {
            return paradigm;
        }

        public String getReason() {
            return reason;
        }
    }

    /**
     * 各范式的识别关键词，命中位置最靠前者胜出
     */
    private static final Map<ParadigmType, List<String>> KEYWORDS = buildKeywords();

    /**
     * 构建范式识别关键词映射，插入顺序即兜底候选顺序
     * @return
     */
    private static Map<ParadigmType, List<String>> buildKeywords() {
        Map<ParadigmType, List<String>> keywords = new LinkedHashMap<>();
        keywords.put(ParadigmType.REACT, List.of("react"));
        keywords.put(ParadigmType.PLAN_EXECUTE, List.of("plan_execute", "planexecute", "plan-execute", "plan"));
        keywords.put(ParadigmType.REWOO, List.of("rewoo"));
        keywords.put(ParadigmType.REFLEXION, List.of("reflexion"));
        keywords.put(ParadigmType.SELF_ASK, List.of("self_ask", "selfask", "self-ask"));
        keywords.put(ParadigmType.SELF_REFINE, List.of("self_refine", "selfrefine", "self-refine", "refine"));
        return keywords;
    }

    /**
     * 引擎解析器：按范式类型产出对应引擎实例，为空时按上下文构建默认引擎
     */
    private final Function<ParadigmType, AbstractAgentLoop> engineResolver;

    /**
     * 强制范式，非空时跳过路由分类直接转发
     */
    private final ParadigmType forcedParadigm;

    /**
     * 使用默认引擎解析器与非强制路由构建
     */
    public RouterEngine() {
        this(null, null);
    }

    /**
     * 按引擎解析器与强制范式构建
     * @param engineResolver 为空时按运行上下文构建默认引擎
     * @param forcedParadigm 为空时执行路由分类
     */
    public RouterEngine(Function<ParadigmType, AbstractAgentLoop> engineResolver, ParadigmType forcedParadigm) {
        this.engineResolver = engineResolver;
        this.forcedParadigm = forcedParadigm;
    }

    /**
     * 运行路由主循环，选定范式后以该引擎独立run()转发，生命周期管线由父类模板负责
     * @param inputs
     * @param context
     * @return
     */
    @Override
    protected Flux<AgentEvent> doRun(List<AgentMessage> inputs, EngineContext context) {
        return Flux.defer(() -> {
            if (context.getModelCaller() == null || context.getResponseParser() == null) {
                return Flux.just(AgentEvent.of(AgentEventType.ERROR,
                        new IllegalStateException("EngineContext未暴露模型调用器或响应解析器")));
            }
            if (forcedParadigm != null) {
                return delegate(forcedParadigm, inputs, context);
            }
            return routingFlux(inputs, context);
        });
    }

    /**
     * 路由分类流：启发式预筛确定候选集，一次模型分类后发射ENGINE_ROUTED并转发选中引擎
     * @param inputs
     * @param context
     * @return
     */
    private Flux<AgentEvent> routingFlux(List<AgentMessage> inputs, EngineContext context) {
        boolean toolsAvailable = context.getToolExecutor() != null;
        List<ParadigmType> candidates = candidates(toolsAvailable);
        AtomicReference<AgentChatResponse> responseRef = new AtomicReference<>();
        List<AgentMessage> routingMessages = List.of(MessageFactory.createUserMessage(
                buildRoutingPrompt(extractQuestion(inputs), candidates)));
        // 分类轮单次模型调用以父类withModelCallEvents发射事件并以withIterationTimeout包裹
        return withIterationTimeout(context, withModelCallEvents(context, routingMessages, List.of(), 0,
                response -> {
                    responseRef.set(response);
                    return Flux.empty();
                }))
                .concatWith(Flux.defer(() -> {
                    String text = textOf(responseRef.get());
                    ParadigmType selected = parseSelection(text, candidates);
                    return Flux.just(AgentEvent.of(AgentEventType.ENGINE_ROUTED,
                                    new RoutingDecision(selected, extractReason(text))))
                            .concatWith(delegate(selected, inputs, context));
                }));
    }

    /**
     * 转发选中引擎，其run()产出完整生命周期与结果事件
     * @param type
     * @param inputs
     * @param context
     * @return
     */
    private Flux<AgentEvent> delegate(ParadigmType type, List<AgentMessage> inputs, EngineContext context) {
        return Flux.defer(() -> {
            AbstractAgentLoop engine = engineResolver != null
                    ? engineResolver.apply(type)
                    : defaultEngine(type, context);
            return engine.run(inputs, context);
        });
    }

    /**
     * 按工具可用性解析候选范式集，无工具时剔除以工具调用为主的范式
     * @param toolsAvailable
     * @return
     */
    private List<ParadigmType> candidates(boolean toolsAvailable) {
        if (toolsAvailable) {
            return List.of(ParadigmType.values());
        }
        return List.of(ParadigmType.SELF_ASK, ParadigmType.REFLEXION, ParadigmType.SELF_REFINE);
    }

    /**
     * 构建路由分类提示词，仅描述候选范式并要求严格JSON输出
     * @param question
     * @param candidates
     * @return
     */
    private String buildRoutingPrompt(String question, List<ParadigmType> candidates) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("你是执行范式路由器，请根据用户任务特征从候选范式名单中选择一个最合适的范式。\n");
        prompt.append("\n候选范式说明:\n");
        for (ParadigmType type : candidates) {
            prompt.append("- ").append(type.name().toLowerCase(Locale.ROOT))
                    .append(": ").append(paradigmDescription(type)).append("\n");
        }
        prompt.append("\n用户任务:\n").append(question);
        prompt.append("\n请严格按以下JSON格式输出，不要输出任何其他内容:\n");
        prompt.append("{\"paradigm\": \"范式标识(小写)\", \"reason\": \"一句话选择理由\"}");
        return prompt.toString();
    }

    /**
     * 范式类型的一句话能力描述
     * @param type
     * @return
     */
    private String paradigmDescription(ParadigmType type) {
        return switch (type) {
            case REACT -> "思考-行动-观察循环，通用工具任务，无需预先规划";
            case PLAN_EXECUTE -> "先规划出工具步与推理步列表再按序执行，适合多步骤流程任务";
            case REWOO -> "一次性规划全部工具步并按依赖并行执行后统一推理，适合工具密集且步骤可并行的任务";
            case REFLEXION -> "执行后自评估，未通过则总结教训重试，适合结果有客观对错的任务";
            case SELF_ASK -> "将复合问题拆解为子问题逐个作答后汇总，适合多实体多跳问题";
            case SELF_REFINE -> "产出草稿后循环批评与修订，适合无客观对错、以质量为导向的写作任务";
        };
    }

    /**
     * 容错解析分类输出：候选范式关键词在文本中命中位置最靠前者胜出，无命中时兜底
     * @param text
     * @param candidates
     * @return
     */
    private ParadigmType parseSelection(String text, List<ParadigmType> candidates) {
        String normalized = text != null ? text.toLowerCase(Locale.ROOT) : "";
        ParadigmType best = null;
        int bestIndex = Integer.MAX_VALUE;
        for (ParadigmType type : candidates) {
            for (String keyword : KEYWORDS.get(type)) {
                int index = normalized.indexOf(keyword);
                if (index >= 0 && index < bestIndex) {
                    bestIndex = index;
                    best = type;
                }
            }
        }
        return best != null ? best : fallback(candidates);
    }

    /**
     * 解析失败兜底：有工具优先ReAct，否则取首个候选范式
     * @param candidates
     * @return
     */
    private ParadigmType fallback(List<ParadigmType> candidates) {
        return candidates.contains(ParadigmType.REACT)
                ? ParadigmType.REACT
                : candidates.get(0);
    }

    /**
     * 从分类输出中提取reason字段内容，缺失时返回空串
     * @param text
     * @return
     */
    private String extractReason(String text) {
        if (text == null || text.isBlank()) {
            return "";
        }
        String normalized = text.trim();
        int keyIndex = normalized.toLowerCase(Locale.ROOT).indexOf("\"reason\"");
        if (keyIndex < 0) {
            keyIndex = normalized.toLowerCase(Locale.ROOT).indexOf("reason");
        }
        if (keyIndex < 0) {
            return "";
        }
        int colonIndex = normalized.indexOf(':', keyIndex);
        if (colonIndex < 0) {
            return "";
        }
        String reason = normalized.substring(colonIndex + 1).trim();
        if (reason.endsWith("}")) {
            reason = reason.substring(0, reason.length() - 1).trim();
        }
        if (reason.startsWith("\"") && reason.endsWith("\"") && reason.length() >= 2) {
            reason = reason.substring(1, reason.length() - 1);
        }
        return reason;
    }

    /**
     * 提取输入中最后一条非空用户消息文本作为路由依据
     * @param inputs
     * @return
     */
    private String extractQuestion(List<AgentMessage> inputs) {
        if (inputs != null) {
            for (int i = inputs.size() - 1; i >= 0; i--) {
                AgentMessage message = inputs.get(i);
                if (message.getRole() == AgentMessageRole.USER) {
                    String text = message.getTextContent();
                    if (text != null && !text.isBlank()) {
                        return text;
                    }
                }
            }
        }
        return "";
    }

    /**
     * 提取响应中全部文本块内容
     * @param response
     * @return
     */
    private String textOf(AgentChatResponse response) {
        if (response == null) {
            return "";
        }
        StringBuilder text = new StringBuilder();
        for (AgentContentBlock block : response.getContent()) {
            if (block instanceof AgentTextBlock textBlock && textBlock.getText() != null) {
                text.append(textBlock.getText());
            }
        }
        return text.toString();
    }

    /**
     * 按运行上下文构建对应范式的默认配置引擎实例，确保模型调用器与工具执行器等组件来自上下文
     * @param type
     * @param context
     * @return
     */
    private AbstractAgentLoop defaultEngine(ParadigmType type, EngineContext context) {
        return switch (type) {
            case REACT -> new ReActEngine(context.getModelCaller(), context.getToolExecutor(),
                    middlewareChain(context), context.getResponseParser(), null);
            case PLAN_EXECUTE -> new PlanExecuteEngine();
            case REWOO -> new ReWooEngine();
            case REFLEXION -> new ReflexionEngine();
            case SELF_ASK -> new SelfAskEngine();
            case SELF_REFINE -> new SelfRefineEngine();
        };
    }
}
