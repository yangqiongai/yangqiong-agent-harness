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
package com.yangqiongai.agent.harness.engine;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import com.yangqiongai.agent.harness.core.message.AgentToolResultBlock;
import com.yangqiongai.agent.harness.core.tool.AgentTool;
import com.yangqiongai.agent.harness.core.tool.AgentToolCallParam;
import com.yangqiongai.agent.harness.tool.HarnessToolkit;
import com.yangqiongai.agent.harness.tool.LoadToolTool;
import com.yangqiongai.agent.harness.tool.ToolFilter;
import com.yangqiongai.agent.harness.tool.ToolLoadingState;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

/**
 * 引擎上下文工具渐进加载测试
 * @author yangqiong
 */
class EngineContextProgressiveTest {

    @Test
    void shouldReturnAllSchemasWithoutProgressiveState() {
        HarnessToolkit toolkit = new HarnessToolkit(null);
        toolkit.addTool(new DeferredStubTool("tool_a"));
        toolkit.addTool(new DeferredStubTool("tool_b"));
        EngineContext ctx = new EngineContext(null, null, AgentRuntimeContext.empty(), toolkit, 10, null);

        List<Map<String, Object>> schemas = ctx.getAllToolSchemas();
        assertThat(schemas).hasSize(2);
    }

    @Test
    void shouldTrimDeferredToolSchemasInProgressiveMode() {
        HarnessToolkit toolkit = new HarnessToolkit(null);
        toolkit.addTool(new DeferredStubTool("resident_tool"));
        toolkit.addTool(new DeferredStubTool("deferred_tool"));
        ToolLoadingState state = new ToolLoadingState(true, Set.of("resident_tool"));
        EngineContext ctx = new EngineContext(null, null, AgentRuntimeContext.empty(), toolkit, 10, null);
        ctx.setToolLoadingState(state);

        List<Map<String, Object>> schemas = ctx.getAllToolSchemas();
        assertThat(extractNames(schemas)).containsExactly("resident_tool");
    }

    @Test
    void shouldKeepMetaToolsVisibleInProgressiveMode() {
        HarnessToolkit toolkit = new HarnessToolkit(null);
        toolkit.addTool(new DeferredStubTool("deferred_tool"));
        toolkit.addTool(new LoadToolTool(toolkit, null));
        ToolLoadingState state = new ToolLoadingState(true, Set.of());
        EngineContext ctx = new EngineContext(null, null, AgentRuntimeContext.empty(), toolkit, 10, null);
        ctx.setToolLoadingState(state);

        List<Map<String, Object>> schemas = ctx.getAllToolSchemas();
        assertThat(extractNames(schemas)).containsExactly("load_tool");
    }

    @Test
    void shouldActivateToolAndExposeSchemaNextRound() {
        HarnessToolkit toolkit = new HarnessToolkit(null);
        toolkit.addTool(new DeferredStubTool("deferred_tool"));
        ToolLoadingState state = new ToolLoadingState(true, Set.of());
        EngineContext ctx = new EngineContext(null, null, AgentRuntimeContext.empty(), toolkit, 10, null);
        ctx.setToolLoadingState(state);
        assertThat(extractNames(ctx.getAllToolSchemas())).isEmpty();

        AgentTool activated = ctx.activateTool("deferred_tool");
        assertThat(activated).isNotNull();
        assertThat(extractNames(ctx.getAllToolSchemas())).containsExactly("deferred_tool");
        // 重复转正幂等，schema不重复
        ctx.activateTool("deferred_tool");
        assertThat(ctx.getAllToolSchemas()).hasSize(1);
    }

    @Test
    void shouldShareStateBetweenEngineContextAndLoadTool() {
        HarnessToolkit toolkit = new HarnessToolkit(null);
        toolkit.addTool(new DeferredStubTool("shared_tool"));
        ToolLoadingState state = new ToolLoadingState(true, Set.of());
        LoadToolTool loadTool = new LoadToolTool(toolkit, state);
        toolkit.addTool(loadTool);
        EngineContext ctx = new EngineContext(null, null, AgentRuntimeContext.empty(), toolkit, 10, null);
        ctx.setToolLoadingState(state);
        assertThat(extractNames(ctx.getAllToolSchemas())).containsExactly("load_tool");

        // load_tool转正后EngineContext下一轮立即可见
        loadTool.callAsync(new AgentToolCallParam(Map.of("tool_name", "shared_tool"))).block();
        assertThat(extractNames(ctx.getAllToolSchemas())).containsExactlyInAnyOrder("load_tool", "shared_tool");
    }

    @Test
    void shouldReturnNullWhenActivateUnknownTool() {
        EngineContext ctx = new EngineContext(null, null, AgentRuntimeContext.empty(), null, 10, null);
        assertThat(ctx.activateTool("not_exists")).isNull();
        assertThat(ctx.activateTool(null)).isNull();
    }

