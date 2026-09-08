# 泱穹智能体框架基础使用手册

> 本手册基于框架的实际测试用例梳理，覆盖从「引入依赖」到「复杂多 Agent 编排」的最常用功能点。所有示例均可在 `src/test/java/com/yangqiongai/agent/harness/` 对应测试中找到真实用例佐证。

## 目录

- [1. 环境与依赖](#1-环境与依赖)
- [2. 构建 Agent 运行时](#2-构建-agent-运行时)
- [3. 模型接入](#3-模型接入)
- [4. 消息与调用](#4-消息与调用)
- [5. 定义工具（AgentTool）](#5-定义工具agenttool)
- [6. 中间件（Middleware）](#6-中间件middleware)
- [7. 记忆与上下文压缩](#7-记忆与上下文压缩)
- [8. 权限控制](#8-权限控制)
- [9. 安全护栏](#9-安全护栏)
- [10. RAG 检索增强](#10-rag-检索增强)
- [11. 计划模式](#11-计划模式)
- [12. 子代理与多 Agent 编排](#12-子代理与多-agent-编排)
- [13. 群聊与辩论](#13-群聊与辩论)
- [14. 结构化输出](#14-结构化输出)
- [15. 持久化与断点续跑](#15-持久化与断点续跑)
- [16. 评测（EvalRunner）](#16-评测evalrunner)
- [17. 运行单元测试](#17-运行单元测试)
- [18. 智能体范式（paradigms 扩展）](#18-智能体范式paradigms-扩展)
- [19. Spring Boot Starter 接入](#19-spring-boot-starter-接入)

---

## 1. 环境与依赖

**环境要求**：JDK 17+、Maven 3.6+。

在 `pom.xml` 引入（框架**零 Spring 依赖**）：

```xml
<dependency>
    <groupId>com.yangqiongai.agent</groupId>
    <artifactId>yangqiong-agent-core</artifactId>
    <version>1.1.0</version>
</dependency>
```

**可选**：多模块场景建议通过 BOM 统一版本，后续引入任意模块（store-jdbc、store-redis、store-vector、spring-boot-starter 等）都无需再写版本号：

```xml
<dependencyManagement>
    <dependencies>
        <dependency>
            <groupId>com.yangqiongai.agent</groupId>
            <artifactId>yangqiong-agent-bom</artifactId>
            <version>1.1.0</version>
            <type>pom</type>
            <scope>import</scope>
        </dependency>
    </dependencies>
</dependencyManagement>
```

---

## 2. 构建 Agent 运行时

核心构建器为 `HarnessRuntimeBuilder`，通过链式调用组装运行时（参考 `HarnessAgentRuntimeTest` / `ReActLoopIntegrationTest`）。

```java
import com.yangqiongai.agent.harness.HarnessRuntimeBuilder;
import com.yangqiongai.agent.harness.core.AgentRuntime;
import com.yangqiongai.agent.harness.engine.AgentRuntimeContext;
import com.yangqiongai.agent.harness.core.message.MessageFactory;

// 构建运行时
AgentRuntime runtime = new HarnessRuntimeBuilder()
    .name("my-agent")
    .model(model)                       // 见第 3 节
    .systemPrompt("你是一个智能助手")
    .maxIters(10)                       // 最大推理迭代次数
    .build();
```

> 运行时同时支持同步 `call(...)` 与响应式流式 `stream(...)` 两种调用方式，见第 4 节。

---

## 3. 模型接入

统一通过 `AgentModel` 抽象对接多厂商模型（参考 `NewFeatureRealModelTest`）。推荐使用 `HarnessModelFactory` + `AgentModelRegistry`：

```java
import com.yangqiongai.agent.harness.core.model.AgentModel;
import com.yangqiongai.agent.harness.core.model.AgentGenerateOptions;
import com.yangqiongai.agent.harness.core.model.registry.AgentModelRegistry;
import com.yangqiongai.agent.harness.model.HarnessModelFactory;
import com.yangqiongai.agent.harness.model.HarnessModelProperties;
import com.yangqiongai.agent.harness.model.provider.OpenAIModelProvider;
import com.yangqiongai.agent.harness.model.provider.AnthropicModelProvider;
import com.yangqiongai.agent.harness.model.provider.DashScopeModelProvider;
import com.yangqiongai.agent.harness.model.provider.GeminiModelProvider;
import com.yangqiongai.agent.harness.model.provider.OllamaModelProvider;

AgentGenerateOptions options = AgentGenerateOptions.builder()
    .temperature(0.1)
    .maxTokens(4096)
    .build();

HarnessModelProperties properties = new HarnessModelProperties();
properties.setApiKey("YOUR_API_KEY");
properties.setBaseUrl("https://api.example.com");
properties.setTimeoutSeconds(120);

AgentModelRegistry registry = new AgentModelRegistry("openai");
registry.registerProviders(List.of(
    new OpenAIModelProvider(), new AnthropicModelProvider(),
    new DashScopeModelProvider(), new GeminiModelProvider(),
    new OllamaModelProvider()));

HarnessModelFactory factory = new HarnessModelFactory(properties, registry);
String modelCode = "openai:deepseek-v4-flash";  // provider:model 形式
AgentModel model = factory.getModel(modelCode, options);
```

也可直接通过构建器绑定模型：

```java
AgentRuntime runtime = new HarnessRuntimeBuilder()
    .name("agent")
    .model(model)
    .build();
```

**生产增强（可选项）**：

| 能力 | 构建器方法 |
| --- | --- |
| 模型路由/降级 | `runtimeFactory(...)` / `FallbackModel` |
| 重试 | `modelRetryConfig(ModelRetryConfig)` |
| 语义缓存 | `enableSemanticCaching(double threshold, int maxEntries)` |
| 速率限制 | `modelRateLimiter(RateLimiter)` |
| 成本预算 | `costBudgetPolicy(...)` / `tokenBudgetPolicy(...)` |
| 模型定价 | `modelPricing(ModelPricing...)` |

---

## 4. 消息与调用

使用 `MessageFactory` 构造消息，`AgentMessageRole` 区分角色（参考 `ReActEngineTest` / `ReActLoopIntegrationTest`）：

```java
import com.yangqiongai.agent.harness.core.event.AgentEvent;
import com.yangqiongai.agent.harness.core.event.AgentEventType;
import com.yangqiongai.agent.harness.core.message.AgentMessage;
import com.yangqiongai.agent.harness.core.message.MessageFactory;

AgentMessage userMsg = MessageFactory.createUserMessage("你好");

// 方式一：同步调用，返回最终 AgentMessage
AgentMessage result = runtime.call(
    List.of(userMsg),
    AgentRuntimeContext.empty()).block();

// 方式二：流式事件（文本增量 / 思考增量 / 工具调用增量 / 结果 / 完成）
runtime.stream(
    List.of(userMsg),
    AgentRuntimeContext.empty()
).subscribe(event -> {
    switch (event.getType()) {
        case TEXT_BLOCK_DELTA -> System.out.print(((AgentTextBlockDeltaEvent) event).getDelta());
        case THINKING_BLOCK_DELTA -> System.out.println("[思考] " + ...);
        case TOOL_CALL_DELTA -> System.out.println("[工具调用] " + ...);
        case AGENT_RESULT -> System.out.println("\n--- 完成 ---");
        case ERROR -> System.err.println("错误: " + event.getPayload());
    }
});
```

---

## 5. 定义工具（AgentTool）

实现 `AgentTool` 接口即可自定义工具（参考 `HarnessToolkitTest` 与 `TestAgentTools`）：

```java
import com.yangqiongai.agent.harness.core.tool.AgentTool;
import com.yangqiongai.agent.harness.core.tool.AgentToolCallParam;
import com.yangqiongai.agent.harness.core.message.AgentToolResultBlock;
import com.yangqiongai.agent.harness.core.message.AgentTextBlock;
import reactor.core.publisher.Mono;
import java.util.List;
import java.util.Map;

public class MyTool implements AgentTool {
    @Override public String getName() { return "my_tool"; }
    @Override public String getDescription() { return "我的自定义工具"; }
    @Override public Map<String, Object> getParameters() {
        return Map.of("param1", Map.of("type", "string", "description", "参数1"));
    }
    @Override public Mono<AgentToolResultBlock> callAsync(AgentToolCallParam param) {
        // 读取参数并执行逻辑
        return Mono.just(AgentToolResultBlock.of(
            List.of(AgentTextBlock.builder().text("执行结果").build())));
    }
}
```

注册工具到运行时，可使用 `HarnessToolkit`：

```java
import com.yangqiongai.agent.harness.tool.HarnessToolkit;

HarnessToolkit toolkit = new HarnessToolkit(null);
toolkit.addTool(new MyTool());

AgentRuntime runtime = new HarnessRuntimeBuilder()
    .name("agent")
    .model(model)
    .toolkit(toolkit)
    .build();
```

**内置工具族**（按需启用）：

- 文件系统：`enableFileToolkit(sandboxRoot, parser)`
- 联网搜索 / 网页抓取：`webSearchProvider(...)` / `webFetcher(...)` / `webEnabled(true)`
- MCP：`mcpServer(McpServerConfig)` / `mcpServers(...)`
- RAG 检索：`enableRagRetrieval(topK, template)`，见第 10 节

---

## 6. 中间件（Middleware）

中间件可在 ReAct 循环的**推理、行动、输出**等阶段插入逻辑（参考 `MiddlewareChainTest`）。自定义中间件：

```java
import com.yangqiongai.agent.harness.core.middleware.AgentMiddleware;
import com.yangqiongai.agent.harness.engine.AgentRuntimeContext;
import reactor.core.publisher.Flux;

AgentMiddleware middleware = new AgentMiddleware() {
    @Override public String onSystemPrompt(String prompt, AgentRuntimeContext ctx) {
        return prompt + "\n请用中文回答。";   // 修改系统提示词
    }
    @Override public Flux<AgentEvent> onReasoning(AgentRuntimeContext ctx,
                                                  List<AgentMessage> messages,
                                                  Function<List<AgentMessage>, Flux<AgentEvent>> next) {
        // 推理前拦截
        return next.apply(messages);
    }
};

AgentRuntime runtime = new HarnessRuntimeBuilder()
    .name("agent")
    .model(model)
    .middleware(middleware)          // 支持多次调用追加
    .build();
```

**内置中间件快捷开关**：

| 中间件 | 构建器入口 |
| --- | --- |
| 输入护栏 | `enableInputGuardrail()` |
| 工具护栏 | `enableToolGuardrail()` |
| 内容审核 | `contentModeration(policy)` |
| 上下文压缩 | `compactionConfig(AgentCompactionConfig)` |
| 工具结果清理 | `toolResultEvictionConfig(...)` -> `maxToolResultChars(...)` |
| 摘要压缩 | `summaryCompactionEnabled(true)` |
| 记忆检索 | `memoryRetrievalEnabled(true)` / `memoryRetrievalTopK(...)` |
| 追踪 | `traceEmitter(TraceEmitter)` |

---

## 7. 记忆与上下文压缩

配置压缩阈值，超过后自动摘要（参考 `CompactionMiddlewareTest`）：

```java
import com.yangqiongai.agent.harness.config.AgentCompactionConfig;
import com.yangqiongai.agent.harness.middleware.CompactionMiddleware;

AgentCompactionConfig config = AgentCompactionConfig.builder()
    .triggerMessages(5)   // 触发压缩的消息条数
    .keepMessages(2)      // 压缩后保留条数
    .build();

AgentRuntime runtime = new HarnessRuntimeBuilder()
    .name("agent")
    .model(model)
    .compactionConfig(config)
    .build();
```

**长短期记忆**（参考 `HarnessRuntimeBuilderMemoryTest`、`LongTermMemoryAdapterTest`）：

```java
import com.yangqiongai.agent.harness.config.AgentMemoryConfig;
import com.yangqiongai.agent.harness.memory.VectorLongTermMemory;
import com.yangqiongai.agent.harness.memory.ForgettingPolicy;
import com.yangqiongai.agent.harness.memory.AgeForgettingPolicy;

// 向量长期记忆（需 embedding 模型）
AgentMemoryConfig memoryConfig = AgentMemoryConfig.builder()...build();

AgentRuntime runtime = new HarnessRuntimeBuilder()
    .name("agent")
    .model(model)
    .memoryConfig(memoryConfig)
    .longTermMemory(new VectorLongTermMemory(...))
    .sessionMemory(...)
    .memoryForgettingPolicy(new AgeForgettingPolicy(...))   // 按时间遗忘
    .build();
```

---

## 8. 权限控制

`AgentPermissionMode` 定义五种管控级别，`PermissionEngine` 评估工具是否放行（参考 `reactLoop_permission_toolBlocked` 用例）：

```java
import com.yangqiongai.agent.harness.config.AgentPermissionContextState;
import com.yangqiongai.agent.harness.config.AgentPermissionMode;

// 只读模式：禁止写/破坏性工具
AgentPermissionContextState state = AgentPermissionContextState.builder()
    .mode(AgentPermissionMode.EXPLORE)
    .build();

AgentRuntime runtime = new HarnessRuntimeBuilder()
    .name("agent")
    .model(model)
    .permissionContextState(state)
    .requireApproval(Set.of("delete_file"))   // 指定需人工审批的工具
    .build();
```

其他模式：`ACCEPT_EDITS`（允许非破坏修改）、`ASK`（破坏性操作需确认）、`DONT_ASK`/`BYPASS`（逐级放宽）。危险工具会触发 `RequireUserConfirmEvent` 请求用户确认。

---

## 9. 安全护栏

框架内置输入/输出护栏与内容审核（参考 `InputGuardrailMiddlewareTest`、`ContentModerationMiddlewareTest`、`GuardrailRuleRegistryTest`）。

**内容审核**：

```java
import com.yangqiongai.agent.harness.guardrail.ContentModerationPolicy;
import com.yangqiongai.agent.harness.guardrail.ModerationVerdict;

ContentModerationPolicy policy = content -> ModerationVerdict.of(true, "内容合规"); // 实现合规策略
AgentRuntime runtime = new HarnessRuntimeBuilder()
    .name("agent")
    .model(model)
    .contentModeration(policy)
    .build();
```

**输入/工具护栏**：

```java
import com.yangqiongai.agent.harness.guardrail.GuardrailRuleRegistry;

AgentRuntime runtime = new HarnessRuntimeBuilder()
    .name("agent")
    .model(model)
    .enableInputGuardrail()                       // 对用户输入做护栏
    .enableToolGuardrail(new GuardrailRuleRegistry()) // 对工具调用做护栏
    .build();
```

---

## 10. RAG 检索增强

通过 `RetrieverRegistry` 注册检索源，`RetrieverTool` 暴露检索能力（参考 `RagRetrievalMiddlewareTest`、`RetrieverToolTest`）：

```java
import com.yangqiongai.agent.harness.rag.Retriever;
import com.yangqiongai.agent.harness.rag.RetrieverRegistry;
import com.yangqiongai.agent.harness.rag.InMemoryRetriever;
import com.yangqiongai.agent.harness.rag.RetrievedChunk;

// 实现检索器
Retriever myRetriever = (query, topK, filter) -> List.of(
    new RetrievedChunk("知识库内容...", 0.95, null));
RetrieverRegistry registry = new RetrieverRegistry();
registry.register("knowledges", myRetriever);

// 在运行时启用 RAG 注入
AgentRuntime runtime = new HarnessRuntimeBuilder()
    .name("agent")
    .model(model)
    .retriever("knowledges", myRetriever)
    .enableRagRetrieval(3, "相关检索资料：\n")   // topK=3
    .build();
```

> 检索片段会自动经 `GuardrailFence` 加围栏标记为**不可信数据**，防御间接提示注入。

### 10.1 向量存储与外部向量库

`yangqiong-agent-store-vector` 提供 `VectorIndex` SPI：Lucene 本地落盘索引为默认实现，pgvector / Milvus / Qdrant 适配器为独立 Maven 模块（不引入即零依赖），上层 `PersistentVectorMemory`（长期记忆）与 `VectorRetriever`（RAG 语义检索）依赖 SPI，切换后端零改动。

```java
import com.yangqiongai.agent.harness.store.vector.PersistentVectorStore;
import com.yangqiongai.agent.harness.store.vector.VectorIndex;
import com.yangqiongai.agent.harness.store.vector.VectorIndexes;

// Lucene 本地落盘（零外部依赖，数据目录自动创建）
try (PersistentVectorStore store = PersistentVectorStore.open(Path.of("data/vector"), embeddingModel)) {
    store.memory().store("tenant-a", "user-1", "s1", "长期记忆内容", Map.of());
    store.vectorRetriever().addDocument("doc-1", "文档块内容", Map.of("category", "guide"));
    AgentRuntime runtime = new HarnessRuntimeBuilder()
        .name("agent").model(model)
        .retriever("knowledge", store.retriever())
        .longTermMemory(store.memory())
        .build();
}

// 外部向量服务：按配置经 ServiceLoader 装配（harness.vector.store=pgvector/milvus/qdrant）
VectorIndex index = VectorIndexes.create("pgvector", Map.of(
    "jdbcUrl", "jdbc:postgresql://localhost:5432/agent",
    "user", "postgres", "password", "postgres",
    "table", "harness_vector", "metric", "cosine"));
try (PersistentVectorStore store = PersistentVectorStore.open(index, embeddingModel)) {
    // 用法与 Lucene 完全一致
}
```

适配器与配置要点：

| 存储 | artifactId | 运行期依赖 | 关键配置键（`VectorIndexes.create` 第二参） |
| --- | --- | --- | --- |
| Lucene（默认） | `yangqiong-agent-store-vector` | 无 | `dataDir`（落盘目录）；相似度固定 cosine |
| pgvector | `yangqiong-agent-store-vector-pgvector` | PostgreSQL 驱动（类路径） | `jdbcUrl` / `user` / `password` / `table`（缺省 `harness_vector`）/ `metric`（cosine/dot/l2） |
| Milvus | `yangqiong-agent-store-vector-milvus` | milvus-sdk-java | `uri` / `token` / `metric` |
| Qdrant | `yangqiong-agent-store-vector-qdrant` | io.qdrant:client（gRPC） | `uri` / `apiKey` / `metric` |

- SPI 集合即隔离单元：Lucene 为 scope 复合桶字段、pgvector 为表内 `collection` 列（单表多集合，维度建表时固定）、Milvus/Qdrant 为真实 collection（按需懒创建）
- 元数据过滤统一为 `MetadataFilter`（等值/IN/范围，AND 组合），独立后端以内存兜底求值，口径与 Lucene 一致
- 各后端均无 XA 语义，向量写入失败以重试补偿；pgvector 支持传入 `DataSource` 复用外部连接池（不参与业务事务）
- 契约测试基类 `VectorIndexContractTest`（store-vector test-jar）覆盖全部后端一致行为，适配器契约测试经环境变量注入连接信息（`HARNESS_PGVECTOR_JDBC_URL` / `HARNESS_MILVUS_URI` / `HARNESS_QDRANT_URI`），未配置自动跳过

---

## 11. 计划模式

让 LLM 先制定计划再逐步执行（参考 `reactLoop_planMode_planCreatedAndExecuted` 用例）：

```java
AgentRuntime runtime = new HarnessRuntimeBuilder()
    .name("agent")
    .model(model)
    .planModeEnabled(true)          // 或 enablePlanMode()
    .build();
```

计划过程通过 `PlanModeMiddleware` 注入计划工具（`plan_write` 等），计划结果存入运行时上下文键 `PlanModeMiddleware.ATTR_PLAN_MODE_TOOLS`。

---

## 12. 子代理与多 Agent 编排

**声明式子代理**（参考 `SubagentsMiddlewareTest`、`AgentSpawnToolTest`）：

```java
import com.yangqiongai.agent.harness.subagent.orchestration.SubagentDeclaration;
import com.yangqiongai.agent.harness.engine.ReActEngine; // 子代理用相同引擎

SubagentDeclaration coder = SubagentDeclaration.builder()
    .name("coder")
    .description("代码生成代理")
    .modelCode(modelCode)
    .systemPrompt(systemPrompt)
    .tools(toolNames)
    .build();

AgentRuntime runtime = new HarnessRuntimeBuilder()
    .name("coordinator")
    .model(model)
    .subagentDeclarations(List.of(coder))
    .enableSubagents()               // 启用子代理委派
    .build();
```

**自动编排策略**（参考 `AutoOrchestrationEngineTest`、`AutoOrchestrateToolTest`）：

```java
import com.yangqiongai.agent.harness.subagent.orchestration.OrchestrationStrategy;

AgentRuntime runtime = new HarnessRuntimeBuilder()
    .name("coordinator")
    .model(model)
    .enableSubagents()
    .orchestrationStrategy(OrchestrationStrategy.ADAPTIVE) // 顺序/并行/自适应/辩论/反思/群聊
    .orchestrationRounds(2)                                // 编排轮次
    .maxOrchestrationRounds(5)
    .build();
```

**多代理交接（Handoff）**（参考 `HandoffToolTest`、`HandoffRegistryTest`）：

```java
AgentRuntime supportAgent = new HarnessRuntimeBuilder().name("support").model(model2).build();
AgentRuntime orderAgent = new HarnessRuntimeBuilder().name("order").model(model3).build();

AgentRuntime coordinator = new HarnessRuntimeBuilder()
    .name("coordinator")
    .model(model)
    .handoffTarget("support", supportAgent)   // 注册可交接目标
    .handoffTarget("order", orderAgent)
    .build();
```

**子代理挂载范式引擎**（参考 `SubagentSpawner`，需引入 `yangqiong-agent-paradigms`，见第 18 节）：

```java
SubagentDeclaration coder = SubagentDeclaration.builder()
    .name("coder")
    .description("代码生成代理")
    .modelCode(modelCode)
    .systemPrompt(systemPrompt)
    .tools(toolNames)
    .agentLoop(new ReWooEngine())    // 子代理以 ReWoo 范式执行，减少模型调用轮次
    .build();

AgentRuntime runtime = new HarnessRuntimeBuilder()
    .name("coordinator")
    .model(model)
    .subagentDeclarations(List.of(coder))
    .enableSubagents()
    .build();
```

---

## 13. 群聊与辩论

**群聊 / 消息中枢（MsgHub）**（参考 `MsgHubTest`）：多 Agent 经消息中枢**轮转发言**，每个代理看到前序发言后继续讨论。

```java
import com.yangqiongai.agent.harness.subagent.orchestration.MsgHub;
import com.yangqiongai.agent.harness.engine.AgentRuntimeContext;

MsgHub hub = new MsgHub();
hub.register(agentA);
hub.register(agentB);
hub.register(agentC);

// 方式一：广播消息，收集所有参与者回复
Map<String, String> replies = hub.broadcast("讨论本周订单优化", AgentRuntimeContext.empty()).block();

// 方式二：轮询讨论，多轮迭代产出讨论历史
List<AgentMessage> discussion = hub.roundRobin("如何提升转化率", AgentRuntimeContext.empty(), 3).block();
```

**辩论（DEBATE）**：在编排策略中启用 `OrchestrationStrategy.DEBATE`，由 `DebateJudge` 综合各方立场并给出反馈，多轮迭代收敛（参考 `AutoOrchestrationEngineTest`）。自定义裁判实现 `DebateJudge#judge(...)`。

**反思（REFLECTION）**：`OrchestrationStrategy.REFLECTION`——执行者作答 → 评审者批判 → 执行者修订。

---

## 14. 结构化输出

强制模型按 JSON Schema 输出，失败自动重试（参考 `ReActEngineStructuredOutputRetryTest`、`StructuredOutputValidatorTest`）：

```java
import com.yangqiongai.agent.harness.config.AgentResponseFormat;
import com.yangqiongai.agent.harness.config.AgentJsonSchema;

AgentResponseFormat format = AgentResponseFormat.builder()
    .strictJson(true)
    .schema(AgentJsonSchema.of("{...}"))   // JSON Schema
    .build();

AgentRuntime runtime = new HarnessRuntimeBuilder()
    .name("agent")
    .model(model)
    .responseFormat(format)
    .structuredOutputRetry(3, "输出不符合要求，请修正")   // 最多重试3次
    .build();
```

**厂商适配三层体系**：不同厂商对结构化输出支持程度不一，框架按三层策略自动适配——原生格式优先（OpenAI / DashScope / Ollama 使用厂商原生 JSON Mode 参数）、预降级（DeepSeek 等不支持原生的厂商自动注入提示词约束）、校验重试兜底（引擎侧统一按 Schema 校验，失败携带反馈自动重试）。

---

## 15. 持久化与断点续跑

框架内置内存版持久化存储，可替换为 JDBC 等实现（参考 `InMemoryDurableStoreBoundTest`、`InMemoryRunLockStoreTest`、`DistributedCrossNodeTest`）：

```java
import com.yangqiongai.agent.harness.durable.InMemoryAgentRunStore;
import com.yangqiongai.agent.harness.durable.InMemoryCheckpointStore;
import com.yangqiongai.agent.harness.durable.InMemoryApprovalStore;
import com.yangqiongai.agent.harness.durable.InMemoryRunLockStore;

AgentRuntime runtime = new HarnessRuntimeBuilder()
    .name("agent")
    .model(model)
    .agentRunStore(new InMemoryAgentRunStore())       // 运行记录
    .checkpointStore(new InMemoryCheckpointStore())   // 检查点/断点续跑
    .approvalStore(new InMemoryApprovalStore())       // 审批记录
    .runLockStore(new InMemoryRunLockStore())         // 分布式运行锁
    .nodeId("node-1")                                // 跨节点标识
    .build();
```

---

## 16. 评测（EvalRunner）

批量运行评测用例，`LLMEvalJudge` / `RuleEvalJudge` 双模式裁决（参考 `EvalRunnerTest`、`EvalRunnerJudgeTest`）：

```java
import com.yangqiongai.agent.harness.eval.EvalCase;
import com.yangqiongai.agent.harness.eval.EvalDataset;
import com.yangqiongai.agent.harness.eval.EvalRunner;
import com.yangqiongai.agent.harness.eval.EvalReport;
import com.yangqiongai.agent.harness.eval.RuleEvalJudge;

EvalDataset dataset = EvalDataset.of(List.of(
    EvalCase.of("问题", "期望输出")
));
EvalReport report = EvalRunner.builder()
    .runtime(runtime)                  // 待测 Agent 运行时
    .dataset(dataset)
    .judge(new RuleEvalJudge())        // 或 LLMEvalJudge(model)
    .timeoutPerCase(Duration.ofSeconds(30))
    .build()
    .run();

System.out.println(report);            // 输出评分报告
```

---

## 17. 运行单元测试

```bash
# 运行全部测试（单元 + 集成 + 边界 + 生产就绪）
mvn test

# 只运行某个测试类
mvn test -Dtest=MsgHubTest

# 真实模型集成测试（需配置密钥，否则自动跳过）
export HARNESS_TEST_API_KEY=your-key
mvn test -Dtest=NewFeatureRealModelTest
```

测试覆盖说明：

| 测试目录 | 覆盖内容 |
| --- | --- |
| `engine/` | ReAct 循环、权限、压缩、成本预算、结构化输出重试 |
| `middleware/` | 各中间件独立行为 |
| `memory/` | 检查点、遗忘策略、向量记忆、会话记忆 |
| `orchestration/` | 群聊 MsgHub、交接 Handoff、自动编排、AskUser |
| `subagent/` | 子代理派生、工具注册、能力策略 |
| `durable/` | 持久化存储边界、运行锁、跨节点 |
| `eval/` | 评测执行与裁判 |
| `mcp/` | MCP 协议客户端与传输 |
| `rag/` / `web/` / `tool/` | 检索、Web、工具执行 |
| `integration/` | ReAct 全流程、多代理多轮交接、真实模型 |

---

## 18. 智能体范式（paradigms 扩展）

引入 `yangqiong-agent-paradigms` 扩展后，可在 ReAct 基座之外使用 5 大范式引擎与 Router 自动路由（参考 `ParadigmEngineRealModelTest`、`RouterEngineTest` 与示例 `ParadigmExample`）：

```xml
<dependency>
    <groupId>com.yangqiongai.agent</groupId>
    <artifactId>yangqiong-agent-paradigms</artifactId>
    <version>1.1.0</version>
</dependency>
```

**范式一览**：

| 引擎 | 决策结构 | 适用场景 |
| --- | --- | --- |
| `PlanExecuteEngine` | 先规划完整计划，再逐步执行 | 目标明确、可预先分解的复杂任务 |
| `ReWooEngine` | 一次性规划按依赖执行后统一推理 | 减少模型调用轮次、降低延迟成本 |
| `ReflexionEngine` | 自评估失败后携带教训重试 | 容错重试、从失败中学习 |
| `SelfAskEngine` | 复合问题拆解为子问题逐个作答 | 多跳问答、显式问题分解 |
| `SelfRefineEngine` | 生成→批评→修订循环 | 对输出质量有高要求的打磨场景 |
| `RouterEngine` | 元层路由，自动选择上述范式 | 不确定任务该用哪个范式时 |

**范式公共配置**（`ParadigmOptions`）：

```java
import com.yangqiongai.agent.harness.paradigms.support.ParadigmOptions;

ParadigmOptions options = new ParadigmOptions()
    .maxSteps(10)            // 单次执行最大步数，默认 10
    .maxReflections(2)       // 最大反思次数（Reflexion），默认 2
    .maxRefinements(2);      // 最大修订次数（Self-Refine），默认 2

AbstractAgentLoop engine = new ReflexionEngine(options);
```

**挂载到运行时**：通过 `HarnessRuntimeBuilder.agentLoop(...)` 挂载，不挂载时默认使用 ReAct 基座：

```java
import com.yangqiongai.agent.harness.paradigms.ReWooEngine;
import com.yangqiongai.agent.harness.paradigms.RouterEngine;

// 挂载指定范式
AgentRuntime runtime = new HarnessRuntimeBuilder()
    .name("agent")
    .model(model)
    .toolkit(toolkit)
    .agentLoop(new ReWooEngine(new ParadigmOptions().maxSteps(8)))
    .build();

// Router 自动路由：启发式预筛 + 轻量模型分类轮，路由决策以 ENGINE_ROUTED 事件输出
AgentRuntime routerRuntime = new HarnessRuntimeBuilder()
    .name("router")
    .model(model)
    .toolkit(toolkit)
    .agentLoop(new RouterEngine())
    .build();
```

Router 路由规则：无工具时自动排除 ReAct / Plan-Execute / ReWoo；分类输出不可解析时回退兜底（有工具 ReAct / 无工具 Self-Ask）；确定性场景可用 `new RouterEngine(resolver, RouterEngine.ParadigmType.REWOO)` 强制指定范式跳过分类轮。

**统一暂停恢复语义**：所有范式引擎继承 `AbstractAgentLoop` 模板，与 ReAct 基座共享同一套暂停/恢复能力——危险工具审批挂起后经 `resume(confirmResults, context)` 账本重放恢复；人工澄清（ask_user）挂起后经 `resumeWithClarification(answers, context)` 按问题文本匹配续跑。

---

## 19. Spring Boot Starter 接入

Spring Boot 3 项目可引入 `yangqiong-agent-spring-boot-starter`，以 `ai.harness.*` 配置前缀自动装配运行时，无需手动构建：

```xml
<dependency>
    <groupId>com.yangqiongai.agent</groupId>
    <artifactId>yangqiong-agent-spring-boot-starter</artifactId>
    <version>1.1.0</version>
</dependency>
```

**自动装配的 Bean**：

| Bean | 条件 | 说明 |
| --- | --- | --- |
| `AgentModelRegistry` / `HarnessModelFactory` / `AgentModel` | `ai.harness.model.enabled`（默认 true） | 注册 OpenAI/Anthropic/DashScope/Gemini/Ollama 五家协议提供方，按 `model-name` 产出默认模型 |
| `DistributedStores` | `ai.harness.store.type=memory`（默认） | 内存存储聚合；注入自定义 `DistributedStores` Bean 后自动失效 |
| `TraceEmitter` | `ai.harness.tracing.enabled=true` | 日志型追踪导出器，默认不装配 |
| `PersistentVectorStore` / `Retriever` | `ai.harness.vector.enabled=true` 且类路径有 store-vector | 向量存储与语义检索器，见下文 |
| `HarnessRuntimeBuilder` / `AgentRuntime` | `ai.harness.runtime.enabled`（默认 true）且模型就绪 | 组装全部组件构建运行时 |

**核心配置项**（`application.yml`）：

```yaml
ai:
  harness:
    model:
      default-provider: dashscope        # openai / anthropic / dashscope / gemini / ollama
      model-name: deepseek-v3
      providers:
        dashscope:
          api-key: ${DASHSCOPE_API_KEY}
          base-url: https://dashscope.aliyuncs.com/compatible-mode/v1
    runtime:
      name: my-agent                     # Agent 名称
      system-prompt: 你是一个智能助手
      max-iters: 10                      # 最大推理迭代次数
      web-enabled: false                 # Web 工具
      ask-user-enabled: false            # 向用户提问工具
      plan-mode-enabled: false           # 计划模式
      subagents-enabled: false           # 多子代理
    store:
      type: memory                       # memory / jdbc / redis（jdbc/redis 由扩展模块提供）
    tracing:
      enabled: false                     # 日志追踪
```

**向量存储（可选）**：类路径额外引入 `yangqiong-agent-store-vector`（本地 Lucene）或任一适配器模块（`store-vector-milvus` / `store-vector-qdrant` / `store-vector-pgvector`），即可通过 `ai.harness.vector` 装配：

```yaml
ai:
  harness:
    vector:
      enabled: true
      store: lucene                      # lucene / milvus / qdrant / pgvector
      config:
        dataDir: ./data/vector           # 透传给对应适配器的归一化键值
```

- `config` 键值按适配器约定**原样透传**（Spring 不转换 Map 键名，须直接写适配器要求的键名形式，否则适配器报缺少配置）：Lucene 用 `dataDir`，Milvus 用 `uri` / `token`，Qdrant 用 `uri` / `api-key`，pgvector 用 `jdbcUrl` / `user` / `password` / `table`
- 容器内已注册 `EmbeddingModel` Bean 时优先使用，否则降级为零依赖的哈希嵌入模型
- 装配产出 `PersistentVectorStore`（含长期向量记忆 `memory()`）与跨集合语义检索器 `Retriever` 两个 Bean；自定义 `Retriever` Bean 注入后检索器装配自动失效

---

© 泱穹智能体框架。更多架构与特性见项目 [README.md](./README.md)。
