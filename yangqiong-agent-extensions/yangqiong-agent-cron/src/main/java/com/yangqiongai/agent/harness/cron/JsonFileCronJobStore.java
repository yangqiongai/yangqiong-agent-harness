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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yangqiongai.agent.harness.durable.serialization.HarnessObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * JSON文件定时任务存储
 * <p>
 * 将全部定时任务以JSON数组写入单文件，方法级synchronized保证进程内原子读写，
 * 目录自动创建，文件不存在或损坏时回退为空列表；适合单机单进程的轻量持久化。
 * </p>
 * @author yangqiong
 */
public class JsonFileCronJobStore implements CronJobStore {

    private static final Logger log = LoggerFactory.getLogger(JsonFileCronJobStore.class);

    /**
     * 任务列表JSON类型引用
     */
    private static final TypeReference<List<CronJob>> JOB_LIST_TYPE = new TypeReference<>() {
    };

    /**
     * 存储文件路径
     */
    private final Path file;

    /**
     * JSON对象映射器
     */
    private final ObjectMapper mapper = HarnessObjectMapper.get();

    /**
     * 构造，绑定存储文件路径
     * @param file
     */
    public JsonFileCronJobStore(Path file) {
        if (file == null) {
            throw new IllegalArgumentException("存储文件路径不能为空");
        }
        this.file = file.toAbsolutePath().normalize();
    }

    /**
     * 查询全部定时任务，文件不存在时返回空列表
     * @return
     */
    @Override
    public List<CronJob> list() {
        synchronized (this) {
            return readAll();
        }
    }

    /**
     * 按ID查询定时任务
     * @param id
     * @return
     */
    @Override
    public Optional<CronJob> get(String id) {
        if (id == null) {
            return Optional.empty();
        }
        synchronized (this) {
            return readAll().stream().filter(job -> id.equals(job.getId())).findFirst();
        }
    }

    /**
     * 保存定时任务（新增或覆盖同名任务）
     * @param job
     */
    @Override
    public void save(CronJob job) {
        if (job == null) {
            return;
        }
        synchronized (this) {
            List<CronJob> jobs = readAll();
            jobs.removeIf(existing -> java.util.Objects.equals(existing.getId(), job.getId()));
            jobs.add(job);
            writeAll(jobs);
        }
    }

    /**
     * 按ID删除定时任务
     * @param id
     */
    @Override
    public void delete(String id) {
        if (id == null) {
            return;
        }
        synchronized (this) {
            List<CronJob> jobs = readAll();
            boolean removed = jobs.removeIf(job -> id.equals(job.getId()));
            if (removed) {
                writeAll(jobs);
            }
        }
    }

    /**
     * 读取全部任务，文件缺失或解析失败时返回空列表
     * @return
     */
    private List<CronJob> readAll() {
        if (!Files.exists(file)) {
            return new ArrayList<>();
        }
        try {
            return new ArrayList<>(mapper.readValue(file.toFile(), JOB_LIST_TYPE));
        } catch (IOException e) {
            log.warn("读取定时任务文件失败，按空列表处理: {}，原因: {}", file, e.getMessage());
            return new ArrayList<>();
        }
    }

    /**
     * 将全部任务写入文件，自动创建父目录
     * @param jobs
     */
    private void writeAll(List<CronJob> jobs) {
        try {
            Path parent = file.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            mapper.writerWithDefaultPrettyPrinter().writeValue(file.toFile(), jobs);
        } catch (IOException e) {
            throw new IllegalStateException("写入定时任务文件失败: " + file, e);
        }
    }
}
