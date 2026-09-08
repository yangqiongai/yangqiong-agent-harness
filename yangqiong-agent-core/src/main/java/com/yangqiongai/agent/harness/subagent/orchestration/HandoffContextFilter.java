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

import java.util.ArrayList;
import java.util.List;

import com.yangqiongai.agent.harness.core.message.AgentMessage;
import com.yangqiongai.agent.harness.core.message.AgentMessageRole;
import com.yangqiongai.agent.harness.core.message.AgentTextBlock;

/**
 * Handoff上下文过滤器
 * @author yangqiong
 */
public interface HandoffContextFilter {

    /**
     * 过滤移交上下文消息
     * @param history
     * @param context
     * @return
     */
    List<AgentMessage> filter(List<AgentMessage> history, String context);

    /**
     * 获取默认过滤器实例
     * @return
     */
    static HandoffContextFilter defaultFilter() {
        return DefaultFilterHolder.INSTANCE;
    }

    /**
     * Handoff上下文默认过滤器
     * @author yangqiong
     */
    final class DefaultHandoffContextFilter implements HandoffContextFilter {

        /**
         * 过滤移交上下文消息
         * @param history
         * @param context
         * @return
         */
        @Override
        public List<AgentMessage> filter(List<AgentMessage> history, String context) {
            List<AgentMessage> result = new ArrayList<>();
            // 上下文文本作为USER消息保留
            if (context != null && !context.isBlank()) {
                result.add(AgentMessage.builder()
                        .role(AgentMessageRole.USER)
                        .content(List.of(AgentTextBlock.builder().text(context).build()))
                        .build());
            }
            // 追加历史中最后一条USER消息
            if (history != null) {
                for (int i = history.size() - 1; i >= 0; i--) {
                    AgentMessage message = history.get(i);
                    if (message != null && message.getRole() == AgentMessageRole.USER) {
                        result.add(message);
                        break;
                    }
                }
            }
            return result;
        }
    }

    /**
     * 默认过滤器实例持有者
     * @author yangqiong
     */
    final class DefaultFilterHolder {

        /**
         * 默认过滤器单例
         */
        static final HandoffContextFilter INSTANCE = new DefaultHandoffContextFilter();
    }
}
