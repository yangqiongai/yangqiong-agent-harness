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
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 默认辩论裁判
 * <p>
 * 确定性实现：汇总各方立场后，综合输出完整度与多方共识选优。
 * 共识得分 = 该立场包含的词元在其它立场中出现的总次数，得分相同时按输出长度、名称稳定排序。
 * </p>
 * @author yangqiong
 */
public class DefaultDebateJudge implements DebateJudge {

    /**
     * 根据各方立场产出判定结果
     * @param task 原始任务
     * @param positions 各方立场
     * @param previousFeedback 前一轮反馈（可为空字符串）
     * @return 判定结果文本
     */
    @Override
    public String judge(String task, List<SubagentResult> positions, String previousFeedback) {
        List<SubagentResult> valid = filterValid(positions);
        StringBuilder sb = new StringBuilder();
        sb.append("辩论裁判判定：\n");
        if (valid.isEmpty()) {
            sb.append("无有效立场，无法判定。");
            return sb.toString();
        }
        // 汇总各方立场
        for (SubagentResult position : valid) {
            sb.append("- ").append(position.name()).append("：").append(position.output()).append("\n");
        }
        // 按共识与输出长度选优
        SubagentResult best = selectBest(valid);
        sb.append("\n综合输出完整度与多方共识，判定最优立场为：").append(best.name());
        sb.append("\n最优立场内容：").append(best.output());
        return sb.toString();
    }

    /**
     * 过滤有效立场（成功且输出非空）
     * @param positions
     * @return
     */
    private List<SubagentResult> filterValid(List<SubagentResult> positions) {
        List<SubagentResult> valid = new ArrayList<>();
        if (positions == null) {
            return valid;
        }
        for (SubagentResult position : positions) {
            if (position != null && position.isSuccess() && position.output() != null) {
                valid.add(position);
            }
        }
        return valid;
    }

    /**
     * 按共识得分、输出长度、名称依次选优
     * @param positions
     * @return
     */
    private SubagentResult selectBest(List<SubagentResult> positions) {
        return positions.stream()
                .max(Comparator
                        .comparingInt((SubagentResult r) -> consensusScore(r, positions))
                        .thenComparingInt(r -> r.output().length())
                        .thenComparing(r -> r.name() != null ? r.name() : ""))
                .orElse(positions.get(0));
    }

    /**
     * 计算指定立场与其它立场的共识得分
     * @param target
     * @param positions
     * @return
     */
    private int consensusScore(SubagentResult target, List<SubagentResult> positions) {
        Set<String> targetTokens = tokenize(target.output());
        int score = 0;
        for (SubagentResult other : positions) {
            if (other == target) {
                continue;
            }
            for (String token : tokenize(other.output())) {
                if (targetTokens.contains(token)) {
                    score++;
                }
            }
        }
        return score;
    }

    /**
     * 按空白拆分输出为词元集合
     * @param text
     * @return
     */
    private Set<String> tokenize(String text) {
        Set<String> tokens = new HashSet<>();
        if (text == null) {
            return tokens;
        }
        for (String token : text.split("\\s+")) {
            if (!token.isBlank()) {
                tokens.add(token);
            }
        }
        return tokens;
    }
}
