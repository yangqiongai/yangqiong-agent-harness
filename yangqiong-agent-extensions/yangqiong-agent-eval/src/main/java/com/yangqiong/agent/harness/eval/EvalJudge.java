/*
 * Copyright 2026 yangqiongtech.com
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
package com.yangqiong.agent.harness.eval;

import java.util.List;

/**
 * 评测判分器
 * @author yangqiong
 */
public interface EvalJudge {

    /**
     * 对单条用例判分，返回得分与判分理由
     * @param evalCase 评测用例
     * @param finalAnswer 实际最终答案
     * @param actualToolNames 实际调用的工具名称列表
     * @return
     */
    JudgeVerdict judge(EvalCase evalCase, String finalAnswer, List<String> actualToolNames);
}
