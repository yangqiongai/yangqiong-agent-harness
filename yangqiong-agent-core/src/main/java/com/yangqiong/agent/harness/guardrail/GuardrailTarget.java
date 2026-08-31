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
package com.yangqiong.agent.harness.guardrail;

/**
 * 护栏作用入口
 * <p>
 * 标识护栏规则生效的内容入口，同一规则可作用于多个入口。
 * </p>
 * @author yangqiong
 */
public enum GuardrailTarget {

    /**
     * 用户输入
     */
    INPUT,

    /**
     * 工具调用参数
     */
    TOOL_PARAM,

    /**
     * 工具执行结果
     */
    TOOL_RESULT,

    /**
     * 检索注入内容（长期记忆/知识库等）
     */
    RETRIEVAL
}
