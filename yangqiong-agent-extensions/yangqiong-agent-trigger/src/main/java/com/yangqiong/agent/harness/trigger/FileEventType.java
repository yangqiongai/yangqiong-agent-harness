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

/**
 * 文件事件类型
 * <p>
 * 描述文件系统监听到的三类变化：新建、修改、删除，供监听器按事件类型分发。
 * </p>
 * @author yangqiong
 */
public enum FileEventType {

    /**
     * 文件或目录被创建
     */
    CREATE,

    /**
     * 文件或目录被修改
     */
    MODIFY,

    /**
     * 文件或目录被删除
     */
    DELETE
}