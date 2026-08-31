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
package com.yangqiong.agent.harness.guardrail;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Function;

import com.yangqiong.agent.harness.core.message.AgentMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 默认内容审查策略
 * <p>
 * 在{@link NoopContentModerationPolicy}放行基础上，提供三层审查：
 * <ol>
 *   <li>护栏规则层：委托{@link GuardrailRuleRegistry}做关键词/正则模式匹配，命中BLOCK则拦截</li>
 *   <li>启发式规则层：内置超长内容、异常字符比例等启发式检测</li>
 *   <li>外部审查层：可配置外部内容审查API（如OpenAI Moderation API），支持自定义审查函数</li>
 * </ol>
 * 三层按顺序执行，任一层命中BLOCK即中止，命中MASK则继续后续层。
 * 未配置任何规则/检测器时等效于NoopModerationPolicy（放行全部）。
 * </p>
 * @author yangqiong
 */
public class DefaultContentModerationPolicy implements ContentModerationPolicy {

    private static final Logger log = LoggerFactory.getLogger(DefaultContentModerationPolicy.class);

    /**
     * 默认最大消息长度（字符数），超过此值时触发BLOCK
     */
    private static final int DEFAULT_MAX_MESSAGE_LENGTH = 100000;

    /**
     * 默认外部审查API超时
     */
    private static final int DEFAULT_API_TIMEOUT_SECONDS = 15;

    /**
     * 护栏规则注册中心（用于关键词/正则模式匹配）
     */
    private final GuardrailRuleRegistry registry;

    /**
     * 最大消息长度阈值
     */
    private final int maxMessageLength;

    /**
     * 外部审查API端点（可选，如OpenAI Moderation API）
     */
    private final String externalApiUrl;

    /**
     * 外部审查API认证令牌
     */
    private final String externalApiToken;

    /**
     * 自定义审查函数列表（用户可追加自定义审查逻辑）
     */
    private final List<Function<AgentMessage, Optional<ModerationVerdict>>> customCheckers;

    /**
     * HTTP客户端（用于外部API调用）
     */
    private final HttpClient httpClient;

    /**
     * 审查统计计数器
     */
    private int totalModerated = 0;
    private int totalBlocked = 0;
    private int totalMasked = 0;

