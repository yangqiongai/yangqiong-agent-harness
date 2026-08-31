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
package com.yangqiong.agent.harness.model;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yangqiong.agent.harness.core.event.AgentEvent;
import com.yangqiong.agent.harness.core.event.AgentTextBlockDeltaEvent;
import com.yangqiong.agent.harness.core.event.AgentThinkingBlockDeltaEvent;
import com.yangqiong.agent.harness.core.event.AgentToolCallDeltaEvent;
import com.yangqiong.agent.harness.core.message.AgentChatUsage;
import com.yangqiong.agent.harness.core.message.AgentContentBlock;
import com.yangqiong.agent.harness.core.message.AgentMessage;
import com.yangqiong.agent.harness.core.message.AgentMessageRole;
import com.yangqiong.agent.harness.core.message.AgentTextBlock;
import com.yangqiong.agent.harness.core.message.AgentThinkingBlock;
import com.yangqiong.agent.harness.core.message.AgentToolUseBlock;
import com.yangqiong.agent.harness.core.model.AgentChatResponse;

/**
 * 模型响应解析器
 * @author yangqiong
 */
public class ModelResponseParser {

    private static final Logger log = LoggerFactory.getLogger(ModelResponseParser.class);

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /**
     * 流式工具调用原始参数分片的保留键，存储未解析的arguments字符串分片
     */
    public static final String RAW_ARGS_KEY = "__raw_args__";

    /**
     * 流式工具调用真实ID的保留键，index派生的稳定id仅在合并阶段使用，最终需恢复真实ID
     */
    public static final String TOOL_CALL_ID_KEY = "__tool_call_id__";

    /**
     * 将响应解析为助手消息
     * @param response
     * @return
     */
    public AgentMessage parseToMessage(AgentChatResponse response) {
        if (response == null) {
            return AgentMessage.builder()
                    .role(AgentMessageRole.ASSISTANT)
                    .build();
        }
        return AgentMessage.builder()
                .role(AgentMessageRole.ASSISTANT)
                .content(response.getContent())
                .chatUsage(response.getChatUsage())
                .build();
    }

    /**
     * 判断响应是否包含工具调用
     * @param response
     * @return
     */
    public boolean hasToolCalls(AgentChatResponse response) {
        if (response == null) {
            return false;
        }
        List<AgentContentBlock> blocks = response.getContent();
        if (blocks == null || blocks.isEmpty()) {
            return false;
        }
        for (AgentContentBlock block : blocks) {
            if (block instanceof AgentToolUseBlock) {
                return true;
            }
        }
        return false;
    }

    /**
     * 提取响应中的工具调用列表
     * @param response
     * @return
     */
    public List<AgentToolUseBlock> extractToolCalls(AgentChatResponse response) {
        List<AgentToolUseBlock> toolCalls = new ArrayList<>();
        if (response == null) {
            return toolCalls;
        }
        List<AgentContentBlock> blocks = response.getContent();
        if (blocks == null || blocks.isEmpty()) {
            return toolCalls;
        }
        for (AgentContentBlock block : blocks) {
            if (block instanceof AgentToolUseBlock toolUseBlock) {
                toolCalls.add(toolUseBlock);
            }
        }
        return toolCalls;
    }

    /**
     * 将流式增量响应解析为事件
     * @param delta
     * @param thinkingBlockId
     * @return
     */
    public AgentEvent parseDeltaToEvent(AgentChatResponse delta, String thinkingBlockId) {
        List<AgentEvent> events = parseDeltasToEvents(delta, thinkingBlockId);
        return events.isEmpty() ? null : events.get(0);
    }

