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
package com.yangqiongai.agent.harness.starter.trace;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.yangqiongai.agent.harness.core.trace.SpanInfo;
import com.yangqiongai.agent.harness.core.trace.TraceEmitter;

/**
 * 日志追踪导出器
 * <p>
 * 将结束的Span以日志形式输出，便于无观测后端时的快速排查。
 * </p>
 * @author yangqiong
 */
public class LoggingTraceEmitter implements TraceEmitter {

    /**
     * 日志器
     */
    private static final Logger log = LoggerFactory.getLogger(LoggingTraceEmitter.class);

    @Override
    public void onSpan(SpanInfo spanInfo) {
        log.info("Agent追踪 span: {}", spanInfo);
    }
}
