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
package com.yangqiong.agent.harness.subagent.orchestration;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yangqiong.agent.harness.core.message.AgentContentBlock;
import com.yangqiong.agent.harness.core.message.AgentTextBlock;
import com.yangqiong.agent.harness.core.model.AgentChatResponse;
import com.yangqiong.agent.harness.core.message.MessageFactory;
import com.yangqiong.agent.harness.model.ModelCaller;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

/**
 * 默认子代理规格生成器
 * <p>
 * 引擎自包含的默认实现，供独立框架开箱即用：通过引擎自身的模型调用器对复杂任务做LLM动态分解，
 * 生成子代理声明列表。仅依赖引擎内部API，不引入任何平台/外围模块依赖。
 * 业务层可通过HarnessRuntimeBuilder.setSpecGenerator()注入自定义实现覆盖默认行为。
 * 模型调用失败或解析失败时降级返回空列表，由编排引擎回退到预配置声明。
 * </p>
 * @author yangqiong
 */
public class DefaultSubagentSpecGenerator implements SubagentSpecGenerator {

    /**
     * 日志器
     */
    private static final Logger log = LoggerFactory.getLogger(DefaultSubagentSpecGenerator.class);

    /**
     * JSON解析器
     */
    private static final ObjectMapper MAPPER = new ObjectMapper();

    /**
     * 模型调用器
     */
    private final ModelCaller modelCaller;

    /**
     * 模型编码（可空）
     */
    private final String modelCode;

    /**
     * 构造默认子代理规格生成器
     * @param modelCaller
     * @param modelCode
     */
    public DefaultSubagentSpecGenerator(ModelCaller modelCaller, String modelCode) {
        this.modelCaller = modelCaller;
        this.modelCode = modelCode;
    }

    /**
     * 根据任务描述调用模型动态生成子代理声明列表
     * <p>
     * 通过系统提示约束要求模型只输出JSON数组，解析失败或模型调用失败时返回空列表。
     * </p>
     * @param task
     * @param maxSubagents
     * @param existingAgentNames
     * @param requestModelCode
     * @return
     */
    @Override
    public Mono<List<SubagentDeclaration>> generateDeclarations(String task, int maxSubagents,
                                                                Collection<String> existingAgentNames,
                                                                String requestModelCode) {
        String prompt = "你是一个复杂任务分解器。请将下面这个复杂任务拆解为不超过"
                + maxSubagents + "个子任务，每个子任务对应一个专业子代理。"
                + "只输出一个JSON数组，不要任何其他文字或代码块标记。"
                + "数组每个元素必须包含三个字段：name（英文标识）、description（中文职责描述）、"
                + "systemPrompt（中文系统提示词，说明该子代理如何完成子任务）。"
                + "JSON字符串值内请勿使用英文双引号，如需强调请使用中文引号「」或英文单引号，确保输出为合法JSON。"
                + "复杂任务：" + task;
        return modelCaller.call(List.of(MessageFactory.createUserMessage(prompt)), List.of())
                .map(response -> parseDeclarations(extractText(response)))
                .defaultIfEmpty(Collections.emptyList())
                .onErrorReturn(Collections.emptyList())
                .doOnNext(decls -> log.info("默认子代理分解完成: count={}", decls.size()));
    }

    /**
     * 是否启用声明动态生成
     * @return
     */
    @Override
    public boolean isDeclarationGenerationEnabled() {
        return true;
    }

    /**
     * 提取模型响应文本
     * @param response
     * @return
     */
    private String extractText(AgentChatResponse response) {
        StringBuilder sb = new StringBuilder();
        if (response.getContent() != null) {
            for (AgentContentBlock block : response.getContent()) {
                if (block instanceof AgentTextBlock textBlock && textBlock.getText() != null) {
                    sb.append(textBlock.getText());
                }
            }
        }
        return sb.toString();
    }

