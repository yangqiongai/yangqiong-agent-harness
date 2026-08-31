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
package com.yangqiong.agent.harness.spi;

import com.yangqiong.agent.harness.HarnessRuntimeBuilder;

/**
 * 运行时定制器
 * <p>
 * 在{@code HarnessRuntimeBuilder.build()}装配前回调，扩展模块可借此向builder注入
 * 配置/工具/中间件等，无需修改builder本身。通过JDK SPI（META-INF/services）或
 * {@code builder.addCustomizer(...)}注册。
 * </p>
 * @author yangqiong
 */
public interface RuntimeCustomizer {

    /**
     * 定制运行时builder
     * @param builder
     */
    void customize(HarnessRuntimeBuilder builder);

    /**
     * 排序权重（越小越先执行），默认0
     * @return
     */
    default int order() {
        return 0;
    }
}
