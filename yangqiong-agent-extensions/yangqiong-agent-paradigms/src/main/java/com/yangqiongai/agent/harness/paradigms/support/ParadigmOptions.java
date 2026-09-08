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
package com.yangqiongai.agent.harness.paradigms.support;

/**
 * 范式公共配置
 * @author yangqiong
 */
public class ParadigmOptions {

    /**
     * 单次执行最大步数，默认10
     */
    private int maxSteps = 10;

    /**
     * 最大反思次数，默认2
     */
    private int maxReflections = 2;

    /**
     * 最大修订次数，默认2
     */
    private int maxRefinements = 2;

    /**
     * 使用全默认值构建
     */
    public ParadigmOptions() {
    }

    /**
     * 设置最大步数，负数抛出IllegalArgumentException
     * @param maxSteps
     * @return
     */
    public ParadigmOptions maxSteps(int maxSteps) {
        if (maxSteps < 0) {
            throw new IllegalArgumentException("maxSteps must be non-negative: " + maxSteps);
        }
        this.maxSteps = maxSteps;
        return this;
    }

    /**
     * 设置最大反思次数，负数抛出IllegalArgumentException
     * @param maxReflections
     * @return
     */
    public ParadigmOptions maxReflections(int maxReflections) {
        if (maxReflections < 0) {
            throw new IllegalArgumentException("maxReflections must be non-negative: " + maxReflections);
        }
        this.maxReflections = maxReflections;
        return this;
    }

    /**
     * 设置最大修订次数，负数抛出IllegalArgumentException
     * @param maxRefinements
     * @return
     */
    public ParadigmOptions maxRefinements(int maxRefinements) {
        if (maxRefinements < 0) {
            throw new IllegalArgumentException("maxRefinements must be non-negative: " + maxRefinements);
        }
        this.maxRefinements = maxRefinements;
        return this;
    }

    /**
     * 获取最大步数
     * @return
     */
    public int getMaxSteps() {
        return maxSteps;
    }

    /**
     * 获取最大反思次数
     * @return
     */
    public int getMaxReflections() {
        return maxReflections;
    }

    /**
     * 获取最大修订次数
     * @return
     */
    public int getMaxRefinements() {
        return maxRefinements;
    }
}
