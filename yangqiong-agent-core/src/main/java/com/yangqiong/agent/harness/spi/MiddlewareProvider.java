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

import java.util.List;

import com.yangqiong.agent.harness.core.middleware.AgentMiddleware;

/**
 * 中间件插件提供者
 * <p>
 * 通过JDK SPI（META-INF/services）发现，{@code HarnessRuntimeBuilder.autoDiscover()}
 * 启用后自动注册提供的中间件进入中间件链。
 * </p>
 * @author yangqiong
 */
public interface MiddlewareProvider {

    /**
     * 提供需要自动注册的中间件
     * @return
     */
    List<AgentMiddleware> provideMiddlewares();

    /**
     * 排序权重（越小越靠前），默认0
     * @return
     */
    default int order() {
        return 0;
    }
}