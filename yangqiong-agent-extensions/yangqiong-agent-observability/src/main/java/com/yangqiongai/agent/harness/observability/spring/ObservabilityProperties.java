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
package com.yangqiongai.agent.harness.observability.spring;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 可观测性扩展配置属性
 * <p>
 * 绑定前缀 yangqiong.agent.observability，由 ObservabilityAutoConfiguration 消费。
 * </p>
 * @author yangqiong
 */
@ConfigurationProperties(prefix = "yangqiong.agent.observability")
public class ObservabilityProperties {

    /**
     * 总开关，true 时激活自动装配
     */
    private boolean enabled;

    /**
     * OTLP Trace 导出端点（如 http://collector:4318），空时关闭 Trace 导出
     */
    private String otlpEndpoint;

    /**
     * 上报服务名，写入 OTLP Resource 属性 service.name
     */
    private String serviceName = "yangqiong-agent-harness";

    /**
     * 单次批量导出的最大 Span 数
     */
    private int traceBatchSize = 64;

    /**
     * 定时刷新间隔毫秒
     */
    private long traceFlushIntervalMs = 5000L;

    /**
     * 导出 HTTP 请求超时毫秒
     */
    private int traceExportTimeoutMs = 5000;

    /**
     * 缓冲区最大 Span 数
     */
    private int traceMaxBufferedSpans = 2048;

    /**
     * 指标采集配置
     */
    private final Metrics metrics = new Metrics();

    /**
     * 跨进程审批等待监控配置
     */
    private final ApprovalWait approvalWait = new ApprovalWait();

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getOtlpEndpoint() {
        return otlpEndpoint;
    }

    public void setOtlpEndpoint(String otlpEndpoint) {
        this.otlpEndpoint = otlpEndpoint;
    }

    public String getServiceName() {
        return serviceName;
    }

    public void setServiceName(String serviceName) {
        this.serviceName = serviceName;
    }

    public int getTraceBatchSize() {
        return traceBatchSize;
    }

    public void setTraceBatchSize(int traceBatchSize) {
        this.traceBatchSize = traceBatchSize;
    }

    public long getTraceFlushIntervalMs() {
        return traceFlushIntervalMs;
    }

    public void setTraceFlushIntervalMs(long traceFlushIntervalMs) {
        this.traceFlushIntervalMs = traceFlushIntervalMs;
    }

    public int getTraceExportTimeoutMs() {
        return traceExportTimeoutMs;
    }

    public void setTraceExportTimeoutMs(int traceExportTimeoutMs) {
        this.traceExportTimeoutMs = traceExportTimeoutMs;
    }

    public int getTraceMaxBufferedSpans() {
        return traceMaxBufferedSpans;
    }

    public void setTraceMaxBufferedSpans(int traceMaxBufferedSpans) {
        this.traceMaxBufferedSpans = traceMaxBufferedSpans;
    }

    public Metrics getMetrics() {
        return metrics;
    }

    public ApprovalWait getApprovalWait() {
        return approvalWait;
    }

    /**
     * 指标采集配置
     * @author yangqiong
     */
    public static class Metrics {

        /**
         * 指标采集开关
         */
        private boolean enabled = true;

        /**
         * 公共标签（如 env=prod），附加到全部指标
         */
        private Map<String, String> commonTags = new LinkedHashMap<>();

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public Map<String, String> getCommonTags() {
            return commonTags;
        }

        public void setCommonTags(Map<String, String> commonTags) {
            this.commonTags = commonTags;
        }
    }

    /**
     * 跨进程审批等待监控配置
     * @author yangqiong
     */
    public static class ApprovalWait {

        /**
         * 审批等待监控开关
         */
        private boolean enabled = true;

        /**
         * 监控的租户范围列表
         */
        private List<String> scopes = List.of("default");

        /**
         * 扫描刷新间隔毫秒
         */
        private long refreshIntervalMs = 30000L;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public List<String> getScopes() {
            return scopes;
        }

        public void setScopes(List<String> scopes) {
            this.scopes = scopes;
        }

        public long getRefreshIntervalMs() {
            return refreshIntervalMs;
        }

        public void setRefreshIntervalMs(long refreshIntervalMs) {
            this.refreshIntervalMs = refreshIntervalMs;
        }
    }
}
