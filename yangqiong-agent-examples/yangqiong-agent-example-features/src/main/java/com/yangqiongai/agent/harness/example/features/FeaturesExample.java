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
package com.yangqiongai.agent.harness.example.features;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.yangqiongai.agent.harness.HarnessRuntimeBuilder;
import com.yangqiongai.agent.harness.config.AgentApprovalMode;
import com.yangqiongai.agent.harness.config.AgentJsonSchema;
import com.yangqiongai.agent.harness.config.AgentResponseFormat;
import com.yangqiongai.agent.harness.core.AgentRuntime;
import com.yangqiongai.agent.harness.core.event.AgentEvent;
import com.yangqiongai.agent.harness.core.event.AgentEventType;
import com.yangqiongai.agent.harness.core.event.AgentResultEvent;
import com.yangqiongai.agent.harness.core.event.ClarificationAnswer;
import com.yangqiongai.agent.harness.core.event.RequireUserClarificationEvent;
import com.yangqiongai.agent.harness.core.message.AgentMessage;
import com.yangqiongai.agent.harness.core.message.AgentTextBlock;
import com.yangqiongai.agent.harness.core.message.AgentToolResultBlock;
import com.yangqiongai.agent.harness.core.message.AgentToolUseBlock;
import com.yangqiongai.agent.harness.core.message.MessageFactory;
import com.yangqiongai.agent.harness.core.model.AgentGenerateOptions;
import com.yangqiongai.agent.harness.core.model.AgentModel;
import com.yangqiongai.agent.harness.core.model.registry.AgentModelRegistry;
import com.yangqiongai.agent.harness.core.tool.AgentTool;
import com.yangqiongai.agent.harness.core.tool.AgentToolCallParam;
import com.yangqiongai.agent.harness.core.trace.SpanInfo;
import com.yangqiongai.agent.harness.durable.MemoryDistributedStores;
import com.yangqiongai.agent.harness.engine.AgentRuntimeContext;
import com.yangqiongai.agent.harness.engine.ReActEngine;
import com.yangqiongai.agent.harness.eval.EvalCase;
import com.yangqiongai.agent.harness.eval.EvalCaseResult;
import com.yangqiongai.agent.harness.eval.EvalDataset;
import com.yangqiongai.agent.harness.eval.EvalReport;
import com.yangqiongai.agent.harness.eval.EvalRunner;
import com.yangqiongai.agent.harness.eval.LLMEvalJudge;
import com.yangqiongai.agent.harness.guardrail.ContentModerationPolicy;
import com.yangqiongai.agent.harness.guardrail.ModerationVerdict;
import com.yangqiongai.agent.harness.middleware.ContentModerationMiddleware;
import com.yangqiongai.agent.harness.model.HarnessModelFactory;
import com.yangqiongai.agent.harness.model.HarnessModelProperties;
import com.yangqiongai.agent.harness.model.StructuredOutputValidator;
import com.yangqiongai.agent.harness.model.provider.AnthropicModelProvider;
import com.yangqiongai.agent.harness.model.provider.DashScopeModelProvider;
import com.yangqiongai.agent.harness.model.provider.OllamaModelProvider;
import com.yangqiongai.agent.harness.model.provider.OpenAIModelProvider;
import com.yangqiongai.agent.harness.planmode.PlanModeManager;
import com.yangqiongai.agent.harness.planmode.PlanModeMiddleware;
import com.yangqiongai.agent.harness.ratelimit.RateLimitExceededException;
import com.yangqiongai.agent.harness.ratelimit.RateLimitedModel;
import com.yangqiongai.agent.harness.ratelimit.TokenBucketRateLimiter;
import com.yangqiongai.agent.harness.tool.HarnessToolkit;
import com.yangqiongai.agent.harness.web.WebFetcher;
import com.yangqiongai.agent.harness.web.WebSearchProvider;
import com.yangqiongai.agent.harness.web.WebSearchResult;
import reactor.core.publisher.Mono;

