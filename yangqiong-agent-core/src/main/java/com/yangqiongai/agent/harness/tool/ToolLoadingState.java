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
package com.yangqiongai.agent.harness.tool;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 工具渐进加载状态
 * <p>
 * 构建时创建，EngineContext（schema裁剪）与LoadToolTool（按需转正）共享同一实例，
 * activatedTools的变更对双方立即可见。
 * </p>
 * @author yangqiong
 */
public class ToolLoadingState {

    /**
     * 元工具名集合：渐进模式下注册的调度型工具，schema始终下发且不进延迟池目录
     */
    public static final Set<String> META_TOOL_NAMES = Set.of("load_tool");

    /**
     * 是否渐进加载模式
     */
    private final boolean progressive;

    /**
     * 常驻工具名集合（始终下发完整schema）
     */
    private final Set<String> alwaysOnTools;

    /**
     * 已通过load_tool转正的工具名集合（线程安全）
     */
    private final Set<String> activatedTools = ConcurrentHashMap.newKeySet();

    /**
     * 全参构造
     * @param progressive 是否渐进加载模式
     * @param alwaysOnTools 常驻工具名集合，null按空集处理
     */
    public ToolLoadingState(boolean progressive, Set<String> alwaysOnTools) {
        this.progressive = progressive;
        this.alwaysOnTools = alwaysOnTools != null ? Set.copyOf(alwaysOnTools) : Set.of();
    }

    /**
     * 是否渐进加载模式
     * @return
     */
    public boolean isProgressive() {
        return progressive;
    }

    /**
     * 获取常驻工具名集合
     * @return
     */
    public Set<String> getAlwaysOnTools() {
        return alwaysOnTools;
    }

    /**
     * 判断工具是否常驻
     * @param toolName
     * @return
     */
    public boolean isAlwaysOn(String toolName) {
        return toolName != null && alwaysOnTools.contains(toolName);
    }

    /**
     * 判断工具是否已转正
     * @param toolName
     * @return
     */
    public boolean isActivated(String toolName) {
        return toolName != null && activatedTools.contains(toolName);
    }

    /**
     * 转正工具（幂等，重复激活无副作用）
     * @param toolName
     * @return 本次调用是否产生新转正
     */
    public boolean activate(String toolName) {
        return toolName != null && activatedTools.add(toolName);
    }

    /**
     * 判断工具schema是否应随请求下发（渐进模式裁剪判定）
     * @param toolName
     * @return
     */
    public boolean isSchemaVisible(String toolName) {
        return isAlwaysOn(toolName) || isActivated(toolName) || (toolName != null && META_TOOL_NAMES.contains(toolName));
    }
}
