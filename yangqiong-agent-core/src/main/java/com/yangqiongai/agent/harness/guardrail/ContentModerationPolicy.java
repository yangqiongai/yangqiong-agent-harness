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
package com.yangqiongai.agent.harness.guardrail;

import com.yangqiongai.agent.harness.core.message.AgentMessage;

/**
 * 内容审查策略 SPI
 * <p>
 * 对Agent消息执行LLM裁判级内容审查，返回PASS/BLOCK/MASK裁决。
 * 引擎侧提供默认{@link NoopContentModerationPolicy}（放行所有内容），
 * 平台侧可注入基于LLM的审查实现（如关键词审查、敏感内容检测等）。
 * </p>
 * @author yangqiong
 */
public interface ContentModerationPolicy {

    /**
     * 审查消息内容
     * @param message 待审查的消息
     * @return 审查裁决
     */
    ModerationVerdict moderate(AgentMessage message);
}