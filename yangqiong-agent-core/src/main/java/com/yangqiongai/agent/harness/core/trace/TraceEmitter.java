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
package com.yangqiongai.agent.harness.core.trace;

/**
 * 追踪导出SPI
 * <p>
 * 引擎侧只定义接口与默认采集逻辑，具体导出（OTel SDK/日志/内存缓冲等）由平台侧实现并反向注入。
 * 未注入TraceEmitter时TraceMiddleware不加入链，静默降级不采集。
 * </p>
 * @author yangqiong
 */
public interface TraceEmitter {

    /**
     * 导出一个已结束的Span
     * @param spanInfo
     */
    void onSpan(SpanInfo spanInfo);
}
