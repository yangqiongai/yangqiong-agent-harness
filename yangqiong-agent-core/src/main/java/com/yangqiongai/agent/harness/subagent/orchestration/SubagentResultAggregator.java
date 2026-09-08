/*
 * Copyright 2026 yangqiongai.com
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.yangqiongai.agent.harness.subagent.orchestration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 子代理结果汇总器
 * <p>
 * 接收子代理执行结果列表，按容错策略处理失败情况，生成最终汇总文本。
 * </p>
 * @author yangqiong
 */
public class SubagentResultAggregator {

    private static final Logger log = LoggerFactory.getLogger(SubagentResultAggregator.class);

    /**
     * 汇总子代理结果
     * @param results 子代理结果列表
     * @param failurePolicy 容错策略
     * @return 汇总文本
     */
    public String aggregate(List<SubagentResult> results, OrchestrationFailurePolicy failurePolicy) {
        if (results == null || results.isEmpty()) {
            return "无子代理执行结果";
        }
        if (failurePolicy == OrchestrationFailurePolicy.FAIL_FAST) {
            for (SubagentResult r : results) {
                if (r.isFailure()) {
                    log.warn("FAIL_FAST策略触发: 子代理[{}]失败: {}", r.name(), r.error());
                    return "编排失败（快速失败）: 子代理[" + r.name() + "]执行失败: " + r.error();
                }
            }
        }
        long successCount = results.stream().filter(SubagentResult::isSuccess).count();
        long failureCount = results.size() - successCount;
        StringBuilder sb = new StringBuilder();
        sb.append("编排完成: 成功").append(successCount).append("个");
        if (failureCount > 0) {
            sb.append("，失败").append(failureCount).append("个");
        }
        sb.append("。\n\n");
        for (SubagentResult r : results) {
            sb.append("## ").append(r.name());
            if (r.isSuccess()) {
                sb.append("（成功，耗时").append(r.duration()).append("ms）\n");
                sb.append(r.output() != null ? r.output() : "").append("\n\n");
            } else {
                sb.append("（失败，耗时").append(r.duration()).append("ms）\n");
                sb.append("错误: ").append(r.error() != null ? r.error() : "未知错误").append("\n\n");
            }
        }
        return sb.toString();
    }

    /**
     * 获取所有失败的子代理结果
     * @param results
     * @return
     */
    public List<SubagentResult> getFailures(List<SubagentResult> results) {
        if (results == null) {
            return List.of();
        }
        return results.stream()
                .filter(SubagentResult::isFailure)
                .collect(Collectors.toList());
    }
}
