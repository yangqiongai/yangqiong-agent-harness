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
package com.yangqiong.agent.harness.model;

import static org.assertj.core.api.Assertions.assertThat;

import com.yangqiong.agent.harness.config.AgentResponseFormat;
import com.yangqiong.agent.harness.config.AgentToolChoice;
import com.yangqiong.agent.harness.core.model.AgentGenerateOptions;
import org.junit.jupiter.api.Test;

/**
 * 生成选项构建器测试
 * @author yangqiong
 */
class GenerateOptionsBuilderTest {

    @Test
    void shouldBuildWithTemperature() {
        AgentGenerateOptions options = new GenerateOptionsBuilder()
                .temperature(0.7)
                .build();
        assertThat(options.getTemperature()).isEqualTo(0.7);
    }

    @Test
    void shouldBuildWithMaxTokens() {
        AgentGenerateOptions options = new GenerateOptionsBuilder()
                .maxTokens(1000)
                .build();
        assertThat(options.getMaxTokens()).isEqualTo(1000);
    }

    @Test
    void shouldBuildWithTopP() {
        AgentGenerateOptions options = new GenerateOptionsBuilder()
                .topP(0.9)
                .build();
        assertThat(options.getTopP()).isEqualTo(0.9);
    }

    @Test
    void shouldStoreToolChoice() {
        AgentGenerateOptions options = new GenerateOptionsBuilder()
                .toolChoice(AgentToolChoice.AUTO)
                .build();
        assertThat(options.getToolChoice()).isEqualTo(AgentToolChoice.AUTO);
    }

    @Test
    void shouldStoreResponseFormat() {
        AgentResponseFormat format = new AgentResponseFormat("text", null);
        AgentGenerateOptions options = new GenerateOptionsBuilder()
                .responseFormat(format)
                .build();
        assertThat(options.getResponseFormat()).isEqualTo(format);
    }

    @Test
    void shouldMergeOptions() {
        AgentGenerateOptions base = AgentGenerateOptions.builder()
                .temperature(0.5)
                .maxTokens(500)
                .build();
        AgentGenerateOptions override = AgentGenerateOptions.builder()
                .temperature(0.9)
                .build();
        AgentGenerateOptions merged = GenerateOptionsBuilder.merge(base, override);
        assertThat(merged.getTemperature()).isEqualTo(0.9);
        assertThat(merged.getMaxTokens()).isEqualTo(500);
    }

    @Test
    void shouldBuildWithNullValues() {
        AgentGenerateOptions options = new GenerateOptionsBuilder().build();
        assertThat(options.getTemperature()).isNull();
        assertThat(options.getMaxTokens()).isNull();
    }

    @Test
    void shouldChainMethods() {
        GenerateOptionsBuilder builder = new GenerateOptionsBuilder()
                .temperature(0.5)
                .maxTokens(100)
                .topP(0.8);
        AgentGenerateOptions options = builder.build();
        assertThat(options.getTemperature()).isEqualTo(0.5);
        assertThat(options.getMaxTokens()).isEqualTo(100);
        assertThat(options.getTopP()).isEqualTo(0.8);
    }
}
