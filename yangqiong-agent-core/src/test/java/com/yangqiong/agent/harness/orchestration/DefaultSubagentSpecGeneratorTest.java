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
package com.yangqiong.agent.harness.orchestration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.util.List;

import com.yangqiong.agent.harness.core.message.AgentTextBlock;
import com.yangqiong.agent.harness.core.model.AgentChatResponse;
import com.yangqiong.agent.harness.core.model.AgentModel;
import com.yangqiong.agent.harness.model.ModelCaller;
import com.yangqiong.agent.harness.subagent.orchestration.DefaultSubagentSpecGenerator;
import com.yangqiong.agent.harness.subagent.orchestration.SubagentDeclaration;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

/**
 * 默认子代理规格生成器测试
 * @author yangqiong
 */
class DefaultSubagentSpecGeneratorTest {

    /**
     * 模型调用失败时返回空列表，由编排引擎回退到预配置声明
     */
    @Test
    void 模型调用异常_降级返回空列表() {
        AgentModel model = Mockito.mock(AgentModel.class);
        when(model.generate(any(), any(), any())).thenThrow(new RuntimeException("模型服务不可用"));
        ModelCaller caller = new ModelCaller(model);
        DefaultSubagentSpecGenerator generator = new DefaultSubagentSpecGenerator(caller, "test-model");
        List<SubagentDeclaration> declarations = generator
                .generateDeclarations("复杂任务", 5, List.of(), "test-model").block();
        assertThat(declarations).isNotNull();
        assertThat(declarations).isEmpty();
    }

    /**
     * 空响应或非法JSON时返回空列表
     */
    @Test
    void 非法JSON_返回空列表() {
        AgentModel model = Mockito.mock(AgentModel.class);
        AgentTextBlock block = AgentTextBlock.builder().text("这不是JSON").build();
        when(model.generate(any(), any(), any())).thenReturn(new AgentChatResponse(List.of(block), null));
        ModelCaller caller = new ModelCaller(model);
        DefaultSubagentSpecGenerator generator = new DefaultSubagentSpecGenerator(caller, "test-model");
        List<SubagentDeclaration> declarations = generator
                .generateDeclarations("复杂任务", 5, List.of(), "test-model").block();
        assertThat(declarations).isNotNull();
        assertThat(declarations).isEmpty();
    }

    /**
     * 有效JSON解析生成子代理声明，name/description/systemPrompt/modelCode字段正确填充
     */
    @Test
    void 有效JSON_解析生成子代理声明() {
        String json = "[{\"name\":\"researcher\",\"description\":\"资料调研\","
                + "\"systemPrompt\":\"负责收集与分析资料\"},"
                + "{\"name\":\"writer\",\"description\":\"撰写报告\","
                + "\"systemPrompt\":\"负责整合输出最终报告\"}]";
        AgentModel model = Mockito.mock(AgentModel.class);
        AgentTextBlock block = AgentTextBlock.builder().text(json).build();
        when(model.generate(any(), any(), any())).thenReturn(new AgentChatResponse(List.of(block), null));
        ModelCaller caller = new ModelCaller(model);
        DefaultSubagentSpecGenerator generator = new DefaultSubagentSpecGenerator(caller, "test-model");
        List<SubagentDeclaration> declarations = generator
                .generateDeclarations("复杂任务", 5, List.of(), "test-model").block();
        assertThat(declarations).hasSize(2);
        assertThat(declarations.get(0).getName()).isEqualTo("researcher");
        assertThat(declarations.get(0).getDescription()).isEqualTo("资料调研");
        assertThat(declarations.get(0).getSystemPrompt()).isEqualTo("负责收集与分析资料");
        assertThat(declarations.get(0).getModelCode()).isEqualTo("test-model");
        assertThat(declarations.get(1).getName()).isEqualTo("writer");
    }

    /**
     * 模型返回带代码块标记的JSON时剥离后仍能解析
     */
    @Test
    void 代码块包裹JSON_剥离后解析成功() {
        String json = "```json\n[{\"name\":\"coder\",\"description\":\"编码\",\"systemPrompt\":\"负责编码实现\"}]\n```";
        AgentModel model = Mockito.mock(AgentModel.class);
        AgentTextBlock block = AgentTextBlock.builder().text(json).build();
        when(model.generate(any(), any(), any())).thenReturn(new AgentChatResponse(List.of(block), null));
        ModelCaller caller = new ModelCaller(model);
        DefaultSubagentSpecGenerator generator = new DefaultSubagentSpecGenerator(caller, null);
        List<SubagentDeclaration> declarations = generator
                .generateDeclarations("复杂任务", 5, List.of(), null).block();
        assertThat(declarations).hasSize(1);
        assertThat(declarations.get(0).getName()).isEqualTo("coder");
    }

    /**
     * 默认实现默认启用声明动态生成
     */
    @Test
    void 默认启用声明动态生成() {
        AgentModel model = Mockito.mock(AgentModel.class);
        ModelCaller caller = new ModelCaller(model);
        DefaultSubagentSpecGenerator generator = new DefaultSubagentSpecGenerator(caller, null);
        assertThat(generator.isDeclarationGenerationEnabled()).isTrue();
    }

    /**
     * 模型返回带前后说明文字的JSON时，截取数组片段后仍能解析
     */
    @Test
    void 带前后说明文字_截取数组后解析成功() {
        String raw = "好的，我已拆分如下：\n"
                + "[{\"name\":\"neon\",\"description\":\"暗夜霓虹主题登录页\","
                + "\"systemPrompt\":\"生成深色背景紫蓝渐变登录页\"}]\n"
                + "以上是两个子代理的声明。";
        AgentModel model = Mockito.mock(AgentModel.class);
        AgentTextBlock block = AgentTextBlock.builder().text(raw).build();
        when(model.generate(any(), any(), any())).thenReturn(new AgentChatResponse(List.of(block), null));
        ModelCaller caller = new ModelCaller(model);
        DefaultSubagentSpecGenerator generator = new DefaultSubagentSpecGenerator(caller, null);
        List<SubagentDeclaration> declarations = generator
                .generateDeclarations("复杂任务", 5, List.of(), null).block();
        assertThat(declarations).hasSize(1);
        assertThat(declarations.get(0).getName()).isEqualTo("neon");
    }

    /**
     * 字符串值内误用未转义英文双引号（如"暗夜霓虹"）时，宽松修复后仍能解析
     */
    @Test
    void 字符串值内未转义引号_宽松修复解析成功() {
        String raw = "[{\"name\":\"neon\",\"description\":\"为\"暗夜霓虹\"配色主题\","
                + "\"systemPrompt\":\"生成深色背景紫蓝渐变登录页\"}]";
        AgentModel model = Mockito.mock(AgentModel.class);
        AgentTextBlock block = AgentTextBlock.builder().text(raw).build();
        when(model.generate(any(), any(), any())).thenReturn(new AgentChatResponse(List.of(block), null));
        ModelCaller caller = new ModelCaller(model);
        DefaultSubagentSpecGenerator generator = new DefaultSubagentSpecGenerator(caller, null);
        List<SubagentDeclaration> declarations = generator
                .generateDeclarations("复杂任务", 5, List.of(), null).block();
        assertThat(declarations).hasSize(1);
        assertThat(declarations.get(0).getDescription()).isEqualTo("为\"暗夜霓虹\"配色主题");
    }
}
