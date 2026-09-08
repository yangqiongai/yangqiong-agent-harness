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
 * 子代理事件传播策略
 * @author yangqiong
 */
public enum SubagentEventEmitPolicy {

    /**
     * 不传播子代理事件
     */
    NONE,

    /**
     * 仅传播子代理最终结果事件
     */
    SUMMARY_ONLY,

    /**
     * 传播子代理全部事件（含推理/工具调用细节），带parentAgentPath标记
     */
    FULL
}
