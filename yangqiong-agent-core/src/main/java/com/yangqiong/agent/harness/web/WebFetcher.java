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
package com.yangqiong.agent.harness.web;

/**
 * 网页抓取提供方SPI
 * <p>
 * 外部平台实现此接口并提供真实网页抓取能力，引擎仅持有SPI引用。
 * 未配置时web_fetch工具返回错误提示。
 * </p>
 * @author yangqiong
 */
public interface WebFetcher {

    /**
     * 抓取网页内容
     * @param url 目标URL
     * @param maxChars 最大字符数
     * @return
     */
    String fetch(String url, int maxChars);
}