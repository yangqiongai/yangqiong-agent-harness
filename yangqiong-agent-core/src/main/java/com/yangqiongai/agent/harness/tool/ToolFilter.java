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

import java.util.List;
import java.util.Map;

/**
 * 工具筛选器
 * <p>
 * 根据用户查询，从全量工具Schema中筛选出与当前查询相关的工具，
 * 减少传递给LLM的无关工具Schema，降低Token消耗、提升工具调用准确率。
 * </p>
 * @author yangqiong
 */
@FunctionalInterface
public interface ToolFilter {

    /**
     * 根据用户查询筛选工具Schema
     * @param toolSchemas 全量工具Schema列表
     * @param userQuery 用户当前查询
     * @return 筛选后的工具Schema列表，至少保留5个
     */
    List<Map<String, Object>> filter(List<Map<String, Object>> toolSchemas, String userQuery);
}