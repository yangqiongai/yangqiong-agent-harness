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

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.yangqiong.agent.harness.eval.EvalCase;
import com.yangqiong.agent.harness.eval.EvalDataset;

/**
 * YAML 评测用例集加载
 * @author yangqiong
 */
public final class YamlEvalDatasetLoader {

    /**
     * YAML 解析器
     */
    private final ObjectMapper mapper = new ObjectMapper(new YAMLFactory());

    /**
     * 从类路径资源加载数据集
     * @param resource
     * @return
     */
    public EvalDataset loadFromClasspath(String resource) {
        ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
        if (classLoader == null) {
            classLoader = YamlEvalDatasetLoader.class.getClassLoader();
        }
        InputStream in = classLoader.getResourceAsStream(resource);
        if (in == null) {
            throw new IllegalArgumentException("类路径下不存在评测数据集资源: " + resource);
        }
        try (InputStream stream = in) {
            return parse(stream, resource);
        } catch (IOException e) {
            throw new UncheckedIOException("读取评测数据集资源失败: " + resource, e);
        }
    }

    /**
     * 从文件系统加载数据集
     * @param file
     * @return
     */
    public EvalDataset load(Path file) {
        try (InputStream in = Files.newInputStream(file)) {
            return parse(in, file.toString());
        } catch (IOException e) {
            throw new UncheckedIOException("读取评测数据集文件失败: " + file, e);
        }
    }

    /**
     * 解析 YAML 流为评测数据集
     * @param in
     * @param source
     * @return
     * @throws IOException
     */
    private EvalDataset parse(InputStream in, String source) throws IOException {
        JsonNode root = mapper.readTree(in);
        if (root == null || !root.isObject()) {
            throw new IllegalArgumentException("评测数据集根节点必须为对象: " + source);
        }
        String name = root.path("name").asText("");
        if (name.isBlank()) {
            throw new IllegalArgumentException("评测数据集缺少name字段: " + source);
        }
        JsonNode casesNode = root.path("cases");
        if (!casesNode.isArray() || casesNode.isEmpty()) {
            throw new IllegalArgumentException("评测数据集cases不能为空: " + source);
        }
        List<EvalCase> cases = new ArrayList<>();
        for (JsonNode caseNode : casesNode) {
            cases.add(parseCase(caseNode, source));
        }
        return EvalDataset.of(name, cases.toArray(new EvalCase[0]));
    }

    /**
     * 解析单个评测用例节点
     * @param caseNode
     * @param source
     * @return
     */
    private EvalCase parseCase(JsonNode caseNode, String source) {
        String id = caseNode.path("id").asText("");
        String query = caseNode.path("query").asText("");
        if (id.isBlank()) {
            throw new IllegalArgumentException("评测用例缺少id字段: " + source);
        }
        if (query.isBlank()) {
            throw new IllegalArgumentException("评测用例[" + id + "]缺少query字段: " + source);
        }
        EvalCase.Builder builder = EvalCase.builder().id(id).query(query);
        List<String> toolNames = readStringList(caseNode.path("expectedToolNames"));
        List<String> keywords = readStringList(caseNode.path("expectedKeywords"));
        List<String> forbiddenToolNames = readStringList(caseNode.path("forbiddenToolNames"));
        if (!toolNames.isEmpty()) {
            builder.expectedToolNames(toolNames);
        }
        if (!keywords.isEmpty()) {
            builder.expectedKeywords(keywords);
        }
        if (!forbiddenToolNames.isEmpty()) {
            builder.forbiddenToolNames(forbiddenToolNames);
        }
        JsonNode toolArgsNode = caseNode.path("expectedToolArgs");
        if (toolArgsNode.isObject() && !toolArgsNode.isEmpty()) {
            builder.expectedToolArgs(parseToolArgs(toolArgsNode));
        }
        JsonNode metadataNode = caseNode.path("metadata");
        if (metadataNode.isObject()) {
            Map<String, Object> metadata = mapper.convertValue(metadataNode,
                    new TypeReference<Map<String, Object>>() {
                    });
            if (!metadata.isEmpty()) {
                builder.metadata(metadata);
            }
        }
        return builder.build();
    }

    /**
     * 解析期望工具调用参数节点（工具名到参数键值映射）
     * @param toolArgsNode
     * @return
     */
    private Map<String, Map<String, Object>> parseToolArgs(JsonNode toolArgsNode) {
        Map<String, Map<String, Object>> toolArgs = new LinkedHashMap<>();
        Iterator<Map.Entry<String, JsonNode>> fields = toolArgsNode.fields();
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> entry = fields.next();
            toolArgs.put(entry.getKey(),
                    mapper.convertValue(entry.getValue(), new TypeReference<Map<String, Object>>() {
                    }));
        }
        return toolArgs;
    }

    /**
     * 读取字符串数组字段
     * @param node
     * @return
     */
    private List<String> readStringList(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return List.of();
        }
        if (!node.isArray()) {
            throw new IllegalArgumentException("该字段必须为字符串数组: " + node);
        }
        List<String> values = new ArrayList<>();
        for (JsonNode item : node) {
            values.add(item.asText());
        }
        return values;
    }
}
