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

import java.util.Set;

import com.yangqiongai.agent.harness.core.middleware.AgentMiddleware;
import com.yangqiongai.agent.harness.core.tool.AgentTool;
import com.yangqiongai.agent.harness.engine.AgentRuntimeContext;

/**
 * 工具目录提示词注入器
 * <p>
 * Level 1 渐进加载：将延迟池工具的名称与一句话描述注入系统提示词，
 * 让 LLM 感知可用工具，再通过 load_tool 工具按需启用完整 schema。
 * </p>
 * @author yangqiong
 */
public class ToolCatalogPromptInjector implements AgentMiddleware {

    /**
     * 目录标题与使用说明
     */
    private static final String CATALOG_HEADER = "## 可用工具目录\n"
            + "以下工具未随本次请求下发完整定义，需要使用时先调用 load_tool 启用：";

    /**
     * 描述截断上限字符数
     */
    private static final int DESC_MAX_CHARS = 60;

    /**
     * 工具箱（实时读取，注册后新工具自动进目录）
     */
    private final HarnessToolkit toolkit;

    /**
     * 目录排除名单（常驻工具、黑名单工具与元工具）
     */
    private final Set<String> excludeTools;

    /**
     * 全参构造
     * @param toolkit 工具箱
     * @param excludeTools 不进目录的工具名集合
     */
    public ToolCatalogPromptInjector(HarnessToolkit toolkit, Set<String> excludeTools) {
        this.toolkit = toolkit;
        this.excludeTools = excludeTools != null ? Set.copyOf(excludeTools) : Set.of();
    }

    /**
     * 在系统提示词末尾追加工具目录
     * @param systemPrompt 原始系统提示词
     * @param context 运行时上下文
     * @return 追加工具目录后的系统提示词
     */
    @Override
    public String onSystemPrompt(String systemPrompt, AgentRuntimeContext context) {
        String catalog = buildCatalog();
        if (catalog == null) {
            return systemPrompt;
        }
        String base = systemPrompt == null ? "" : systemPrompt;
        if (!base.isEmpty() && !base.endsWith("\n")) {
            base = base + "\n\n";
        }
        return base + catalog;
    }

    /**
     * 生成工具目录文本，无延迟池工具时返回null
     * @return
     */
    private String buildCatalog() {
        if (toolkit == null || toolkit.isEmpty()) {
            return null;
        }
        StringBuilder sb = new StringBuilder();
        for (AgentTool tool : toolkit.getTools()) {
            if (tool == null || excludeTools.contains(tool.getName())) {
                continue;
            }
            if (sb.isEmpty()) {
                sb.append(CATALOG_HEADER);
            }
            sb.append("\n- ").append(tool.getName()).append(": ").append(firstSentence(tool.getDescription()));
        }
        return sb.isEmpty() ? null : sb.toString();
    }

    /**
     * 截取描述首句作为目录摘要
     * @param description
     * @return
     */
    private String firstSentence(String description) {
        if (description == null || description.isBlank()) {
            return "（无描述）";
        }
        String text = description.strip();
        int cut = text.length();
        for (char sep : new char[]{'。', '；', ';', '\n'}) {
            int idx = text.indexOf(sep);
            if (idx >= 0 && idx < cut) {
                cut = idx;
            }
        }
        String sentence = text.substring(0, cut).strip();
        if (sentence.length() > DESC_MAX_CHARS) {
            sentence = sentence.substring(0, DESC_MAX_CHARS) + "…";
        }
        return sentence;
    }
}
