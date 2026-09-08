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
package com.yangqiongai.agent.harness.model.routing;

import java.util.Map;

/**
 * 路由决策
 * @author yangqiong
 */
public final class RouteDecision {

    /**
     * 选中的路由候选，无可用候选时为null
     */
    private final RouteCandidate selected;

    /**
     * 被排除的模型编码与原因映射
     */
    private final Map<String, String> excluded;

    /**
     * 选中候选的得分
     */
    private final double score;

    private RouteDecision(RouteCandidate selected, Map<String, String> excluded, double score) {
        this.selected = selected;
        this.excluded = excluded == null ? Map.of() : Map.copyOf(excluded);
        this.score = score;
    }

    /**
     * 构建选中决策
     * @param selected
     * @param score
     * @param excluded
     * @return
     */
    public static RouteDecision of(RouteCandidate selected, double score, Map<String, String> excluded) {
        return new RouteDecision(selected, excluded, score);
    }

    /**
     * 构建无可用候选决策
     * @param excluded
     * @return
     */
    public static RouteDecision empty(Map<String, String> excluded) {
        return new RouteDecision(null, excluded, 0.0d);
    }

    /**
     * 获取选中的路由候选
     * @return
     */
    public RouteCandidate getSelected() {
        return selected;
    }

    /**
     * 获取被排除的模型编码与原因映射
     * @return
     */
    public Map<String, String> getExcluded() {
        return excluded;
    }

    /**
     * 获取选中候选的得分
     * @return
     */
    public double getScore() {
        return score;
    }

    @Override
    public String toString() {
        return "RouteDecision{selected=" + (selected == null ? "null" : selected.getModelCode())
                + ", excluded=" + excluded + ", score=" + score + "}";
    }
}