/**
 * 实用特性综合示例
 * <p>
 * 演示框架第二批实用特性：Web检索/抓取、AskUser澄清暂停续接、模型速率限制、
 * LLM-as-Judge评测、执行追踪Span、结构化输出自动修复、内容审查（拦截/脱敏）、
 * 文件沙箱工具、计划模式。所有演示均为真实模型调用，未配置API密钥时提示退出。
 * </p>
 * @author yangqiong
 */
public final class FeaturesExample {

    /**
     * 默认API密钥（请替换为你自己的密钥）
     */
    private static final String DEFAULT_API_KEY = "";

    /**
     * 默认模型名称
     */
    private static final String DEFAULT_MODEL = "deepseek-v4-flash";

    /**
     * 默认API基础地址
     */
    private static final String DEFAULT_BASE_URL = "https://api.deepseek.com";

    /**
     * 单次演示超时秒数
     */
    private static final int TIMEOUT_SECONDS = 120;

    private FeaturesExample() {
    }

    /**
     * 示例入口，不带参数时按顺序执行全部演示，也可传入演示名只跑指定项
     * @param args 演示名：web-search/web-fetch/ask-user/rate-limit/eval/trace/structured-output/moderation/file-tool/plan-mode
     */
    public static void main(String[] args) {
        String apiKey = env("AI_API_KEY", DEFAULT_API_KEY);
        String modelName = env("AI_MODEL", DEFAULT_MODEL);
        String baseUrl = env("AI_BASE_URL", DEFAULT_BASE_URL);
        if (apiKey == null || apiKey.isBlank()) {
            System.out.println("请先配置环境变量 AI_API_KEY（可通过 AI_MODEL/AI_BASE_URL 切换模型与地址）");
            return;
        }

        AgentModel model = buildModel(apiKey, modelName, baseUrl);
        AgentGenerateOptions options = AgentGenerateOptions.builder()
                .temperature(0.1)
                .maxTokens(4096)
                .build();

        Map<String, Runnable> demos = new LinkedHashMap<>();
        demos.put("web-search", () -> demoWebSearch(model, options));
        demos.put("web-fetch", () -> demoWebFetch(model, options));
        demos.put("ask-user", () -> demoAskUser(model, options));
        demos.put("rate-limit", () -> demoRateLimit(model, options));
        demos.put("eval", () -> demoEvalJudge(model, options));
        demos.put("trace", () -> demoTrace(model, options));
        demos.put("structured-output", () -> demoStructuredOutput(model));
        demos.put("moderation", () -> demoModeration(model, options));
        demos.put("file-tool", () -> demoFileTool(model, options));
        demos.put("plan-mode", () -> demoPlanMode(model, options));

        List<String> selected = args.length > 0 ? List.of(args) : List.copyOf(demos.keySet());
        for (String name : selected) {
            Runnable demo = demos.get(name);
            if (demo == null) {
                System.out.println("未知演示名: " + name + "，可选: " + demos.keySet());
                continue;
            }
            runDemo(name, demo);
        }
    }

    /**
     * 执行单个演示并隔离异常，保证单项失败不影响后续演示
     * @param name
     * @param demo
     */
    private static void runDemo(String name, Runnable demo) {
        System.out.println();
        System.out.println("======================================================");
        System.out.println("== 演示: " + name);
        System.out.println("======================================================");
        try {
            demo.run();
        } catch (Exception e) {
            System.out.println("演示执行异常: " + e.getMessage());
        }
    }

    /**
     * Web检索演示：注入内存搜索Provider，模型应调用web_search工具并基于结果回答
     * @param model
     * @param options
     */
    private static void demoWebSearch(AgentModel model, AgentGenerateOptions options) {
        InMemoryWebSearchProvider searchProvider = new InMemoryWebSearchProvider();
        AgentRuntime runtime = baseBuilder("features-web-search", model, options)
                .systemPrompt("你是擅长使用工具的智能助手。当用户请求联网搜索时，请使用web_search工具查询。")
                .webSearchProvider(searchProvider)
                .maxIters(5)
                .build();
        try {
            List<AgentEvent> events = runtime.stream(
                    List.of(MessageFactory.createUserMessage("请使用web_search工具搜索人工智能相关的内容")),
                    AgentRuntimeContext.empty()
            ).collectList().block(Duration.ofSeconds(TIMEOUT_SECONDS));
            printToolCallSummary(events, "web_search");
            printResult(events);
            System.out.println("搜索服务被调用次数: " + searchProvider.getSearchCount());
        } finally {
            runtime.close().block();
        }
    }

