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
package com.yangqiongai.agent.harness.core.event;

import java.util.Objects;

/**
 * 范式阶段信息
 * @author yangqiong
 */
public final class ParadigmStageInfo {

    /**
     * 阶段：执行产出候选
     */
    public static final String EXECUTE = "EXECUTE";

    /**
     * 阶段：自评估
     */
    public static final String EVALUATE = "EVALUATE";

    /**
     * 阶段：反思教训生成
     */
    public static final String REFLECT = "REFLECT";

    /**
     * 阶段：批评
     */
    public static final String CRITIQUE = "CRITIQUE";

    /**
     * 阶段：修订
     */
    public static final String REVISE = "REVISE";

    /**
     * 范式阶段名（本类EXECUTE/EVALUATE/REFLECT/CRITIQUE/REVISE常量之一）
     */
    private final String stage;

    /**
     * 反思/修订轮次，从0起算
     */
    private final int attempt;

    public ParadigmStageInfo(String stage, int attempt) {
        this.stage = stage;
        this.attempt = attempt;
    }

    /**
     * 获取范式阶段名
     * @return
     */
    public String getStage() {
        return stage;
    }

    /**
     * 获取反思/修订轮次
     * @return
     */
    public int getAttempt() {
        return attempt;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ParadigmStageInfo that = (ParadigmStageInfo) o;
        return attempt == that.attempt
                && Objects.equals(stage, that.stage);
    }

    @Override
    public int hashCode() {
        return Objects.hash(stage, attempt);
    }

    @Override
    public String toString() {
        return "ParadigmStageInfo{stage=" + stage + ", attempt=" + attempt + "}";
    }
}
