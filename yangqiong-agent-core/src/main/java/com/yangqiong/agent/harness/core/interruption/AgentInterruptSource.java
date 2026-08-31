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
package com.yangqiong.agent.harness.core.interruption;

/**
 * Agent中断来源
 * @author yangqiong
 */
public enum AgentInterruptSource {

    /**
     * 用户主动中断
     */
    USER,

    /**
     * 系统中断
     */
    SYSTEM,

    /**
     * 超时中断
     */
    TIMEOUT,

    /**
     * 异常中断
     */
    ERROR
}
