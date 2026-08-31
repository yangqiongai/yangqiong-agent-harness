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
package com.yangqiong.agent.harness.subagent.orchestration;

import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Handoff目标注册表
 * @author yangqiong
 */
public class HandoffRegistry {

    /**
     * 日志
     */
    private static final Logger log = LoggerFactory.getLogger(HandoffRegistry.class);

    /**
     * 最大目标数量
     */
    public static final int MAX_TARGETS = 64;

    /**
     * 命名目标表
     */
    private final Map<String, HandoffTarget> targets = new ConcurrentHashMap<>();

    /**
     * 注册目标
     * @param target
     * @return
     */
    public boolean register(HandoffTarget target) {
        if (target == null || target.runtime() == null || target.runtime().getName() == null) {
            return false;
        }
        return register(target.runtime().getName(), target);
    }

    /**
     * 按显式名称注册目标，注册名可与运行时名称不同（作为handoff的target参数）
     * @param name
     * @param target
     * @return
     */
    public boolean register(String name, HandoffTarget target) {
        if (name == null || name.isBlank() || target == null || target.runtime() == null) {
            return false;
        }
        HandoffTarget existing = targets.get(name);
        if (existing != null) {
            // 同名覆盖告警
            log.warn("Handoff目标同名覆盖: name={}", name);
        } else if (targets.size() >= MAX_TARGETS) {
            log.warn("Handoff目标数量已达上限: max={}", MAX_TARGETS);
            return false;
        }
        targets.put(name, target);
        return true;
    }

    /**
     * 获取目标
     * @param name
     * @return
     */
    public Optional<HandoffTarget> get(String name) {
        return Optional.ofNullable(targets.get(name));
    }

    /**
     * 移除目标
     * @param name
     * @return
     */
    public HandoffTarget remove(String name) {
        return targets.remove(name);
    }

    /**
     * 获取全部目标名称
     * @return
     */
    public Set<String> names() {
        return targets.keySet();
    }
}
