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

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * 评测数据集
 * @author yangqiong
 */
public final class EvalDataset {

    /**
     * 数据集名称
     */
    private final String name;

    /**
     * 评测用例列表
     */
    private final List<EvalCase> cases;

    private EvalDataset(String name, List<EvalCase> cases) {
        this.name = name;
        this.cases = cases;
    }

    /**
     * 创建评测数据集
     * @param name
     * @param cases
     * @return
     */
    public static EvalDataset of(String name, EvalCase... cases) {
        Objects.requireNonNull(name, "name不能为空");
        List<EvalCase> caseList = cases == null || cases.length == 0
                ? Collections.emptyList()
                : List.copyOf(Arrays.asList(cases));
        return new EvalDataset(name, caseList);
    }

    /**
     * 获取数据集名称
     * @return
     */
    public String getName() {
        return name;
    }

    /**
     * 获取评测用例列表
     * @return
     */
    public List<EvalCase> getCases() {
        return cases;
    }
}
