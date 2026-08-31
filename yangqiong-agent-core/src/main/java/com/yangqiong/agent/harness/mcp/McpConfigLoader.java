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
package com.yangqiong.agent.harness.mcp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * MCP服务器配置YAML加载，支持多服务或单服务，覆盖stdio与HTTP传输
 * @author yangqiong
 */
public final class McpConfigLoader {

    /**
     * 多服务容器key
     */
    private static final String SERVERS_KEY = "mcpServers";

    /**
     * 顶层多服务容器键别名
     */
    private static final String SERVERS_ALIAS = "servers";

    /**
     * 环境变量占位符匹配
     */
    private static final Pattern ENV_PLACEHOLDER = Pattern.compile("\\$\\{([^}]+)}");

    private static final ObjectMapper YAML = new ObjectMapper(new YAMLFactory());

    /**
     * JSON解析器
     */
    private static final ObjectMapper JSON = new ObjectMapper();

    private McpConfigLoader() {
    }

    /**
     * 解析JSON资源中的MCP服务器配置
     * @param inputStream
     * @return
     */
    public static List<McpServerConfig> fromJson(InputStream inputStream) {
        try {
            return parse(JSON.readTree(inputStream));
        } catch (IOException e) {
            throw new IllegalArgumentException("MCP配置JSON解析失败：" + e.getMessage(), e);
        }
    }

    /**
     * 解析JSON文件中的MCP服务器配置
     * @param file
     * @return
     */
    public static List<McpServerConfig> fromJson(Path file) {
        try (InputStream in = Files.newInputStream(file)) {
            return fromJson(in);
        } catch (IOException e) {
            throw new IllegalArgumentException("MCP配置文件读取失败：" + file + "：" + e.getMessage(), e);
        }
    }

    /**
     * 解析JSON文本中的MCP服务器配置
     * @param json
     * @return
     */
    public static List<McpServerConfig> fromJson(String json) {
        try (InputStream in = new java.io.ByteArrayInputStream(json.getBytes(java.nio.charset.StandardCharsets.UTF_8))) {
            return fromJson(in);
        } catch (IOException e) {
            throw new IllegalArgumentException("MCP配置JSON解析失败：" + e.getMessage(), e);
        }
    }

    /**
     * 解析YAML资源中的MCP服务器配置
     * @param inputStream
     * @return
     */
    public static List<McpServerConfig> fromYaml(InputStream inputStream) {
        try {
            JsonNode root = YAML.readTree(inputStream);
            return parse(root);
        } catch (IOException e) {
            throw new IllegalArgumentException("MCP配置YAML解析失败：" + e.getMessage(), e);
        }
    }

    /**
     * 解析YAML文件中的MCP服务器配置
     * @param file
     * @return
     */
    public static List<McpServerConfig> fromYaml(Path file) {
        try (InputStream in = Files.newInputStream(file)) {
            return fromYaml(in);
        } catch (IOException e) {
            throw new IllegalArgumentException("MCP配置文件读取失败：" + file + "：" + e.getMessage(), e);
        }
    }

    /**
     * 解析YAML文本中的MCP服务器配置
     * @param yaml
     * @return
     */
    public static List<McpServerConfig> fromYaml(String yaml) {
        try (InputStream in = new java.io.ByteArrayInputStream(yaml.getBytes(java.nio.charset.StandardCharsets.UTF_8))) {
            return fromYaml(in);
        } catch (IOException e) {
            throw new IllegalArgumentException("MCP配置YAML解析失败：" + e.getMessage(), e);
        }
    }

    private static List<McpServerConfig> parse(JsonNode root) {
        if (root == null || !root.isObject() || root.isEmpty()) {
            return List.of();
        }
        JsonNode container = root.path(SERVERS_KEY).isObject() ? root.path(SERVERS_KEY)
                : root.path(SERVERS_ALIAS).isObject() ? root.path(SERVERS_ALIAS) : null;
        List<McpServerConfig> configs = new ArrayList<>();
        if (container != null) {
            // 多服务形态：mcpServers下每个键是一个服务名
            Iterator<Map.Entry<String, JsonNode>> fields = container.fields();
            fields.forEachRemaining(entry -> {
                if (entry.getValue().isObject()) {
                    configs.add(parseServer(entry.getKey(), entry.getValue()));
                }
            });
        } else if (isServerObject(root)) {
            // 单服务形态：根节点即一个服务
            configs.add(parseServer(text(root, "name", "mcp"), root));
        }
        return configs;
    }

    private static boolean isServerObject(JsonNode root) {
        return root.path("command").isTextual() || root.path("endpoint").isTextual()
                || root.path("url").isTextual();
    }

