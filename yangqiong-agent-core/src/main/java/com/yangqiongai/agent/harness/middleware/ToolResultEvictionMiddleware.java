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
package com.yangqiongai.agent.harness.middleware;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import com.yangqiongai.agent.harness.engine.AgentRuntimeContext;
import com.yangqiongai.agent.harness.config.AgentToolResultEvictionConfig;
import com.yangqiongai.agent.harness.core.event.AgentEvent;
import com.yangqiongai.agent.harness.core.message.AgentContentBlock;
import com.yangqiongai.agent.harness.core.message.AgentMessage;
import com.yangqiongai.agent.harness.core.message.AgentTextBlock;
import com.yangqiongai.agent.harness.core.message.AgentToolResultBlock;
import com.yangqiongai.agent.harness.core.middleware.AgentMiddleware;
import reactor.core.publisher.Flux;

/**
 * 工具结果驱逐中间件
 * @author yangqiong
 */
public class ToolResultEvictionMiddleware implements AgentMiddleware {

    /**
     * 驱逐配置
     */
    private final AgentToolResultEvictionConfig config;

    public ToolResultEvictionMiddleware(AgentToolResultEvictionConfig config) {
        this.config = config;
    }

    /**
     * 推理阶段前清理历史工具结果
     * @param context
     * @param input
     * @param next
     * @return
     */
    @Override
    public Flux<AgentEvent> onReasoning(AgentRuntimeContext context, List<AgentMessage> input,
                                         Function<List<AgentMessage>, Flux<AgentEvent>> next) {
        List<AgentMessage> messages = input;
        if (config != null && config.getMaxResultChars() > 0 && messages != null) {
            messages = evictOldToolResults(messages);
        }
        return next.apply(messages);
    }

    /**
     * 清理旧工具结果，超过最大字符数的截断为预览
     * @param messages
     * @return
     */
    private List<AgentMessage> evictOldToolResults(List<AgentMessage> messages) {
        List<AgentMessage> result = new ArrayList<>(messages.size());
        int maxChars = config.getMaxResultChars();
        int preview = config.getPreviewChars() > 0 ? config.getPreviewChars() : Math.max(1, maxChars / 4);
        for (AgentMessage message : messages) {
            if (message == null || message.getContent() == null) {
                result.add(message);
                continue;
            }
            result.add(evictMessageIfNeeded(message, maxChars, preview));
        }
        return result;
    }

    /**
     * 对单条消息中的工具结果进行截断
     * @param message
     * @param maxChars
     * @param preview
     * @return
     */
    private AgentMessage evictMessageIfNeeded(AgentMessage message, int maxChars, int preview) {
        boolean mutated = false;
        List<AgentContentBlock> newContent = new ArrayList<>(message.getContent().size());
        for (AgentContentBlock block : message.getContent()) {
            if (block instanceof AgentToolResultBlock toolResult) {
                String text = toolResult.getTextContent();
                if (text != null && text.length() > maxChars) {
                    String truncated = text.substring(0, Math.min(preview, text.length())) + "...[已截断]";
                    AgentTextBlock previewBlock = AgentTextBlock.builder().text(truncated).build();
                    AgentToolResultBlock newResult = toolResult.isError()
                            ? AgentToolResultBlock.error(toolResult.getToolUseId(), truncated)
                            : AgentToolResultBlock.of(toolResult.getToolUseId(), List.of(previewBlock));
                    newContent.add(newResult);
                    mutated = true;
                    continue;
                }
            }
            newContent.add(block);
        }
        if (!mutated) {
            return message;
        }
        return AgentMessage.builder()
                .name(message.getName())
                .role(message.getRole())
                .content(newContent)
                .chatUsage(message.getChatUsage())
                .latency(message.getLatency())
                .build();
    }
}
