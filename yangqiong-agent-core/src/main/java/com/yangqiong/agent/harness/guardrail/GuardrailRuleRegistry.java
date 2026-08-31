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

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.yangqiong.agent.harness.guardrail.GuardrailRule.GuardrailAction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 护栏规则注册中心
 * <p>
 * 可配置规则库：内置中英文注入检测默认规则，支持按名称覆盖（同名规则替换）、
 * 增删与启停；并聚合外部{@link InjectionDetector}扩展检测结果。
 * 四类入口（输入/工具参数/工具结果/检索内容）共用同一规则库。
 * </p>
 * @author yangqiong
 */
public class GuardrailRuleRegistry {

    private static final Logger log = LoggerFactory.getLogger(GuardrailRuleRegistry.class);

    /**
     * 命中片段日志保留长度
     */
    private static final int SNIPPET_LIMIT = 40;

    /**
     * 按名称索引的规则库（同名规则覆盖）
     */
    private final Map<String, GuardrailRule> rulesByName = new ConcurrentHashMap<>();

    /**
     * 外部注入检测器列表
     */
    private final List<InjectionDetector> detectors = new CopyOnWriteArrayList<>();

    /**
     * JSON反序列化器
     */
    private static final ObjectMapper JSON_MAPPER = new ObjectMapper();

    /**
     * YAML反序列化器
     */
    private static final ObjectMapper YAML_MAPPER = new ObjectMapper(new YAMLFactory());

    /**
     * 热更新调度器
     */
    private volatile ScheduledExecutorService reloadScheduler;

    /**
     * 最近一次加载的规则文件修改时间
     */
    private volatile long lastLoadedMillis;

    public GuardrailRuleRegistry() {
        registerDefaults();
    }

    /**
     * 注册默认注入检测规则（中英文）
     */
    private void registerDefaults() {
        add(GuardrailRule.of("injection-ignore-previous",
                "ignore\\s+(all\\s+)?(previous|prior)\\s+(instructions?|prompts?|rules?)"
                        + "|disregard\\s+(the\\s+above|previous|all)",
                GuardrailRule.GuardrailAction.BLOCK));
        add(GuardrailRule.of("injection-reveal-system",
                "reveal\\s+your\\s+(system\\s+)?(prompt|instructions)"
                        + "|show\\s+me\\s+your\\s+(system\\s+)?prompt"
                        + "|print\\s+your\\s+system\\s+prompt",
                GuardrailRule.GuardrailAction.BLOCK));
        add(GuardrailRule.of("injection-role-hijack",
                "you\\s+are\\s+now\\s+|new\\s+instructions\\s*:\\s*|act\\s+as\\s+a\\s+(different|new)\\s+",
                GuardrailRule.GuardrailAction.BLOCK));
        add(GuardrailRule.of("injection-jailbreak",
                "jailbreak|DAN\\s+mode|developer\\s+mode",
                GuardrailRule.GuardrailAction.BLOCK));
        add(GuardrailRule.of("injection-ignore-previous-zh",
                "忽略(之前|以上|上述|前面)(的)?(所有)?(指令|提示|规则|设定)"
                        + "|无视(以上|上述|之前)(的)?(指令|提示|规则)",
                GuardrailRule.GuardrailAction.BLOCK));
        add(GuardrailRule.of("injection-reveal-system-zh",
                "(泄露|显示|打印|输出|告诉我)你(的)?(系统|内部)(提示词?|指令|设定|规则)"
                        + "|你的(系统|内部)提示(词)?是什么",
                GuardrailRule.GuardrailAction.BLOCK));
        add(GuardrailRule.of("injection-role-hijack-zh",
                "你现在(是|扮演)|新(的)?指令[:：]|请(你)?扮演(一个)?(另|新的)"
                        + "|从现在开始(你)?(是|要)",
                GuardrailRule.GuardrailAction.BLOCK));
        add(GuardrailRule.of("sensitive-secret",
                "(api[_-]?key|secret|token|password)\\s*[:=]\\s*\\S{8,}",
                GuardrailRule.GuardrailAction.MASK));
    }

    /**
     * 添加或覆盖规则（同名覆盖，用于企业定制覆盖内置规则）
     * @param rule
     * @return
     */
    public GuardrailRuleRegistry add(GuardrailRule rule) {
        if (rule != null) {
            rulesByName.put(rule.getName(), rule);
        }
        return this;
    }

    /**
     * 按名称移除规则
     * @param name
     * @return
     */
    public GuardrailRuleRegistry remove(String name) {
        if (name != null) {
            rulesByName.remove(name);
        }
        return this;
    }

    /**
     * 按名称启停规则
     * @param name
     * @param enabled
     * @return
     */
    public GuardrailRuleRegistry setEnabled(String name, boolean enabled) {
        GuardrailRule rule = name != null ? rulesByName.get(name) : null;
        if (rule != null) {
            rule.setEnabled(enabled);
        }
        return this;
    }

    /**
     * 注册外部注入检测器
     * @param detector
     * @return
     */
    public GuardrailRuleRegistry addDetector(InjectionDetector detector) {
        if (detector != null) {
            detectors.add(detector);
        }
        return this;
    }

