English | [中文](./README.md)

![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)
![JDK](https://img.shields.io/badge/JDK-17-orange.svg)
![Gitee](https://gitee.com/yangqiongtech/yangqiong-agent-harness/badge/star.svg)

# Yangqiong Agent Harness

> **Yangqiong Agent Harness** is an **enterprise-grade AI Agent runtime framework** built in **pure Java (JDK 17)**: **ReAct loop + 6 paradigm engines with auto-routing, sub-agent and multi-agent orchestration, checkpoint-based durable execution, permission & safety guardrails, full-link observability and evaluation** — **zero Spring dependency**, driven by reactive event streams (Project Reactor).
>
> From model integration, tool execution and memory management to multi-agent orchestration, evaluation and production governance, it provides a one-stop runtime foundation for building **stable, controllable, observable and extensible** agent applications in large enterprises.

***

## Built for Enterprise Java

The real proving ground for agents is the enterprise production environment: integrating with legacy systems, passing security & compliance audits, and fitting into existing engineering systems. Yangqiong Agent Harness is natively built on Java, so your existing investments become the capability foundation of your agents:

### 1. Mature engineering ecosystem, lower delivery risk

- Java has 20+ years of enterprise provenance — toolchains (Maven/Gradle, IDEA, SonarQube, Jenkins), repositories and operations (JVM monitoring, APM, containerization) are all mature. Reuse your existing CI/CD and monitoring instead of introducing a new runtime language.

### 2. Strong typing & compile-time checks, more stable AI applications

- AI output is inherently non-deterministic; Java's **static strong typing + compile-time validation** catches tool definitions, message structures and config binding errors early. Interfaces and generics keep tool registration and model protocol adapters self-consistent at compile time.

### 3. Natural integration with Java enterprise systems

- Enterprises run on **Java**: Spring stacks, microservices, middleware (MQ, caching, distributed transactions), ORM data layers, ERP/CRM core systems. Yangqiong integrates with **zero rewriting** — via Java tools and MCP, agents orchestrate existing enterprise services like local method calls, injecting LLM intelligence straight into core business flows.

### 4. Proven concurrency & performance foundation

- Backed by the JVM's mature threading model and **reactive programming**, sustaining high-concurrency tool orchestration and streaming output with minimal thread resources. GC tuning, thread-pool governance and failure isolation are production-proven enterprise capabilities.

### 5. Rigorous security & compliance

- Leverage the mature Java ecosystem — **permission frameworks (Spring Security/Shiro), audit logging, encryption & compliance toolkits** — for enterprise-grade control over tool invocation, meeting strict requirements of finance and government sectors.

***

## Java Agent Framework Landscape

### 1. Overview: Java agent frameworks are now a multi-horse race

For the past two years Python dominated agent development and Java developers had to hand-stitch HTTP calls. **As of 2026**, that has changed: Java now has multiple production-ready frameworks:

| Framework | Positioning | Form | Planning Mode | MCP | Durable Checkpointing | Multi-Paradigm / Auto-Routing | Multi-Agent Orchestration | Dynamic Sub-agents | Reliable Tool Execution | Middleware Chain | Eval / Observability | Extension Ecosystem |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| **Spring AI** | Official Spring AI ecosystem (Broadcom) | ⚠️ Tool/assembly (agent loop DIY) | ReAct loop | ✅ | ❌ | ❌ | ❌ | ❌ | ⚠️ | ✅ Advisor | ✅ / ✅ Micrometer | ✅ Spring ecosystem |
| **LangChain4j** | Framework-agnostic, 20+ model providers | ⚠️ Tool/assembly (AI Services composition) | ReAct loop | ✅ | ❌ | ❌ | ❌ | ⚠️ | ⚠️ | ⚠️ Event listeners | ❌ / ✅ Event observation | ✅ Modular SPI |
| **Semantic Kernel (Java)** | Microsoft cross-language SDK | ⚠️ Assembly SDK (weak Java agent) | ReAct loop | ✅ | ❌ | ❌ | ⚠️ Limited on Java | ⚠️ | ⚠️ Filter interceptable | ✅ Filter | ❌ / ✅ OTel | ⚠️ Few connectors |
| **Google ADK (Java)** | Google Agent Development Kit | ✅ Out-of-the-box agent framework | Hierarchical + Planner | ✅ | ✅ | ⚠️ Planner | ✅ Sequential / parallel / loop / handoff | ⚠️ Build-time composition | ⚠️ | ✅ Callback | ✅ / ✅ | ⚠️ Google-centric |
| **AgentScope (Java)** | Alibaba multi-agent framework (ReAct + Harness engineering) | ✅ Out-of-the-box agent framework | ReAct + Plan/Reflect | ✅ | ✅ (distributed session resume) | ⚠️ Orchestration strategies | ⚠️ Supervisor spawn + A2A delegation | ✅ Markdown declaration + runtime spawn | ⚠️ Async tools | ✅ Multi-stage hooks | ❌ / ✅ Studio | ⚠️ Modular but nascent |
| **Yangqiong Agent Harness** | **Enterprise Java agent runtime** | ✅ **Out-of-the-box (engine / paradigms / orchestration / eval built-in)** | 6 planning modes + auto-routing | ✅ | ✅ | ✅ **6 paradigms** | ✅ **6-strategy orchestration** | ✅ **Declarative builder fully configurable (model/tools/permissions/paradigm mounting) + runtime dynamic spawn** | ✅ **Schema validation + timeout wrapping + backoff retry + fault-injection tested** | ✅ Reactive onion model | ✅ **Dual-judge eval + stability runs** / ✅ OTel + cost accounting | ✅ **12 official extension modules + SPI auto-discovery** |

> Background: Spring AI ships a convenient Starter backed by the Spring ecosystem; LangChain4j reached 1.0 GA in May 2025 with high developer adoption per JetBrains surveys, thanks to being framework-agnostic with broad provider coverage and built-in guardrails. Both are 1.0 GA and support MCP. AgentScope Java 2.0 went GA in 2026, known for its "two-layer agent architecture" (ReActAgent reasoning core + HarnessAgent engineering layer) and distributed deployment.

### 2. Pain points: the "last mile" to enterprise production is still missing

Mature frameworks make calling LLMs from Java easy, but for **real high-value production systems** there are common gaps:

- **Framework coupling** — Spring AI is deeply bound to Spring Boot; LangChain4j is framework-agnostic but still needs manual wiring under Spring. Hard to land uniformly in pure-Java / multi-framework environments.
- **Durable checkpointing largely absent** — as the table shows, Spring AI, LangChain4j and Semantic Kernel lack **mid-workflow state persistence (Checkpointing)**; interrupted long tasks cannot resume.
- **Missing production governance** — multi-turn sessions lack cost budgets, token metering, rate limiting, model routing/fallback; distributed run locks and consistent cross-node scheduling are rare.
- **Orchestration stuck at "single agent + tools"** — multi-agent handoff, automatic task orchestration, sub-agent mutual exclusion and result aggregation are often borrowed concepts without fine-grained state machines and governance.

### 3. Yangqiong: closing the "last mile"

Yangqiong Agent Harness is designed against these gaps — **complementary, not reinventing the wheel**:

- ✅ **Zero framework coupling** — no Spring required; runs on pure Java SE, and fits naturally into Spring Boot / Quarkus / microservice environments alike
- ✅ **First-class durability** — `AgentRunStore` / `CheckpointStore` checkpoint resume, `RunLockStore` distributed mutual exclusion for cross-node consistent scheduling (authoritative circuit breaker, no "fake resume")
- ✅ **Full production governance out of the box** — cost budgets, token metering, model routing/fallback/retry, semantic caching and rate limiting
- ✅ **Mature multi-agent orchestration** — sub-agent delegation, automatic task orchestration, handoff, result aggregation, backed by state machines and permission approval
- ✅ **Strong engineering guarantees** — static typing, compile-time validation, 900+ unit tests (100+ test classes) in core alone, safety guardrails and audit loop

> In short: Spring AI and LangChain4j answer "can Java call an LLM"; Yangqiong Agent Harness answers "can it run **stably and controllably into enterprise production**".

***

## Core Features

| Domain | Capabilities |
| --- | --- |
| Core runtime | ReAct reasoning loop, reactive streaming output, event-driven, multi-turn context management |
| Model layer | Multi-vendor integration (OpenAI/Anthropic/DashScope/Ollama/Gemini/HTTP), unified protocol adapters, routing/fallback/retry/cache/rate-limit/cost tracking |
| Tools | Unified executor, input validation, tool filters, tool-result eviction |
| Memory | Session memory, long-term memory, semantic vector retrieval, LRU checkpoints, forgetting policies, context compaction |
| Multi-agent | Sub-agent delegation (mountable with any paradigm engine), 6 orchestration strategies (sequential/parallel/adaptive/debate/reflection/group chat), handoff, message hub, result aggregation |
| Paradigms | ReAct base + Plan-Execute, ReWoo, Reflexion, Self-Ask, Self-Refine engines + Router meta-layer auto-routing, unified approval pause / ledger resume / clarification semantics |
| Safety | Tool permission engine, content moderation, input/output guardrails, prompt-injection detection, audit logging |
| Reliability | Durable run stores, checkpoint resume, cross-node distribution, structured-output auto-retry |
| Observability | Full-link tracing, token metering, cost accounting, evaluation, scoring reports |
| Planning | Plan Mode, MCP protocol, Skill management, RAG retrieval augmentation |


## Capability Map

| Domain | Key capabilities | Docs |
| --- | --- | --- |
| Models | Multi-vendor protocol adapters (OpenAI/Anthropic/DashScope/Gemini/Ollama/HTTP), health routing, fallback/retry, semantic caching, rate limiting, cost metering | [Model integration](./USAGE.md#3-模型接入) |
| Tools (tool/mcp/web) | Unified executor + JSON Schema validation, MCP transports, web search/fetch, sandboxed file tools, invocation audit | [Defining tools](./USAGE.md#5-定义工具agenttool) |
| Memory | Session + long-term layered memory, vector recall, auto compaction, forgetting policies | [Memory & compaction](./USAGE.md#7-记忆与上下文压缩) |
| RAG | Retriever system, Top-K injection, fence against indirect prompt injection, retrieval as a tool | [RAG](./USAGE.md#10-rag-检索增强) |
| Multi-agent | 6 orchestration strategies (sequential/parallel/adaptive/debate/reflection/group chat), MsgHub, handoff, dynamic spawn & aggregation | [Sub-agents & orchestration](./USAGE.md#12-子代理与多-agent-编排), [Group chat & debate](./USAGE.md#13-群聊与辩论) |
| Paradigms | ReAct base + 5 paradigm engines + Router auto-routing, unified pause/resume/clarification semantics | [Paradigms](./USAGE.md#18-智能体范式paradigms-扩展) |
| Safety & compliance | 5-mode permission tiers, content moderation, input/output guardrails, injection detection, audit logging | [Permissions](./USAGE.md#8-权限控制), [Guardrails](./USAGE.md#9-安全护栏) |
| Reliability & durability | Run stores, checkpoint resume, approval stores, distributed run locks | [Durability](./USAGE.md#15-持久化与断点续跑) |
| Observability & eval | Tracing spans, cost/token budgets, rule + LLM dual-judge evaluation, stability runs | [Evaluation](./USAGE.md#16-评测evalrunner) |
| Plan mode & skills | Plan-then-execute, level-advertised skill injection, on-demand loading | [Plan mode](./USAGE.md#11-计划模式) |
| Structured output & interruption | Forced JSON Schema output with auto-retry, runtime interruption | [Structured output](./USAGE.md#14-结构化输出) |

> Note: the linked manual sections are in Chinese; use your browser's translate feature if needed.

***

## Modules

Yangqiong Agent Harness is a **Maven multi-module project** — the core has zero Spring, adapters and extensions ship independently:

| Module | Artifact | Purpose |
| --- | --- | --- |
| Aggregator root | `yangqiong-agent-harness` | Project aggregation / versions / quality governance |
| Core engine | `yangqiong-agent-core` | ReAct engine, models, tools, memory, orchestration, guardrails (zero Spring) |
| BOM | `yangqiong-agent-bom` | Dependency version catalog |
| Spring Boot Starter | `yangqiong-agent-spring-boot-starter` | Auto-configuration, one-dependency setup |
| Paradigms | `yangqiong-agent-paradigms` | Plan-Execute / ReWoo / Reflexion / Self-Ask / Self-Refine + Router auto-routing |
| Model providers | `yangqiong-agent-model-providers` | Anthropic / DashScope / Gemini / Ollama via SPI auto-discovery |
| JDBC store | `yangqiong-agent-store-jdbc` | Runs / checkpoints / approvals / memory / locks persisted to MySQL (MyBatis-Plus) |
| Redis store | `yangqiong-agent-store-redis` | Distributed runtime governance store (zero Spring) |
| Vector store | `yangqiong-agent-store-vector` | Vector persistence for semantic retrieval |
| Local mode | `yangqiong-agent-local-agent` | One-command local agent (SQLite + workspace + shell tools + checkpoint resume) |
| Server | `yangqiong-agent-server` | HTTP service, SSE event stream, approval resume APIs |
| Observability | `yangqiong-agent-observability` | Micrometer / OpenTelemetry, built-in Grafana dashboard |
| Eval | `yangqiong-agent-eval` | YAML cases, rule + LLM dual judge, stability runs, report export |
| Scheduling | `yangqiong-agent-cron` | Cron-scheduled agent tasks |
| Notifications | `yangqiong-agent-notify` | Multi-channel notifications |
| Triggers | `yangqiong-agent-trigger` | File-event triggers for agent runs |
| Examples | `yangqiong-agent-examples` | Runnable examples: hello-world, spring-boot, multi-agent, paradigms, eval, observability, local, server |

***

## Quick Start

**1. Add the dependency (no Spring required):**

```xml
<dependency>
    <groupId>com.yangqiong.agent</groupId>
    <artifactId>yangqiong-agent-core</artifactId>
    <version>1.0.0-SNAPSHOT</version>
</dependency>
```

> 💡 Spring Boot users can add `yangqiong-agent-spring-boot-starter` for auto-configuration instead.

**2. Build an agent runtime:**

```java
AgentModel model = new OpenAIChatModel(/* config */);

AgentRuntime runtime = new HarnessRuntimeBuilder()
    .name("my-agent")
    .model(model)
    .systemPrompt("You are an enterprise assistant")
    .askUserEnabled(true)     // optional: proactively ask the user when info is missing
    .maxIters(10)
    .build();

AgentMessage result = runtime.call(
    List.of(MessageFactory.createUserMessage("Hello")),
    AgentRuntimeContext.empty()
).block();
```

**3. Stream events:**

```java
runtime.stream(
    List.of(MessageFactory.createUserMessage("Hello")),
    AgentRuntimeContext.empty()
).subscribe(event -> {
    switch (event.getType()) {
        case TEXT_BLOCK_DELTA -> System.out.print(((AgentTextBlockDeltaEvent) event).getDelta());
        case AGENT_RESULT -> System.out.println("\n--- done ---");
        case ERROR -> System.err.println("error: " + event.getPayload());
    }
});
```

**4. Define a tool (`AgentTool`):**

```java
public class MyTool implements AgentTool {
    @Override
    public String getName() { return "my_tool"; }

    @Override
    public String getDescription() { return "My custom tool"; }

    @Override
    public Map<String, Object> getParameters() {
        return Map.of("param1", Map.of("type", "string", "description", "Parameter 1"));
    }

    @Override
    public Mono<AgentToolResultBlock> callAsync(AgentToolCallParam param) {
        return Mono.just(AgentToolResultBlock.of(
            List.of(AgentTextBlock.builder().text("result").build())));
    }
}

HarnessToolkit toolkit = new HarnessToolkit(null);
toolkit.addTool(new MyTool());
AgentRuntime runtime = builder.toolkit(toolkit).build();
```

**5. Advanced (middleware / sub-agents / paradigms):**

- **Custom middleware** — implement `AgentMiddleware` to intercept the system prompt and reasoning chain ([Middleware](./USAGE.md#6-中间件middleware))
- **Sub-agents & permissions** — declare sub-agents via `subagentDeclarations`, configure the 5 permission modes via `permissionContextState` ([Permissions](./USAGE.md#8-权限控制), [Sub-agents & orchestration](./USAGE.md#12-子代理与多-agent-编排))
- **Mount a paradigm engine** — switch execution paradigms in one line, see [Paradigms](./USAGE.md#18-智能体范式paradigms-扩展)

```java
AgentRuntime planner = new HarnessRuntimeBuilder()
    .name("planner")
    .model(model)
    .agentLoop(new ReWooEngine())      // or PlanExecute / Reflexion / SelfAsk / SelfRefine
    .build();

// When the task type is unknown, let RouterEngine pick the best paradigm automatically
AgentRuntime router = new HarnessRuntimeBuilder()
    .name("router")
    .model(model)
    .toolkit(toolkit)
    .agentLoop(new RouterEngine())     // routing decision emitted as ENGINE_ROUTED events
    .build();
```

***

## Tech Stack

| Component | Choice |
| --- | --- |
| Language | Java 17 |
| Reactive | Project Reactor 3.7 |
| Build | Maven 3.6+ |
| Serialization | Jackson |
| Testing | JUnit 5 · Mockito · AssertJ · Reactor Test |
| Dependencies | **Zero Spring, minimal third-party dependencies** |

***

## License

[Apache License 2.0](./LICENSE)

## Community & Contact

- **Website** (architecture, extension guides, runnable examples): <https://www.yangqiongtech.com>
- **Feedback / Issues**: [Feedback guide](https://www.yangqiongtech.com/feedback.html); please do NOT disclose security vulnerabilities publicly — email 1781618435@qq.com with subject prefixed `[SECURITY]`
- **QQ Group (community chat)**: 1107572553
- **GitHub**: <https://github.com/yangqiongtech/yangqiong-agent-harness>
- **Gitee mirror**: <https://gitee.com/yangqiongtech/yangqiong-agent-harness>
