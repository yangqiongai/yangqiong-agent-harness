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
package com.yangqiongai.agent.harness.core.event;

/**
 * 需要用户澄清事件
 * <p>
 * 模型在任务中途发现自己缺少关键信息时，主动通过 ask_user 工具向用户提问，
 * 引擎暂停循环并发出此事件，外部应用注入答案后通过 resumeWithClarification 续接。
 * </p>
 * @author yangqiong
 */
public class RequireUserClarificationEvent extends AgentEvent {

    /**
     * 问题描述
     */
    private final String question;

    /**
     * 工具调用ID
     */
    private final String toolCallId;

    /**
     * 候选选项列表（可空，前端渲染为可点击选择按钮）
     */
    private final java.util.List<String> options;

    public RequireUserClarificationEvent(String question, String toolCallId) {
        this(question, toolCallId, null);
    }

    public RequireUserClarificationEvent(String question, String toolCallId, java.util.List<String> options) {
        super(AgentEventType.REQUIRE_USER_CLARIFICATION, question);
        this.question = question;
        this.toolCallId = toolCallId;
        this.options = options;
    }

    /**
     * 获取问题描述
     * @return
     */
    public String getQuestion() {
        return question;
    }

    /**
     * 获取工具调用ID
     * @return
     */
    public String getToolCallId() {
        return toolCallId;
    }

    /**
     * 获取候选选项列表
     * @return
     */
    public java.util.List<String> getOptions() {
        return options;
    }
}