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
package com.yangqiongai.agent.harness.web;

import com.yangqiongai.agent.harness.core.message.AgentTextBlock;
import com.yangqiongai.agent.harness.core.message.AgentToolResultBlock;
import com.yangqiongai.agent.harness.core.tool.AgentTool;
import com.yangqiongai.agent.harness.core.tool.AgentToolCallParam;
import com.yangqiongai.agent.harness.guardrail.GuardrailFence;
import reactor.core.publisher.Mono;

import java.net.InetAddress;
import java.net.URI;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 网页抓取工具
 * <p>
 * 实现AgentTool接口，供LLM通过web_fetch工具抓取网页内容。
 * 仅允许http/https协议，默认拒绝环回/内网/链路本地地址（SSRF防护），
 * 结果加GuardrailFence围栏。未配置WebFetcher时返回错误提示。
 * </p>
 * @author yangqiong
 */
public class WebFetchTool implements AgentTool {

    private static final String TOOL_NAME = "web_fetch";

    private static final int DEFAULT_MAX_CHARS = 8000;

    private final WebFetcher fetcher;

    /**
     * 是否启用SSRF防护（拒绝环回/内网/链路本地目标）
     */
    private final boolean ssrfGuardEnabled;

    /**
     * 构造
     * @param fetcher 网页抓取提供方，null表示未配置
     */
    public WebFetchTool(WebFetcher fetcher) {
        this(fetcher, true);
    }

    /**
     * 构造
     * @param fetcher 网页抓取提供方，null表示未配置
     * @param ssrfGuardEnabled 是否启用SSRF防护，仅限明确需要访问内网的本地部署关闭
     */
    public WebFetchTool(WebFetcher fetcher, boolean ssrfGuardEnabled) {
        this.fetcher = fetcher;
        this.ssrfGuardEnabled = ssrfGuardEnabled;
    }

    /**
     * 获取工具名称
     * @return
     */
    @Override
    public String getName() {
        return TOOL_NAME;
    }

    /**
     * 获取工具描述
     * @return
     */
    @Override
    public String getDescription() {
        return "抓取指定URL的网页内容。仅支持http和https协议，结果带围栏标注。";
    }

    /**
     * 获取工具参数定义
     * @return
     */
    @Override
    public Map<String, Object> getParameters() {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");

        Map<String, Object> properties = new LinkedHashMap<>();

        Map<String, Object> urlProp = new LinkedHashMap<>();
        urlProp.put("type", "string");
        urlProp.put("description", "目标网页URL（仅支持http/https协议）");
        properties.put("url", urlProp);

        Map<String, Object> maxCharsProp = new LinkedHashMap<>();
        maxCharsProp.put("type", "integer");
        maxCharsProp.put("default", DEFAULT_MAX_CHARS);
        maxCharsProp.put("description", "最大返回字符数");
        properties.put("maxChars", maxCharsProp);

        schema.put("properties", properties);
        schema.put("required", List.of("url"));
        return schema;
    }

    /**
     * 执行抓取
     * @param param
     * @return
     */
    @Override
    public Mono<AgentToolResultBlock> callAsync(AgentToolCallParam param) {
        if (fetcher == null) {
            return Mono.just(AgentToolResultBlock.error("未配置WebFetcher，网页抓取不可用"));
        }

        Map<String, Object> input = param != null ? param.getInput() : null;
        if (input == null || input.isEmpty()) {
            return Mono.just(AgentToolResultBlock.error("工具参数为空"));
        }

        Object urlObj = input.get("url");
        if (urlObj == null || urlObj.toString().isBlank()) {
            return Mono.just(AgentToolResultBlock.error("url 参数缺失"));
        }

        String url = urlObj.toString().trim();
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            return Mono.just(AgentToolResultBlock.error("仅支持http和https协议，不支持的协议: " + url));
        }
        if (ssrfGuardEnabled) {
            String violation = ssrfViolation(url);
            if (violation != null) {
                return Mono.just(AgentToolResultBlock.error(violation));
            }
        }

        int maxChars = parseMaxChars(input.get("maxChars"));

        String content;
        try {
            content = fetcher.fetch(url, maxChars);
        } catch (Exception e) {
            return Mono.just(AgentToolResultBlock.error("网页抓取失败: " + e.getMessage()));
        }

        if (content == null || content.isBlank()) {
            return Mono.just(AgentToolResultBlock.error("网页内容为空"));
        }

        String fencedContent = content.length() > maxChars
                ? content.substring(0, maxChars) + "\n\n(内容已截断，完整内容超过" + maxChars + "字符)"
                : content;

        return Mono.just(AgentToolResultBlock.of(List.of(
                AgentTextBlock.builder().text(
                        "以下为" + url + "的网页内容：\n\n"
                        + GuardrailFence.fence(fencedContent)).build())));
    }

    /**
     * 解析maxChars参数
     * @param value
     * @return
     */
    private int parseMaxChars(Object value) {
        if (value == null) {
            return DEFAULT_MAX_CHARS;
        }
        try {
            int m = Integer.parseInt(value.toString().trim());
            return m > 0 ? Math.min(m, 50000) : DEFAULT_MAX_CHARS;
        } catch (NumberFormatException e) {
            return DEFAULT_MAX_CHARS;
        }
    }

    /**
     * SSRF防护校验：拒绝环回、内网、链路本地与云元数据等内部目标
     * <p>
     * 仅对字面量IP与localhost类主机名做离线判定，不做DNS解析（避免引入网络依赖
     * 与解析/连接间的TOCTOU窗口）；公网域名的最终解析由WebFetcher实现方在建立
     * 连接时防护。
     * </p>
     * @param url
     * @return 违规描述，未违规返回null
     */
    private String ssrfViolation(String url) {
        URI uri;
        try {
            uri = URI.create(url);
        } catch (Exception e) {
            return "非法URL: " + url;
        }
        String host = uri.getHost();
        if (host == null || host.isBlank()) {
            return "URL缺少主机名: " + url;
        }
        String lower = host.toLowerCase(Locale.ROOT);
        if (lower.equals("localhost") || lower.endsWith(".localhost")
                || lower.endsWith(".local") || lower.endsWith(".internal")) {
            return "禁止访问内部地址: " + url;
        }
        try {
            // 字面量IP离线判定；主机名会尝试解析，解析结果为内网地址同样拒绝，
            // 解析失败（如离线环境）不在此拦截，由WebFetcher实现方在连接时防护
            InetAddress address = InetAddress.getByName(lower);
            if (address.isLoopbackAddress() || address.isSiteLocalAddress()
                    || address.isLinkLocalAddress() || address.isAnyLocalAddress()
                    || address.isMulticastAddress()) {
                return "禁止访问内部地址: " + url;
            }
        } catch (Exception ignored) {
            // 解析失败场景交由WebFetcher实现方在连接时防护
        }
        return null;
    }

    /**
     * 是否只读
     * @return
     */
    @Override
    public boolean isReadOnly() {
        return true;
    }
}