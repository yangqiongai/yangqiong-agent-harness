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
package com.yangqiongai.agent.harness.eval;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 稳定性评测执行，重复运行数据集并按用例聚合通过率与耗时分布
 * @author yangqiong
 */
public final class StabilityRunner {

    /**
     * 被复用的单轮评测执行器
     */
    private final EvalRunner runner;

    /**
     * 构建稳定性评测执行器
     * @param runner
     * @return
     */
    public StabilityRunner(EvalRunner runner) {
        this.runner = runner;
    }

    /**
     * 串行运行指定轮数
     * @param dataset
     * @param runs
     * @return
     */
    public StabilityReport run(EvalDataset dataset, int runs) {
        return run(dataset, runs, 1, null);
    }

    /**
     * 按指定轮数、并发度与判分器运行，轮内复用 EvalRunner 并发能力
     * @param dataset
     * @param runs
     * @param concurrency
     * @param judge
     * @return
     */
    public StabilityReport run(EvalDataset dataset, int runs, int concurrency, EvalJudge judge) {
        if (runs < 1) {
            throw new IllegalArgumentException("运行轮数必须大于等于1");
        }
        if (concurrency < 1) {
            throw new IllegalArgumentException("并发度必须大于等于1");
        }
        Map<String, List<EvalCaseResult>> resultsByCase = new LinkedHashMap<>();
        for (int i = 0; i < runs; i++) {
            EvalReport roundReport = runner.run(dataset, concurrency, judge);
            for (EvalCaseResult result : roundReport.getResults()) {
                resultsByCase.computeIfAbsent(result.getCaseId(), key -> new ArrayList<>()).add(result);
            }
        }
        List<CaseStability> caseStats = new ArrayList<>();
        for (Map.Entry<String, List<EvalCaseResult>> entry : resultsByCase.entrySet()) {
            caseStats.add(new CaseStability(entry.getKey(), entry.getValue()));
        }
        return new StabilityReport(dataset.getName(), runs, caseStats);
    }
}
