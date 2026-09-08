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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.FileSystems;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 文件监听触发器
 * <p>
 * 封装JDK WatchService对目录递归监听，将创建/修改/删除事件按后缀过滤后派发给监听器，
 * 并对同一批次的连续变化做防抖合并（默认500ms）以降低高频写入时的回调次数；
 * 新建子目录会被自动纳入监听，进程退出前应调用{@link #close()}释放资源。
 * </p>
 * @author yangqiong
 */
public class FileWatchTrigger implements AutoCloseable {

    /**
     * 默认防抖窗口
     */
    private static final long DEFAULT_DEBOUNCE_MILLIS = 500L;

    /**
     * 监听线程名
     */
    private static final String THREAD_NAME = "file-watch-trigger";

    /**
     * 日志
     */
    private static final Logger LOG = LoggerFactory.getLogger(FileWatchTrigger.class);

    /**
     * 根目录
     */
    private final Path root;

    /**
     * 事件监听器
     */
    private final FileEventTrigger listener;

    /**
     * 防抖窗口（毫秒）
     */
    private final long debounceMillis;

    /**
     * 关注的文件后缀集合，为空表示监听全部文件
     */
    private final Set<String> suffixes;

    /**
     * 监听键对应的目录
     */
    private final Map<WatchKey, Path> keyDirs = new ConcurrentHashMap<>();

    /**
     * 事件合并队列
     */
    private final LinkedBlockingQueue<PathEvent> pendingEvents = new LinkedBlockingQueue<>();

    /**
     * 是否运行中
     */
    private final AtomicBoolean running = new AtomicBoolean(false);

    /**
     * 底层监听服务
     */
    private WatchService watchService;

    /**
     * 派发线程
     */
    private Thread dispatchThread;

    /**
     * 构造，使用默认防抖窗口并监听全部文件
     * @param root
     * @param listener
     */
    public FileWatchTrigger(Path root, FileEventTrigger listener) {
        this(root, listener, DEFAULT_DEBOUNCE_MILLIS, Set.of());
    }

    /**
     * 构造
     * @param root
     * @param listener
     * @param debounceMillis
     * @param suffixes
     */
    public FileWatchTrigger(Path root, FileEventTrigger listener, long debounceMillis, Set<String> suffixes) {
        if (root == null) {
            throw new IllegalArgumentException("监听目录不能为空");
        }
        if (listener == null) {
            throw new IllegalArgumentException("事件监听器不能为空");
        }
        this.root = root.toAbsolutePath().normalize();
        this.listener = listener;
        this.debounceMillis = Math.max(0L, debounceMillis);
        this.suffixes = suffixes == null ? Set.of() : Set.copyOf(suffixes);
    }

    /**
     * 开始监听，重复调用幂等
     */
    public synchronized void start() {
        if (!Files.isDirectory(root)) {
            throw new IllegalArgumentException("监听目录不存在: " + root);
        }
        if (!running.compareAndSet(false, true)) {
            return;
        }
        try {
            watchService = FileSystems.getDefault().newWatchService();
            registerRecursively(root);
            dispatchThread = new Thread(this::dispatchLoop, THREAD_NAME);
            dispatchThread.setDaemon(true);
            dispatchThread.start();
            LOG.info("文件监听触发器已启动: {}", root);
        } catch (IOException e) {
            running.set(false);
            throw new UncheckedIOException("文件监听启动失败: " + root, e);
        }
    }

    /**
     * 停止监听并释放资源
     */
    @Override
    public void close() {
        if (!running.compareAndSet(true, false)) {
            return;
        }
        if (watchService != null) {
            try {
                watchService.close();
            } catch (IOException e) {
                LOG.warn("关闭文件监听服务异常: {}", root, e);
            }
        }
        if (dispatchThread != null) {
            dispatchThread.interrupt();
            try {
                dispatchThread.join(2000L);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        keyDirs.clear();
        pendingEvents.clear();
    }

    /**
     * 派发循环，取事件并入队等待下次批次派发
     */
    private void dispatchLoop() {
        try {
            while (running.get()) {
                WatchKey key = watchService.take();
                Path dir = keyDirs.get(key);
                key.reset();
                if (dir == null) {
                    continue;
                }
                collectAndEnqueue(key, dir);
                flushDueEvents();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            if (running.get()) {
                LOG.warn("文件监听派发循环异常", e);
            }
        }
    }

    /**
     * 收集当前监听键上的事件并按路径合并入队
     * @param key
     * @param dir
     */
    private void collectAndEnqueue(WatchKey key, Path dir) {
        for (WatchEvent<?> event : key.pollEvents()) {
            WatchEvent.Kind<?> kind = event.kind();
            if (kind == StandardWatchEventKinds.OVERFLOW) {
                LOG.warn("文件监听事件溢出: {}", dir);
                continue;
            }
            Object context = event.context();
            if (!(context instanceof Path)) {
                continue;
            }
            Path full = dir.resolve((Path) context).normalize();
            FileEventType type = mapKind(kind);
            if (type != null) {
                pendingEvents.offer(new PathEvent(full, type));
            }
            if (kind == StandardWatchEventKinds.ENTRY_CREATE && Files.isDirectory(full)) {
                registerRecursively(full);
            }
        }
    }

    /**
     * 取出当前批次事件按路径合并，未到期则等待剩余时间
     * @throws InterruptedException
     */
    private void flushDueEvents() throws InterruptedException {
        Map<Path, FileEventType> merged = new LinkedHashMap<>();
        PathEvent first = pendingEvents.poll();
        if (first == null) {
            return;
        }
        long deadline = System.currentTimeMillis() + debounceMillis;
        merged.put(first.path, first.type);
        while (System.currentTimeMillis() < deadline) {
            PathEvent event = pendingEvents.poll(deadline - System.currentTimeMillis(), TimeUnit.MILLISECONDS);
            if (event == null) {
                break;
            }
            merged.put(event.path, event.type);
        }
        dispatch(merged);
    }

    /**
     * 按后缀过滤后派发合并后的事件
     * @param merged
     */
    private void dispatch(Map<Path, FileEventType> merged) {
        for (Map.Entry<Path, FileEventType> entry : merged.entrySet()) {
            Path path = entry.getKey();
            if (isSupported(path)) {
                try {
                    listener.onEvent(path, entry.getValue());
                } catch (Exception e) {
                    LOG.warn("文件事件回调异常: {}", path, e);
                }
            }
        }
    }

    /**
     * 是否符合后缀过滤规则
     * @param path
     * @return
     */
    private boolean isSupported(Path path) {
        if (suffixes.isEmpty() || !Files.isRegularFile(path)) {
            return suffixes.isEmpty();
        }
        String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
        for (String suffix : suffixes) {
            if (name.endsWith(suffix.toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }

    /**
     * 将监听事件类型映射为内部事件类型
     * @param kind
     * @return
     */
    private static FileEventType mapKind(WatchEvent.Kind<?> kind) {
        if (kind == StandardWatchEventKinds.ENTRY_CREATE) {
            return FileEventType.CREATE;
        }
        if (kind == StandardWatchEventKinds.ENTRY_MODIFY) {
            return FileEventType.MODIFY;
        }
        if (kind == StandardWatchEventKinds.ENTRY_DELETE) {
            return FileEventType.DELETE;
        }
        return null;
    }

    /**
     * 递归注册自给定目录起的所有目录监听
     * @param start
     */
    private void registerRecursively(Path start) {
        try {
            Files.walkFileTree(start, new SimpleFileVisitor<Path>() {
                @Override
                public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                    register(dir);
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException e) {
            LOG.warn("注册目录监听失败: {}", start, e);
        }
    }

    /**
     * 注册单个目录监听
     * @param dir
     * @throws IOException
     */
    private void register(Path dir) throws IOException {
        WatchKey key = dir.register(watchService,
                StandardWatchEventKinds.ENTRY_CREATE,
                StandardWatchEventKinds.ENTRY_MODIFY,
                StandardWatchEventKinds.ENTRY_DELETE);
        keyDirs.put(key, dir.toAbsolutePath().normalize());
    }

    /**
     * 待派发的事件
     * @param path
     * @param type
     */
    private record PathEvent(Path path, FileEventType type) {
    }
}