    /**
     * Web抓取演示：注入内存抓取Provider，模型应调用web_fetch工具并复述内容
     * @param model
     * @param options
     */
    private static void demoWebFetch(AgentModel model, AgentGenerateOptions options) {
        InMemoryWebFetcher fetcher = new InMemoryWebFetcher();
        AgentRuntime runtime = baseBuilder("features-web-fetch", model, options)
                .systemPrompt("你是擅长使用工具的智能助手。当用户请求抓取网页内容时，请使用web_fetch工具。")
                .webFetcher(fetcher)
                .maxIters(5)
                .build();
        try {
            List<AgentEvent> events = runtime.stream(
                    List.of(MessageFactory.createUserMessage("请使用web_fetch工具抓取https://example.com的内容")),
                    AgentRuntimeContext.empty()
            ).collectList().block(Duration.ofSeconds(TIMEOUT_SECONDS));
            printToolCallSummary(events, "web_fetch");
            printResult(events);
            System.out.println("抓取服务被调用次数: " + fetcher.getFetchCount());
        } finally {
            runtime.close().block();
        }
    }

    /**
     * AskUser澄清演示：缺少关键信息时模型调用ask_user暂停等待，注入答案后续接完成
     * @param model
     * @param options
     */
    private static void demoAskUser(AgentModel model, AgentGenerateOptions options) {
        AgentRuntime runtime = baseBuilder("features-ask-user", model, options)
                .systemPrompt("你是严谨的预算评估助手。你有一条铁律：在用户提供具体预算金额之前，"
                        + "绝对不允许输出任何预算评估结论，必须调用ask_user工具询问用户的预算金额，"
                        + "等用户提供金额后再给出评估。")
                .askUserEnabled(true)
                .maxIters(5)
                .build();
        try {
            // 暂停与续接必须复用同一个上下文，暂停快照保存在其attributes中
            AgentRuntimeContext ctx = AgentRuntimeContext.empty();
            List<AgentEvent> events = runtime.stream(
                    List.of(MessageFactory.createUserMessage(
                            "请帮我做一个预算评估。注意：请先使用ask_user工具询问我的预算金额，"
                                    + "待我回答后再评估，不要直接输出评估结论。")),
                    ctx
            ).collectList().block(Duration.ofSeconds(TIMEOUT_SECONDS));

            RequireUserClarificationEvent pending = firstClarification(events);
            if (pending == null) {
                System.out.println("模型未触发ask_user（真实模型行为存在不确定性），演示结束");
                return;
            }
            System.out.println("模型提问: " + pending.getQuestion());

            // 连续澄清场景下循环续接，直到产出最终结果或达到轮次上限
            AgentMessage result = null;
            for (int round = 0; round < 3 && pending != null; round++) {
                String answer = round == 0 ? "预算上限100万" : "预算上限200万";
                System.out.println("自动应答: " + answer);
                List<AgentEvent> resumeEvents = runtime.resumeWithClarification(
                        List.of(new ClarificationAnswer(pending.getToolCallId(), answer)), ctx
                ).collectList().block(Duration.ofSeconds(TIMEOUT_SECONDS));
                result = extractResult(resumeEvents);
                if (result != null) {
                    break;
                }
                pending = firstClarification(resumeEvents);
            }
            if (result != null) {
                System.out.println("最终评估: " + extractText(result));
            } else {
                System.out.println("多轮澄清后仍未产出最终结果");
            }
        } finally {
            runtime.close().block();
        }
    }

