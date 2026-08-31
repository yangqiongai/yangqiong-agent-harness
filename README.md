[English](./README.en.md) | 中文

![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)
![JDK](https://img.shields.io/badge/JDK-17-orange.svg)
![Gitee](https://gitee.com/yangqiongtech/yangqiong-agent-harness/badge/star.svg)

# 泱穹 Agent Harness（泱穹智能体框架）

> **泱穹智能体框架** 是一套用**纯 Java** 构建的**企业级 AI Agent 运行时框架**：**ReAct 循环 + 六大范式引擎（含自动路由）、子代理与多代理编排、断点持久化执行、权限与安全护栏、全链路可观测与评测**，**零 Spring 依赖**，基于 Reactor 响应式事件流驱动。
>
> 从模型接入、工具执行、记忆管理到多代理编排、评测与生产治理，为大型企业构建**稳定、可控、可观测、可扩展**的智能体应用提供一站式运行时底座。

***

## 为企业级 Java 而生

智能体落地的真正考场是企业生产环境：要对接存量系统、要过安全合规、要融入既有工程体系。泱穹智能体框架原生构建于 Java 之上，让企业的现有投入直接成为智能体的能力底座：

### 1. 成熟的工程生态，降低落地风险

- Java 拥有 20 余年企业级沉淀，工具链（Maven/Gradle、IDEA、SonarQube、Jenkins）、依赖仓库、运维体系（JVM 监控、APM、容器化）全部成熟，可直接复用企业现有 CI/CD 与监控设施，无需引入新的运行时语言。

### 2. 强类型与编译期校验，AI 应用更稳定

- AI 应用的输出天然不确定，而 Java **静态强类型 + 编译期校验**能在早期发现工具定义、消息结构、配置绑定等错误；接口抽象与泛型让工具注册、模型协议适配等在编译期即可自洽。

### 3. 天然集成 Java 海量企业存量系统

- 企业庞大的 **Spring 技术栈、微服务、中间件（消息队列、缓存、分布式事务）、ORM 数据层、ERP/CRM 核心系统**均为 Java 生态。泱穹智能体框架**零重写集成** —— 通过 Java 工具与 MCP 直接对接这些系统，Agent 能像调用本地方法一样编排企业既有服务，将 LLM 的智能无缝注入核心业务流。

### 4. 卓越的并发与性能底座

- 基于 JVM 成熟的线程模型与 **响应式编程**，以极低的线程资源支撑高并发工具编排与流式输出；JVM 的 GC 调优、线程池治理、故障隔离均是经过生产验证的企业级能力。

### 5. 严苛的安全与合规体系

- Java 生态完备的**权限框架（Spring Security/Shiro）、审计与日志体系、加解密与合规工具**，为智能体工具调用提供企业级安全管控，满足金融、政务等高合规行业要求。

***

## 当前 Java 智能体框架现状分析

### 1. 现状总览：Java 智能体框架进入"多强争霸"

过去两年，Python 一直主导 AI 智能体开发，Java 开发者往往只能手动拼接 HTTPS 调用。**截至 2026 年**，这一局面已被彻底改写，Java 已形成多个生产可用框架：

| 框架                         | 定位                             | 框架形态                          | 规划模式                 | MCP | 断点持久化      | 多范式引擎/自动路由 | 多代理编排                     | 子代理动态生成                                        | 工具可靠执行                                 | 中间件链       | 评测/可观测                              | 扩展生态                       |
| -------------------------- | ------------------------------ | ----------------------------- | -------------------- | --- | ---------- | ---------- | ------------------------- | ---------------------------------------------- | -------------------------------------- | ---------- | ----------------------------------- | -------------------------- |
| **Spring AI**              | Spring 官方 AI 生态（Broadcom）      | ⚠️ 工具/组装类（Agent 循环需自行拼装）      | ReAct 循环             | ✅   | ❌          | ❌          | ❌                         | ❌                                              | ⚠️                                     | ✅ Advisor  | ✅ / ✅ Micrometer                    | ✅ Spring 生态                |
| **LangChain4j**            | 框架无关、20+ 模型提供方                 | ⚠️ 工具/组装类（AI Services 组装式）    | ReAct 循环             | ✅   | ❌          | ❌          | ❌                         | ❌                                              | ⚠️                                     | ⚠️ 事件监听    | ❌ / ✅ 事件观测                          | ✅ 模块化 SPI                  |
| **Semantic Kernel (Java)** | Microsoft 跨语言 SDK              | ⚠️ 组装类 SDK（Java 端 Agent 弱）    | ReAct 循环             | ✅   | ❌          | ❌          | ⚠️ Java 端有限               | ⚠️                                             | ⚠️ Filter 可拦截                          | ✅ Filter   | ❌ / ✅ OTel                          | ⚠️ 连接器较少                   |
| **Google ADK (Java)**      | Google Agent 开发套件              | ✅ 开箱智能体框架                     | 分层式 + Planner        | ✅   | ✅          | ⚠️ Planner | ✅ 顺序/并行/循环/移交             | ⚠️ 构建时组合                                       | ⚠️                                     | ✅ Callback | ✅ / ✅                               | ⚠️ Google 生态为主             |
| **AgentScope (Java)**      | 阿里开源多智能体框架（ReAct + Harness 工程） | ✅ 开箱智能体框架                     | ReAct + Plan/Reflect | ✅   | ✅（分布式会话恢复） | ⚠️ 编排策略    | ⚠️ Supervisor 派生 + A2A 委派 | ✅ Markdown 声明 + 运行时派生                          | ⚠️ 异步工具                                | ✅ 多阶段 Hook | ❌ / ✅ Studio                        | ⚠️ 模块化但生态起步                |
| **泱穹 Agent Harness**       | **企业级 Java Agent 运行时**         | ✅ **开箱智能体框架（引擎/范式/编排/评测全内建）** | 6 大规划模式 + 自动路由       | ✅   | ✅          | ✅ **6 范式** | ✅ **六策略编排**               | ✅ **声明式 builder 全套可配（模型/工具/权限/范式挂载）+ 运行时动态派生** | ✅ **Schema 校验 + 超时包装 + 退避重试 + 故障注入测试** | ✅ 响应式洋葱模型  | ✅ **双裁判评测 + 稳定性跑批** / ✅ OTel + 成本核算 | ✅ **12 官方扩展模块 + SPI 自动发现** |

> 参考背景：Spring AI 依托 Spring 生态推出便捷 Starter；LangChain4j 2025 年 5 月发布 1.0 GA，据 JetBrains 调研获得较高开发者采用率，因其框架无关、提供方覆盖广、内置护栏等特性。两者均达成 1.0 GA 并支持 MCP 协议。AgentScope Java 2.0 于 2026 年 GA，以"双层 Agent 架构"（ReActAgent 推理核心 + HarnessAgent 工程层）与分布式部署见长。

### 2. 现状痛点：企业落地仍有"最后一公里"缺口

成熟框架让 Java 组上 LLM 变得简单，但当面向**真正的高价值生产系统**时，普遍存在以下缺口：

- **框架强耦合**：Spring AI 深度绑定 Spring Boot；LangChain4j 虽框架无关，但在 Spring 下仍需手动装配。难以在纯 Java / 多框架环境统一落地。
- **断点持久化普遍缺失**：上表可见 Spring AI、LangChain4j、Semantic Kernel 均未提供**工作流中途状态持久化（Checkpointing）**，长任务中断后无法续跑。
- **企业级运行治理缺位**：多轮会话缺乏成本预算、Token 计量、限流、模型路由/降级等生产管控；跨节点分布式的运行锁与一致调度少见。
- **编排大多停留在"单 Agent + 工具"**：多 Agent 交接、任务自动编排、子代理互斥与结果聚合多是搬用概念，缺少精细的状态机与治理支撑。

### 3. 泱穹：补上 Java 企业级智能体的"最后一公里"

泱穹智能体框架正是针对上述缺口而设计，与前代框架形成**互补而非重复造轮子**的差异化定位：

- ✅ **零框架强耦合**——不强制集成 Spring，纯 Java SE 即可运行，天然适配 Spring 全家桶 / Quarkus / 微服务任意场景
- ✅ **一等公民的持久化**——内置 `AgentRunStore`/`CheckpointStore` 断点续跑、`RunLockStore` 分布式互斥，跨节点一致调度（权威断路器，不做"假续跑"）
- ✅ **全套生产治理开箱即用**——成本预算、Token 计量、模型路由/降级/重试/语义缓存/限流，企业无需自行拼装
- ✅ **成熟的多 Agent 编排**——子代理委派、任务自动编排、多 Agent 上下文交接（Handoff）、结果聚合，配套状态机与权限审批
- ✅ **强工程保障**——静态强类型、编译期校验，core 模块 900+ 单元测试（100+ 测试类）护航，安全护栏与审计闭环

> 简单说：Spring AI 与 LangChain4j 解决了"Java 能不能接 LLM"，泱穹智能体框架负责解决"接了之后能否稳定可控地跑进企业生产系统"。

***

## 核心特性

| 能力域   | 特性                                                                                                     |
| ----- | ------------------------------------------------------------------------------------------------------ |
| 核心运行  | ReAct 推理循环、响应式流式输出、事件驱动、多轮上下文管理                                                                        |
| 模型层   | 多厂商模型接入（OpenAI/Anthropic/DashScope/Ollama/Gemini/HTTP）、统一协议适配、模型路由/降级/重试/缓存/限流/成本追踪                    |
| 工具层   | 通用工具执行器、工具输入校验、工具过滤器、工具结果清理                                                                            |
| 记忆层   | 会话记忆、长期记忆、语义向量检索、LRU 检查点、遗忘策略、上下文压缩                                                                    |
| 多代理   | 子代理委派（可挂载任意范式引擎）、六大编排策略（顺序/并行/自适应/辩论/反思/群聊）、多代理交接（Handoff）、消息中枢、结果聚合                                   |
| 智能体范式 | ReAct 基座 + Plan-Execute、ReWoo、Reflexion、Self-Ask、Self-Refine 五大范式引擎 + Router 元层自动路由，统一审批挂起/账本恢复/人工澄清语义 |
| 安全    | 工具权限引擎、内容审核、输入/输出护栏、提示注入检测、审计日志                                                                        |
| 可靠性   | 持久化运行存储、断点续跑、分布式跨节点、结构化输出重试                                                                            |
| 可观测   | 全链路追踪、Token 计量、成本核算、评测体系、评分报告                                                                          |
| 规划    | 计划模式（Plan Mode）、MCP 模型上下文协议、技能（Skill）管理、RAG 检索增强                                                       |

***

## 架构总览

### 分层架构

![泱穹 Agent Harness 分层架构图](https://yangqiong-1306352078.cos.ap-shanghai.myqcloud.com/website/biz/architecture-overview.jpg)

***

## 功能模块详解

### 模型接入层（model）

统一 `AgentModel` 抽象，一套接口对接多模型厂商，并内置完整的生产级增强能力。

- **多厂商支持**：OpenAI、Anthropic、通义千问 DashScope、Gemini、Ollama、通用 HTTP
- **协议适配**：`OpenAI / Anthropic / DashScope / Ollama / Gemini` 协议适配器，屏蔽厂商请求/响应与流式差异（OpenAI 内置于 core，其余经 `yangqiong-agent-model-providers` 扩展提供，支持 SPI 自动发现）
- **模型路由**：`HealthAwareModelRouter` 基于健康状态在候选模型间路由，提升可用性
- **降级**：`FallbackModel` 主模型失败自动切换备用模型
- **重试**：`RetryableModelCaller` 支持退避重试
- **缓存**：`CachingModelCaller` + `SemanticCachingModelCaller`（语义缓存）+ 本地缓存
- **限流**：`TokenBucketRateLimiter` 令牌桶限流 + 模型级限流注册
- **成本与计量**：`CostTracker`、`ModelPricing` 定价库、Token 用量统计
- **模型工厂**：`HarnessModelFactory` + `ModelResponseParser` 统一解析

```
统一 AgentModel 接口：AgentChatResponse / AgentGenerateOptions / TokenMetrics
  ├── OpenAIChatModel      └─ 协议适配器 Adapter（屏蔽厂商差异）
  ├── AnthropicChatModel
  ├── DashScopeChatModel    ┌─ 路由 RoutingModel ─ HealthAwareModelRouter
  ├── OllamaChatModel       ├─ 降级 FallbackModel
  └── HttpChatModel         ├─ 重试 RetryableModelCaller
                            ├─ 缓存 Caching / SemanticCaching
                            └─ 限流 RateLimitedModel · 成本 CostTracker
```

### 工具执行层（tool / mcp / web）

- **通用执行器**：`ToolExecutor` 统一调度，`HarnessToolkit` 线程安全注册，`ToolInputValidator` 输入 JSON Schema 校验
- **过滤器**：`KeywordToolFilter` 按规则过滤可用工具
- **结果治理**：`ToolResultEvictionMiddleware` 防止工具结果撑爆上下文
- **MCP 集成**：`McpClient` 提供 HTTP/SSE、流式及 stdio 三种传输，`McpToolAdapter` 自动将 MCP 服务包装为 Agent 工具
- **Web 能力**：网页抓取 `WebFetchTool`、联网搜索 `WebSearchTool`
- **文件能力**：`FileToolkit` 提供文件系统读写工具
- **审计**：`ToolExecutionStore` 记录工具调用全过程

### 记忆与上下文（memory / core.memory）

- **分层记忆**：短期会话记忆 `SessionMemory` + 长期记忆 `LongTermMemory`，支持沙盒隔离与跨会话召回
- **向量检索**：`VectorLongTermMemory` 基于 Embedding 语义召回，`HashingEmbeddingModel` 可作本地无外部依赖的实现，`EmbeddingCache` 加速
- **上下文压缩**：`CompactionMiddleware` 超阈值自动摘要，`SummaryCompactionMiddleware`
- **遗忘策略**：`ForgettingPolicy`（按访问时间/年龄）自动清理过期记忆
- **检查点**：`CheckpointManager` LRU 淘汰防止 OOM，支持持久化快照
- **工具结果清理**：`ToolResultEvictionMiddleware` 维护上下文长度

```
┌──────────────────────── 记忆分层 ────────────────────────┐
│  AgentRuntime                                            │
│   ┌──────────────┐         ┌──────────────────────┐      │
│   │ SessionMemory │ 短期     │  LongTermMemory 长期  │      │
│   │  会话内上下文  │ ──────► │   跨会话可持久化       │      │
│   └──────────────┘         │   VectorLongTerm ↑     │      │
│                             │   语义向量召回           │      │
│   Compaction 压缩 · 遗忘策略  │   Embedding 生成        │      │
│   Checkpoint LRU  · 结果清理 └──────────────────────┘      │
└──────────────────────────────────────────────────────────────┘
```

### RAG 检索增强（rag）

基于知识库的检索增强生成，让 Agent 依据真实业务资料作答，减少幻觉。

- **检索器体系**：`Retriever` 接口定义统一检索契约，`InMemoryRetriever` 提供内存实现；`RetrieverRegistry` 按名称管理多个检索源（有界容量 `MAX_RETRIEVERS=64`，超限拒绝注册）
- **检索注入**：`RagRetrievalMiddleware` 在推理前检索 Top-K 相关片段并注入系统消息；检索片段经 `GuardrailFence` 加围栏标记为**不可信数据**，有效防御**间接提示注入**
- **文档解析**：`DocumentParser`/`TextDocumentParser` 支持文本文档解析与切片，`RetrievedChunk` 封装片段内容与来源
- **检索工具化**：`RetrieverTool` 将检索能力暴露为 Agent 工具，让 LLM 自主按需发起检索
- **失败兜底**：检索失败或为空时静默透传，不影响主流程

```
用户提问 ┄┄► RagRetrievalMiddleware
              │  从 RetrieverRegistry 选取检索源
              ▼
         Retriever.retrieve(query, topK)
              │  返回 RetrievedChunk 集合
              ▼  经 GuardrailFence 围栏标记不可信
         注入 system 消息 ──► 模型生成有据作答
```

### 多代理与编排（orchestration / subagent / planmode）

泱穹内置**六大编排策略**（`OrchestrationStrategy`），覆盖单 Agent 到复杂多 Agent 协作的全场景，统一由 `AutoOrchestrationEngine` 调度：

- **顺序执行（SEQUENTIAL）**：子代理按序执行，前一个的输出作为后一个的输入，适用于强依赖的任务流水线
- **并行执行（PARALLEL）**：子代理同时执行，结果统一汇总，最大化吞吐
- **自适应（ADAPTIVE）**：由 LLM 依据任务特征自动选择策略（含辩论/反思/群聊），未注入 LLM 时按子代理数量启发式决策
- **辩论（DEBATE）**：多子代理独立作答，`DebateJudge` 裁判综合各方立场并给出反馈，多轮迭代收敛出最终结论
- **反思（REFLECTION）**：执行者作答 → 评审者批判 → 执行者据批判修订，多轮迭代自我改进
- **群聊（GROUP\_CHAT）**：多子代理经 `MsgHub` 消息中枢**轮转发言**，每个代理看到前序发言后继续讨论，联合求解

配套编排原语：

- **子代理委派**：`SubagentsMiddleware` + `AgentSpawnTool` 动态派生子代理，`SubagentToolRegistrar` 自动暴露其工具
- **消息中枢（MsgHub）**：广播式消息路由 + 轮询讨论，支持参与者注册/注销、防复读防代演约束、历史上限淘汰（`MAX_HISTORY`）
- **多代理交接**：`HandoffTool` 实现子代理间上下文交接（`HandoffContextFilter`），支持多轮协作与角色切换
- **自动编排**：`AutoOrchestrationEngine` 自动拆解任务、生成子代理规格 `SubagentSpecGenerator`、聚合结果 `SubagentResultAggregator`
- **用户澄清**：`AskUserTool` 在信息不足时主动向用户提问，`AutoOrchestrateTool` 触发自动编排
- **计划模式**：`PlanModeManager` 让 LLM 先规划后执行，`PlanModeMiddleware` 注入

```
主协调 Agent
  │  spawn_subagent / handoff / auto_orchestrate
  ├──► 顺序/并行 子代理流水线
  ├──► 辩论模式：A/B/C 各自作答 → DebateJudge 裁判多轮收敛
  ├──► 反思模式：执行者 ↔ 评审者 迭代修订
  └──► 群聊模式：MsgHub 消息中枢轮转发言，联合讨论
                    结果汇聚 → 统一返回
```

### 智能体范式（paradigms 扩展）

`yangqiong-agent-paradigms` 扩展在 ReAct 基座之外提供 **5 大经典范式引擎 + Router 元层路由**，所有范式继承 core 的 `AbstractAgentLoop` 模板，共享统一的生命周期基础设施：

| 范式引擎                | 决策结构            | 评估信号          | 适用场景            |
| ------------------- | --------------- | ------------- | --------------- |
| `ReActEngine`（基座）   | 推理→行动→观察循环      | 工具观察结果        | 通用任务执行          |
| `PlanExecuteEngine` | 先规划完整计划再逐步执行    | 计划完成度         | 目标明确、可预先分解的复杂任务 |
| `ReWooEngine`       | 一次性规划按依赖执行后统一推理 | 计划间变量依赖       | 减少模型调用轮次、降低延迟成本 |
| `ReflexionEngine`   | 自评估失败后携带教训重试    | 失败反思（Lessons） | 容错重试、从失败中学习     |
| `SelfAskEngine`     | 复合问题拆解逐个作答      | 子问题答案链        | 多跳问答、显式问题分解     |
| `SelfRefineEngine`  | 生成→批评→修订循环      | 自评反馈          | 对输出质量有高要求的打磨场景  |

范式公共配置 `ParadigmOptions`：`maxSteps`（最大步数，默认 10）、`maxReflections`（最大反思次数，默认 2）、`maxRefinements`（最大修订次数，默认 2）。

- **元层路由**：`RouterEngine` 先按工具可用性启发式预筛（无工具排除 ReAct/Plan-Execute/ReWoo），再经一次轻量模型分类轮选定最佳范式转发执行；路由决策以 `ENGINE_ROUTED` 事件承载，便于下游观测与断言；分类不可解析时回退兜底（有工具 ReAct / 无工具 Self-Ask）；`forcedParadigm` 可强制指定范式跳过分类轮
- **统一运行语义**：所有范式引擎复用父类模板的审批挂起/账本恢复（`resume`）、人工澄清（`resumeWithClarification`）、迭代超时包装、Token/成本预算、检查点持久化能力
- **子代理挂载范式**：`SubagentDeclaration` 新增 `agentLoop` 字段，子代理可声明挂载任意范式引擎执行专项任务（如让代码生成子代理使用 ReWoo 降低调用成本）

### 安全与合规（permission / guardrail）

- **权限引擎**：`PermissionEngine` + `ToolPolicyGate` 按工具维度进行审批门禁
- **权限分级**：`AgentPermissionMode` 提供五种模式，覆盖只读到放开操作的全谱系管控：
  - `EXPLORE` 只读（禁止写/破坏性工具）· `ACCEPT_EDITS` 允许非破坏修改
  - `ASK` 破坏性操作需确认 · `DONT_ASK`/`BYPASS` 逐级放宽
  - 危险操作触发 `RequireUserConfirmEvent` 请求用户确认
- **内容审核**：`ContentModerationMiddleware` 对模型输出做内容合规审核（`ModerationVerdict`）
- **输入/输出护栏**：`InputGuardrailMiddleware`、`OutputGuardrailMiddleware` 通过自定义规则约束交互
- **提示注入检测**：`InjectionDetector` 识别指令注入风险；`GuardrailFence` 为外部数据加围栏，防御间接注入
- **审计日志**：`AuditSink` 记录工具调用与审批全过程（有界缓冲，防内存泄漏）

### 可靠性与持久化（durable / engine）

- **运行存储**：`AgentRunStore`（内存/可替换为 JDBC）持久化运行记录
- **检查点**：`CheckpointStore` 断点续跑能力
- **审批存储**：`ApprovalStore` 审批记录持久化
- **运行锁**：`RunLockStore` 分布式互斥，支持跨节点一致调度
- **状态机**：`DurableExecutionTracker` 精确追踪运行状态，`ApprovalCoordinator` 协调审批流
- **持久化执行**：跨进程重启后可恢复执行状态

### 可观测与评测（trace / eval / ratelimit / config）

- **链路追踪**：`AgentTracingMiddleware` + `TraceEmitter`/`TraceSpan` 记录调用链路与工具时序
- **成本预算**：`CostBudgetPolicy`、`TokenBudgetPolicy` 设定费用/Token 阈值，超限自动中断
- **评测体系**：`EvalRunner` 批量运行评测用例，`LLMEvalJudge`/`RuleEvalJudge` 双模式裁决，输出 `EvalReport` 评分报告
- **流式事件**：文本增量/思考增量/工具调用增量/结果 等事件流，便于前端实时渲染

### 技能（Skill）管理

技能是预定义的提示词 + 工具集合，用于增强 Agent 特定领域能力，形成可复用的能力包。

- **技能箱**：`AgentSkillBox` 聚合多个 `AgentSkill`，`SkillManager` 提供按名称索引查询与摘要生成
- **提示注入**：`SkillPromptInjector` 按"层级广告（Level-1 Advertise）"机制注入技能摘要，`SkillManager.buildSummaries()` 优先取 YAML `description`、无则自动提取首句，控制 token 开销
- **按需加载**：`LoadSkillTool` 让 LLM 动态加载技能进上下文，`ReadSkillResourceTool` 读取技能内部资源

### 序列化（serialization）

- **对象映射**：`HarnessObjectMapper` 统一消息/内容块/检查点序列化，支持跨存储持久化
- **检查点序列化**：`AgentCheckpointSerializer` 将运行时检查点/消息快照序列化，配合断点续跑使用
- **安全往返**：`SerializationRoundTripTest` 验证消息与内容的往返一致性，保障可恢复性

### 中断控制与结构化输出（config / interruption）

- **中断控制**：`AgentRuntime.interrupt(context)` 主动终止运行，底层 `AgentInterruptControl` 支持运行时安全终止
- **上下文缓存**：`ContextCachingConfig` 复用上下文窗口
- **结构化输出**：`AgentResponseFormat` + `StructuredOutputValidator` 强制 JSON Schema 输出，失败自动重试
- **工具选择**：`AgentToolChoice` 精细化控制模型可用的工具集

***

## 模块一览

泱穹智能体框架采用 **Maven 多模块聚合工程**，核心零 Spring、适配与扩展独立交付：

| 模块                  | artifactId                                   | 定位                | 说明                                                                                          |
| ------------------- | -------------------------------------------- | ----------------- | ------------------------------------------------------------------------------------------- |
| 聚合根                 | `yangqiong-agent-harness`                    | 工程聚合/版本/质量治理      | `packaging=pom`，统一依赖与插件版本                                                                   |
| 核心引擎                | `yangqiong-agent-core`                       | 引擎核心（零 Spring）    | ReAct 引擎、模型/工具/记忆/编排/护栏，纯 Java 可运行                                                          |
| BOM                 | `yangqiong-agent-bom`                        | 依赖版本清单            | 供业务侧统一导入                                                                                    |
| Spring Boot Starter | `yangqiong-agent-spring-boot-starter`        | Spring 自动装配       | 一行依赖接入 `AgentRuntime`，`ai.harness.*` 配置前缀                                                   |
| 智能体范式               | `yangqiong-agent-paradigms`                  | 范式引擎扩展            | Plan-Execute / ReWoo / Reflexion / Self-Ask / Self-Refine + Router 自动路由                     |
| 模型提供方               | `yangqiong-agent-model-providers`            | 多厂商模型接入           | Anthropic / DashScope / Gemini / Ollama 协议模型，SPI 自动发现                                       |
| 共享存储 JDBC           | `yangqiong-agent-store-jdbc`                 | MyBatis-Plus 存储实现 | 运行记录/检查点/审批/记忆/锁 持久化到 MySQL                                                                 |
| 共享存储 Redis          | `yangqiong-agent-store-redis`                | Jedis 存储实现        | 分布式运行治理存储（零 Spring）                                                                         |
| 向量存储                | `yangqiong-agent-store-vector`               | 向量存储扩展            | 语义检索向量持久化                                                                                   |
| 本地模式                | `yangqiong-agent-local-agent`                | 一键装配本地智能体         | SQLite 存储 + 工作区 + Shell 工具 + 检查点续跑                                                          |
| 服务化                 | `yangqiong-agent-server`                     | HTTP 服务承载         | 会话管理、SSE 事件流、审批恢复接口                                                                         |
| 可观测                 | `yangqiong-agent-observability`              | 监控集成              | Micrometer / OpenTelemetry，内置 Grafana 看板                                                    |
| 评测                  | `yangqiong-agent-eval`                       | 评测体系              | YAML 用例集、规则/LLM 双裁判、稳定性跑批、报告导出                                                              |
| 定时调度                | `yangqiong-agent-cron`                       | Cron 任务           | Cron 表达式调度 Agent 任务                                                                         |
| 通知                  | `yangqiong-agent-notify`                     | 通知渠道              | 多渠道通知服务                                                                                     |
| 事件触发                | `yangqiong-agent-trigger`                    | 文件事件触发器           | 文件监听触发 Agent 运行                                                                             |
| 示例聚合                | `yangqiong-agent-examples`                   | 可运行示例             | hello-world / spring-boot / multi-agent / paradigms / eval / observability / local / server |
| 文档                  | `README.md` / `USAGE.md`                     | 对外文档              | 架构、模块矩阵、使用手册（中文版，英文简介见 `README.en.md`）                                                |

> 📖 详细说明请参阅 [USAGE.md](./USAGE.md)（基础使用手册）。

***

## 快速开始

> 📖 需要分功能点的完整使用指引？请参阅 **[基础使用手册 →](./USAGE.md)**。

### 1. 引入依赖（零 Spring）

```xml
<dependency>
    <groupId>com.yangqiong.agent</groupId>
    <artifactId>yangqiong-agent-core</artifactId>
    <version>1.0.0-SNAPSHOT</version>
</dependency>
```

> 💡 Spring Boot 用户可直接引入 `yangqiong-agent-spring-boot-starter` 实现自动装配，无需手动构建运行时。

### 2. 构建 Agent 运行时

```java
// 创建模型
AgentModel model = new OpenAIChatModel(/* 配置 */);

// 使用构建器创建 Agent 运行时
AgentRuntime runtime = new HarnessRuntimeBuilder()
    .name("my-agent")
    .model(model)
    .systemPrompt("你是一个智能助手")
    .askUserEnabled(true)     // 可选：信息不足时主动反问用户（暂停等待澄清后续接）
    .maxIters(10)
    .build();

// 调用 Agent
AgentMessage result = runtime.call(
    List.of(MessageFactory.createUserMessage("你好")),
    AgentRuntimeContext.empty()
).block();
```

### 3. 流式输出

```java
runtime.stream(
    List.of(MessageFactory.createUserMessage("你好")),
    AgentRuntimeContext.empty()
).subscribe(event -> {
    switch (event.getType()) {
        case TEXT_BLOCK_DELTA -> System.out.print(((AgentTextBlockDeltaEvent) event).getDelta());
        case AGENT_RESULT -> System.out.println("\n--- 完成 ---");
        case ERROR -> System.err.println("错误: " + event.getPayload());
    }
});
```

### 4. 定义工具（AgentTool）

```java
public class MyTool implements AgentTool {
    @Override
    public String getName() { return "my_tool"; }

    @Override
    public String getDescription() { return "我的自定义工具"; }

    @Override
    public Map<String, Object> getParameters() {
        return Map.of("param1", Map.of("type", "string", "description", "参数1"));
    }

    @Override
    public Mono<AgentToolResultBlock> callAsync(AgentToolCallParam param) {
        // 执行工具逻辑
        return Mono.just(AgentToolResultBlock.of(
            List.of(AgentTextBlock.builder().text("执行结果").build())));
    }
}

HarnessToolkit toolkit = new HarnessToolkit(null);
toolkit.addTool(new MyTool());
AgentRuntime runtime = builder.toolkit(toolkit).build();
```

### 5. 自定义中间件

```java
AgentMiddleware middleware = new AgentMiddleware() {
    @Override
    public String onSystemPrompt(String prompt, AgentRuntimeContext ctx) {
        return prompt + "\n请用中文回答。";
    }

    @Override
    public Flux<AgentEvent> onReasoning(AgentRuntimeContext ctx,
                                         List<AgentMessage> messages,
                                         Function<List<AgentMessage>, Flux<AgentEvent>> next) {
        // 在推理前拦截
        return next.apply(messages);
    }
};
```

内置中间件：

| 中间件                            | 功能                |
| ------------------------------ | ----------------- |
| `CompactionMiddleware`         | 历史消息压缩，超出阈值时自动摘要  |
| `ToolResultEvictionMiddleware` | 工具结果清理，防止上下文超长    |
| `PlanModeMiddleware`           | 计划模式，让 LLM 先规划再执行 |
| `SubagentsMiddleware`          | 子代理委派执行           |
| `SkillPromptInjector`          | 技能提示词注入           |
| `AgentTracingMiddleware`       | 调用链路追踪            |
| `OutputGuardrailMiddleware`    | 输出护栏              |

### 6. 子代理与权限（示例）

```java
// 声明子代理
AgentRuntime runtime = new HarnessRuntimeBuilder()
    .subagentDeclarations(List.of(SubagentDeclaration.builder()
        .name("coder")
        .description("代码生成代理")
        .modelCode(modelCode)
        .systemPrompt(systemPrompt)
        .tools(toolNames)
        .build()))
    .enableSubagents()
    .build();

// 权限控制：只读模式，禁止写操作
AgentRuntime readonlyRuntime = new HarnessRuntimeBuilder()
    .permissionContextState(AgentPermissionContextState.builder()
        .mode(AgentPermissionMode.EXPLORE)
        .build())
    .build();
```

### 7. 挂载智能体范式（paradigms 扩展）

```java
// 引入 yangqiong-agent-paradigms 后，通过 agentLoop 挂载范式引擎
AgentRuntime runtime = new HarnessRuntimeBuilder()
    .name("planner")
    .model(model)
    .agentLoop(new ReWooEngine())              // 或 PlanExecute/Reflexion/SelfAsk/SelfRefine/Router
    .build();

// 不确定任务类型时，交给 RouterEngine 自动路由择优
AgentRuntime runtime = new HarnessRuntimeBuilder()
    .name("router")
    .model(model)
    .toolkit(toolkit)
    .agentLoop(new RouterEngine())             // 路由决策以 ENGINE_ROUTED 事件输出
    .build();
```

***

## 技术栈

| 组件   | 选型                                         |
| ---- | ------------------------------------------ |
| 语言   | Java 17                                    |
| 响应式  | Project Reactor 3.7                        |
| 构建   | Maven 3.6+                                 |
| 序列化  | Jackson                                    |
| 测试   | JUnit 5 · Mockito · AssertJ · Reactor Test |
| 外部依赖 | **零 Spring，最小化三方依赖**                       |

***

## 开源协议

本项目基于 [Apache License 2.0](./LICENSE) 协议开源。

## 社区与反馈

- **官网**（架构详解、扩展指南、可运行示例）：<https://www.yangqiongtech.com>
- **问题反馈**：[反馈指南](https://www.yangqiongtech.com/feedback.html)；安全漏洞请勿公开披露，邮件至 1781618435@qq.com（标题注明【安全漏洞】）
- **技术交流 QQ 群**：1107572553（用于交流）
- **Gitee 镜像**：<https://gitee.com/yangqiongtech/yangqiong-agent-harness>

