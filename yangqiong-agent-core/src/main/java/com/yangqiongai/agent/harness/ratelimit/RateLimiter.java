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
package com.yangqiongai.agent.harness.ratelimit;

/**
 * 模型速率限制器
 * <p>
 * SPI接口，提供按调度返回等待时长的限流能力。
 * </p>
 * @author yangqiong
 */
public interface RateLimiter {

    /**
     * 获取本次调用需等待的纳秒数，0表示立即可用
     * <p>
     * 实现方应预留（消耗）一个调用配额，并返回其调度等待时间。
     * </p>
     * @return
     */
    long waitNanos();

    /**
     * 获取当前可用速率（每秒可执行的调用数），供观测
     * @return
     */
    double getCurrentRate();
}