    /**
     * 速率限制演示：高容量令牌桶不阻塞正常调用，极低容量下第二次调用被限流拒绝
     * @param model
     * @param options
     */
    private static void demoRateLimit(AgentModel model, AgentGenerateOptions options) {
        AgentRuntime highCapacityRuntime = baseBuilder("features-rate-limit", model, options)
                .systemPrompt("你是简洁的智能助手，请用一句话回答用户的问题。")
                .modelRateLimiter(new TokenBucketRateLimiter(100, 100))
                .maxIters(3)
                .build();
        try {
            AgentMessage result = highCapacityRuntime.call(
                    List.of(MessageFactory.createUserMessage("你好，请用一句话简单介绍一下你自己")),
                    AgentRuntimeContext.empty()
            ).block(Duration.ofSeconds(TIMEOUT_SECONDS));
            System.out.println("高容量限流调用成功: " + extractText(result));
        } finally {
            highCapacityRuntime.close().block();
        }

        System.out.println();
        // 极低速率：1个令牌且每秒仅补充0.01个，第二次调用需等待超过最大等待时间从而被限流
        RateLimitedModel limitedModel = new RateLimitedModel(model,
                new TokenBucketRateLimiter(1, 0.01), Duration.ofSeconds(60));
        AgentRuntime lowCapacityRuntime = baseBuilder("features-rate-limit-delay", limitedModel, options)
                .systemPrompt("你是简洁的智能助手，请用一句话回答用户的问题。")
                .maxIters(3)
                .build();
        try {
            AgentMessage first = lowCapacityRuntime.call(
                    List.of(MessageFactory.createUserMessage("你好，请用一句话介绍你自己")),
                    AgentRuntimeContext.empty()
            ).block(Duration.ofSeconds(TIMEOUT_SECONDS));
            System.out.println("首次调用（消耗唯一令牌）: " + extractText(first));

            List<AgentEvent> events = lowCapacityRuntime.stream(
                    List.of(MessageFactory.createUserMessage("请再次介绍你自己")),
                    AgentRuntimeContext.empty()
            ).collectList().block(Duration.ofSeconds(TIMEOUT_SECONDS));
            boolean rateLimited = events != null && events.stream()
                    .filter(e -> e.getType() == AgentEventType.ERROR)
                    .anyMatch(e -> e.getPayload() instanceof RateLimitExceededException);
            System.out.println(rateLimited
                    ? "第二次调用被限流拒绝（RateLimitExceededException），符合预期"
                    : "第二次调用未被限流（令牌恰好可用），结果: "
                        + (events == null ? "无事件" : extractText(extractResult(events))));
        } finally {
            lowCapacityRuntime.close().block();
        }
    }

    /**
     * LLM-as-Judge评测演示：规则评分（工具命中+关键词命中）与LLM判分结合
     * @param model
     * @param options
     */
    private static void demoEvalJudge(AgentModel model, AgentGenerateOptions options) {
        HarnessToolkit toolkit = new HarnessToolkit(null);
        toolkit.addTool(weatherTool());

        AgentRuntime runtime = baseBuilder("features-eval", model, options)
                .systemPrompt("你是乐于助人的智能助手，查询天气用get_weather工具，一般知识问题直接回答，请用中文。")
                .toolkit(toolkit)
                .maxIters(5)
                .build();
        try {
            EvalDataset dataset = EvalDataset.of("特性示例评测",
                    EvalCase.builder()
                            .id("weather-1")
                            .query("请查询北京的天气")
                            .expectedToolNames(List.of("get_weather"))
                            .expectedKeywords(List.of("晴"))
                            .build()
            );
            EvalReport report = new EvalRunner(runtime).run(dataset, 1, new LLMEvalJudge(model));
            System.out.println(report.summary());
            EvalCaseResult caseResult = report.getResults().get(0);
            System.out.println("规则评分通过: " + caseResult.isPassed()
                    + " | LLM判分: " + caseResult.getJudgeScore()
                    + " | 判分理由: " + caseResult.getJudgeReason());
        } finally {
            runtime.close().block();
        }
    }

