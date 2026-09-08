# yangqiong-agent-example-multi-agent

多 Agent 编排示例：演示泱穹智能体框架的三种多 Agent 协作能力。使用桩模型驱动，**无需 API Key**，开箱即跑。

## 演示重点

- 群聊模式：`MsgHub` 轮转讨论，多个专业 Agent 就同一主题依次发言
- 交接模式：`handoff` 协调委派，协调 Agent 将任务移交给专业子 Agent
- 自动编排：`AutoOrchestrationEngine` 辩论模式，多 Agent 辩论并汇总双方观点

## 运行

```bash
mvn -pl yangqiong-agent-examples/yangqiong-agent-example-multi-agent exec:java \
  -Dexec.mainClass=com.yangqiongai.agent.harness.example.multiagent.MultiAgentExample
```

## 真实模型示例（差旅审批助手）

`RealModelTravelApprovalExample` 对接真实大模型，演示两种企业级差旅审批场景：
工具（汇率换算/天气）、技能（差旅合规政策）与多步 ReAct 循环，以及多子代理
并行委派（费用核算 + 合规校验并汇总意见）。

运行前需配置环境变量（**API 密钥通过环境变量注入，代码不硬编码任何 token**）：

| 环境变量 | 必填 | 默认值 | 说明 |
| --- | --- | --- | --- |
| `HARNESS_TEST_API_KEY` | ✅ | - | 大模型 API 密钥 |
| `HARNESS_TEST_MODEL_CODE` | - | `openai:deepseek-v4-flash` | 模型编码 |
| `HARNESS_TEST_BASE_URL` | - | `https://api.deepseek.com` | API 基础地址 |
| `HARNESS_TEST_TIMEOUT_SECONDS` | - | `120` | 单次请求超时秒数 |

```bash
# PowerShell
set HARNESS_TEST_API_KEY=sk-xxx

mvn -pl yangqiong-agent-examples/yangqiong-agent-example-multi-agent exec:java \
  -Dexec.mainClass=com.yangqiongai.agent.harness.example.multiagent.RealModelTravelApprovalExample
```

未配置 `HARNESS_TEST_API_KEY` 时，示例启动会提示退出，不会静默崩溃。
场景一采用同步 `call` 打印审批结论，场景二采用异步 `stream`
打印综合审批意见，便于对比同步与响应式两种调用方式。

## 预期输出

```text
========== 演示1：群聊模式（MsgHub 轮转讨论） ==========
参与讨论Agent: [产品专家, 库存专家, 订单专家]
发言: ...
========== 演示2：交接模式（handoff 协调委派） ==========
协调Agent答复: 产品信息：智能音箱，售价299元。
========== 演示3：自动编排（辩论模式） ==========
辩论汇总: ...
```

## 关键代码位置

- 主类：`MultiAgentExample.java`（桩模型演示）
- 真实模型示例：`RealModelTravelApprovalExample.java`（需 API Key）
- 桩模型：`StubModel`（固定文本）、`HandoffModel`（脚本驱动交接）
- 桩执行器：`EchoSpawner`（回显输入，用于自动编排）

## 扩展到真实模型

将 `StubModel` / `HandoffModel` 替换为 `HarnessModelFactory` 构建的真实模型即可，
装配方式参考 `yangqiong-agent-example-hello-world`；完整真实场景可参考
本节上方 `RealModelTravelApprovalExample`。
