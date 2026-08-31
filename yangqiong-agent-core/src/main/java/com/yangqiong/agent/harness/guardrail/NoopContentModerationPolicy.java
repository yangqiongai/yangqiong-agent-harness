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
package com.yangqiong.agent.harness.guardrail;

import com.yangqiong.agent.harness.core.message.AgentMessage;

/**
 * 默认内容审查策略（放行所有内容）
 * <p>
 * 未注入外部审查实现时使用，不执行任何审查，所有消息均返回PASS。
 * 平台侧注入真实审查策略后替换此实现。
 * </p>
 * @author yangqiong
 */
public class NoopContentModerationPolicy implements ContentModerationPolicy {

    /**
     * 放行所有消息
     * @param message
     * @return
     */
    @Override
    public ModerationVerdict moderate(AgentMessage message) {
        return ModerationVerdict.pass();
    }
}