    /**
     * 执行追踪演示：TraceEmitter采集agent_run根Span与reasoning/acting子Span层级
     * @param model
     * @param options
     */
    private static void demoTrace(AgentModel model, AgentGenerateOptions options) {
        List<SpanInfo> spans = new java.util.ArrayList<>();
        AgentRuntime runtime = baseBuilder("features-trace", model, options)
                .systemPrompt("你是简洁的智能助手，请用一句话回答用户的问题。")
                .traceEmitter(spans::add)
                .maxIters(3)
                .build();
        try {
            List<AgentEvent> events = runtime.stream(
                    List.of(MessageFactory.createUserMessage("你好，请用一句话介绍你自己")),
                    AgentRuntimeContext.empty()
            ).collectList().block(Duration.ofSeconds(TIMEOUT_SECONDS));
            printResult(events);
            System.out.println("采集到 " + spans.size() + " 个Span:");
            for (SpanInfo span : spans) {
                System.out.printf("  %-12s 耗时=%5dms traceId=%s spanId=%s parent=%s%n",
                        span.getOperation(), span.getDurationMs(),
                        shortId(span.getTraceId()), shortId(span.getSpanId()),
                        span.getParentSpanId() == null ? "-" : shortId(span.getParentSpanId()));
            }
        } finally {
            runtime.close().block();
        }
    }

    /**
     * 结构化输出演示：诱导首次输出不合规内容，引擎自动注入校验错误重试修正
     * @param model
     */
    private static void demoStructuredOutput(AgentModel model) {
        AgentResponseFormat format = AgentResponseFormat.jsonSchema(AgentJsonSchema.builder()
                .name("person_schema")
                .schema(Map.of(
                        "type", "object",
                        "properties", Map.of("name", Map.of("type", "string")),
                        "required", List.of("name")))
                .build());
        AgentGenerateOptions options = AgentGenerateOptions.builder()
                .temperature(0.1)
                .maxTokens(1024)
                .responseFormat(format)
                .build();

        AgentRuntime runtime = baseBuilder("features-structured-output", model, options)
                .systemPrompt("你是智能助手。")
                .structuredOutputRetry(3, "输出不符合JSON Schema：%s，请严格按Schema重新输出JSON")
                .maxIters(6)
                .build();
        try {
            AgentRuntimeContext ctx = AgentRuntimeContext.empty();
            List<AgentEvent> events = runtime.stream(
                    List.of(MessageFactory.createUserMessage(
                            "请先输出不少于30字的自然语言解释，说明你的处理思路，然后再输出一个JSON对象，"
                                    + "其中name字段的值为任意字符串")),
                    ctx
            ).collectList().block(Duration.ofSeconds(TIMEOUT_SECONDS));
            AgentMessage result = extractResult(events);
            String text = extractText(result);
            System.out.println("最终输出: " + text);
            System.out.println("Schema校验: "
                    + (StructuredOutputValidator.validate(text, format) == null ? "通过" : "未通过"));
            Object retryCount = ctx.get(ReActEngine.ATTR_STRUCTURED_RETRY_COUNT);
            System.out.println("结构化重试次数: " + (retryCount == null ? "0（首次即合规）" : retryCount));
        } finally {
            runtime.close().block();
        }
    }

