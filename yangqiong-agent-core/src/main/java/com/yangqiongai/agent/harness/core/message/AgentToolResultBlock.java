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
package com.yangqiongai.agent.harness.core.message;

import java.util.List;
import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Agent工具结果块
 * @author yangqiong
 */
public final class AgentToolResultBlock implements AgentContentBlock {

    /**
     * 工具调用ID
     */
    private final String toolUseId;

    /**
     * 结果内容块列表
     */
    private final List<AgentContentBlock> content;

    /**
     * 是否为错误结果
     */
    private final boolean error;

    /**
     * 是否为澄清请求（ask_user 工具的专用标记，不走普通结果路径）
     */
    private final boolean clarificationRequest;

    private AgentToolResultBlock(String toolUseId, List<AgentContentBlock> content, boolean error) {
        this(toolUseId, content, error, false);
    }

    @JsonCreator
    private AgentToolResultBlock(@JsonProperty("toolUseId") String toolUseId,
                                 @JsonProperty("content") List<AgentContentBlock> content,
                                 @JsonProperty("error") boolean error,
                                 @JsonProperty("clarificationRequest") boolean clarificationRequest) {
        this.toolUseId = toolUseId;
        this.content = content != null ? List.copyOf(content) : List.of();
        this.error = error;
        this.clarificationRequest = clarificationRequest;
    }

    /**
     * 创建正常工具结果
     * @param content
     * @return
     */
    public static AgentToolResultBlock of(List<AgentContentBlock> content) {
        return new AgentToolResultBlock(null, content, false);
    }

    /**
     * 创建带工具调用ID的正常结果
     * @param toolUseId
     * @param content
     * @return
     */
    public static AgentToolResultBlock of(String toolUseId, List<AgentContentBlock> content) {
        return new AgentToolResultBlock(toolUseId, content, false);
    }

    /**
     * 创建错误工具结果
     * @param errorMessage
     * @return
     */
    public static AgentToolResultBlock error(String errorMessage) {
        AgentTextBlock textBlock = AgentTextBlock.builder().text(errorMessage).build();
        return new AgentToolResultBlock(null, List.of(textBlock), true);
    }

    /**
     * 创建带工具调用ID的错误结果
     * @param toolUseId
     * @param errorMessage
     * @return
     */
    public static AgentToolResultBlock error(String toolUseId, String errorMessage) {
        AgentTextBlock textBlock = AgentTextBlock.builder().text(errorMessage).build();
        return new AgentToolResultBlock(toolUseId, List.of(textBlock), true);
    }

    /**
     * 创建澄清请求结果（ask_user 工具专用，带澄清标记，不走普通结果路径）
     * @param question
     * @return
     */
    public static AgentToolResultBlock clarification(String question) {
        AgentTextBlock textBlock = AgentTextBlock.builder().text(question).build();
        return new AgentToolResultBlock(null, List.of(textBlock), false, true);
    }

    /**
     * 返回带指定工具调用ID的副本，用于补全缺失的toolUseId
     * @param toolUseId
     * @return
     */
    public AgentToolResultBlock withToolUseId(String toolUseId) {
        if (Objects.equals(this.toolUseId, toolUseId)) {
            return this;
        }
        return new AgentToolResultBlock(toolUseId, this.content, this.error, this.clarificationRequest);
    }

    /**
     * 获取工具调用ID
     * @return
     */
    public String getToolUseId() {
        return toolUseId;
    }

    /**
     * 获取结果内容
     * @return
     */
    public List<AgentContentBlock> getContent() {
        return content;
    }

    /**
     * 是否为错误结果
     * @return
     */
    public boolean isError() {
        return error;
    }

    /**
     * 是否为澄清请求
     * @return
     */
    public boolean isClarificationRequest() {
        return clarificationRequest;
    }

    /**
     * 获取结果文本内容
     * @return
     */
    @JsonIgnore
    public String getTextContent() {
        if (content == null || content.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (AgentContentBlock block : content) {
            if (block instanceof AgentTextBlock textBlock) {
                sb.append(textBlock.getText());
            }
        }
        return sb.toString();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        AgentToolResultBlock that = (AgentToolResultBlock) o;
        return error == that.error
                && clarificationRequest == that.clarificationRequest
                && Objects.equals(toolUseId, that.toolUseId)
                && Objects.equals(content, that.content);
    }

    @Override
    public int hashCode() {
        return Objects.hash(toolUseId, content, error, clarificationRequest);
    }

    @Override
    public String toString() {
        return "AgentToolResultBlock{toolUseId='" + toolUseId + "', error=" + error
                + ", clarification=" + clarificationRequest + "}";
    }
}
