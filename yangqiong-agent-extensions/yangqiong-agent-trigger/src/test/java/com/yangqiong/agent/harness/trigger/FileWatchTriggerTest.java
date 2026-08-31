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
package com.yangqiong.agent.harness.trigger;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;

import com.yangqiong.agent.harness.core.message.AgentMessage;
import com.yangqiong.agent.harness.cron.CronJob;
import com.yangqiong.agent.harness.cron.CronTaskRunner;
import reactor.core.publisher.Mono;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 文件监听触发器测试
 * @author yangqiong
 */
class FileWatchTriggerTest {

    /**
     * 临时目录
     */
    @TempDir
    Path tempDir;

    /**
     * 收集到的事件
     */
    private final List<PathEvent> events = new CopyOnWriteArrayList<>();

    /**
     * 触发器
     */
    private FileWatchTrigger trigger;

    /**
     * 清理监听器
     */
    @AfterEach
    void tearDown() {
        if (trigger != null) {
            trigger.close();
        }
    }

    /**
     * 监听新建与修改事件并派发
     * @throws Exception
     */
    @Test
    void detectsCreateAndModify() throws Exception {
        startTrigger(500L, Set.of());

        Path file = tempDir.resolve("a.txt");
        Files.writeString(file, "hello", StandardCharsets.UTF_8);

        await(() -> containsPath(file));

        assertThat(containsPath(file)).isTrue();
    }

    /**
     * 按后缀过滤，未匹配的文件不派发
     * @throws Exception
     */
    @Test
    void filtersBySuffix() throws Exception {
        startTrigger(500L, Set.of(".md"));

        Path txt = tempDir.resolve("a.txt");
        Files.writeString(txt, "txt");

        Path md = tempDir.resolve("b.md");
        Files.writeString(md, "md");

        await(() -> containsPath(md));

        assertThat(containsPath(txt)).isFalse();
    }

    /**
     * 新建子目录自动纳入监听
     * @throws Exception
     */
    @Test
    void watchesNewSubdirectory() throws Exception {
        startTrigger(500L, Set.of());

        Path sub = tempDir.resolve("sub");
        Files.createDirectory(sub);

        await(() -> containsPath(sub));

        Path nested = sub.resolve("n.txt");
        Files.writeString(nested, "nested");

        await(() -> containsPath(nested));

        assertThat(containsPath(nested)).isTrue();
    }

    /**
     * 删除事件被派发
     * @throws Exception
     */
    @Test
    void detectsDelete() throws Exception {
        startTrigger(500L, Set.of());

        Path file = tempDir.resolve("d.txt");
        Files.writeString(file, "data");
        await(() -> containsPath(file));

        Files.delete(file);

        await(() -> events.stream().anyMatch(e -> e.path.equals(file) && e.type == FileEventType.DELETE));

        assertThat(events.stream().anyMatch(e -> e.path.equals(file) && e.type == FileEventType.DELETE)).isTrue();
    }

    /**
     * Agent触发器按模板拼装任务并唤起执行器
     */
    @Test
    void agentTriggerBuildsJobAndInvokesRunner() {
        AtomicReference<CronJob> captured = new AtomicReference<>();
        CronTaskRunner runner = job -> {
            captured.set(job);
            return Mono.<AgentMessage>empty();
        };
        FileEventAgentTrigger agentTrigger =
                new FileEventAgentTrigger(runner, "请处理文件 {path}");
        Path path = tempDir.resolve("new.pdf");

        agentTrigger.onEvent(path, FileEventType.CREATE);

        assertThat(captured.get()).isNotNull();
        assertThat(captured.get().getInstruction()).contains(path.toString()).contains("CREATE");
    }

    /**
     * 启动带防抖与后缀过滤的监听器
     * @param debounceMillis
     * @param suffixes
     */
    private void startTrigger(long debounceMillis, Set<String> suffixes) {
        trigger = new FileWatchTrigger(tempDir,
                (path, type) -> events.add(new PathEvent(path, type)),
                debounceMillis, suffixes);
        trigger.start();
    }

    /**
     * 轮询等待条件满足，超时抛出断言失败
     * @param condition
     * @throws InterruptedException
     */
    private void await(java.util.function.BooleanSupplier condition) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 8000L;
        while (System.currentTimeMillis() < deadline) {
            if (condition.getAsBoolean()) {
                return;
            }
            Thread.sleep(60L);
        }
        throw new AssertionError("等待事件派发超时");
    }

    /**
     * 是否派发过指定路径的事件
     * @param path
     * @return
     */
    private boolean containsPath(Path path) {
        return events.stream().anyMatch(e -> e.path.equals(path));
    }

    /**
     * 记录事件
     * @param path
     * @param type
     */
    private record PathEvent(Path path, FileEventType type) {
    }
}