    /**
     * 内容审查演示：BLOCK策略在进入LLM前拦截输入，MASK策略脱敏后放行
     * @param model
     * @param options
     */
    private static void demoModeration(AgentModel model, AgentGenerateOptions options) {
        System.out.println("-- BLOCK 拦截 --");
        ContentModerationPolicy blockPolicy = message -> message.getTextContent().contains("禁止")
                ? ModerationVerdict.block("输入包含禁止内容") : ModerationVerdict.pass();
        AgentRuntime blockRuntime = baseBuilder("features-moderation-block", model, options)
                .systemPrompt("你是智能助手。")
                .contentModeration(blockPolicy)
                .maxIters(3)
                .build();
        try {
            List<AgentEvent> events = blockRuntime.stream(
                    List.of(MessageFactory.createUserMessage("这是一条包含禁止词汇的输入")),
                    AgentRuntimeContext.empty()
            ).collectList().block(Duration.ofSeconds(TIMEOUT_SECONDS));
            boolean blocked = events.stream()
                    .filter(e -> e.getType() == AgentEventType.ERROR)
                    .anyMatch(e -> e.getPayload() instanceof ContentModerationMiddleware.ContentModerationException);
            System.out.println(blocked ? "输入在进入LLM前被拦截（ContentModerationException），模型未被调用"
                    : "输入未被拦截");
        } finally {
            blockRuntime.close().block();
        }

        System.out.println();
        System.out.println("-- MASK 脱敏 --");
        String sensitive = "机密数据ABC123";
        ContentModerationPolicy maskPolicy = message -> message.getTextContent().contains(sensitive)
                ? ModerationVerdict.mask("敏感信息") : ModerationVerdict.pass();
        AgentRuntime maskRuntime = baseBuilder("features-moderation-mask", model, options)
                .systemPrompt("你是智能助手。请原样逐字复述用户输入的内容，不要增加任何其他内容。")
                .contentModeration(maskPolicy)
                .maxIters(3)
                .build();
        try {
            List<AgentEvent> events = maskRuntime.stream(
                    List.of(MessageFactory.createUserMessage("这句话包含" + sensitive + "，请原样复述")),
                    AgentRuntimeContext.empty()
            ).collectList().block(Duration.ofSeconds(TIMEOUT_SECONDS));
            String output = extractText(extractResult(events));
            System.out.println("模型输出: " + output);
            System.out.println(output.contains(sensitive) ? "脱敏失败：输出复现了敏感词"
                    : "脱敏成功：敏感词已被替换，未进入模型");
        } finally {
            maskRuntime.close().block();
        }
    }

    /**
     * 文件沙箱演示：Agent通过file_read在沙箱目录内读取文件并汇报内容
     * @param model
     * @param options
     */
    private static void demoFileTool(AgentModel model, AgentGenerateOptions options) {
        try {
            Path sandbox = Files.createTempDirectory("harness-features-sandbox-");
            Files.writeString(sandbox.resolve("report.txt"), "季度营收为1000万元", StandardCharsets.UTF_8);

            AgentRuntime runtime = baseBuilder("features-file-tool", model, options)
                    .systemPrompt("你是擅长使用文件工具的智能助手。当用户请求读取工作区文件内容时，"
                            + "请使用file_read工具读取沙箱内文件，并汇报文件中的关键信息。")
                    .enableFileToolkit(sandbox.toString(), null)
                    .maxIters(5)
                    .build();
            try {
                List<AgentEvent> events = runtime.stream(
                        List.of(MessageFactory.createUserMessage(
                                "请使用file_read工具读取工作区内report.txt文件的内容，并告诉我文件里写了什么")),
                        AgentRuntimeContext.empty()
                ).collectList().block(Duration.ofSeconds(TIMEOUT_SECONDS));
                printToolCallSummary(events, "file_read");
                printResult(events);
            } finally {
                runtime.close().block();
            }
        } catch (Exception e) {
            System.out.println("创建沙箱目录失败: " + e.getMessage());
        }
    }

