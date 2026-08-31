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
package com.yangqiong.agent.harness.core.memory;

/**
 * 会话运行摘要快照
 * @author yangqiong
 */
public final class SessionSummary {

    /**
     * 摘要文本
     */
    private final String summary;

    /**
     * 已摘要到的消息序号（不含，即下次增量摘要从此序号开始）
     */
    private final int summarizedMessageCount;

    public SessionSummary(String summary, int summarizedMessageCount) {
        this.summary = summary;
        this.summarizedMessageCount = summarizedMessageCount;
    }

    /**
     * 获取摘要文本
     * @return
     */
    public String getSummary() {
        return summary;
    }

    /**
     * 获取已摘要到的消息序号
     * @return
     */
    public int getSummarizedMessageCount() {
        return summarizedMessageCount;
    }
}