    private static McpServerConfig parseServer(String name, JsonNode node) {
        McpTransportType transport = parseTransport(text(node, "transport", "stdio"));
        McpServerConfig.Builder builder = McpServerConfig.builder(name, transport);
        String endpoint = text(node, "endpoint", text(node, "url", null));
        if (endpoint != null && !endpoint.isBlank()) {
            builder.endpoint(interpolate(endpoint));
        }
        String command = textOrNull(node, "command");
        JsonNode args = node.path("args");
        List<String> argList = new ArrayList<>();
        if (args.isArray()) {
            for (JsonNode arg : args) {
                argList.add(arg.asText());
            }
        }
        // 兼容裸npx/npx.cmd命令：优先重写为node.exe+npx-cli.js直连，Windows兜底解析npx绝对路径
        if (command != null && transport == McpTransportType.STDIO && isBareNpx(command)
                && !npxResolveDisabled()) {
            NodeExec nodeExec = resolveNodeExec();
            if (nodeExec != null) {
                command = nodeExec.command;
                if (nodeExec.firstArg != null) {
                    List<String> rewritten = new ArrayList<>();
                    rewritten.add(nodeExec.firstArg);
                    rewritten.addAll(argList);
                    argList = rewritten;
                }
            }
        }
        if (command != null) {
            builder.command(command);
        }
        if (!argList.isEmpty()) {
            builder.args(argList);
        }
        JsonNode env = node.path("env");
        if (env.isObject()) {
            builder.env(interpolateMap(env));
        }
        JsonNode headers = node.path("headers");
        if (headers.isObject()) {
            builder.headers(interpolateMap(headers));
        }
        String initTimeout = textOrNull(node, "initTimeout");
        if (initTimeout != null) {
            builder.initTimeout(parseDuration(initTimeout));
        }
        String requestTimeout = textOrNull(node, "requestTimeout");
        if (requestTimeout != null) {
            builder.requestTimeout(parseDuration(requestTimeout));
        }
        String prefix = textOrNull(node, "prefixToolNames");
        if (prefix != null) {
            builder.prefixToolNames(Boolean.parseBoolean(prefix));
        }
        String protocolVersion = textOrNull(node, "protocolVersion");
        if (protocolVersion != null) {
            builder.protocolVersion(protocolVersion);
        }
        JsonNode capabilities = node.path("clientCapabilities");
        if (capabilities.isObject()) {
            boolean roots = capabilities.path("roots").asBoolean(false);
            boolean sampling = capabilities.path("sampling").asBoolean(false);
            builder.clientCapabilities(new McpClientCapabilities(roots, sampling));
        }
        return builder.build();
    }

    private static McpTransportType parseTransport(String value) {
        return switch (value.trim().toLowerCase()) {
            case "sse" -> McpTransportType.SSE;
            case "streamable-http", "streamable", "http" -> McpTransportType.STREAMABLE_HTTP;
            case "stdio" -> McpTransportType.STDIO;
            default -> throw new IllegalArgumentException("不支持的MCP传输类型：" + value);
        };
    }

    /**
     * 对环境变量占位符进行替换
     * @param value
     * @return
     */
    private static String interpolate(String value) {
        if (value == null || value.indexOf("${") < 0) {
            return value;
        }
        Matcher matcher = ENV_PLACEHOLDER.matcher(value);
        StringBuilder result = new StringBuilder();
        while (matcher.find()) {
            String envValue = System.getenv(matcher.group(1));
            matcher.appendReplacement(result, envValue != null ? envValue : matcher.group(0));
        }
        matcher.appendTail(result);
        return result.toString();
    }

    /**
     * 对环境变量占位符进行替换
     * @param node
     * @return
     */
    private static Map<String, String> interpolateMap(JsonNode node) {
        java.util.LinkedHashMap<String, String> map = new java.util.LinkedHashMap<>();
        node.fields().forEachRemaining(entry -> {
            if (entry.getValue().isValueNode()) {
                map.put(entry.getKey(), interpolate(entry.getValue().asText()));
            }
        });
        return map;
    }

    /**
     * 解析超时配置，支持数字秒与ms/s/m/h单位
     * @param value
     * @return
     */
    private static Duration parseDuration(String value) {
        String raw = value.trim();
        if (raw.endsWith("ms")) {
            return Duration.ofMillis(Long.parseLong(raw.substring(0, raw.length() - 2)));
        }
        if (raw.endsWith("s")) {
            return Duration.ofSeconds(Long.parseLong(raw.substring(0, raw.length() - 1)));
        }
        if (raw.endsWith("m")) {
            return Duration.ofMinutes(Long.parseLong(raw.substring(0, raw.length() - 1)));
        }
        if (raw.endsWith("h")) {
            return Duration.ofHours(Long.parseLong(raw.substring(0, raw.length() - 1)));
        }
        return Duration.ofSeconds(Long.parseLong(raw));
    }

    private static String text(JsonNode node, String field, String defaultValue) {
        JsonNode n = node.path(field);
        return n.isTextual() ? n.asText() : defaultValue;
    }

    private static String textOrNull(JsonNode node, String field) {
        JsonNode n = node.path(field);
        return n.isValueNode() ? n.asText() : null;
    }

