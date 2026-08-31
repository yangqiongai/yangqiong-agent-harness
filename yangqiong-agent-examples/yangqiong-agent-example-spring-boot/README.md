# yangqiong-agent-example-spring-boot

基于 `yangqiong-agent-spring-boot-starter` 的 Spring Boot Web 示例，演示 Starter 自动装配接入方式。

## 演示重点

- Starter 自动装配：`AgentRuntime` Bean 由自动配置构建，业务代码直接注入使用
- 配置驱动：`ai.harness.*` 前缀统一管理模型、运行时、存储、追踪
- REST 接入：通过 `/api/agent/chat` 对外暴露 Agent 对话能力

## 运行

```bash
# 设置模型密钥
set OPENAI_API_KEY=sk-xxx

# 启动应用
mvn -pl yangqiong-agent-examples/yangqiong-agent-example-spring-boot spring-boot:run
```

## 调用

```bash
curl -X POST http://localhost:8080/api/agent/chat \
  -H "Content-Type: application/json" \
  -d '{"message":"你好，请用一句话介绍你自己"}'
```

## 关键代码位置

- 入口类：`ExampleSpringBootApplication.java`
- 对话接口：`AgentController.java`（注入自动装配的 `AgentRuntime`）
- 配置：`src/main/resources/application.yml`

## 存储切换

默认使用内存存储（`ai.harness.store.type=memory`）。切换为 JDBC/Redis 时：

1. 额外引入扩展模块坐标（如 `yangqiong-agent-store-jdbc`）
2. 定义自己的 `DistributedStores` Bean（扩展模块提供 `JdbcStoresFactory` / `RedisStores` 手动装配）
3. 将 `ai.harness.store.type` 改为对应类型
