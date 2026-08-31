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
package com.yangqiong.agent.harness.eval;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import com.yangqiong.agent.harness.eval.EvalDataset;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * YAML 评测用例集加载测试
 * @author yangqiong
 */
class YamlEvalDatasetLoaderTest {

    private final YamlEvalDatasetLoader loader = new YamlEvalDatasetLoader();

    @TempDir
    Path tempDir;

    @Test
    void shouldLoadDatasetFromClasspath() {
        EvalDataset dataset = loader.loadFromClasspath("eval/smoke-suite.yaml");

        assertThat(dataset.getName()).isEqualTo("smoke-suite");
        assertThat(dataset.getCases()).hasSize(3);

        var weatherCase = dataset.getCases().get(0);
        assertThat(weatherCase.getId()).isEqualTo("weather-tool-call");
        assertThat(weatherCase.getQuery()).contains("北京");
        assertThat(weatherCase.getExpectedToolNames()).containsExactly("search");
        assertThat(weatherCase.getExpectedKeywords()).containsExactly("天气");
        assertThat(weatherCase.getMetadata()).containsEntry("scene", "tool-selection");

        var multiToolCase = dataset.getCases().get(2);
        assertThat(multiToolCase.getExpectedToolNames()).containsExactly("search", "calendar");
        assertThat(multiToolCase.getMetadata()).containsEntry("level", "composite");
    }

    @Test
    void shouldLoadMinimalDatasetFromFile() throws IOException {
        Path file = tempDir.resolve("minimal.yaml");
        Files.writeString(file, String.join("\n",
                "name: mini",
                "cases:",
                "  - id: c1",
                "    query: 你好"));

        EvalDataset dataset = loader.load(file);

        assertThat(dataset.getName()).isEqualTo("mini");
        assertThat(dataset.getCases()).hasSize(1);
        assertThat(dataset.getCases().get(0).getId()).isEqualTo("c1");
        assertThat(dataset.getCases().get(0).getExpectedToolNames()).isEmpty();
        assertThat(dataset.getCases().get(0).getMetadata()).isEmpty();
    }

    @Test
    void shouldRejectMissingName() throws IOException {
        Path file = tempDir.resolve("no-name.yaml");
        Files.writeString(file, "cases:\n  - id: c1\n    query: 你好\n");

        assertThatThrownBy(() -> loader.load(file))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("name");
    }

    @Test
    void shouldRejectEmptyCases() throws IOException {
        Path file = tempDir.resolve("no-cases.yaml");
        Files.writeString(file, "name: empty\n");

        assertThatThrownBy(() -> loader.load(file))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("cases");
    }

    @Test
    void shouldRejectMissingQuery() throws IOException {
        Path file = tempDir.resolve("no-query.yaml");
        Files.writeString(file, "name: bad\ncases:\n  - id: c1\n");

        assertThatThrownBy(() -> loader.load(file))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("query");
    }

    @Test
    void shouldRejectMissingClasspathResource() {
        assertThatThrownBy(() -> loader.loadFromClasspath("eval/not-exist.yaml"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("不存在");
    }

    @Test
    void shouldLoadExpectedToolArgsAndForbiddenTools() throws IOException {
        Path file = tempDir.resolve("args.yaml");
        Files.writeString(file, String.join("\n",
                "name: args-suite",
                "cases:",
                "  - id: weather-args",
                "    query: 北京天气怎么样",
                "    expectedToolNames:",
                "      - get_weather",
                "    expectedToolArgs:",
                "      get_weather:",
                "        city: 北京",
                "  - id: math-no-tool",
                "    query: 1+1等于几",
                "    forbiddenToolNames:",
                "      - get_weather",
                "      - calculator"));

        EvalDataset dataset = loader.load(file);
        var argsCase = dataset.getCases().get(0);
        assertThat(argsCase.getExpectedToolArgs())
                .containsOnlyKeys("get_weather")
                .hasEntrySatisfying("get_weather", args -> assertThat(args).containsEntry("city", "北京"));

        var forbiddenCase = dataset.getCases().get(1);
        assertThat(forbiddenCase.getForbiddenToolNames()).containsExactly("get_weather", "calculator");
        assertThat(forbiddenCase.getExpectedToolNames()).isEmpty();
    }

    @Test
    void shouldKeepMetadataTypesFromYaml() throws IOException {
        Path file = tempDir.resolve("metadata.yaml");
        Files.writeString(file, String.join("\n",
                "name: meta-check",
                "cases:",
                "  - id: c1",
                "    query: 你好",
                "    metadata:",
                "      weight: 2",
                "      tags:",
                "        - base"));

        EvalDataset dataset = loader.load(file);
        Map<String, Object> metadata = dataset.getCases().get(0).getMetadata();

        assertThat(metadata).containsEntry("weight", 2);
        assertThat(metadata.get("tags")).isEqualTo(List.of("base"));
    }
}
