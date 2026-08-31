English | [中文](./README.md)

# Yangqiong Agent Harness

> **Yangqiong Agent Harness** is an **enterprise-grade AI Agent runtime framework** built in **pure Java (JDK 17)**: **ReAct loop + 6 paradigm engines with auto-routing, sub-agent and multi-agent orchestration, checkpoint-based durable execution, permission & safety guardrails, full-link observability and evaluation** — **zero Spring dependency**, driven by reactive event streams (Project Reactor).
>
> From model integration, tool execution and memory management to multi-agent orchestration, evaluation and production governance, it provides a one-stop runtime foundation for building **stable, controllable, observable and extensible** agent applications in large enterprises.

***

## Why Java?

- **Mature engineering ecosystem** — Maven/Gradle, IDE toolchain, JVM monitoring, APM and containerization are battle-tested; reuse your existing CI/CD instead of introducing a new runtime language.
- **Strong typing, compile-time guarantees** — AI output is inherently non-deterministic; static typing catches tool definitions, message structures and config binding errors early.
- **Zero-rewrite integration with existing systems** — connect to Spring stacks, microservices, middleware, ORM and ERP/CRM systems via Java tools and MCP; agents orchestrate enterprise services like local method calls.
- **Proven concurrency foundation** — reactive programming sustains high-concurrency tool orchestration and streaming output with minimal thread resources.
- **Enterprise security & compliance** — leverage the mature Java ecosystem (Spring Security/Shiro, audit logging, encryption) to meet strict requirements of finance and government sectors.

***

## What Makes It Different

Most Java agent frameworks solve "how to call an LLM from Java". Yangqiong Agent Harness solves "how to run agents **stably and controllably in enterprise production**":

- ✅ **Zero framework coupling** — no Spring required; runs on pure Java SE, and fits naturally into Spring Boot / Quarkus / microservice environments alike
- ✅ **First-class durability** — checkpoint-based resume for long-running tasks, distributed run locks for cross-node consistent scheduling
- ✅ **Full production governance out of the box** — cost budgets, token metering, model routing/fallback/retry, semantic caching and rate limiting
- ✅ **Mature multi-agent orchestration** — sub-agent delegation, automatic task orchestration, handoff, result aggregation, backed by state machines and permission approval
- ✅ **6 paradigm engines + auto routing** — ReAct, Plan-Execute, ReWoo, Reflexion, Self-Ask, Self-Refine, plus a Router meta-layer that picks the best paradigm automatically

***

## Core Features

- **Agent Loop & Paradigms** — ReAct base loop; 5 additional paradigm engines + Router auto-routing; unified approval pause / ledger resume / human clarification semantics
- **Model Layer** — OpenAI / Anthropic / DashScope / Gemini / Ollama / generic HTTP via SPI auto-discovery; health-aware routing, fallback, retry, semantic caching, rate limiting, cost tracking
- **Tools** — unified executor with JSON Schema input validation; MCP integration (HTTP/SSE, streamable, stdio transports); web search & fetch; sandboxed file tools
- **Memory** — session memory + long-term vector memory, context compaction, forgetting policies, checkpoint LRU
- **Multi-Agent** — 6 orchestration strategies (sequential / parallel / adaptive / debate / reflection / group chat), MsgHub, handoff, sub-agent paradigm mounting
- **Safety** — permission engine (5 modes), content moderation, input/output guardrails, prompt-injection detection with guardrail fencing, audit logging
- **Reliability** — durable run stores, checkpoint resume, cross-node distribution, structured-output auto-retry
- **Observability & Eval** — full-link tracing, token/cost accounting, rule + LLM-as-Judge evaluation with stability runs

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

**2. Build an agent runtime:**

```java
AgentModel model = new OpenAIChatModel(/* config */);

AgentRuntime runtime = new HarnessRuntimeBuilder()
    .name("my-agent")
    .model(model)
    .systemPrompt("You are an enterprise assistant")
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

**4. Mount paradigms (extension module):**

```java
AgentRuntime runtime = new HarnessRuntimeBuilder()
    .name("planner")
    .model(model)
    .agentLoop(new ReWooEngine())   // or PlanExecute / Reflexion / SelfAsk / SelfRefine / Router
    .build();
```

> Spring Boot users can add `yangqiong-agent-spring-boot-starter` for auto-configuration instead.

***

## Modules

| Module | Artifact | Purpose |
| --- | --- | --- |
| Core engine | `yangqiong-agent-core` | ReAct engine, models, tools, memory, orchestration, guardrails (zero Spring) |
| BOM | `yangqiong-agent-bom` | Dependency version catalog |
| Spring Boot Starter | `yangqiong-agent-spring-boot-starter` | Auto-configuration, one-dependency setup |
| Paradigms | `yangqiong-agent-paradigms` | 5 paradigm engines + Router auto-routing |
| Model providers | `yangqiong-agent-model-providers` | Anthropic / DashScope / Gemini / Ollama via SPI |
| Stores | `yangqiong-agent-store-jdbc` / `-redis` / `-vector` | Durable stores for MySQL / Redis / vector search |
| Local mode | `yangqiong-agent-local-agent` | One-command local agent (SQLite + workspace + shell) |
| Server | `yangqiong-agent-server` | HTTP service, SSE event stream, approval resume APIs |
| Observability | `yangqiong-agent-observability` | Micrometer / OpenTelemetry, built-in Grafana dashboard |
| Eval | `yangqiong-agent-eval` | YAML cases, rule + LLM dual judge, stability runs |
| Scheduling & events | `yangqiong-agent-cron` / `-notify` / `-trigger` | Cron tasks, notifications, file-event triggers |
| Examples | `yangqiong-agent-examples` | Runnable examples: hello-world, spring-boot, multi-agent, paradigms, eval, etc. |

***

## Tech Stack

Java 17 · Project Reactor 3.7 · Maven 3.6+ · Jackson · **Zero Spring, minimal third-party dependencies**

***

## Documentation

> 📖 The full documentation (USAGE.md, guides, examples) is currently maintained in Chinese. The [official website](https://www.yangqiongtech.com) provides architecture overviews, extension guides and runnable examples.
>
> - [USAGE.md](./USAGE.md) — detailed usage manual (Chinese)
> - [Official website](https://www.yangqiongtech.com) (Chinese)
> - [Gitee mirror](https://gitee.com/yangqiongtech/yangqiong-agent-harness)

## License

[Apache License 2.0](./LICENSE)

## Community & Contact

- **Website** (docs, guides & examples): <https://www.yangqiongtech.com>
- **Feedback / Issues**: [Feedback guide](https://www.yangqiongtech.com/feedback.html); please do NOT disclose security vulnerabilities publicly — email 1781618435@qq.com with subject prefixed `[SECURITY]`
- **QQ Group (community chat)**: 1107572553
- **Gitee mirror**: <https://gitee.com/yangqiongtech/yangqiong-agent-harness>