    /**
     * 将流式增量响应解析为多个事件，区分文本增量、思考增量与工具调用增量
     * <p>
     * 一个增量分片可能同时包含多种内容块，此方法返回所有匹配的事件。
     * </p>
     * @param delta
     * @param thinkingBlockId
     * @return
     */
    public List<AgentEvent> parseDeltasToEvents(AgentChatResponse delta, String thinkingBlockId) {
        List<AgentEvent> events = new ArrayList<>();
        if (delta == null) {
            return events;
        }
        List<AgentContentBlock> blocks = delta.getContent();
        if (blocks == null || blocks.isEmpty()) {
            return events;
        }
        for (AgentContentBlock block : blocks) {
            if (block instanceof AgentTextBlock textBlock) {
                String text = textBlock.getText();
                if (text != null && !text.isEmpty()) {
                    events.add(new AgentTextBlockDeltaEvent(text));
                }
            } else if (block instanceof AgentThinkingBlock thinkingBlock) {
                String thinking = thinkingBlock.getThinking();
                if (thinking != null && !thinking.isEmpty()) {
                    events.add(new AgentThinkingBlockDeltaEvent(thinking));
                }
            } else if (block instanceof AgentToolUseBlock toolUseBlock) {
                // 清除内部保留键，避免泄露给事件消费者
                Map<String, Object> cleanInput = new HashMap<>(toolUseBlock.getInput());
                cleanInput.remove(RAW_ARGS_KEY);
                cleanInput.remove(TOOL_CALL_ID_KEY);
                events.add(new AgentToolCallDeltaEvent(toolUseBlock.getToolName(),
                        toolUseBlock.getToolUseId(), cleanInput));
            }
        }
        return events;
    }

    /**
     * 合并多分片流式响应为单个完整响应
     * <p>
     * 文本块按顺序拼接；工具调用块按toolUseId合并，同名同ID的工具调用参数会被合并。
     * </p>
     * @param responses
     * @return
     */
    public AgentChatResponse mergeResponses(List<AgentChatResponse> responses) {
        if (responses == null || responses.isEmpty()) {
            return new AgentChatResponse(List.of(), null);
        }
        List<AgentTextBlock> textBlocks = new ArrayList<>();
        Map<String, AgentToolUseBlock> toolCallMap = new HashMap<>();
        List<AgentContentBlock> otherBlocks = new ArrayList<>();
        List<AgentChatUsage> usages = new ArrayList<>();
        for (AgentChatResponse response : responses) {
            if (response == null) {
                continue;
            }
            List<AgentContentBlock> blocks = response.getContent();
            if (blocks != null) {
                for (AgentContentBlock block : blocks) {
                    if (block instanceof AgentTextBlock textBlock) {
                        textBlocks.add(textBlock);
                    } else if (block instanceof AgentToolUseBlock toolUseBlock) {
                        // 按toolUseId合并分片工具调用
                        String key = toolUseBlock.getToolUseId() != null
                                ? toolUseBlock.getToolUseId() : toolUseBlock.getToolName();
                        AgentToolUseBlock existing = toolCallMap.get(key);
                        if (existing != null) {
                            toolCallMap.put(key, mergeToolUseBlocks(existing, toolUseBlock));
                        } else {
                            toolCallMap.put(key, toolUseBlock);
                        }
                    } else {
                        otherBlocks.add(block);
                    }
                }
            }
            if (response.getChatUsage() != null) {
                usages.add(response.getChatUsage());
            }
        }
        // 合并文本块与非文本块，工具调用块需完成原始参数解析与真实ID恢复
        List<AgentContentBlock> mergedBlocks = new ArrayList<>();
        mergedBlocks.addAll(mergeTextBlocks(textBlocks));
        for (AgentToolUseBlock toolBlock : toolCallMap.values()) {
            AgentToolUseBlock finalized = finalizeToolBlock(toolBlock);
            //  fix 防御：丢弃无法执行的匿名工具调用残块（流式分片未聚合成功时名称为空），
            // 回传给API会导致invalid_request_error（tool_use.name不能为空）
            if (finalized.getToolName() == null || finalized.getToolName().isBlank()) {
                log.warn("丢弃匿名工具调用残块, id={}, inputKeys={}",
                        finalized.getToolUseId(), finalized.getInput().keySet());
            } else {
                mergedBlocks.add(finalized);
            }
        }
        mergedBlocks.addAll(otherBlocks);
        AgentChatUsage mergedUsage = usages.isEmpty() ? null : mergeUsage(usages);
        return new AgentChatResponse(mergedBlocks, mergedUsage);
    }