    /**
     * 判断命令是否为裸npx形式
     * @param command
     * @return
     */
    private static boolean isBareNpx(String command) {
        String base = Paths.get(command).getFileName().toString().toLowerCase();
        return base.equals("npx") || base.equals("npx.cmd") || base.equals("npx.bat");
    }

    private static boolean npxResolveDisabled() {
        String value = System.getProperty("mcp.disableNpxResolve",
                System.getenv("MCP_DISABLE_NPX_RESOLVE"));
        return "true".equalsIgnoreCase(value) || "1".equals(value);
    }

    /**
     * 当前是否Windows平台
     * @return
     */
    private static boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase().contains("win");
    }

    /**
     * node可执行文件名，Windows为node.exe，其余平台为node
     * @return
     */
    static String nodeExecutableName() {
        return isWindows() ? "node.exe" : "node";
    }

    /**
     * 解析npx运行方式，优先node直连，Windows兜底为npx绝对路径
     * @return
     */
    private static NodeExec resolveNodeExec() {
        // 优先：node与npx-cli.js同目录（Windows的npm布局）→ 重写为node直连，绕开cmd层
        Path node = locateNodeExecutable();
        if (node != null) {
            Path npxCli = node.getParent().resolve(Paths.get("node_modules", "npm", "bin", "npx-cli.js"));
            if (Files.isRegularFile(npxCli)) {
                return new NodeExec(node.toString(), npxCli.toString());
            }
        }
        // Windows兜底：npx-cli.js不相邻或未找到node时，在PATH解析npx绝对路径，避免依赖运行时PATH
        if (isWindows()) {
            Path npx = locateOnPath(NPX_NAMES, System.getenv("PATH"));
            if (npx != null) {
                return new NodeExec(npx.toString(), null);
            }
        }
        // 其余场景（含Linux/macOS）保留裸npx交由PATH解析
        return null;
    }

    /**
     * npx可执行文件名候选，Windows优先.cmd
     */
    private static final String[] NPX_NAMES = {"npx.cmd", "npx.bat", "npx"};

    /**
     * 在PATH中查找指定名称的可执行文件，返回第一个命中的绝对路径
     * @param names
     * @param pathEnv
     * @return
     */
    static Path locateOnPath(String[] names, String pathEnv) {
        if (pathEnv == null) {
            return null;
        }
        for (String name : names) {
            for (String dir : pathEnv.split(Pattern.quote(java.io.File.pathSeparator))) {
                if (dir.isBlank()) {
                    continue;
                }
                Path candidate = Paths.get(dir, name);
                if (Files.isRegularFile(candidate)) {
                    return candidate;
                }
            }
        }
        return null;
    }

    /**
     * 定位node可执行文件，优先环境变量与常见安装目录，最后扫描PATH
     * @return
     */
    private static Path locateNodeExecutable() {
        String[] envKeys = {"YQ_NODE", "YQ_NODE_HOME", "NODE_HOME"};
        for (String key : envKeys) {
            String value = System.getenv(key);
            if (value != null) {
                Path candidate = toNodeExecutable(Paths.get(value));
                if (candidate != null) {
                    return candidate;
                }
            }
        }
        if (isWindows()) {
            String[] roots = {"ProgramFiles", "ProgramFiles(x86)", "LOCALAPPDATA"};
            for (String rootKey : roots) {
                String root = System.getenv(rootKey);
                if (root != null) {
                    Path candidate = toNodeExecutable(Paths.get(root, "nodejs"));
                    if (candidate != null) {
                        return candidate;
                    }
                }
            }
        } else {
            // Linux/macOS常见安装目录
            String[] roots = {"/usr/local", "/usr", "/opt"};
            for (String root : roots) {
                Path candidate = toNodeExecutable(Paths.get(root, "bin"));
                if (candidate != null) {
                    return candidate;
                }
            }
        }
        String pathEnv = System.getenv("PATH");
        if (pathEnv != null) {
            for (String dir : pathEnv.split(Pattern.quote(java.io.File.pathSeparator))) {
                if (dir.isBlank()) {
                    continue;
                }
                Path candidate = toNodeExecutable(Paths.get(dir));
                if (candidate != null) {
                    return candidate;
                }
            }
        }
        return null;
    }

    /**
     * 将路径值规范为node可执行文件，支持直接指向可执行文件或所在目录
     * @param base
     * @return
     */
    private static Path toNodeExecutable(Path base) {
        String name = nodeExecutableName();
        Path candidate = base.getFileName() != null
                && base.getFileName().toString().equalsIgnoreCase(name)
                ? base : base.resolve(name);
        return Files.isRegularFile(candidate) ? candidate : null;
    }

    /**
     * npx运行方式解析结果
     */
    private static final class NodeExec {
        /**
         * 实际执行的命令（node可执行或npx绝对路径）
         */
        private final String command;

        /**
         * 可空，非空时插入args首位（node + npx-cli.js直连形式）
         */
        private final String firstArg;

        private NodeExec(String command, String firstArg) {
            this.command = command;
            this.firstArg = firstArg;
        }
    }
}