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
package com.yangqiongai.agent.harness.paradigms.support;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * 计划JSON解析器测试
 * @author yangqiong
 */
class PlanParserTest {

    @Test
    void shouldParseBareJsonArray() {
        String raw = "[{\"type\":\"tool\",\"tool\":\"get_weather\",\"args\":{\"city\":\"北京\",\"days\":3},\"desc\":\"查询北京天气\"}]";
        List<PlanStep> steps = PlanParser.parse(raw);
        assertThat(steps).hasSize(1);
        PlanStep step = steps.get(0);
        assertThat(step.getStepId()).isEqualTo(1);
        assertThat(step.getType()).isEqualTo("tool");
        assertThat(step.getToolName()).isEqualTo("get_weather");
        assertThat(step.getArguments()).containsEntry("city", "北京").containsEntry("days", 3);
        assertThat(step.getDescription()).isEqualTo("查询北京天气");
    }

    @Test
    void shouldParseMarkdownFenceWithJsonLanguage() {
        String raw = "```json\n[{\"type\":\"reason\",\"desc\":\"分析问题\"},{\"type\":\"tool\",\"tool\":\"search\",\"args\":{\"q\":\"AI\"}}]\n```";
        List<PlanStep> steps = PlanParser.parse(raw);
        assertThat(steps).hasSize(2);
        assertThat(steps.get(0).getType()).isEqualTo("reason");
        assertThat(steps.get(1).getType()).isEqualTo("tool");
        assertThat(steps.get(1).getToolName()).isEqualTo("search");
    }

    @Test
    void shouldParsePlainFenceWithoutLanguage() {
        String raw = "```\n[{\"type\":\"reason\",\"desc\":\"直接推理\"}]\n```";
        List<PlanStep> steps = PlanParser.parse(raw);
        assertThat(steps).hasSize(1);
        assertThat(steps.get(0).getType()).isEqualTo("reason");
        assertThat(steps.get(0).getDescription()).isEqualTo("直接推理");
    }

    @Test
    void shouldParseWithPrefixAndSuffixNoise() {
        String raw = "好的，以下是执行计划：\n[{\"type\":\"tool\",\"tool\":\"calc\",\"args\":{\"expr\":\"1+1\"}}]\n以上即为全部步骤，请执行。";
        List<PlanStep> steps = PlanParser.parse(raw);
        assertThat(steps).hasSize(1);
        assertThat(steps.get(0).getToolName()).isEqualTo("calc");
        assertThat(steps.get(0).getArguments()).containsEntry("expr", "1+1");
    }

    @Test
    void shouldParseWhenFenceSurroundedByNoise() {
        String raw = "计划如下：\n```json\n[{\"type\":\"reason\",\"desc\":\"先思考\"}]\n```\n请按上述计划执行。";
        List<PlanStep> steps = PlanParser.parse(raw);
        assertThat(steps).hasSize(1);
        assertThat(steps.get(0).getDescription()).isEqualTo("先思考");
    }

    @Test
    void shouldParseBracketsInsideStringLiterals() {
        String raw = "[{\"type\":\"reason\",\"desc\":\"处理数组 a[0] 与对象 {b:1} 以及 } 与 ] 字符\"},"
                + "{\"type\":\"tool\",\"tool\":\"calc\",\"args\":{\"expr\":\"(1+2)*3\",\"note\":\"含\\\"引号\\\"文本\"}}]";
        List<PlanStep> steps = PlanParser.parse(raw);
        assertThat(steps).hasSize(2);
        assertThat(steps.get(0).getDescription()).isEqualTo("处理数组 a[0] 与对象 {b:1} 以及 } 与 ] 字符");
        assertThat(steps.get(1).getArguments()).containsEntry("expr", "(1+2)*3");
        assertThat(steps.get(1).getArguments()).containsEntry("note", "含\"引号\"文本");
    }

    @Test
    void shouldParseNestedFences() {
        String raw = "```\n```json\n[{\"type\":\"reason\",\"desc\":\"嵌套围栏\"}]\n```\n```";
        List<PlanStep> steps = PlanParser.parse(raw);
        assertThat(steps).hasSize(1);
        assertThat(steps.get(0).getDescription()).isEqualTo("嵌套围栏");
    }

    @Test
    void shouldParseReasonStep() {
        String raw = "[{\"type\":\"reason\",\"desc\":\"分析用户意图\"}]";
        List<PlanStep> steps = PlanParser.parse(raw);
        assertThat(steps).hasSize(1);
        PlanStep step = steps.get(0);
        assertThat(step.getType()).isEqualTo("reason");
        assertThat(step.getToolName()).isNull();
        assertThat(step.getArguments()).isNull();
        assertThat(step.getDescription()).isEqualTo("分析用户意图");
    }

    @Test
    void shouldParseToolFieldAliases() {
        String raw = "[{\"type\":\"tool\",\"toolName\":\"web_search\",\"arguments\":{\"query\":\"范式\"},\"desc\":\"检索\"}]";
        List<PlanStep> steps = PlanParser.parse(raw);
        assertThat(steps).hasSize(1);
        PlanStep step = steps.get(0);
        assertThat(step.getToolName()).isEqualTo("web_search");
        assertThat(step.getArguments()).containsEntry("query", "范式");
    }

    @Test
    void shouldParseToolStepWithoutArguments() {
        String raw = "[{\"type\":\"tool\",\"tool\":\"now\",\"desc\":\"取当前时间\"}]";
        List<PlanStep> steps = PlanParser.parse(raw);
        assertThat(steps).hasSize(1);
        assertThat(steps.get(0).getArguments()).isNotNull().isEmpty();
    }

    @Test
    void shouldAssignSequentialStepIds() {
        String raw = "[{\"type\":\"reason\",\"desc\":\"a\"},{\"type\":\"tool\",\"tool\":\"t\",\"args\":{},\"desc\":\"b\"},{\"type\":\"reason\",\"desc\":\"c\"}]";
        List<PlanStep> steps = PlanParser.parse(raw);
        assertThat(steps).extracting(PlanStep::getStepId).containsExactly(1, 2, 3);
        assertThat(steps).extracting(PlanStep::getType).containsExactly("reason", "tool", "reason");
    }

    @Test
    void shouldParseSingleObjectPlan() {
        String raw = "{\"type\":\"reason\",\"description\":\"单步推理\"}";
        List<PlanStep> steps = PlanParser.parse(raw);
        assertThat(steps).hasSize(1);
        assertThat(steps.get(0).getStepId()).isEqualTo(1);
        assertThat(steps.get(0).getDescription()).isEqualTo("单步推理");
    }

    @Test
    void shouldReturnNullWhenNoJsonPresent() {
        assertThat(PlanParser.parse("这不是JSON文本，只是普通说明。")).isNull();
    }

    @Test
    void shouldReturnNullWhenJsonMalformed() {
        assertThat(PlanParser.parse("[{\"type\":\"tool\",\"tool\":\"x\"")).isNull();
        assertThat(PlanParser.parse("随机文字 [[[")).isNull();
    }

    @Test
    void shouldReturnNullWhenInputNullOrBlank() {
        assertThat(PlanParser.parse(null)).isNull();
        assertThat(PlanParser.parse("")).isNull();
        assertThat(PlanParser.parse("   ")).isNull();
    }

    @Test
    void shouldReturnNullForEmptyArray() {
        assertThat(PlanParser.parse("[]")).isNull();
        assertThat(PlanParser.parse("```json\n[]\n```")).isNull();
    }
}