    @Test
    void shouldNotAffectDynamicAndPlanToolSchemas() {
        HarnessToolkit toolkit = new HarnessToolkit(null);
        toolkit.addTool(new DeferredStubTool("resident_tool"));
        ToolLoadingState state = new ToolLoadingState(true, Set.of("resident_tool"));
        EngineContext ctx = new EngineContext(null, null, AgentRuntimeContext.empty(), toolkit, 10, null);
        ctx.setToolLoadingState(state);
        ctx.registerDynamicTools(List.of(new DeferredStubTool("dyn_tool")));
        AgentRuntimeContext runtimeCtx = AgentRuntimeContext.empty();
        runtimeCtx.getAttributes().put("_planModeTools", List.of(new DeferredStubTool("plan_enter")));

        EngineContext ctxWithPlan = new EngineContext(null, null, runtimeCtx, toolkit, 10, null);
        ctxWithPlan.setToolLoadingState(state);
        ctxWithPlan.registerDynamicTools(List.of(new DeferredStubTool("dyn_tool")));
        assertThat(extractNames(ctxWithPlan.getAllToolSchemas()))
                .containsExactlyInAnyOrder("resident_tool", "dyn_tool", "plan_enter");
    }

    @Test
    void shouldComposeWithToolFilterInProgressiveMode() {
        HarnessToolkit toolkit = new HarnessToolkit(null);
        toolkit.addTool(new DeferredStubTool("resident_tool"));
        toolkit.addTool(new DeferredStubTool("other_tool"));
        ToolLoadingState state = new ToolLoadingState(true, Set.of("resident_tool", "other_tool"));
        EngineContext ctx = new EngineContext(null, null, AgentRuntimeContext.empty(), toolkit, 10, null);
        ctx.setToolLoadingState(state);
        ctx.setToolFilter((schemas, query) -> schemas.stream()
                .filter(s -> "resident_tool".equals(extractName(s)))
                .toList());

        List<Map<String, Object>> schemas = ctx.getAllToolSchemas("找常驻工具");
        assertThat(extractNames(schemas)).containsExactly("resident_tool");
    }

    @Test
    void shouldHandleConcurrentActivationAndSchemaRead() throws InterruptedException {
        HarnessToolkit toolkit = new HarnessToolkit(null);
        for (int i = 0; i < 20; i++) {
            toolkit.addTool(new DeferredStubTool("tool_" + i));
        }
        ToolLoadingState state = new ToolLoadingState(true, Set.of());
        EngineContext ctx = new EngineContext(null, null, AgentRuntimeContext.empty(), toolkit, 10, null);
        ctx.setToolLoadingState(state);

        int threads = 8;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch latch = new CountDownLatch(threads);
        Set<String> failures = new HashSet<>();
        for (int t = 0; t < threads; t++) {
            final int tid = t;
            pool.submit(() -> {
                try {
                    for (int j = 0; j < 50; j++) {
                        ctx.activateTool("tool_" + ((tid + j) % 20));
                        List<Map<String, Object>> schemas = ctx.getAllToolSchemas();
                        if (schemas.size() < 0) {
                            failures.add("negative");
                        }
                    }
                } finally {
                    latch.countDown();
                }
            });
        }
        assertThat(latch.await(10, TimeUnit.SECONDS)).isTrue();
        pool.shutdownNow();
        assertThat(failures).isEmpty();
        assertThat(state.isActivated("tool_0")).isTrue();
    }

    @Test
    void shouldPassProgressiveStateThroughEngineConfig() {
        HarnessToolkit toolkit = new HarnessToolkit(null);
        toolkit.addTool(new DeferredStubTool("deferred_tool"));
        ToolLoadingState state = new ToolLoadingState(true, Set.of());
        EngineConfig config = new EngineConfig("agent", "prompt", 10, toolkit, null, 1)
                .toolLoadingState(state);

        EngineContext ctx = config.toEngineContext(AgentRuntimeContext.empty());
        assertThat(ctx.getToolLoadingState()).isSameAs(state);
        assertThat(extractNames(ctx.getAllToolSchemas())).isEmpty();
    }

    private List<String> extractNames(List<Map<String, Object>> schemas) {
        return schemas.stream().map(this::extractName).toList();
    }

    private String extractName(Map<String, Object> schema) {
        Object function = schema.get("function");
        if (function instanceof Map<?, ?> fn) {
            Object name = fn.get("name");
            if (name != null) {
                return name.toString();
            }
        }
        return null;
    }

    /**
     * 延迟池桩工具
     */
    private static class DeferredStubTool implements AgentTool {
        private final String name;

        DeferredStubTool(String name) {
            this.name = name;
        }

        @Override
        public String getName() {
            return name;
        }

        @Override
        public String getDescription() {
            return "延迟池桩工具。";
        }

        @Override
        public Map<String, Object> getParameters() {
            Map<String, Object> schema = new HashMap<>();
            schema.put("type", "object");
            return schema;
        }

        @Override
        public Mono<AgentToolResultBlock> callAsync(AgentToolCallParam param) {
            return Mono.empty();
        }
    }
}
