# yangqiong-agent-example-hello-world

泱穹智能体框架最简接入示例：纯 Java `main()` 方法，内存存储，零 Spring 依赖。

## 演示重点

- 核心引擎手动装配：模型配置 → 存储聚合 → 运行时构建 → 一次对话
- 零 Spring：仅引入 `yangqiong-agent-core`，演示非 Spring 场景接入

## 运行

```bash
# 设置模型密钥（可选，未设置时仅输出装配指引）
set OPENAI_API_KEY=sk-xxx

# 运行
mvn -pl yangqiong-agent-examples/yangqiong-agent-example-hello-world exec:java \
  -Dexec.mainClass=helloworld.example.com.yangqiong.agent.harness.HelloWorldExample
```

## 关键代码位置

- 主类：`HelloWorldExample.java`
- 内存存储聚合：`MemoryStores`（实现 `DistributedStores`）
- 模型装配：`HarnessModelProperties` + `AgentModelRegistry` + `HarnessModelFactory`