    /**
     * 检测内容命中情况：规则库与外部检测器共同参与，
     * 返回内容中最早命中（起始位置最小，同位置取匹配段更长）的规则命中，保证判定确定性
     * @param content
     * @param target
     * @return
     */
    public Optional<GuardrailViolation> detect(String content, GuardrailTarget target) {
        if (content == null || content.isEmpty()) {
            return Optional.empty();
        }
        GuardrailViolation best = null;
        int bestStart = Integer.MAX_VALUE;
        int bestEnd = -1;
        for (GuardrailRule rule : rulesByName.values()) {
            if (!rule.isEnabled() || !rule.appliesTo(target)) {
                continue;
            }
            Matcher matcher = rule.getPattern().matcher(content);
            if (matcher.find()
                    && (matcher.start() < bestStart
                        || (matcher.start() == bestStart && matcher.end() > bestEnd))) {
                bestStart = matcher.start();
                bestEnd = matcher.end();
                best = new GuardrailViolation(rule, target, snippet(matcher.group()));
            }
        }
        if (best != null) {
            return Optional.of(best);
        }
        for (InjectionDetector detector : detectors) {
            try {
                Optional<GuardrailViolation> violation = detector.detect(content, target);
                if (violation.isPresent()) {
                    return violation;
                }
            } catch (Exception e) {
                log.warn("[Guardrail] 外部检测器执行失败: {}", detector.getClass().getSimpleName(), e);
            }
        }
        return Optional.empty();
    }

    /**
     * 对内容做脱敏：全部MASK规则命中片段替换为占位符
     * @param content
     * @param target
     * @return
     */
    public String mask(String content, GuardrailTarget target) {
        if (content == null || content.isEmpty()) {
            return content;
        }
        String masked = content;
        for (GuardrailRule rule : rulesByName.values()) {
            if (!rule.isEnabled() || rule.getAction() != GuardrailRule.GuardrailAction.MASK
                    || !rule.appliesTo(target)) {
                continue;
            }
            masked = rule.getPattern().matcher(masked).replaceAll("[已脱敏]");
        }
        return masked;
    }

    /**
     * 列出全部规则快照
     * @return
     */
    public List<GuardrailRule> listRules() {
        return new ArrayList<>(rulesByName.values());
    }

    /**
     * 从JSON规则文件加载（支持{"rules":[...]}或裸数组）
     * @param path
     * @return
     */
    public GuardrailRuleRegistry loadFromJson(Path path) {
        if (path == null || !Files.exists(path)) {
            log.warn("护栏规则JSON文件不存在: {}", path);
            return this;
        }
        try {
            String content = new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
            apply(parse(content, JSON_MAPPER));
        } catch (IOException e) {
            log.warn("加载护栏规则JSON失败: {}", path, e);
        }
        return this;
    }

    /**
     * 从YAML规则文件加载
     * @param path
     * @return
     */
    public GuardrailRuleRegistry loadFromYaml(Path path) {
        if (path == null || !Files.exists(path)) {
            log.warn("护栏规则YAML文件不存在: {}", path);
            return this;
        }
        try {
            String content = new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
            apply(parse(content, YAML_MAPPER));
        } catch (IOException e) {
            log.warn("加载护栏规则YAML失败: {}", path, e);
        }
        return this;
    }

    /**
     * 从文件加载，按扩展名自动选择解析器（.yml/.yaml走YAML，其余走JSON）
     * @param path
     * @return
     */
    public GuardrailRuleRegistry loadFromFile(Path path) {
        if (path == null || !Files.exists(path)) {
            log.warn("护栏规则文件不存在: {}", path);
            return this;
        }
        Path fileName = path.getFileName();
        String name = fileName != null ? fileName.toString().toLowerCase(Locale.ROOT) : "";
        return name.endsWith(".yml") || name.endsWith(".yaml")
                ? loadFromYaml(path) : loadFromJson(path);
    }

    /**
     * 从classpath资源加载规则（按扩展名自动选择解析器）
     * @param classpathResource 如 guardrail/rules.yaml
     * @return
     */
    public GuardrailRuleRegistry loadFromResource(String classpathResource) {
        if (classpathResource == null) {
            return this;
        }
        try (InputStream in = GuardrailRuleRegistry.class.getClassLoader()
                .getResourceAsStream(classpathResource)) {
            if (in == null) {
                log.warn("护栏规则资源不存在: {}", classpathResource);
                return this;
            }
            String content = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            String name = classpathResource.toLowerCase(Locale.ROOT);
            ObjectMapper mapper = name.endsWith(".yml") || name.endsWith(".yaml")
                    ? YAML_MAPPER : JSON_MAPPER;
            apply(parse(content, mapper));
        } catch (IOException e) {
            log.warn("加载护栏规则资源失败: {}", classpathResource, e);
        }
        return this;
    }

