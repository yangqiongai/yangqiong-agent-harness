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

import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

import java.util.List;
import java.util.Set;

import com.yangqiong.agent.harness.engine.AgentLoop;

/**
 * 子代理声明
 * @author yangqiong
 */
@Data
@Builder
public class SubagentDeclaration {

    /**
     * 子代理名称
     */
    private String name;

    /**
     * 子代理描述
     */
    private String description;

    /**
     * Agent编码，默认使用default处理器
     */
    @Builder.Default
    private String agentCode = "default";

    /**
     * 执行范式循环，非空时替换子代理默认
     */
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private AgentLoop agentLoop;

    /**
     * 模型编码，可选，为空时走默认模型
     */
    private String modelCode;

    /**
     * 系统提示词，定义子代理的专业能力和行为规范
     */
    private String systemPrompt;

    /**
     * 工具列表（工具名称），仅作生成提示参考，不参与运行时筛选
     */
    private List<String> tools;

    /**
     * 温度参数
     */
    private Double temperature;

    /**
     * 最大迭代次数
     */
    private Integer maxIterations;

    /**
     * 是否继承主代理工具箱，默认true
     */
    @Builder.Default
    private Boolean inheritTools = Boolean.TRUE;

    /**
     * 工具白名单，非空时子代理仅可用这些工具
     */
    private Set<String> allowedTools;

    /**
     * 工具黑名单，这些工具对子代理不可用
     */
    private Set<String> deniedTools;

    /**
     * 是否继承主代理技能箱，默认true
     */
    @Builder.Default
    private Boolean inheritSkills = Boolean.TRUE;

    /**
     * 是否继承主代理MCP工具，默认true
     */
    @Builder.Default
    private Boolean inheritMcp = Boolean.TRUE;

    /**
     * 是否继承主代理中间件链，默认true
     */
    @Builder.Default
    private Boolean inheritMiddlewares = Boolean.TRUE;
}