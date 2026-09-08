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

import java.util.List;

/**
 * 辩论裁判
 * @author yangqiong
 */
public interface DebateJudge {

    /**
     * 根据各方立场产出判定结果
     * @param task 原始任务
     * @param positions 各方立场
     * @param previousFeedback 前一轮反馈（可为空字符串）
     * @return 判定结果文本（中间轮作为下一轮反馈，最终轮作为辩论结论）
     */
    String judge(String task, List<SubagentResult> positions, String previousFeedback);
}
