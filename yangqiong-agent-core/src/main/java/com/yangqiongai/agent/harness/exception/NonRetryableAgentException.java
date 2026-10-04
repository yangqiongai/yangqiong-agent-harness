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
package com.yangqiongai.agent.harness.exception;

/**
 * 不可重试的Agent执行终态异常
 * <p>
 * 用于认证/计费类模型错误（401/402/403）与连续工具失败超过阈值中止等
 * 重试必然同样失败的终态场景，上层据此短路重试逻辑避免无效token消耗
 * </p>
 * @author yangqiong
 */
public class NonRetryableAgentException extends RuntimeException {

    /**
     * 构造不可重试异常
     * @param message
     */
    public NonRetryableAgentException(String message) {
        super(message);
    }

    /**
     * 构造不可重试异常
     * @param message
     * @param cause
     */
    public NonRetryableAgentException(String message, Throwable cause) {
        super(message, cause);
    }
}
