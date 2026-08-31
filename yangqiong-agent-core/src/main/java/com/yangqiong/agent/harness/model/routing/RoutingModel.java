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
package com.yangqiong.agent.harness.model.routing;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;

import com.yangqiong.agent.harness.core.message.AgentMessage;
import com.yangqiong.agent.harness.core.model.AgentChatResponse;
import com.yangqiong.agent.harness.core.model.AgentGenerateOptions;
import com.yangqiong.agent.harness.core.model.AgentModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;

/**
 * 路由模型
 * @author yangqiong
 */
public class RoutingModel implements AgentModel {

    private static final Logger log = LoggerFactory.getLogger(RoutingModel.class);

    /**
     * 路由候选列表
     */
    private final List<RouteCandidate> candidates;

    /**
     * 模型路由器
     */
    private final ModelRouter router;

    /**
     * 模型健康登记
     */
    private final ModelHealthRegistry registry;

    /**
     * 任务类型提供者，可空
     */
    private final Supplier<String> taskTypeSupplier;

    RoutingModel(List<RouteCandidate> candidates, ModelRouter router,
                 ModelHealthRegistry registry, Supplier<String> taskTypeSupplier) {
        if (candidates == null || candidates.isEmpty()) {
            throw new IllegalArgumentException("路由候选列表不能为空");
        }
        this.candidates = List.copyOf(candidates);
        this.router = Objects.requireNonNull(router, "模型路由器不能为空");
        this.registry = Objects.requireNonNull(registry, "模型健康登记不能为空");
        this.taskTypeSupplier = taskTypeSupplier;
    }

    /**
     * 创建路由模型构建器
     * @return
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * 同步生成模型响应，先路由选中候选模型再委托调用，成功登记健康、失败登记异常后原样抛出
     * @param messages
     * @param tools
     * @param options
     * @return
     */
    @Override
    public AgentChatResponse generate(List<AgentMessage> messages, List<Map<String, Object>> tools,
                                        AgentGenerateOptions options) {
        RouteCandidate selected = selectCandidate(messages);
        try {
            AgentChatResponse response = selected.getModel().generate(messages, tools, options);
            registry.recordSuccess(selected.getModelCode());
            return response;
        } catch (RuntimeException e) {
            log.warn("[Routing] 模型{}调用失败: {}", selected.getModelCode(), e.getMessage());
            registry.recordFailure(selected.getModelCode());
            throw e;
        }
    }

    /**
     * 流式生成模型响应，先路由选中候选模型再委托调用，流完成登记成功、流出错登记失败
     * @param messages
     * @param tools
     * @param options
     * @return
     */
    @Override
    public Flux<AgentChatResponse> stream(List<AgentMessage> messages, List<Map<String, Object>> tools,
                                            AgentGenerateOptions options) {
        RouteCandidate selected = selectCandidate(messages);
        String modelCode = selected.getModelCode();
        return selected.getModel().stream(messages, tools, options)
                .doOnComplete(() -> registry.recordSuccess(modelCode))
                .doOnError(error -> {
                    log.warn("[Routing] 模型{}流式调用失败: {}", modelCode, error.getMessage());
                    registry.recordFailure(modelCode);
                });
    }

    /**
     * 执行路由选择并校验选中候选可调用
     * @param messages
     * @return
     */
    private RouteCandidate selectCandidate(List<AgentMessage> messages) {
        String taskType = taskTypeSupplier == null ? null : taskTypeSupplier.get();
        RouteDecision decision = router.select(taskType, messages, candidates);
        if (decision == null || decision.getSelected() == null || decision.getSelected().getModel() == null) {
            throw new IllegalStateException("模型路由未选中任何可用候选");
        }
        return decision.getSelected();
    }

    /**
     * 路由模型构建器
     * @author yangqiong
     */
    public static class Builder {

        /**
         * 路由候选列表
         */
        private final List<RouteCandidate> candidateList = new ArrayList<>();

        /**
         * 模型路由器
         */
        private ModelRouter router;

        /**
         * 模型健康登记
         */
        private ModelHealthRegistry registry;

        /**
         * 任务类型提供者
         */
        private Supplier<String> taskTypeSupplier;

        public Builder candidate(RouteCandidate candidate) {
            this.candidateList.add(candidate);
            return this;
        }

        public Builder candidates(List<RouteCandidate> candidates) {
            this.candidateList.addAll(candidates);
            return this;
        }

        public Builder router(ModelRouter router) {
            this.router = router;
            return this;
        }

        public Builder registry(ModelHealthRegistry registry) {
            this.registry = registry;
            return this;
        }

        public Builder taskTypeSupplier(Supplier<String> taskTypeSupplier) {
            this.taskTypeSupplier = taskTypeSupplier;
            return this;
        }

        public RoutingModel build() {
            return new RoutingModel(candidateList, router, registry, taskTypeSupplier);
        }
    }
}
