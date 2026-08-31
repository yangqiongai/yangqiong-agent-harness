# yangqiong-agent-example-local

本地模式示例：演示 `yangqiong-agent-local` 扩展的一键装配能力，纯 Java `main()` 方法，零 Spring。
模型驱动支持两种方式：配置 API Key 走远端 OpenAI 兼容服务（无需本机安装大模型），或由本机推理服务驱动（Ollama / LM Studio）。

## 演示重点

- 本地模型发现：`LocalModelDiscoverer` 探测 Ollama（11434）、LM Studio（1234）与 llama.cpp（8080）端点及模型列表
- 一键装配：`LocalAgentHarness.builder()` → `create()` 完成 SQLite 存储 + 工作区骨架 + 模型 + Shell 工具装配
- 同步对话：`runtime.call(...)` 驱动 ReAct 循环，调用 `local_shell` 工具执行 `echo hello` 并汇报结果
- 会话落盘：`SessionTranscriptStore` 将问答以 JSONL 逐行追加写入工作区 `sessions` 目录
- 检查点续跑：`resumeLatest()` 按作用域定位最近检查点，打印是否存在并提供续跑入口

## 运行前提（二选一）

**方式一（推荐，无需本机大模型）：API Key 走远端 OpenAI 兼容服务**
本机未安装大模型时，通过环境变量提供远端服务密钥即可跑通本地模式完整流程（SQLite 落盘、工作区、Shell 工具、检查点续跑）。

**方式二：本机推理服务**
任选其一：
- **Ollama**：从 https://ollama.com 安装并启动服务，拉取一个模型（默认端口 11434）
  ```powershell
  ollama pull llama3
  ```
- **LM Studio**：从 https://lmstudio.ai 安装，加载任意模型并开启本地服务（默认端口 1234）

未配置 API Key 且未探测到任何端点时，示例会友好提示两种方式并退出，不会静默崩溃、也不会使用假模型兜底。

## 运行（PowerShell）

```powershell
# 编译（工程根目录执行，首次运行或代码变更后需要）
mvn -pl yangqiong-agent-examples/yangqiong-agent-example-local -am compile

# 运行示例
mvn -pl yangqiong-agent-examples/yangqiong-agent-example-local exec:java `
  -Dexec.mainClass=com.yangqiong.agent.harness.examples.local.LocalModeExample
```

### 方式一：配置 API Key（远端服务）

```powershell
# DeepSeek 示例（可替换为任意 OpenAI 兼容服务）
$env:AI_API_KEY="sk-xxx"
$env:AI_MODEL="deepseek-chat"
$env:AI_BASE_URL="https://api.deepseek.com/v1"

mvn -pl yangqiong-agent-examples/yangqiong-agent-example-local exec:java `
  -Dexec.mainClass=com.yangqiong.agent.harness.examples.local.LocalModeExample
```

### 方式二：本机推理服务

无需任何环境变量，直接运行即可；示例会自动探测本机 Ollama / LM Studio 端点。

## 演示流程

```text
========== 探测本地推理服务 ==========
发现端点: 类型=OLLAMA，地址=http://localhost:11434
  可用模型: [llama3]
========== 构建本地装配配置 ==========
存储根目录: C:\Users\you\.yangqiong-agent-local-demo
Shell工具: 已开启，ReAct最大迭代次数: 4
========== 装配本地智能体运行时 ==========
工作区根目录: C:\Users\you\.yangqiong-agent-local-demo\workspace
  - AGENTS.md（智能体指令文件）
  - MEMORY.md（记忆文件）
  - sessions/（会话JSONL目录: ...）
  - memory/（记忆目录）
  - skills/（技能目录）
  - knowledge/（知识目录）
========== 发起一次同步对话 ==========
用户: 请用 local_shell 工具执行 echo hello，并把命令输出结果汇报给我。
Agent回复: 命令 echo hello 的输出结果为：hello
========== 会话JSONL落盘 ==========
会话文件: C:\Users\you\.yangqiong-agent-local-demo\workspace\sessions\demo-session.jsonl
会话JSONL行数: 2
========== 演示resumeLatest检查点续跑入口 ==========
是否存在可恢复的检查点: true
最近检查点: runId=...，version=...
```

## 数据目录位置

本地装配以单一根目录为轴，本示例固定使用用户目录下的 `.yangqiong-agent-local-demo`：

| 路径 | 说明 |
| --- | --- |
| `{rootDir}/harness.db` | SQLite 单文件数据库（WAL 模式），承载运行记录、检查点、会话记忆等全部共享存储 |
| `{rootDir}/workspace/` | 本地工作区，含 `AGENTS.md`、`MEMORY.md` 与 `sessions`、`memory`、`skills`、`knowledge` 目录骨架 |

删除该目录即可完全重置示例数据（工作区已存在的文件不会被装配过程覆盖）。

## 关键代码位置

- 主类：`LocalModeExample.java`
- 一键装配：`LocalAgentHarness` / `LocalHarnessConfig` / `LocalAgentSession`
- 模型发现：`LocalModelDiscoverer` / `LocalModelEndpoint`
- 会话落盘：`SessionTranscriptStore`
- 检查点续跑：`LocalAgentResume`（`resumeLatest()` 装配产物）
