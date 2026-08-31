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
package com.yangqiong.agent.harness.core.skill;

import java.util.Collections;
import java.util.Map;

/**
 * Agent技能定义
 * @author yangqiong
 */
public final class AgentSkill {

    /**
     * 技能名称
     */
    private final String name;

    /**
     * 技能描述
     */
    private final String description;

    /**
     * 技能来源
     * <p>已知值：</p>
     * <ul>
     *   <li>BUILTIN：框架内置技能</li>
     *   <li>TRUSTED：受信任来源技能</li>
     *   <li>COMMUNITY：社区贡献技能</li>
     *   <li>AGENT_CREATED：Agent 运行时动态创建的技能</li>
     * </ul>
     */
    private final String source;

    /**
     * 技能内容
     */
    private final String skillContent;

    /**
     * 技能资源
     */
    private final Map<String, String> resources;

    /**
     * 构造技能（含描述）
     * @param name
     * @param source
     * @param description
     * @param skillContent
     * @param resources
     */
    public AgentSkill(String name, String source, String description, String skillContent, Map<String, String> resources) {
        this.name = name;
        this.source = source;
        this.description = description != null ? description : "";
        this.skillContent = skillContent;
        this.resources = resources != null ? Map.copyOf(resources) : Collections.emptyMap();
    }

    /**
     * 构造技能（无描述，兼容旧调用方）
     * @param name
     * @param source
     * @param skillContent
     * @param resources
     */
    public AgentSkill(String name, String source, String skillContent, Map<String, String> resources) {
        this(name, source, "", skillContent, resources);
    }

    /**
     * 获取技能名称
     * @return
     */
    public String getName() {
        return name;
    }

    /**
     * 获取技能描述
     * @return
     */
    public String getDescription() {
        return description;
    }

    /**
     * 获取技能来源
     * @return
     */
    public String getSource() {
        return source;
    }

    /**
     * 获取技能内容
     * @return
     */
    public String getSkillContent() {
        return skillContent;
    }

    /**
     * 获取技能资源
     * @return
     */
    public Map<String, String> getResources() {
        return resources;
    }
}