    /**
     * 解析模型返回的子代理声明JSON，剥离代码块标记与前后说明文字，
     * 解析失败时对字符串内未转义的英文双引号做转义后重试
     * @param text
     * @return
     */
    private List<SubagentDeclaration> parseDeclarations(String text) {
        List<SubagentDeclaration> declarations = new ArrayList<>();
        if (text == null || text.isBlank()) {
            return declarations;
        }
        JsonNode node = parseJsonLenient(extractJsonArray(text));
        if (node == null || !node.isArray()) {
            log.warn("子代理声明JSON解析失败，返回空列表");
            return new ArrayList<>();
        }
        for (JsonNode item : node) {
            String name = item.hasNonNull("name") ? item.get("name").asText() : null;
            String description = item.hasNonNull("description") ? item.get("description").asText() : "";
            String systemPrompt = item.hasNonNull("systemPrompt")
                    ? item.get("systemPrompt").asText() : null;
            if (name == null || name.isBlank()) {
                continue;
            }
            declarations.add(SubagentDeclaration.builder()
                    .name(name)
                    .description(description)
                    .systemPrompt(systemPrompt)
                    .modelCode(modelCode)
                    .maxIterations(5)
                    .build());
        }
        return declarations;
    }

    /**
     * 剥离代码块标记，截取首个 [ 到最后一个 ] 之间的JSON数组文本
     * @param text
     * @return
     */
    private String extractJsonArray(String text) {
        String trimmed = text.trim();
        if (trimmed.startsWith("```")) {
            int firstNewline = trimmed.indexOf('\n');
            if (firstNewline > 0) {
                trimmed = trimmed.substring(firstNewline + 1);
            }
            int lastFence = trimmed.lastIndexOf("```");
            if (lastFence > 0) {
                trimmed = trimmed.substring(0, lastFence);
            }
            trimmed = trimmed.trim();
        }
        int start = trimmed.indexOf('[');
        int end = trimmed.lastIndexOf(']');
        if (start >= 0 && end > start) {
            return trimmed.substring(start, end + 1);
        }
        return trimmed;
    }

    /**
     * 解析JSON数组：先严格解析，失败时修复字符串值内杂散英文双引号后重试
     * @param json
     * @return
     */
    private JsonNode parseJsonLenient(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return MAPPER.readTree(json);
        } catch (Exception ignored) {
            // 模型常在中文文案中直接使用英文双引号导致JSON非法，转义后重试
        }
        try {
            return MAPPER.readTree(escapeStrayQuotes(json));
        } catch (Exception e) {
            log.warn("子代理声明JSON解析失败，返回空列表", e);
            return null;
        }
    }

    /**
     * 修复字符串值内未转义的英文双引号：仅当双引号后紧跟结构字符（, ] } :）才视为字符串结束，
     * 否则视为字符串内容并转义，兼容模型输出中文文案时误用英文双引号的情况
     * @param json
     * @return
     */
    private String escapeStrayQuotes(String json) {
        StringBuilder sb = new StringBuilder(json.length() + 16);
        boolean inString = false;
        boolean escaped = false;
        for (int i = 0; i < json.length(); i++) {
            char c = json.charAt(i);
            if (escaped) {
                sb.append(c);
                escaped = false;
                continue;
            }
            if (c == '\\') {
                sb.append(c);
                escaped = true;
                continue;
            }
            if (c == '"') {
                boolean structural = isStructuralAfter(json, i);
                if (inString && !structural) {
                    // 字符串内容中的杂散引号，转义
                    sb.append('\\');
                }
                sb.append(c);
                if (inString && structural) {
                    inString = false;
                } else if (!inString) {
                    inString = true;
                }
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    /**
     * 判断字符串结束引号后（忽略空白）是否为结构字符
     * @param json
     * @param quoteIndex
     * @return
     */
    private boolean isStructuralAfter(String json, int quoteIndex) {
        int next = quoteIndex + 1;
        while (next < json.length() && (json.charAt(next) == ' '
                || json.charAt(next) == '\t' || json.charAt(next) == '\n' || json.charAt(next) == '\r')) {
            next++;
        }
        if (next >= json.length()) {
            return true;
        }
        char ch = json.charAt(next);
        return ch == ',' || ch == '}' || ch == ']' || ch == ':';
    }
}
