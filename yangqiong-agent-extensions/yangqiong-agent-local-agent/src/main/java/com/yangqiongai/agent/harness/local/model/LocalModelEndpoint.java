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
package com.yangqiongai.agent.harness.local.model;

import java.util.List;

/**
 * 本地模型端点
 * @author yangqiong
 */
public record LocalModelEndpoint(

        /**
         * 端点类型
         */
        LocalModelEndpointType type,

        /**
         * 服务基础地址，不含尾部斜杠
         */
        String baseUrl,

        /**
         * 端点上可用的模型名称列表
         */
        List<String> models) {

    /**
     * 紧凑构造器，保证模型列表不可变
     */
    public LocalModelEndpoint {
        models = models == null ? List.of() : List.copyOf(models);
    }
}
