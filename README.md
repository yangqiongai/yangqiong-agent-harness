[English](./README.en.md) | 中文

![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)
![JDK](https://img.shields.io/badge/JDK-17-orange.svg)
![Gitee](https://gitee.com/yangqiongtech/yangqiong-agent-harness/badge/star.svg)

# 泱穹 Agent Harness（泱穹智能体框架）

> **泱穹智能体框架** 是一套用**纯 Java** 构建的**开箱即用的企业级 AI Agent 运行时框架**：**ReAct 循环 + 六大范式引擎（含自动路由）、子代理与多代理编排、断点持久化执行、权限与安全护栏、全链路可观测与评测**，**零 Spring 依赖**，基于 Reactor 响应式事件流驱动。
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

过去几年，Python 一直主导 AI 智能体开发，Java 开发者缺少可用且好用的智能体框架。2026 年这一局面已被改写，Java 已形成多个生产可用框架：

| 对比维度                | **泱穹 Agent Harness**              | Spring AI                    | LangChain4j                     | AgentScope (Java)                        |
| ------------------- | --------------------------------- | ---------------------------- | ------------------------------- | ---------------------------------------- |
| 定位                  | **企业级 Java Agent 运行时**            | Spring 官方 AI 生态              | 框架无关、20+ 模型提供方                  | 阿里开源多智能体框架                               |
| 框架形态                | ✅ **开箱智能体框架**                     | ⚠️ 工具/组装类                    | ⚠️ 工具/组装类                       | ✅ 智能体框架                                  |
| 规划模式                | ✅ **AgentLoop 6范式**               | ReAct 循环                     | ReAct 循环                        | ReAct 循环                                 |
| MCP                 | ✅ **多格式解析+自定义组装**                  | ✅                            | ✅                               | ✅                                        |
| 断点持久化               | ✅                                 | ❌                            | ❌                               | ✅                                        |
| 人工在环（审批/澄清）         | ✅ **审批挂起+账本恢复 / ask_user 澄清续接**   | ❌                            | ❌                               | ⚠️ 中断概念，无恢复闭环                            |
| 多范式引擎/自动路由          | ✅ **6 范式**                        | ❌                            | ❌                               | ⚠️ 编排策略                                  |
| 多代理编排               | ✅ **六策略编排**                       | ❌                            | ❌                               | ⚠️ Supervisor 派生 + A2A 委派                |
| 子代理动态生成             | ✅ **builder 全可配 + 运行时派生**         | ❌                            | ⚠️                              | ✅ Markdown 声明 + 运行时派生                    |
| 工具可靠执行              | ✅ **校验 + 超时 + 重试**                | ⚠️                           | ⚠️                              | ⚠️ 异步工具                                  |
| 工具渐进加载              | ✅ **按需启用**                        | ❌                            | ❌                              | ❌                                        |
| 计划模式                | ✅ **先规划后执行，计划内容可读取**              | ❌                            | ❌                               | ❌                                        |
| 结构化输出可靠性            | ✅ **三层保障（原生JSON/降级/校验重试）**        | ⚠️ 基础字段映射                    | ⚠️ 基础类型映射                       | ⚠️                                       |
| 成本与流量治理             | ✅ **预算/Token计量/语义缓存/限流**          | ⚠️ Micrometer 指标             | ⚠️ Token 估算                     | ❌                                        |
| 安全护栏与注入检测           | ✅ **输入/输出护栏 + 提示注入检测 + 内容审核**     | ⚠️ Advisor 可扩展               | ⚠️ 内建基础护栏                       | ❌                                        |
| 中间件链                | ✅ 响应式洋葱                           | ✅ Advisor                    | ⚠️ 事件监听                         | ✅ 多阶段 Hook                               |
| 评测/可观测              | ✅ **双裁判 + 跑批** / ✅ OTel           | ✅ / ✅ Micrometer             | ❌ / ✅ 事件观测                      | ❌ / ✅ Studio                             |
| 扩展生态                | ✅ **12 扩展 + SPI**                 | ✅ Spring 生态                  | ✅ 模块化 SPI                       | ⚠️ 生态起步                                  |
| 接入形态                | ✅ **纯 Java SE + Spring Boot Starter 双形态，一键本地模式** | ⚠️ 仅 Spring Boot | ⚠️ 手动装配                         | ✅ 独立运行                                   |

> 说明：**Semantic Kernel (Java)** 与 **Google ADK (Java)** 未纳入上表——前者是跨语言 SDK，Java 端 Agent 能力薄弱、Agent 循环基本要自行组装，实用性有限；后者深度绑定 Google 生态（Vertex AI / Gemini 模型家族），离开该生态落地门槛高、不好用。二者在企业真实项目中竞争力不足，故不作主要对比对象。


### 2. 现状痛点：企业落地仍有"最后一公里"缺口

成熟框架让 Java 接上 LLM 变得简单，但当面向**真正的高价值生产系统**时，普遍存在以下缺口：

- **框架强耦合**：Spring AI 深度绑定 Spring Boot；LangChain4j 虽框架无关，但在 Spring 下仍需手动装配。难以在纯 Java / 多框架环境统一落地。
- **断点持久化普遍缺失**：上表可见 Spring AI、LangChain4j 均未提供**工作流中途状态持久化（Checkpointing）**，长任务中断后无法续跑。
- **企业级运行治理缺位**：多轮会话缺乏成本预算、Token 计量、限流、模型路由/降级等生产管控；跨节点分布式的运行锁与一致调度少见。
- **编排大多停留在"单 Agent + 工具"**：多 Agent 交接、任务自动编排、子代理互斥与结果聚合多是搬用概念，缺少精细的状态机与治理支撑。

### 3. 泱穹：补上 Java 企业级智能体的"最后一公里"

泱穹智能体框架正是针对上述缺口而设计，与前代框架形成**互补而非重复造轮子**的差异化定位：

- ✅ **零框架强耦合**——不强制集成 Spring，纯 Java SE 即可运行，天然适配 Spring 全家桶 / Quarkus / 微服务任意场景
- ✅ **原生持久化执行**——内置 `AgentRunStore`/`CheckpointStore` 断点续跑、`RunLockStore` 分布式互斥，跨节点一致调度
- ✅ **人工在环闭环**——工具审批挂起+账本恢复、ask_user 人工澄清续接，长任务安全可控
- ✅ **全套生产治理开箱即用**——成本预算、Token 计量、模型路由/降级/重试/语义缓存/限流，企业无需自行拼装
- ✅ **成熟的多 Agent 编排**——子代理委派、六策略编排、多 Agent 上下文交接（Handoff）、结果聚合，配套状态机与权限体系
- ✅ **强工程保障**——静态强类型、编译期校验，core 模块 900+ 单元测试（100+ 测试类）护航，双裁判评测与全链路可观测闭环

> 简单说：Spring AI 与 LangChain4j 解决了"Java 能不能接 LLM"，泱穹智能体框架负责解决"接了之后能否稳定可控地跑进企业生产系统"。

***

## 核心特性

| 能力域   | 特性                                                                                                     |
| ----- | ------------------------------------------------------------------------------------------------------ |
| 核心运行  | ReAct 推理循环、响应式流式输出、事件驱动、多轮上下文管理                                                                        |
| 模型层   | 多厂商模型接入（OpenAI/Anthropic/DashScope/Ollama/Gemini/HTTP）、统一协议适配、模型路由/降级/重试/缓存/限流/成本追踪                    |
| 工具层   | 通用工具执行器、工具输入校验、工具过滤器、工具结果清理、工具渐进加载（按需启用）                             |
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

## 功能模块速览

| 领域                  | 关键能力                                                                     | 详细文档                                                |
| ------------------- | ------------------------------------------------------------------------ | --------------------------------------------------- |
| 模型接入（model）         | 多厂商协议适配（OpenAI/Anthropic/DashScope/Gemini/Ollama/HTTP）、健康路由、降级重试、语义缓存、限流、成本计量 | [模型接入](./USAGE.md#3-模型接入)                           |
| 工具执行（tool/mcp/web）  | 统一执行器与 JSON Schema 校验、工具渐进加载（按需启用）、MCP 三种传输、联网搜索/网页抓取、文件沙箱、调用审计                | [定义工具](./USAGE.md#5-定义工具agenttool)                  |
| 记忆与上下文（memory）      | 会话+长期分层记忆、向量语义召回、超限自动压缩、遗忘策略                                              | [记忆与上下文压缩](./USAGE.md#7-记忆与上下文压缩)                   |
| RAG 检索增强（rag）       | 检索器体系、Top-K 注入、围栏防间接提示注入、检索工具化                                            | [RAG 检索增强](./USAGE.md#10-rag-检索增强)                  |
| 多代理与编排              | 六大编排策略（顺序/并行/自适应/辩论/反思/群聊）、MsgHub 消息中枢、Handoff 交接、动态派生与结果聚合                | [子代理与编排](./USAGE.md#12-子代理与多-agent-编排)、[群聊与辩论](./USAGE.md#13-群聊与辩论) |
| 智能体范式（paradigms）    | ReAct 基座 + 5 大范式引擎 + Router 自动路由，统一审批挂起/账本恢复/人工澄清语义                       | [智能体范式](./USAGE.md#18-智能体范式paradigms-扩展)            |
| 安全与合规               | 权限五模式分级、内容审核、输入/输出护栏、注入检测、审计日志                                            | [权限控制](./USAGE.md#8-权限控制)、[安全护栏](./USAGE.md#9-安全护栏) |
| 可靠性与持久化             | 运行存储、检查点断点续跑、审批存储、分布式运行锁                                                  | [持久化与断点续跑](./USAGE.md#15-持久化与断点续跑)                  |
| 可观测与评测              | 链路追踪 Span、成本/Token 预算、规则+LLM 双裁判评测、稳定性跑批                                  | [评测](./USAGE.md#16-评测evalrunner)                    |
| 计划模式与技能             | 先规划后执行、技能箱层级广告注入、技能/工具渐进加载（按需加载）                                          | [计划模式](./USAGE.md#11-计划模式)                          |
| 结构化输出与中断            | JSON Schema 强制输出+失败自动重试、运行时主动中断                                           | [结构化输出](./USAGE.md#14-结构化输出)                        |

***

## 扩展模块一览

基于泱穹智能体框架核心的扩展性工程：

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
| 文档                  | `README.md` / `USAGE.md`                     | 对外文档              | 架构、模块矩阵、使用手册（英文版见 `README.en.md`）                                                |

> 📖 详细说明请参阅 [USAGE.md](./USAGE.md)（基础使用手册）。

***

## 快速开始

> 📖 需要分功能点的完整使用指引？请参阅 **[基础使用手册 →](./USAGE.md)**。

### 1. 引入依赖（零 Spring）

```xml
<dependency>
    <groupId>com.yangqiong.agent</groupId>
    <artifactId>yangqiong-agent-core</artifactId>
    <version>1.0.0</version>
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

### 5. 进阶能力（中间件 / 子代理 / 范式）

- **自定义中间件**：实现 `AgentMiddleware` 拦截系统提示词与推理链路（[中间件](./USAGE.md#6-中间件middleware)）
- **子代理与权限**：`subagentDeclarations` 声明子代理、`permissionContextState` 配置五级权限模式（[权限控制](./USAGE.md#8-权限控制)、[子代理与编排](./USAGE.md#12-子代理与多-agent-编排)）
- **挂载范式引擎**：一行切换执行范式，详见 [智能体范式](./USAGE.md#18-智能体范式paradigms-扩展)

```java
AgentRuntime planner = new HarnessRuntimeBuilder()
    .name("planner")
    .model(model)
    .agentLoop(new ReWooEngine())      // 或 PlanExecute/Reflexion/SelfAsk/SelfRefine
    .build();

// 不确定任务类型时，交给 RouterEngine 自动路由择优
AgentRuntime router = new HarnessRuntimeBuilder()
    .name("router")
    .model(model)
    .toolkit(toolkit)
    .agentLoop(new RouterEngine())     // 路由决策以 ENGINE_ROUTED 事件输出
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
- **GitHub 地址**：<https://github.com/yangqiongtech/yangqiong-agent-harness>
- **Gitee 地址**：<https://gitee.com/yangqiongtech/yangqiong-agent-harness>
