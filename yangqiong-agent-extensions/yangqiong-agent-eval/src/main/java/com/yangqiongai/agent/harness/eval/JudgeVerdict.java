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

/**
 * 判分结论
 * @author yangqiong
 */
public record JudgeVerdict(double score, String reasoning) {

    /**
     * 构建判分结论
     * @param score 得分（0-1）
     * @param reasoning 判分理由
     */
    public JudgeVerdict {
        if (Double.isNaN(score) || Double.isInfinite(score)) {
            throw new IllegalArgumentException("score必须为有限数值");
        }
        if (score < 0.0 || score > 1.0) {
            throw new IllegalArgumentException("score必须在0-1之间");
        }
    }
}
