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
package com.yangqiongai.agent.harness.trigger;

import com.yangqiongai.agent.harness.cron.CronJob;
import com.yangqiongai.agent.harness.cron.CronTaskRunner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;

/**
 * Agent事件触发器
 * <p>
 * 将文件事件翻译为一次Agent执行：按指令模板生成定时任务并交给{@link CronTaskRunner}异步唤起Agent，
 * 指令模板中的{@code {path}}占位符会替换为事件路径，事件类型注入任务名称。
 * </p>
 * @author yangqiong
 */
public class FileEventAgentTrigger implements FileEventTrigger {

    /**
     * 路径占位符
     */
    private static final String PATH_PLACEHOLDER = "{path}";

    /**
     * 日志
     */
    private static final Logger LOG = LoggerFactory.getLogger(FileEventAgentTrigger.class);

    /**
     * 任务执行器
     */
    private final CronTaskRunner runner;

    /**
     * 触发指令模板
     */
    private final String instructionTemplate;

    /**
     * 租户作用域标识
     */
    private final String scopeId;

    /**
     * 会话标识
     */
    private final String sessionId;

    /**
     * 构造
     * @param runner
     * @param instructionTemplate
     */
    public FileEventAgentTrigger(CronTaskRunner runner, String instructionTemplate) {
        this(runner, instructionTemplate, null, null);
    }

    /**
     * 构造
     * @param runner
     * @param instructionTemplate
     * @param scopeId
     * @param sessionId
     */
    public FileEventAgentTrigger(CronTaskRunner runner, String instructionTemplate, String scopeId, String sessionId) {
        if (runner == null) {
            throw new IllegalArgumentException("任务执行器不能为空");
        }
        this.runner = runner;
        this.instructionTemplate = instructionTemplate != null ? instructionTemplate : "";
        this.scopeId = scopeId;
        this.sessionId = sessionId;
    }

    @Override
    public void onEvent(Path path, FileEventType type) {
        if (path == null || type == null) {
            return;
        }
        String instruction = instructionTemplate.replace(PATH_PLACEHOLDER, path.toString()) + "\n事件路径: " + path + "\n事件类型: " + type;
        CronJob job = CronJob.builder()
                .name("file-event:" + type)
                .instruction(instruction)
                .scopeId(scopeId)
                .sessionId(sessionId)
                .enabled(true)
                .build();
        runner.run(job).subscribe(
                result -> LOG.debug("文件事件唤起Agent完成: {}", job),
                error -> LOG.warn("文件事件唤起Agent失败: {}", job, error));
    }
}