    /**
     * 启动规则文件热更新：周期轮询文件修改时间，变更即重新加载
     * @param path 规则文件路径
     * @param interval 轮询间隔，null或非正数时默认5秒
     * @return
     */
    public GuardrailRuleRegistry autoReload(Path path, Duration interval) {
        stopAutoReload();
        if (path == null || !Files.exists(path)) {
            throw new IllegalArgumentException("护栏规则文件不存在: " + path);
        }
        try {
            lastLoadedMillis = Files.getLastModifiedTime(path).toMillis();
        } catch (IOException e) {
            log.warn("读取护栏规则文件修改时间失败: {}", path, e);
        }
        long nanos = interval != null && !interval.isZero() && !interval.isNegative()
                ? interval.toNanos() : TimeUnit.SECONDS.toNanos(5);
        reloadScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "guardrail-rule-reload");
            t.setDaemon(true);
            return t;
        });
        reloadScheduler.scheduleWithFixedDelay(() -> {
            try {
                long modified = Files.getLastModifiedTime(path).toMillis();
                if (modified != lastLoadedMillis) {
                    lastLoadedMillis = modified;
                    loadFromFile(path);
                    log.info("护栏规则已热更新: {}", path);
                }
            } catch (Exception e) {
                log.warn("护栏规则热更新失败: {}", path, e);
            }
        }, nanos, nanos, TimeUnit.NANOSECONDS);
        return this;
    }

    /**
     * 停止规则文件热更新
     * @return
     */
    public GuardrailRuleRegistry stopAutoReload() {
        ScheduledExecutorService executor = this.reloadScheduler;
        if (executor != null) {
            executor.shutdownNow();
            this.reloadScheduler = null;
        }
        return this;
    }

    /**
     * 解析并应用规则配置
     * @param config
     */
    private void apply(GuardrailRuleConfig config) {
        if (config == null || config.getRules() == null) {
            return;
        }
        for (GuardrailRuleConfig.RuleEntry entry : config.getRules()) {
            if (entry == null || entry.getName() == null || entry.getRegex() == null) {
                continue;
            }
            add(toRule(entry));
        }
    }

    /**
     * 配置项转运行时规则
     * @param entry
     * @return
     */
    private GuardrailRule toRule(GuardrailRuleConfig.RuleEntry entry) {
        GuardrailAction action = parseAction(entry.getAction());
        GuardrailTarget[] targets = parseTargets(entry.getTargets());
        GuardrailRule rule = new GuardrailRule(entry.getName(),
                Pattern.compile(entry.getRegex(), Pattern.CASE_INSENSITIVE),
                action, targets, entry.getDescription());
        rule.setEnabled(entry.getEnabled());
        return rule;
    }

    /**
     * 解析动作枚举，非法值回退BLOCK
     * @param value
     * @return
     */
    private GuardrailAction parseAction(String value) {
        try {
            return GuardrailAction.valueOf(String.valueOf(value).trim().toUpperCase(Locale.ROOT));
        } catch (Exception e) {
            return GuardrailAction.BLOCK;
        }
    }

    /**
     * 解析作用入口集合，缺省或非法时覆盖全部入口
     * @param names
     * @return
     */
    private GuardrailTarget[] parseTargets(List<String> names) {
        if (names == null || names.isEmpty()) {
            return GuardrailTarget.values();
        }
        List<GuardrailTarget> result = new ArrayList<>();
        for (String name : names) {
            try {
                result.add(GuardrailTarget.valueOf(name.trim().toUpperCase(Locale.ROOT)));
            } catch (Exception ignored) {
                // 跳过非法入口名
            }
        }
        return result.isEmpty() ? GuardrailTarget.values()
                : result.toArray(new GuardrailTarget[0]);
    }

    /**
     * 反序列化规则配置，支持{"rules":[...]}包裹与裸数组两种结构
     * @param content
     * @param mapper
     * @return
     */
    private GuardrailRuleConfig parse(String content, ObjectMapper mapper) {
        if (content == null || content.isBlank()) {
            return new GuardrailRuleConfig();
        }
        try {
            GuardrailRuleConfig wrapped = mapper.readValue(content, GuardrailRuleConfig.class);
            if (wrapped != null && wrapped.getRules() != null && !wrapped.getRules().isEmpty()) {
                return wrapped;
            }
        } catch (IOException ignored) {
            // 尝试裸数组结构
        }
        try {
            List<GuardrailRuleConfig.RuleEntry> list = mapper.readValue(content,
                    new TypeReference<List<GuardrailRuleConfig.RuleEntry>>() { });
            if (list != null && !list.isEmpty()) {
                GuardrailRuleConfig config = new GuardrailRuleConfig();
                config.setRules(list);
                return config;
            }
        } catch (IOException e) {
            log.warn("护栏规则配置解析失败，已忽略本次加载", e);
        }
        return new GuardrailRuleConfig();
    }

    /**
     * 截取命中片段用于日志（避免泄露全文）
     * @param matched
     * @return
     */
    private String snippet(String matched) {
        if (matched == null) {
            return null;
        }
        return matched.length() <= SNIPPET_LIMIT ? matched : matched.substring(0, SNIPPET_LIMIT) + "...";
    }
}