    /**
     * 计划模式演示：注册plan_enter/plan_write/plan_exit工具，模型先规划后执行
     * @param model
     * @param options
     */
    private static void demoPlanMode(AgentModel model, AgentGenerateOptions options) {
        AgentRuntime runtime = baseBuilder("features-plan-mode", model, options)
                .systemPrompt("你是一位严谨的研发项目经理。对于复杂任务，你必须遵循计划模式工作流："
                        + "先调用plan_enter进入计划模式，再调用plan_write写入详细执行计划，"
                        + "最后调用plan_exit退出计划模式并按计划执行。"
                        + "除非完成规划，否则不要直接输出最终结论。")
                .planModeEnabled(true)
                .maxIters(8)
                .build();
        try {
            AgentRuntimeContext ctx = AgentRuntimeContext.empty();
            List<AgentEvent> events = runtime.stream(
                    List.of(MessageFactory.createUserMessage(
                            "请为\"开发一个Spring Boot预算审核系统\"制定一份执行计划。"
                                    + "注意：必须使用计划模式工具，先规划再执行。")),
                    ctx
            ).collectList().block(Duration.ofSeconds(TIMEOUT_SECONDS));

            Object tools = ctx.get(PlanModeMiddleware.ATTR_PLAN_MODE_TOOLS);
            if (tools instanceof List<?> planTools) {
                System.out.println("已注册计划模式工具: " + planTools.stream()
                        .map(t -> ((AgentTool) t).getName())
                        .collect(Collectors.joining(", ")));
            }
            printResult(events);
            Object plan = ctx.get(PlanModeManager.ATTR_PLAN_CONTENT);
            if (plan != null && !String.valueOf(plan).isBlank()) {
                System.out.println("已保存的计划内容: " + String.valueOf(plan));
            } else {
                System.out.println("模型未触发plan_write（真实模型行为存在不确定性），计划内容为空");
            }
        } finally {
            runtime.close().block();
        }
    }

    /**
     * 构建基础运行时构建器，统一审批放行与内存级分布式存储
     * @param name
     * @param model
     * @param options
     * @return
     */
    private static HarnessRuntimeBuilder baseBuilder(String name, AgentModel model, AgentGenerateOptions options) {
        return new HarnessRuntimeBuilder()
                .name(name)
                .model(model)
                .generateOptions(options)
                // 示例无人值守运行，FULL_ACCESS完全放行工具调用，生产环境请按需收紧
                .approvalMode(AgentApprovalMode.FULL_ACCESS)
                .stores(new MemoryDistributedStores());
    }

    /**
     * 装配模型，可通过环境变量 AI_API_KEY/AI_MODEL/AI_BASE_URL 覆盖默认值
     * @param apiKey
     * @param modelName
     * @param baseUrl
     * @return
     */
    private static AgentModel buildModel(String apiKey, String modelName, String baseUrl) {
        HarnessModelProperties properties = new HarnessModelProperties();
        properties.setApiKey(apiKey);
        properties.setBaseUrl(baseUrl);
        properties.setModelName(modelName);
        AgentModelRegistry registry = new AgentModelRegistry(properties.getDefaultProvider());
        registry.registerProvider(new OpenAIModelProvider());
        registry.registerProvider(new AnthropicModelProvider());
        registry.registerProvider(new DashScopeModelProvider());
        registry.registerProvider(new OllamaModelProvider());
        return new HarnessModelFactory(properties, registry).getModel(properties.getModelName(), null);
    }

    /**
     * 天气查询演示工具，按传入城市回显天气信息
     * @return
     */
    private static AgentTool weatherTool() {
        return new AgentTool() {

            @Override
            public String getName() {
                return "get_weather";
            }

            @Override
            public String getDescription() {
                return "查询指定城市的实时天气";
            }

            @Override
            public Map<String, Object> getParameters() {
                return Map.of(
                        "type", "object",
                        "properties", Map.of(
                                "city", Map.of("type", "string", "description", "城市名称")),
                        "required", List.of("city"));
            }

            @Override
            public boolean isReadOnly() {
                return true;
            }

            @Override
            public Mono<AgentToolResultBlock> callAsync(AgentToolCallParam param) {
                Object city = param.getInput().getOrDefault("city", "未知城市");
                return Mono.just(AgentToolResultBlock.of(List.of(
                        AgentTextBlock.builder()
                                .text(city + "今日晴，气温22℃，微风，适合出行")
                                .build())));
            }
        };
    }

    /**
     * 查找事件流中首个用户澄清事件
     * @param events
     * @return
     */
    private static RequireUserClarificationEvent firstClarification(List<AgentEvent> events) {
        if (events == null) {
            return null;
        }
        return events.stream()
                .filter(e -> e.getType() == AgentEventType.REQUIRE_USER_CLARIFICATION)
                .map(e -> (RequireUserClarificationEvent) e)
                .findFirst().orElse(null);
    }

