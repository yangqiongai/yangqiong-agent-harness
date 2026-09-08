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
package com.yangqiongai.agent.harness.cron;

import com.yangqiongai.agent.harness.core.message.AgentMessage;
import reactor.core.publisher.Mono;

/**
 * 定时任务执行器
 * <p>
 * 任务触发时由调度器异步调用，将定时任务翻译为一次Agent执行并返回其结果。
 * </p>
 * @author yangqiong
 */
@FunctionalInterface
public interface CronTaskRunner {

    /**
     * 执行定时任务
     * @param job
     * @return
     */
    Mono<AgentMessage> run(CronJob job);
}
