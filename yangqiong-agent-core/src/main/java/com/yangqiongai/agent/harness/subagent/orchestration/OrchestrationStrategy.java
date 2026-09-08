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

/**
 * 编排策略
 * @author yangqiong
 */
public enum OrchestrationStrategy {

    /**
     * 顺序执行：子代理按序依次执行，前一个的输出作为后一个的输入
     */
    SEQUENTIAL,

    /**
     * 并行执行：子代理同时执行，结果统一汇总
     */
    PARALLEL,

    /**
     * 自适应：由LLM根据任务特征选择具体编排策略（含辩论、反思、群聊），未注入LLM时按子代理数量启发式选择
     */
    ADAPTIVE,

    /**
     * 辩论：多子代理各自独立作答，裁判综合各方立场并给出反馈，多轮迭代后产出最终结论
     */
    DEBATE,

    /**
     * 反思：执行者作答、评审者批判、执行者据批判修订，多轮迭代后产出修订结论
     */
    REFLECTION,

    /**
     * 群聊：多子代理经消息中枢轮转发言，各代理看到前序发言后继续讨论
     */
    GROUP_CHAT
}