    /**
     * 输出指定工具是否被模型调用
     * @param events
     * @param toolName
     */
    private static void printToolCallSummary(List<AgentEvent> events, String toolName) {
        if (events == null) {
            return;
        }
        boolean called = events.stream()
                .filter(e -> e.getType() == AgentEventType.TOOL_CALL_START)
                .map(AgentEvent::getPayload)
                .filter(List.class::isInstance)
                .flatMap(payload -> ((List<?>) payload).stream())
                .filter(AgentToolUseBlock.class::isInstance)
                .map(AgentToolUseBlock.class::cast)
                .anyMatch(tool -> toolName.equals(tool.getToolName()));
        System.out.println("工具调用 " + toolName + ": " + (called ? "已调用" : "未调用"));
    }

    /**
     * 输出最终结果消息文本
     * @param events
     */
    private static void printResult(List<AgentEvent> events) {
        AgentMessage result = extractResult(events);
        if (result == null) {
            System.out.println("未产生最终结果");
            return;
        }
        System.out.println("最终结果: " + extractText(result));
    }

    /**
     * 从事件流中提取最终结果消息
     * @param events
     * @return
     */
    private static AgentMessage extractResult(List<AgentEvent> events) {
        if (events == null) {
            return null;
        }
        return events.stream()
                .filter(e -> e.getType() == AgentEventType.AGENT_RESULT)
                .map(e -> ((AgentResultEvent) e).getResult())
                .findFirst()
                .orElse(null);
    }

    /**
     * 提取消息中的文本内容
     * @param message
     * @return
     */
    private static String extractText(AgentMessage message) {
        if (message == null || message.getContent() == null) {
            return "";
        }
        return message.getContent().stream()
                .filter(AgentTextBlock.class::isInstance)
                .map(AgentTextBlock.class::cast)
                .map(AgentTextBlock::getText)
                .collect(Collectors.joining());
    }

    /**
     * 截断ID用于日志展示
     * @param id
     * @return
     */
    private static String shortId(String id) {
        return id == null ? "-" : id.length() <= 8 ? id : id.substring(0, 8);
    }

    /**
     * 读取环境变量，为空时返回默认值
     * @param key
     * @param defaultValue
     * @return
     */
    private static String env(String key, String defaultValue) {
        String value = System.getenv(key);
        return value != null && !value.isBlank() ? value : defaultValue;
    }

    /**
     * 内存Web检索提供方，返回固定搜索结果并统计调用次数
     * @author yangqiong
     */
    private static class InMemoryWebSearchProvider implements WebSearchProvider {

        /**
         * 搜索调用次数
         */
        private int searchCount = 0;

        @Override
        public List<WebSearchResult> search(String query, int topK) {
            searchCount++;
            return List.of(
                    new WebSearchResult("人工智能简介", "https://example.com/ai-intro",
                            "人工智能（AI）是计算机科学的一个分支，致力于创建能够模拟人类智能的系统。", 0.95),
                    new WebSearchResult("深度学习入门", "https://example.com/deep-learning",
                            "深度学习是机器学习的一个子集，使用多层神经网络进行学习。", 0.88)
            );
        }

        /**
         * 获取搜索调用次数
         * @return
         */
        int getSearchCount() {
            return searchCount;
        }
    }

    /**
     * 内存网页抓取提供方，返回固定页面内容并统计调用次数
     * @author yangqiong
     */
    private static class InMemoryWebFetcher implements WebFetcher {

        /**
         * 抓取调用次数
         */
        private int fetchCount = 0;

        @Override
        public String fetch(String url, int maxChars) {
            fetchCount++;
            return "这是" + url + "的模拟网页内容，包含一些示例文本用于测试。";
        }

        /**
         * 获取抓取调用次数
         * @return
         */
        int getFetchCount() {
            return fetchCount;
        }
    }
}
