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
package com.yangqiong.agent.harness.observability.trace;

import java.io.IOException;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.yangqiong.agent.harness.core.trace.SpanInfo;

/**
 * OTLP Span批量缓冲导出器
 * <p>
 * 缓冲 TraceMiddleware 回调导出的 SpanInfo，按批量大小与定时策略编码为 OTLP/JSON
 * 并 POST 到 Collector 的 /v1/traces。导出失败仅记告警并丢弃该批，不影响 Agent 主流程；
 * flush 与 close 均保证不向外抛出异常。
 * </p>
 * @author yangqiong
 */
public final class OtlpBatchBuffer implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(OtlpBatchBuffer.class);

    /**
     * OTLP Traces 导出路径
     */
    private static final String TRACES_PATH = "/v1/traces";

    /**
     * JSON报文Content-Type
     */
    private static final String CONTENT_TYPE = "application/json";

    /**
     * 已计时Span：core仅在Span结束时回调且只有耗时，结束墙钟由入队时刻回推补齐
     */
    public record TimedSpan(SpanInfo span, long endEpochMs) {
    }

    /**
     * HTTP传输通道抽象，供测试注入替代真实网络
     */
    @FunctionalInterface
    public interface HttpTransport {

        /**
         * POST报文到目标URL
         * @param url
         * @param contentType
         * @param body
         * @param timeoutMs
         * @return 是否成功（2xx）
         * @throws IOException
         */
        boolean post(String url, String contentType, byte[] body, int timeoutMs) throws IOException;
    }

    /**
     * OTLP端点
     */
    private final String tracesUrl;

    /**
     * 服务名
     */
    private final String serviceName;

    /**
     * 单批最大Span数
     */
    private final int batchSize;

    /**
     * 导出超时毫秒
     */
    private final int timeoutMs;

    /**
     * Span缓冲队列，容量即缓冲上限
     */
    private final ArrayBlockingQueue<TimedSpan> queue;

    /**
     * HTTP传输通道
     */
    private final HttpTransport transport;

    /**
     * 定时刷新调度器，flushIntervalMs为0时不创建
     */
    private final ScheduledExecutorService scheduler;

    /**
     * 关闭标记，保证close幂等
     */
    private final AtomicBoolean closed = new AtomicBoolean(false);

    /**
     * 缓冲溢出丢弃计数
     */
    private final AtomicLong droppedSpans = new AtomicLong();

    /**
     * 构造缓冲导出器
     * @param endpoint OTLP端点，自动补全/v1/traces路径
     * @param serviceName 服务名
     * @param batchSize 单批最大Span数
     * @param flushIntervalMs 定时刷新间隔毫秒，0表示仅手动或关闭时刷新
     * @param timeoutMs 导出超时毫秒
     * @param maxBufferedSpans 缓冲上限，超限丢弃最旧
     * @param transport HTTP传输通道，null时使用JDK HttpURLConnection实现
     */
    public OtlpBatchBuffer(String endpoint, String serviceName, int batchSize, long flushIntervalMs,
                           int timeoutMs, int maxBufferedSpans, HttpTransport transport) {
        this.tracesUrl = normalizeEndpoint(endpoint);
        this.serviceName = serviceName;
        this.batchSize = Math.max(1, batchSize);
        this.timeoutMs = Math.max(100, timeoutMs);
        this.queue = new ArrayBlockingQueue<>(Math.max(1, maxBufferedSpans));
        this.transport = transport != null ? transport : defaultTransport();
        if (flushIntervalMs > 0) {
            this.scheduler = Executors.newSingleThreadScheduledExecutor(runnable -> {
                Thread thread = new Thread(runnable, "agent-otlp-flusher");
                thread.setDaemon(true);
                return thread;
            });
            this.scheduler.scheduleWithFixedDelay(this::safeFlush, flushIntervalMs, flushIntervalMs,
                    TimeUnit.MILLISECONDS);
        } else {
            this.scheduler = null;
        }
    }

    /**
     * 构造使用默认HTTP传输的缓冲导出器
     * @param endpoint
     * @param serviceName
     * @param batchSize
     * @param flushIntervalMs
     * @param timeoutMs
     * @param maxBufferedSpans
     */
    public OtlpBatchBuffer(String endpoint, String serviceName, int batchSize, long flushIntervalMs,
                           int timeoutMs, int maxBufferedSpans) {
        this(endpoint, serviceName, batchSize, flushIntervalMs, timeoutMs, maxBufferedSpans, null);
    }

    /**
     * 入队一个已结束Span，缓冲满时淘汰最旧并计数
     * @param spanInfo
     */
    public void offer(SpanInfo spanInfo) {
        if (spanInfo == null || closed.get()) {
            return;
        }
        TimedSpan timed = new TimedSpan(spanInfo, System.currentTimeMillis());
        while (!queue.offer(timed)) {
            TimedSpan evicted = queue.poll();
            if (evicted == null) {
                continue;
            }
            droppedSpans.incrementAndGet();
            if (droppedSpans.get() % 100 == 1) {
                log.warn("[OtlpBatchBuffer] Span缓冲溢出，累计丢弃 {} 个Span", droppedSpans.get());
            }
        }
    }

    /**
     * 立即清空缓冲并分批导出，任何异常内部消化不外抛
     */
    public synchronized void flush() {
        if (closed.get()) {
            return;
        }
        while (!queue.isEmpty()) {
            List<TimedSpan> batch = drainBatch();
            if (batch.isEmpty()) {
                return;
            }
            exportBatch(batch);
        }
    }

    /**
     * 关闭缓冲器：停掉定时任务并做最后一次导出，幂等
     */
    @Override
    public void close() {
        if (!closed.compareAndSet(false, true)) {
            return;
        }
        if (scheduler != null) {
            scheduler.shutdownNow();
        }
        flushInternalAfterClose();
    }

    /**
     * 当前待导出Span数
     * @return
     */
    public int pendingCount() {
        return queue.size();
    }

    /**
     * 累计丢弃Span数
     * @return
     */
    public long droppedCount() {
        return droppedSpans.get();
    }

    /**
     * 关闭后的收尾导出（close幂等语义下允许越过closed标记执行）
     */
    private synchronized void flushInternalAfterClose() {
        while (!queue.isEmpty()) {
            List<TimedSpan> batch = drainBatch();
            if (batch.isEmpty()) {
                return;
            }
            exportBatch(batch);
        }
    }

    /**
     * 定时任务入口：捕获一切异常防止调度任务被终止
     */
    private void safeFlush() {
        try {
            flush();
        } catch (Exception e) {
            log.warn("[OtlpBatchBuffer] 定时导出异常: {}", e.getMessage());
        }
    }

    /**
     * 取出最多一批待导出Span
     * @return
     */
    private List<TimedSpan> drainBatch() {
        List<TimedSpan> batch = new ArrayList<>(batchSize);
        queue.drainTo(batch, batchSize);
        return batch;
    }

    /**
     * 编码并导出一批Span，失败告警丢弃
     * @param batch
     */
    private void exportBatch(List<TimedSpan> batch) {
        try {
            String payload = OtlpJsonEncoder.encode(batch, serviceName);
            boolean success = transport.post(tracesUrl, CONTENT_TYPE,
                    payload.getBytes(StandardCharsets.UTF_8), timeoutMs);
            if (!success) {
                log.warn("[OtlpBatchBuffer] OTLP导出非2xx响应，丢弃 {} 个Span", batch.size());
            }
        } catch (Exception e) {
            log.warn("[OtlpBatchBuffer] OTLP导出失败，丢弃 {} 个Span: {}", batch.size(), e.getMessage());
        }
    }

    /**
     * 补全端点的/v1/traces路径
     * @param endpoint
     * @return
     */
    private static String normalizeEndpoint(String endpoint) {
        String base = endpoint.endsWith("/") ? endpoint.substring(0, endpoint.length() - 1) : endpoint;
        return base.endsWith(TRACES_PATH) ? base : base + TRACES_PATH;
    }

    /**
     * 默认JDK HttpURLConnection传输实现
     * @return
     */
    private static HttpTransport defaultTransport() {
        return (url, contentType, body, timeoutMs) -> {
            HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
            connection.setRequestMethod("POST");
            connection.setConnectTimeout(timeoutMs);
            connection.setReadTimeout(timeoutMs);
            connection.setDoOutput(true);
            connection.setRequestProperty("Content-Type", contentType);
            try (OutputStream out = connection.getOutputStream()) {
                out.write(body);
            }
            int code = connection.getResponseCode();
            return code >= 200 && code < 300;
        };
    }
}
