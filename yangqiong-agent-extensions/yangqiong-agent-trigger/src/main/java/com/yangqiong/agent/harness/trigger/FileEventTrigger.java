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

import java.nio.file.Path;

/**
 * 文件事件监听器
 * <p>
 * 文件监听触发器的回调出口：监听器将文件变化事件按路径与类型派发给实现方，
 * 供实现方决定如何响应（如唤起Agent执行或直接落盘处理）。
 * </p>
 * @author yangqiong
 */
@FunctionalInterface
public interface FileEventTrigger {

    /**
     * 处理一次文件事件
     * @param path
     * @param type
     */
    void onEvent(Path path, FileEventType type);
}