    /**
     * 合并两个工具调用分片
     * <p>
     * 流式分片的arguments是JSON字符串片段，需拼接而非Map合并；
     * 真实工具调用ID仅在首个分片出现，合并后保留。
     * </p>
     * @param base 基础块
     * @param delta 增量块
     * @return
     */
    private AgentToolUseBlock mergeToolUseBlocks(AgentToolUseBlock base, AgentToolUseBlock delta) {
        String toolName = delta.getToolName() != null && !delta.getToolName().isBlank()
                ? delta.getToolName() : base.getToolName();
        String toolUseId = delta.getToolUseId() != null ? delta.getToolUseId() : base.getToolUseId();
        Map<String, Object> mergedInput = new HashMap<>();
        // 合并非保留键字段（非流式场景的普通Map合并）
        mergeNonReservedKeys(mergedInput, base.getInput());
        mergeNonReservedKeys(mergedInput, delta.getInput());
        // 拼接原始参数分片
        String baseRaw = getRawArgs(base.getInput());
        String deltaRaw = getRawArgs(delta.getInput());
        if (baseRaw != null || deltaRaw != null) {
            mergedInput.put(RAW_ARGS_KEY, (baseRaw != null ? baseRaw : "") + (deltaRaw != null ? deltaRaw : ""));
        }
        // 保留真实工具调用ID
        String realId = getToolCallId(base.getInput());
        if (realId == null) {
            realId = getToolCallId(delta.getInput());
        }
        if (realId != null) {
            mergedInput.put(TOOL_CALL_ID_KEY, realId);
        }
        return new AgentToolUseBlock(toolName, toolUseId, mergedInput);
    }

    /**
     * 完成工具调用块的最终处理：解析拼接后的原始参数、恢复真实工具调用ID
     * @param block
     * @return
     */
    @SuppressWarnings("unchecked")
    private AgentToolUseBlock finalizeToolBlock(AgentToolUseBlock block) {
        Map<String, Object> input = new HashMap<>(block.getInput());
        String rawArgs = (String) input.remove(RAW_ARGS_KEY);
        String realId = (String) input.remove(TOOL_CALL_ID_KEY);
        if (rawArgs != null && !rawArgs.isBlank()) {
            try {
                Map<String, Object> parsed = MAPPER.readValue(rawArgs, Map.class);
                input.putAll(parsed);
            } catch (Exception e) {
                // 解析失败保留原始字符串，便于排查
                input.put("_raw", rawArgs);
            }
        }
        String toolUseId = realId != null ? realId : block.getToolUseId();
        return new AgentToolUseBlock(block.getToolName(), toolUseId, input);
    }

    /**
     * 将源Map中的非保留键合并到目标Map
     * @param target
     * @param source
     */
    private void mergeNonReservedKeys(Map<String, Object> target, Map<String, Object> source) {
        if (source == null) {
            return;
        }
        for (Map.Entry<String, Object> entry : source.entrySet()) {
            String key = entry.getKey();
            if (!RAW_ARGS_KEY.equals(key) && !TOOL_CALL_ID_KEY.equals(key)) {
                target.put(key, entry.getValue());
            }
        }
    }

    /**
     * 提取保留键__raw_args__的值
     * @param input
     * @return
     */
    private String getRawArgs(Map<String, Object> input) {
        if (input == null) {
            return null;
        }
        Object val = input.get(RAW_ARGS_KEY);
        return val != null ? val.toString() : null;
    }

    /**
     * 提取保留键__tool_call_id__的值
     * @param input
     * @return
     */
    private String getToolCallId(Map<String, Object> input) {
        if (input == null) {
            return null;
        }
        Object val = input.get(TOOL_CALL_ID_KEY);
        return val != null ? val.toString() : null;
    }

    /**
     * 累加Token用量统计
     * @param usages
     * @return
     */
    public AgentChatUsage mergeUsage(List<AgentChatUsage> usages) {
        if (usages == null || usages.isEmpty()) {
            return new AgentChatUsage(0, 0, 0);
        }
        int promptTokens = 0;
        int completionTokens = 0;
        int totalTokens = 0;
        for (AgentChatUsage usage : usages) {
            if (usage == null) {
                continue;
            }
            promptTokens += usage.getPromptTokens();
            completionTokens += usage.getCompletionTokens();
            totalTokens += usage.getTotalTokens();
        }
        return new AgentChatUsage(promptTokens, completionTokens, totalTokens);
    }

    /**
     * 拼接多个文本块为一个文本块
     * @param textBlocks
     * @return
     */
    public List<AgentContentBlock> mergeTextBlocks(List<AgentTextBlock> textBlocks) {
        if (textBlocks == null || textBlocks.isEmpty()) {
            return List.of();
        }
        if (textBlocks.size() == 1) {
            return new ArrayList<>(List.of(textBlocks.get(0)));
        }
        StringBuilder sb = new StringBuilder();
        for (AgentTextBlock block : textBlocks) {
            if (block.getText() != null) {
                sb.append(block.getText());
            }
        }
        return new ArrayList<>(List.of(AgentTextBlock.builder().text(sb.toString()).build()));
    }
}
