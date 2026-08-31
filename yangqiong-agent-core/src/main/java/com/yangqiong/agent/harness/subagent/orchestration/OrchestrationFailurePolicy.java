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
package com.yangqiong.agent.harness.subagent.orchestration;

/**
 * 编排容错策略
 * @author yangqiong
 */
public enum OrchestrationFailurePolicy {

    /**
     * 快速失败：第一个子代理失败即中止整个编排
     */
    FAIL_FAST,

    /**
     * 继续执行：某个子代理失败后继续执行其余子代理，最终汇总所有结果
     */
    CONTINUE_ON_FAILURE
}
