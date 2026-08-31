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
package com.yangqiong.agent.harness.local.workspace;

import java.util.HashMap;
import java.util.Map;

import com.yangqiong.agent.harness.core.event.AgentEvent;
import com.yangqiong.agent.harness.event.AgentEventListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 会话转录自动同步器
 * <p>
 * 作为运行时事件监听器，把助手文本增量聚合为一条条会话记录，
 * 通过{@link SessionTranscriptStore}自动落盘到 sessions/{sessionId}.jsonl；
 * 宿主在发起运行前可显式调用{@link #recordUserInput(String)}记录用户侧输入。
 * 至此会话落盘无需手动调用，实现"运行即落盘"的双向闭环。
 * </p>
 * @author yangqiong
 */
public class TranscriptAutoSynchronizer implements AgentEventListener {

    private static final Logger log = LoggerFactory.getLogger(TranscriptAutoSynchronizer.class);

    /**
     * 单次助手回复最大聚合字符数，兜底防止增量异常堆积
     */
    private static final int MAX_SETTLE_CHARS = 100_000;

    /**
     * 会话转录存储
     */
    private final SessionTranscriptStore transcript;

    /**
     * 目标会话ID
     */
    private final String sessionId;

    /**
     * 当前助手回复增量累积
     */
    private final StringBuilder settled = new StringBuilder();

    /**
     * 当前是否处于助手增量累积期
     */
    private boolean open;

    /**
     * 当前尝试的追踪标识
     */
    private String attemptId;

    /**
     * 构造
     * @param transcript
     * @param sessionId
     */
    public TranscriptAutoSynchronizer(SessionTranscriptStore transcript, String sessionId) {
        if (transcript == null) {
            throw new IllegalArgumentException("转录存储不能为空");
        }
        if (sessionId == null || sessionId.isBlank()) {
            throw new IllegalArgumentException("会话ID不能为空");
        }
        this.transcript = transcript;
        this.sessionId = sessionId;
    }

    /**
     * 记录用户输入（宿主在发起运行前显式调用）
     * @param text
     */
    public void recordUserInput(String text) {
        if (text == null || text.isBlank()) {
            return;
        }
        Map<String, Object> record = new HashMap<>();
        record.put("role", "user");
        record.put("type", "text");
        record.put("content", text);
        record.put("ts", System.currentTimeMillis());
        transcript.append(sessionId, record);
    }

    /**
     * 处理运行时事件：聚合助手文本增量，在回合结束/开始边界落盘一条助手记录
     * @param event
     */
    @Override
    public void onEvent(AgentEvent event) {
        switch (event.getType()) {
            case TEXT_BLOCK_DELTA:
                holdDelta(event);
                break;
            case AGENT_START:
                flush();
                open = true;
                attemptId = traceOf(event);
                break;
            case AGENT_RESULT:
            case COMPLETED:
                open = false;
                flush();
                break;
            default:
                // 其余类型不落盘
        }
    }

    /**
     * 收拢文本增量，达到上限或新回合边界时提前落盘
     * @param event
     */
    private void holdDelta(AgentEvent event) {
        Object payload = event.getPayload();
        if (payload == null) {
            return;
        }
        if (!open) {
            open = true;
            attemptId = traceOf(event);
        }
        settled.append(payload);
        if (settled.length() >= MAX_SETTLE_CHARS) {
            flush();
        }
    }

    /**
     * 落盘当前累积的助手记录并清空缓冲
     */
    private void flush() {
        if (settled.length() == 0) {
            return;
        }
        Map<String, Object> record = new HashMap<>();
        record.put("role", "assistant");
        record.put("type", "text");
        record.put("content", settled.toString());
        record.put("ts", System.currentTimeMillis());
        if (attemptId != null) {
            record.put("attemptId", attemptId);
        }
        transcript.append(sessionId, record);
        settled.setLength(0);
        attemptId = null;
        open = false;
    }

    /**
     * 从事件提取可观测性追踪标识作为单次回复的尝试编号，无则返回null
     * @param event
     * @return
     */
    private static String traceOf(AgentEvent event) {
        if (event.getSpanId() != null && !event.getSpanId().isBlank()) {
            return event.getSpanId();
        }
        return event.getTraceId();
    }
}