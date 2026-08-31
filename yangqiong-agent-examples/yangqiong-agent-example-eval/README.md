# 评测扩展示例（yangqiong-agent-example-eval）

演示 `yangqiong-agent-eval` 扩展的完整评测链路：**内置演示工具 + YAML 用例集 + 单轮评测 + 稳定性跑批 + 报告导出**。

## 运行方式

```powershell
# 可选环境变量（默认值与 hello-world 示例一致）
$env:AI_API_KEY="sk-xxx"; $env:AI_MODEL="claude-sonnet-5"; $env:AI_BASE_URL="https://ooioo.work"; $env:EVAL_ROUNDS="3"

mvn -pl yangqiong-agent-examples/yangqiong-agent-example-eval exec:java -Dexec.mainClass=com.yangqiong.agent.harness.example.eval.EvalExample
```

## 示例内容

1. **演示工具**：三个只读工具，真实参与模型工具选择
   - `get_weather` —— 按传入城市回显天气信息
   - `book_meeting_room` —— 预订会议室
   - `query_order` —— **故障注入**：每个订单前两次调用模拟超时失败，第三次起成功，用于评测失败重试恢复能力
2. **用例集**：`src/main/resources/eval/agent-smoke-suite.yaml`，共 21 条，覆盖五类场景
   - 工具选择 + **参数校验**（`expectedToolArgs`，如 `city: 北京`，字符串包含匹配、数值相等匹配）
   - 失败恢复（工具故障注入后模型是否重试直至成功）
   - 工具选择（仅名称与关键词）
   - **误调用检测**（`forbiddenToolNames`，纯知识问题禁止调用工具，误调用即工具维度 0 分）
   - 多工具组合（一次任务串联天气查询 + 会议室预订）
3. **单轮评测**：`EvalRunner.run(dataset, 3)`，并发 3 执行，输出通过率/平均分/平均耗时
4. **稳定性跑批**：`StabilityRunner.run(dataset, rounds, 2, null)`，默认 5 轮（`EVAL_ROUNDS` 可调），识别波动（flaky）用例
5. **报告导出**：输出到 `target/eval/`
   - `report.json` —— 结构化数据，便于接入 CI
   - `report.md` —— 单轮明细表格（含参数命中列），可直接发布
   - `stability-report.md` —— 稳定性统计（通过率、得分波动、最大耗时、flaky 标记）

## 评分规则

- 评分维度：工具命中率、参数匹配率、关键词命中率
- 综合得分按用例期望维度动态加权：仅关键词时为关键词命中率；工具+关键词为 0.6/0.4；工具+参数为 0.6/0.4；三维齐全为 0.4/0.3/0.3，满分 1.0 判定通过
- 调用任一禁止工具（`forbiddenToolNames`）时工具维度得 0 分

## 注意事项

- 示例使用 `approvalMode(FULL_ACCESS)` 跳过人工审批，保证评测无人值守运行；生产评测请按需收紧
- 每条用例会真实调用模型 API，稳定性轮数越多 token 消耗越大，建议先小轮数验证
