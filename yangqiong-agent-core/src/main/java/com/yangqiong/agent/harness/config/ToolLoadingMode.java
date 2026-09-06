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
package com.yangqiong.agent.harness.config;

/**
 * 工具下发模式
 * @author yangqiong
 */
public enum ToolLoadingMode {

    /**
     * 全量下发（默认，全部工具schema随每轮模型请求下发）
     */
    FULL,

    /**
     * 渐进加载：常驻工具 + 目录注入 + 按需启用（对齐skill渐进模式）
     */
    PROGRESSIVE
}
