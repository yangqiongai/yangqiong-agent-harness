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
package com.yangqiongai.agent.harness.eval;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.yangqiongai.agent.harness.core.message.AgentMessage;
import com.yangqiongai.agent.harness.core.model.AgentChatResponse;
import com.yangqiongai.agent.harness.core.model.AgentGenerateOptions;
import com.yangqiongai.agent.harness.core.model.AgentModel;
import com.yangqiongai.agent.harness.core.message.MessageFactory;
import com.yangqiongai.agent.harness.core.message.AgentTextBlock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * LLM 评测判分器
 * @author yangqiong
 */
public final class LLMEvalJudge implements EvalJudge {

    private static final Logger log = LoggerFactory.getLogger(LLMEvalJudge.class);

    private static final Pattern SCORE_PATTERN = Pattern.compile(
            "(?i)score\\s*[:=]\\s*([01](?:\\.\\d+)?|1\\.0)");

    private static final Pattern REASONING_PATTERN = Pattern.compile(
            "(?i)reasoning\\s*[:=]\\s*(.+)");

    private final AgentModel model;

    private final AgentGenerateOptions options;

    private final EvalJudge fallback;

    /**
     * 构建 LLM 判分器
     * @param model 用于判分的模型
     * @param fallback 解析失败时的回退判分器，为null时使用RuleEvalJudge
     */
    public LLMEvalJudge(AgentModel model, EvalJudge fallback) {
        this.model = model;
        this.options = AgentGenerateOptions.builder()
                .temperature(0.0)
                .maxTokens(1024)
                .build();
        this.fallback = fallback != null ? fallback : RuleEvalJudge.instance();
    }

    /**
     * 构建 LLM 判分器，使用默认回退
     * @param model
     */
    public LLMEvalJudge(AgentModel model) {
        this(model, RuleEvalJudge.instance());
    }

    /**
     * 调用 LLM 判分，解析失败时回退规则评分
     * @param evalCase
     * @param finalAnswer
     * @param actualToolNames
     * @return
     */
    @Override
    public JudgeVerdict judge(EvalCase evalCase, String finalAnswer, List<String> actualToolNames) {
        try {
            String prompt = buildJudgePrompt(evalCase, finalAnswer, actualToolNames);
            AgentMessage systemMsg = MessageFactory.createSystemMessage(
                    "你是一个评测判分器，请根据任务描述、期望要点和实际回答，输出评分（0-1）和理由。"
                            + "请严格按照以下格式输出：\n"
                            + "Score: <0-1之间的数值>\n"
                            + "Reasoning: <判分理由>");
            AgentMessage userMsg = MessageFactory.createUserMessage(prompt);
            AgentChatResponse response = model.generate(List.of(systemMsg, userMsg), List.of(), options);
            String text = extractText(response);
            return parseVerdict(text);
        } catch (Exception e) {
            log.warn("LLM判分失败，回退规则评分: {}", e.getMessage());
            return fallback.judge(evalCase, finalAnswer, actualToolNames);
        }
    }

    /**
     * 构建判分提示词
     * @param evalCase
     * @param finalAnswer
     * @param actualToolNames
     * @return
     */
    private String buildJudgePrompt(EvalCase evalCase, String finalAnswer, List<String> actualToolNames) {
        StringBuilder sb = new StringBuilder();
        sb.append("## 任务描述\n").append(evalCase.getQuery()).append("\n\n");
        if (evalCase.getExpectedToolNames() != null && !evalCase.getExpectedToolNames().isEmpty()) {
            sb.append("## 期望调用的工具\n").append(evalCase.getExpectedToolNames()).append("\n\n");
        }
        if (evalCase.getExpectedKeywords() != null && !evalCase.getExpectedKeywords().isEmpty()) {
            sb.append("## 期望回答包含的关键词\n").append(evalCase.getExpectedKeywords()).append("\n\n");
        }
        sb.append("## 实际调用的工具\n").append(actualToolNames).append("\n\n");
        sb.append("## 实际最终回答\n").append(finalAnswer).append("\n\n");
        sb.append("请根据以上信息，对本次回答质量进行评分（0-1），并给出判分理由。");
        return sb.toString();
    }

    /**
     * 提取响应中的文本内容
     * @param response
     * @return
     */
    private String extractText(AgentChatResponse response) {
        if (response == null || response.getContent() == null || response.getContent().isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (Object block : response.getContent()) {
            if (block instanceof AgentTextBlock textBlock) {
                sb.append(textBlock.getText());
            }
        }
        return sb.toString();
    }

    /**
     * 解析 LLM 输出中的评分与理由
     * @param text
     * @return
     */
    private JudgeVerdict parseVerdict(String text) {
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("LLM输出为空，无法解析判分结果");
        }
        Matcher scoreMatcher = SCORE_PATTERN.matcher(text);
        if (!scoreMatcher.find()) {
            throw new IllegalArgumentException("无法从LLM输出中解析score: " + text);
        }
        double score = Double.parseDouble(scoreMatcher.group(1));
        if (score < 0.0 || score > 1.0) {
            score = Math.max(0.0, Math.min(1.0, score));
        }
        String reasoning = "";
        Matcher reasoningMatcher = REASONING_PATTERN.matcher(text);
        if (reasoningMatcher.find()) {
            reasoning = reasoningMatcher.group(1).trim();
        }
        return new JudgeVerdict(score, reasoning);
    }
}