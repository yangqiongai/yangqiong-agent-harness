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
package com.yangqiongai.agent.harness.web;

import java.util.List;

/**
 * Web检索提供方SPI
 * <p>
 * 外部平台实现此接口并提供真实搜索引擎调用，引擎仅持有SPI引用。
 * 未配置时web_search工具返回错误提示。
 * </p>
 * @author yangqiong
 */
public interface WebSearchProvider {

    /**
     * 搜索查询
     * @param query 搜索查询文本
     * @param topK 返回结果条数上限
     * @return
     */
    List<WebSearchResult> search(String query, int topK);
}