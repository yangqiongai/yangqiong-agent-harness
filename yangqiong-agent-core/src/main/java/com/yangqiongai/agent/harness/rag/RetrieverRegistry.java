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
package com.yangqiongai.agent.harness.rag;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 检索器注册表
 * <p>
 * 按名称注册/获取/移除检索器，支持有界容量（MAX_RETRIEVERS=64），
 * 超限时拒绝新注册并抛出 IllegalStateException。
 * </p>
 * @author yangqiong
 */
public class RetrieverRegistry {

    /**
     * 最大检索器数量
     */
    public static final int MAX_RETRIEVERS = 64;

    /**
     * 检索器名称->实例映射
     */
    private final Map<String, Retriever> registry = new ConcurrentHashMap<>();

    /**
     * 注册检索器
     * @param name
     * @param retriever
     * @throws IllegalStateException 注册表已满时
     * @throws IllegalArgumentException name或retriever为null
     */
    public void register(String name, Retriever retriever) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("检索器名称不能为空");
        }
        if (retriever == null) {
            throw new IllegalArgumentException("检索器实例不能为空");
        }
        if (registry.size() >= MAX_RETRIEVERS && !registry.containsKey(name)) {
            throw new IllegalStateException("检索器注册表已满，最大容量为 " + MAX_RETRIEVERS);
        }
        registry.put(name, retriever);
    }

    /**
     * 获取检索器
     * @param name
     * @return 未找到时返回 null
     */
    public Retriever get(String name) {
        if (name == null) {
            return null;
        }
        return registry.get(name);
    }

    /**
     * 移除检索器
     * @param name
     * @return 被移除的检索器，未找到时返回 null
     */
    public Retriever remove(String name) {
        if (name == null) {
            return null;
        }
        return registry.remove(name);
    }

    /**
     * 获取所有已注册检索器名称
     * @return
     */
    public Set<String> names() {
        return registry.keySet();
    }

    /**
     * 获取当前注册数量
     * @return
     */
    public int size() {
        return registry.size();
    }
}