    DefaultContentModerationPolicy(GuardrailRuleRegistry registry, int maxMessageLength,
                                   String externalApiUrl, String externalApiToken,
                                   List<Function<AgentMessage, Optional<ModerationVerdict>>> customCheckers) {
        this.registry = registry != null ? registry : new GuardrailRuleRegistry();
        this.maxMessageLength = maxMessageLength > 0 ? maxMessageLength : DEFAULT_MAX_MESSAGE_LENGTH;
        this.externalApiUrl = externalApiUrl;
        this.externalApiToken = externalApiToken;
        this.customCheckers = customCheckers != null
                ? new CopyOnWriteArrayList<>(customCheckers) : new CopyOnWriteArrayList<>();
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(DEFAULT_API_TIMEOUT_SECONDS))
                .build();
    }

    /**
     * 创建默认配置的内容审查策略（护栏规则注册中心，无外部API）
     * @return
     */
    public static DefaultContentModerationPolicy create() {
        return new Builder().build();
    }

    /**
     * 审查消息内容
     * @param message 待审查消息
     * @return 审查裁决，默认返回PASS
     */
    @Override
    public ModerationVerdict moderate(AgentMessage message) {
        if (message == null) {
            return ModerationVerdict.pass();
        }
        totalModerated++;
        String text = message.getTextContent();
        if (text == null || text.isEmpty()) {
            return ModerationVerdict.pass();
        }

        // 第一层：护栏规则层（关键词/正则模式匹配）
        ModerationVerdict ruleVerdict = checkRules(text);
        if (ruleVerdict.isBlock()) {
            totalBlocked++;
            return ruleVerdict;
        }

        // 第二层：启发式规则层
        ModerationVerdict heuristicVerdict = checkHeuristics(text);
        if (heuristicVerdict.isBlock()) {
            totalBlocked++;
            return heuristicVerdict;
        }

        // 第三层：外部审查API层
        if (externalApiUrl != null && !externalApiUrl.isBlank()) {
            ModerationVerdict apiVerdict = checkExternalApi(text, message);
            if (apiVerdict.isBlock()) {
                totalBlocked++;
                return apiVerdict;
            }
            if (apiVerdict.isMask()) {
                totalMasked++;
                return apiVerdict;
            }
        }

        // 第四层：自定义审查函数层
        for (Function<AgentMessage, Optional<ModerationVerdict>> checker : customCheckers) {
            try {
                Optional<ModerationVerdict> verdict = checker.apply(message);
                if (verdict.isPresent() && verdict.get().isBlock()) {
                    totalBlocked++;
                    return verdict.get();
                }
            } catch (Exception e) {
                log.warn("[DefaultContentModeration] 自定义审查函数执行失败: {}", checker.getClass().getSimpleName(), e);
            }
        }

        return ModerationVerdict.pass();
    }

    /**
     * 获取审查统计
     * @return
     */
    public ModerationStats getStats() {
        return new ModerationStats(totalModerated, totalBlocked, totalMasked);
    }

    /**
     * 护栏规则层检测：委托GuardrailRuleRegistry
     * @param text
     * @return
     */
    private ModerationVerdict checkRules(String text) {
        Optional<GuardrailViolation> violation = registry.detect(text, GuardrailTarget.INPUT);
        if (violation.isPresent()) {
            GuardrailRule.GuardrailAction action = violation.get().getRule().getAction();
            if (action == GuardrailRule.GuardrailAction.BLOCK) {
                log.warn("[DefaultContentModeration] 护栏规则拦截: {}", violation.get().describe());
                return ModerationVerdict.block("护栏规则命中: " + violation.get().getRule().getName());
            }
            if (action == GuardrailRule.GuardrailAction.MASK) {
                log.warn("[DefaultContentModeration] 护栏规则脱敏: {}", violation.get().describe());
                return ModerationVerdict.mask("护栏规则脱敏: " + violation.get().getRule().getName());
            }
        }
        return ModerationVerdict.pass();
    }

    /**
     * 启发式规则层检测
     * @param text
     * @return
     */
    private ModerationVerdict checkHeuristics(String text) {
        // 超长内容检查
        if (text.length() > maxMessageLength) {
            log.warn("[DefaultContentModeration] 超长内容拦截: length={}, max={}",
                    text.length(), maxMessageLength);
            return ModerationVerdict.block("消息内容超过最大长度限制: "
                    + text.length() + "/" + maxMessageLength);
        }

        // 异常字符比例检查（非字母数字空白比例过高，可能是垃圾内容）
        int nonStandard = 0;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (!Character.isLetterOrDigit(c) && !Character.isWhitespace(c)
                    && c != '.' && c != ',' && c != '!' && c != '?'
                    && c != ';' && c != ':' && c != '-' && c != '_'
                    && c != '(' && c != ')' && c != '[' && c != ']'
                    && c != '{' && c != '}' && c != '\"' && c != '\''
                    && c != '/' && c != '@' && c != '#' && c != '$'
                    && c != '%' && c != '&' && c != '*' && c != '+'
                    && c != '=' && c != '<' && c != '>' && c != '~'
                    && c != '`' && c != '|' && c != '\\') {
                nonStandard++;
            }
        }
        double ratio = (double) nonStandard / text.length();
        if (ratio > 0.5 && text.length() > 50) {
            log.warn("[DefaultContentModeration] 异常字符比例过高拦截: ratio={}, length={}",
                    String.format("%.2f", ratio), text.length());
            return ModerationVerdict.block("内容包含异常字符比例过高: "
                    + String.format("%.1f%%", ratio * 100));
        }

        return ModerationVerdict.pass();
    }

    /**
     * 外部审查API层检测
     * @param text
     * @param message
     * @return
     */
    private ModerationVerdict checkExternalApi(String text, AgentMessage message) {
        try {
            // 构造请求体：兼容OpenAI Moderation API格式
            String requestBody = "{\"input\":\"" + escapeJson(text) + "\"}";

            HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                    .uri(URI.create(externalApiUrl))
                    .header("Content-Type", "application/json")
                    .timeout(Duration.ofSeconds(DEFAULT_API_TIMEOUT_SECONDS));

            if (externalApiToken != null && !externalApiToken.isBlank()) {
                requestBuilder.header("Authorization", "Bearer " + externalApiToken);
            }

            HttpRequest request = requestBuilder.POST(
                    HttpRequest.BodyPublishers.ofString(requestBody, StandardCharsets.UTF_8))
                    .build();

            HttpResponse<String> response = httpClient.send(request,
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

            if (response.statusCode() == 200) {
                return parseModerationResponse(response.body());
            } else {
                log.warn("[DefaultContentModeration] 外部审查API返回非200: status={}", response.statusCode());
            }
        } catch (Exception e) {
            log.warn("[DefaultContentModeration] 外部审查API调用失败: {}", e.getMessage());
        }
        return ModerationVerdict.pass();
    }

    /**
     * 解析外部审查API响应（兼容OpenAI Moderation API格式）
     * @param responseBody
     * @return
     */
    private ModerationVerdict parseModerationResponse(String responseBody) {
        // 简单解析：检查flagged字段
        if (responseBody.contains("\"flagged\":true") || responseBody.contains("\"flagged\": true")) {
            // 提取类别信息
            String category = extractCategory(responseBody);
            return ModerationVerdict.block("外部审查API拦截: " + category);
        }
        return ModerationVerdict.pass();
    }

    /**
     * 从审核响应中提取违规类别
     * @param responseBody
     * @return
     */
    private String extractCategory(String responseBody) {
        // 尝试提取第一个为true的类别
        String[] categories = {"sexual", "hate", "harassment", "self-harm",
                "violence", "sexual/minors", "hate/threatening",
                "violence/graphic", "self-harm/intent", "self-harm/instructions",
                "harassment/threatening", "illicit", "illicit/violent"};
        for (String cat : categories) {
            String escaped = cat.replace("/", "\\/");
            if (responseBody.contains("\"" + escaped + "\":true")
                    || responseBody.contains("\"" + escaped + "\": true")) {
                return cat;
            }
        }
        return "unknown";
    }

    /**
     * 转义JSON字符串（防止注入）
     * @param text
     * @return
     */
    private String escapeJson(String text) {
        return text.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    /**
     * 审查统计
     * @author yangqiong
     */
    public static final class ModerationStats {

        private final int totalModerated;
        private final int totalBlocked;
        private final int totalMasked;

        ModerationStats(int totalModerated, int totalBlocked, int totalMasked) {
            this.totalModerated = totalModerated;
            this.totalBlocked = totalBlocked;
            this.totalMasked = totalMasked;
        }

        public int getTotalModerated() {
            return totalModerated;
        }

        public int getTotalBlocked() {
            return totalBlocked;
        }

        public int getTotalMasked() {
            return totalMasked;
        }
    }

    /**
     * 构建器
     * @author yangqiong
     */
    public static class Builder {

        private GuardrailRuleRegistry registry;
        private int maxMessageLength = DEFAULT_MAX_MESSAGE_LENGTH;
        private String externalApiUrl;
        private String externalApiToken;
        private final List<Function<AgentMessage, Optional<ModerationVerdict>>> customCheckers
                = new CopyOnWriteArrayList<>();

        /**
         * 设置护栏规则注册中心
         * @param registry
         * @return
         */
        public Builder registry(GuardrailRuleRegistry registry) {
            this.registry = registry;
            return this;
        }

        /**
         * 设置最大消息长度（字符数），超长触发BLOCK
         * @param maxMessageLength
         * @return
         */
        public Builder maxMessageLength(int maxMessageLength) {
            this.maxMessageLength = maxMessageLength;
            return this;
        }

        /**
         * 设置外部审查API端点（如OpenAI Moderation API: https://api.openai.com/v1/moderations）
         * @param apiUrl
         * @param apiToken
         * @return
         */
        public Builder externalApi(String apiUrl, String apiToken) {
            this.externalApiUrl = apiUrl;
            this.externalApiToken = apiToken;
            return this;
        }

        /**
         * 添加自定义审查函数
         * @param checker
         * @return
         */
        public Builder addCustomChecker(Function<AgentMessage, Optional<ModerationVerdict>> checker) {
            if (checker != null) {
                this.customCheckers.add(checker);
            }
            return this;
        }

        /**
         * 构建默认内容审查策略
         * @return
         */
        public DefaultContentModerationPolicy build() {
            return new DefaultContentModerationPolicy(
                    registry, maxMessageLength,
                    externalApiUrl, externalApiToken,
                    customCheckers);
        }
    }
}