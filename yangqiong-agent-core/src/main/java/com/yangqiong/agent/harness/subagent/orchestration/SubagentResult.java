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

/**
 * 子代理执行结果
 * @author yangqiong
 *
 * @param name 子代理名称
 * @param status 执行状态
 * @param output 执行输出（成功时）
 * @param error 错误信息（失败时）
 * @param duration 执行耗时（毫秒）
 */
public record SubagentResult(
        String name,
        SubagentStatus status,
        String output,
        String error,
        long duration
) {

    /**
     * 子代理执行状态
     */
    public enum SubagentStatus {
        SUCCESS,
        FAILED,
        TIMEOUT,
        SKIPPED
    }

    /**
     * 创建成功结果
     * @param name
     * @param output
     * @param duration
     * @return
     */
    public static SubagentResult success(String name, String output, long duration) {
        return new SubagentResult(name, SubagentStatus.SUCCESS, output, null, duration);
    }

    /**
     * 创建失败结果
     * @param name
     * @param error
     * @param duration
     * @return
     */
    public static SubagentResult failure(String name, String error, long duration) {
        return new SubagentResult(name, SubagentStatus.FAILED, null, error, duration);
    }

    /**
     * 创建超时结果
     * @param name
     * @param error
     * @param duration
     * @return
     */
    public static SubagentResult timeout(String name, String error, long duration) {
        return new SubagentResult(name, SubagentStatus.TIMEOUT, null, error, duration);
    }

    /**
     * 判断是否成功
     * @return
     */
    public boolean isSuccess() {
        return status == SubagentStatus.SUCCESS;
    }

    /**
     * 判断是否失败
     * @return
     */
    public boolean isFailure() {
        return status == SubagentStatus.FAILED || status == SubagentStatus.TIMEOUT;
    }
}
