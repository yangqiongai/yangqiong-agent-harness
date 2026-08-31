# 实用特性示例（yangqiong-agent-example-features）

综合演示框架第二批实用特性，与 `NewFeatureRealModelTest` 集成测试覆盖的能力一一对应：**Web检索/抓取、AskUser澄清、模型速率限制、LLM-as-Judge评测、执行追踪、结构化输出自动修复、内容审查、文件沙箱、计划模式**。

## 运行方式

```powershell
# 必填环境变量（默认模型与 hello-world 示例一致）
$env:AI_API_KEY="sk-xxx"; $env:AI_MODEL="deepseek-v4-flash"; $env:AI_BASE_URL="https://api.deepseek.com"

# 运行全部演示
mvn -pl yangqiong-agent-examples/yangqiong-agent-example-features exec:java -Dexec.mainClass=com.yangqiong.agent.harness.example.features.FeaturesExample

# 只运行指定演示（可传多个）
mvn -pl yangqiong-agent-examples/yangqiong-agent-example-features exec:java -Dexec.mainClass=com.yangqiong.agent.harness.example.features.FeaturesExample -Dexec.args="ask-user trace"
```

## 示例内容（10 项演示）

| 演示名 | 特性 | 说明 |
|---|---|---|
| `web-search` | Web检索 | 注入内存 `WebSearchProvider`，模型调用 `web_search` 工具并基于结果回答 |
| `web-fetch` | Web抓取 | 注入内存 `WebFetcher`，模型调用 `web_fetch` 工具抓取页面内容 |
| `ask-user` | AskUser澄清 | 模型调用 `ask_user` 暂停等待（`REQUIRE_USER_CLARIFICATION` 事件），注入答案后 `resumeWithClarification` 续接完成 |
| `rate-limit` | 模型速率限制 | 高容量令牌桶不阻塞正常调用；极低容量下第二次调用收到 `RateLimitExceededException` |
| `eval` | LLM-as-Judge评测 | `EvalRunner` + `LLMEvalJudge`，规则评分（工具命中+关键词命中）与 LLM 判分结合输出报告 |
| `trace` | 执行追踪 | `TraceEmitter` 采集 `agent_run` 根 Span 与 `reasoning/acting` 子 Span，打印 traceId/spanId/父子层级与耗时 |
| `structured-output` | 结构化输出自动修复 | JSON Schema 约束 + `structuredOutputRetry`，诱导首次输出不合规后引擎注入校验错误自动重试修正 |
| `moderation` | 内容审查 | BLOCK 策略在进入 LLM 前拦截输入（模型不被调用）；MASK 策略将敏感词脱敏后放行 |
| `file-tool` | 文件沙箱工具 | `enableFileToolkit` 限定沙箱目录，模型通过 `file_read` 读取工作区文件 |
| `plan-mode` | 计划模式 | 注册 `plan_enter/plan_write/plan_exit` 三工具，模型先规划（写入上下文）后执行 |

## 关键实现点

- **暂停/续接同上下文**：AskUser 演示中暂停与续接必须复用同一个 `AgentRuntimeContext`，暂停快照保存在其 `attributes` 中
- **连续澄清循环**：模型收到答案后可能再次追问，演示按最多 3 轮循环 `resumeWithClarification` 直到产出最终结果
- **内存 Provider**：Web 检索/抓取使用内置内存实现（无外网依赖），并统计调用次数验证工具真实被调用
- **真实模型不确定性**：`ask-user`/`plan-mode` 依赖模型主动触发特定工具，未触发时友好提示而非报错

## 注意事项

- 示例使用 `approvalMode(FULL_ACCESS)` 跳过人工审批保证无人值守运行，生产环境请按需收紧
- 每项演示均真实调用模型 API，全量运行约消耗 15+ 次调用，建议先用 `-Dexec.args` 选择性运行
