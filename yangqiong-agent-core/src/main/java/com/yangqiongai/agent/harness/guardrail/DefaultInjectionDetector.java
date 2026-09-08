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
package com.yangqiongai.agent.harness.guardrail;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 默认注入检测器
 * <p>
 * 在{@link GuardrailRuleRegistry}关键词正则基础上，补充多层启发式检测：
 * 高熵检测（obfuscated/encoded内容）、Unicode homoglyph攻击检测、
 * Base64编码指令检测、重复填充检测、超长词元检测。
 * 并可装配可选的LLM裁判检测器，对可疑内容做二次LLM级判定。
 * 与GuardrailRuleRegistry的正则规则形成互补，覆盖规则无法发现的变种攻击。
 * </p>
 * @author yangqiong
 */
public class DefaultInjectionDetector implements InjectionDetector {

    private static final Logger log = LoggerFactory.getLogger(DefaultInjectionDetector.class);

    /**
     * 高熵阈值（Shannon熵，超过此值认为可能是编码混淆内容）
     */
    private static final double ENTROPY_THRESHOLD = 4.5;

    /**
     * 检测高熵所需的最小内容长度（短文本高熵无意义，如"abc123"）
     */
    private static final int MIN_ENTROPY_LENGTH = 30;

    /**
     * 超长词元阈值（字符数超过此值、无空格且非纯URL，可能是token smuggling）
     */
    private static final int MAX_WORD_LENGTH = 100;

    /**
     * 重复字符检测的参数：连续重复字符数超过此值（如"aaaa...a"）
     */
    private static final int REPETITION_THRESHOLD = 15;

    /**
     * 最大检测长度（超过此长度的内容截断后检测，防止OOM）
     */
    private static final int MAX_DETECT_LENGTH = 50000;

    /**
     * 检测器名称，作为 GuardrailRule 的 name 回填
     */
    private static final String DETECTOR_NAME = "default-injection-detector";

    /**
     * 高熵规则名称
     */
    private static final String RULE_HIGH_ENTROPY = "injection-high-entropy";

    /**
     * 同形词攻击规则名称
     */
    private static final String RULE_HOMOGLYPH = "injection-homoglyph";

    /**
     * Base64编码指令规则名称
     */
    private static final String RULE_BASE64 = "injection-base64-encoded";

    /**
     * 重复填充规则名称
     */
    private static final String RULE_REPETITION = "injection-repetition-padding";

    /**
     * 超长词元规则名称
     */
    private static final String RULE_LONG_TOKEN = "injection-long-token";

    /**
     * 是否启用高熵检测
     */
    private final boolean entropyEnabled;

    /**
     * 是否启用同形词检测
     */
    private final boolean homoglyphEnabled;

    /**
     * 是否启用Base64编码指令检测
     */
    private final boolean base64Enabled;

    /**
     * 是否启用重复填充检测
     */
    private final boolean repetitionEnabled;

    /**
     * 是否启用超长词元检测
     */
    private final boolean longTokenEnabled;

    /**
     * 可选的LLM裁判检测器（对可疑内容做二次判定，减少误报）
     */
    private final InjectionDetector llmJudge;

    /**
     * 启用的检测规则名称集合（用于审计日志）
     */
    private final Set<String> enabledRules;

    /**
     * Unicode同形词映射：易混淆的ASCII字符到Unicode字符列表
     */
    private static final HomoglyphMap HOMOGLYPHS = new HomoglyphMap();

    DefaultInjectionDetector(boolean entropyEnabled, boolean homoglyphEnabled,
                             boolean base64Enabled, boolean repetitionEnabled,
                             boolean longTokenEnabled, InjectionDetector llmJudge) {
        this.entropyEnabled = entropyEnabled;
        this.homoglyphEnabled = homoglyphEnabled;
        this.base64Enabled = base64Enabled;
        this.repetitionEnabled = repetitionEnabled;
        this.longTokenEnabled = longTokenEnabled;
        this.llmJudge = llmJudge;
        this.enabledRules = new HashSet<>();
        if (entropyEnabled) enabledRules.add(RULE_HIGH_ENTROPY);
        if (homoglyphEnabled) enabledRules.add(RULE_HOMOGLYPH);
        if (base64Enabled) enabledRules.add(RULE_BASE64);
        if (repetitionEnabled) enabledRules.add(RULE_REPETITION);
        if (longTokenEnabled) enabledRules.add(RULE_LONG_TOKEN);
    }

    /**
     * 创建默认配置的检测器（全部启发式检测启用，无LLM裁判）
     * @return
     */
    public static DefaultInjectionDetector create() {
        return new Builder().build();
    }

    /**
     * 检测内容是否包含注入攻击
     * @param content 待检测文本
     * @param target 内容入口
     * @return 命中时返回违规描述，未命中返回empty
     */
    @Override
    public Optional<GuardrailViolation> detect(String content, GuardrailTarget target) {
        if (content == null || content.isEmpty()) {
            return Optional.empty();
        }
        // 截断超长内容防止OOM
        String sample = content.length() > MAX_DETECT_LENGTH
                ? content.substring(0, MAX_DETECT_LENGTH) : content;

        GuardrailViolation found = null;

        // 按检测类别从最具体到最泛化执行，避免高熵/重复检测先吞掉语义更明确的攻击
        // Unicode同形词攻击检测（最明确）
        if (homoglyphEnabled) {
            found = checkHomoglyph(sample, target);
        }
        // Base64编码指令检测（比高熵更明确）
        if (found == null && base64Enabled) {
            found = checkBase64EncodedInstruction(sample, target);
        }
        // 超长词元检测
        if (found == null && longTokenEnabled) {
            found = checkLongToken(sample, target);
        }
        // 重复填充检测
        if (found == null && repetitionEnabled) {
            found = checkRepetition(sample, target);
        }
        // 高熵检测（最泛化，可能误报，放最后）
        if (found == null && entropyEnabled) {
            found = checkEntropy(sample, target);
        }

        if (found != null) {
            if (llmJudge != null) {
                return llmJudge.detect(content, target);
            }
            return Optional.of(found);
        }
        return Optional.empty();
    }

    /**
     * 获取已启用的检测规则名称集合
     * @return
     */
    public Set<String> getEnabledRules() {
        return new HashSet<>(enabledRules);
    }

    /**
     * 高熵检测：计算Shannon熵，超过阈值认为是编码混淆内容
     * @param content
     * @param target
     * @return
     */
    private GuardrailViolation checkEntropy(String content, GuardrailTarget target) {
        if (content.length() < MIN_ENTROPY_LENGTH) {
            return null;
        }
        // 只检测包含非空白字符较多的内容（纯中文/纯英文长文本熵也高，但不应误报）
        // 降低误报：只检测字母数字+符号占比较高的内容，CJK字符视为正常文本不计入可疑字符
        int alphaNumSymbol = 0;
        int totalNonSpace = 0;
        for (int i = 0; i < content.length(); i++) {
            char c = content.charAt(i);
            if (Character.isWhitespace(c)) {
                continue;
            }
            totalNonSpace++;
            boolean isCjk = (c >= 0x4E00 && c <= 0x9FFF)        // CJK统一汉字
                    || (c >= 0x3400 && c <= 0x4DBF)           // CJK扩展A
                    || (c >= 0x20000 && c <= 0x2A6DF)         // CJK扩展B
                    || (c >= 0x3040 && c <= 0x30FF)           // 日文假名
                    || (c >= 0xAC00 && c <= 0xD7AF);          // 韩文
            if (!isCjk) {
                alphaNumSymbol++;
            }
        }
        if (totalNonSpace == 0) {
            return null;
        }
        double ratio = (double) alphaNumSymbol / totalNonSpace;
        if (ratio < 0.75) {
            // 非CJK非空白字符比例不够高，不触发高熵检测（避免误报正常混合中文文本）
            return null;
        }

        double entropy = shannonEntropy(content);
        if (entropy > ENTROPY_THRESHOLD) {
            String snippet = snippet(content, 60);
            log.debug("[DefaultInjectionDetector] 高熵内容命中: entropy={}, snippet={}", entropy, snippet);
            return new GuardrailViolation(
                    GuardrailRule.of(RULE_HIGH_ENTROPY,
                            "高熵检测: entropy=" + entropy,
                            GuardrailRule.GuardrailAction.WARN),
                    target, "high-entropy(" + String.format("%.2f", entropy) + ")");
        }
        return null;
    }

    /**
     * 计算Shannon熵
     * @param text
     * @return
     */
    private double shannonEntropy(String text) {
        int len = text.length();
        int[] freq = new int[65536];
        for (int i = 0; i < len; i++) {
            freq[text.charAt(i)]++;
        }
        double entropy = 0.0;
        for (int count : freq) {
            if (count > 0) {
                double p = (double) count / len;
                entropy -= p * (Math.log(p) / Math.log(2));
            }
        }
        return entropy;
    }

    /**
     * Unicode同形词攻击检测：检测内容中是否包含Unicode字符冒充ASCII字符
     * @param content
     * @param target
     * @return
     */
    private GuardrailViolation checkHomoglyph(String content, GuardrailTarget target) {
        // 检测是否包含已知的Unicode同形词字符
        int homoglyphCount = 0;
        StringBuilder found = new StringBuilder();
        for (int i = 0; i < content.length(); i++) {
            char c = content.charAt(i);
            if (HOMOGLYPHS.isHomoglyph(c)) {
                homoglyphCount++;
                if (found.length() < 20) {
                    found.append(c);
                }
            }
        }
        // 阈值：同形词字符超过总字符的1%，或至少3个同形词
        if (homoglyphCount > 0 && (homoglyphCount >= 3
                || (double) homoglyphCount / content.length() > 0.01)) {
            log.debug("[DefaultInjectionDetector] Unicode同形词攻击检测命中: count={}, chars={}", homoglyphCount, found);
            return new GuardrailViolation(
                    GuardrailRule.of(RULE_HOMOGLYPH,
                            "Unicode同形词攻击: homoglyphCount=" + homoglyphCount,
                            GuardrailRule.GuardrailAction.BLOCK),
                    target, "homoglyph(" + homoglyphCount + "): " + found);
        }
        return null;
    }

    /**
     * Base64编码指令检测：检测内容中是否包含Base64编码的指令模式
     * @param content
     * @param target
     * @return
     */
    private GuardrailViolation checkBase64EncodedInstruction(String content, GuardrailTarget target) {
        // 检测关键词：base64, decode, 编码等 + 疑似Base64字符串
        String lower = content.toLowerCase();
        boolean hasKeyword = lower.contains("base64")
                || lower.contains("decode")
                || lower.contains("decoding")
                || lower.contains("base-64")
                || lower.contains("编码")
                || lower.contains("解码");

        if (!hasKeyword) {
            return null;
        }

        // 找疑似Base64的长字符串（包含关键词时长度>=12即可，降低长度要求）
        String[] tokens = content.split("\\s+");
        for (String token : tokens) {
            String cleaned = token.trim();
            if (cleaned.length() >= 12 && isBase64Like(cleaned, 12)) {
                log.debug("[DefaultInjectionDetector] Base64编码指令检测命中: token={}",
                        snippet(cleaned, 40));
                return new GuardrailViolation(
                        GuardrailRule.of(RULE_BASE64,
                                "Base64编码指令: len=" + cleaned.length(),
                                GuardrailRule.GuardrailAction.WARN),
                        target, "base64(" + cleaned.length() + " chars)");
            }
        }
        return null;
    }

    /**
     * 判断字符串是否疑似Base64编码
     * @param s
     * @param minLen 最小长度要求
     * @return
     */
    private boolean isBase64Like(String s, int minLen) {
        if (s.length() < minLen) {
            return false;
        }
        // Base64字符集：A-Za-z0-9+/=
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (!((c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z')
                    || (c >= '0' && c <= '9') || c == '+' || c == '/' || c == '=')) {
                return false;
            }
        }
        return true;
    }

    /**
     * 重复填充检测：检测连续重复字符是否超过阈值
     * @param content
     * @param target
     * @return
     */
    private GuardrailViolation checkRepetition(String content, GuardrailTarget target) {
        int maxRun = 1;
        int currentRun = 1;
        char currentChar = content.charAt(0);
        for (int i = 1; i < content.length(); i++) {
            if (content.charAt(i) == currentChar) {
                currentRun++;
                if (currentRun > maxRun) {
                    maxRun = currentRun;
                }
            } else {
                currentRun = 1;
                currentChar = content.charAt(i);
            }
        }
        if (maxRun >= REPETITION_THRESHOLD) {
            log.debug("[DefaultInjectionDetector] 重复填充检测命中: maxRun={}, char='{}'",
                    maxRun, currentChar);
            return new GuardrailViolation(
                    GuardrailRule.of(RULE_REPETITION,
                            "重复字符填充: maxRun=" + maxRun + ", char='" + currentChar + "'",
                            GuardrailRule.GuardrailAction.WARN),
                    target, "repetition(" + maxRun + "x '" + currentChar + "')");
        }
        return null;
    }

    /**
     * 超长词元检测：检测无空格的超长词元，可能是token smuggling
     * @param content
     * @param target
     * @return
     */
    private GuardrailViolation checkLongToken(String content, GuardrailTarget target) {
        String[] tokens = content.split("\\s+");
        for (String token : tokens) {
            if (token.length() > MAX_WORD_LENGTH) {
                // 排除纯URL（URL通常很长但属于正常内容）
                if (token.startsWith("http://") || token.startsWith("https://")
                        || token.startsWith("ftp://")) {
                    continue;
                }
                log.debug("[DefaultInjectionDetector] 超长词元检测命中: len={}", token.length());
                return new GuardrailViolation(
                        GuardrailRule.of(RULE_LONG_TOKEN,
                                "超长词元: len=" + token.length(),
                                GuardrailRule.GuardrailAction.WARN),
                        target, "long-token(" + token.length() + " chars)");
            }
        }
        return null;
    }

    /**
     * 截取内容片段（用于日志，避免泄露全文）
     * @param text
     * @param maxLen
     * @return
     */
    private static String snippet(String text, int maxLen) {
        if (text == null) {
            return null;
        }
        return text.length() <= maxLen ? text : text.substring(0, maxLen) + "...";
    }

    /**
     * Unicode同形词映射
     * <p>
     * 维护易用于攻击的Unicode字符与ASCII字符的对应关系。
     * </p>
     */
    private static final class HomoglyphMap {

        /**
         * 同形词Unicode字符集
         */
        private final Set<Character> homoglyphs = new HashSet<>();

        HomoglyphMap() {
            // 拉丁字母补充：常见混淆字符
            // A: Α(U+0391), ᗅ(U+15C5)
            // a: α(U+03B1), à(U+00E0), á(U+00E1), â(U+00E2), ã(U+00E3), ä(U+00E4)
            // B: Β(U+0392), Ɓ(U+0181)
            // b: Ь(U+042C), ᖾ(U+15BE)
            // C: С(U+0421), Ϲ(U+03F9)
            // c: с(U+0441), ⅽ(U+217D)
            // E: Ε(U+0395), ℰ(U+2130)
            // e: е(U+0435), ℯ(U+212F)
            // H: Η(U+0397), Н(U+041D)
            // h: ћ(U+045B), һ(U+04BB)
            // I: Ι(U+0399), І(U+0406), Ӏ(U+04C0)
            // i: і(U+0456), ⅰ(U+2170), ǐ(U+01D0)
            // K: Κ(U+039A), K(U+212A)
            // k: κ(U+03BA), ḳ(U+1E33)
            // M: Μ(U+039C), Ϻ(U+03FA)
            // m: m(U+006D) - ASCII
            // O: Ο(U+039F), 0(U+0030), О(U+041E)
            // o: ο(U+03BF), о(U+043E), 0(U+0030)
            // P: Ρ(U+03A1), Р(U+0420)
            // p: р(U+0440), ρ(U+03C1)
            // S: Ѕ(U+0405), Տ(U+054F)
            // s: ѕ(U+0455), ѕ(U+0455)
            // T: Τ(U+03A4), Т(U+0422)
            // t: т(U+0442), ţ(U+0163)
            // X: Χ(U+03A7), Х(U+0425)
            // x: х(U+0445), χ(U+03C7)
            // Y: Υ(U+03A5), Ү(U+04AE)
            // y: у(U+0443), γ(U+03B3)

            // 收集常用混淆Unicode字符（Cyrillic + Greek + Latin supplements）
            int[] homoglyphCodepoints = {
                    0x0391, 0x03B1, 0x0392, 0x042C, 0x0421, 0x03F9,
                    0x0441, 0x0395, 0x0435, 0x0397, 0x041D, 0x0399,
                    0x0406, 0x04C0, 0x0456, 0x039A, 0x03BA, 0x039C,
                    0x039F, 0x041E, 0x03BF, 0x043E, 0x03A1, 0x0420,
                    0x0440, 0x03C1, 0x0405, 0x03A4, 0x0422, 0x0442,
                    0x03A7, 0x0425, 0x0445, 0x03C7, 0x03A5, 0x04AE,
                    0x0443, 0x03B3, 0x00E0, 0x00E1, 0x00E2, 0x00E3,
                    0x00E4, 0x0181, 0x15BE, 0x217D, 0x2130, 0x212F,
                    0x04BB, 0x01D0, 0x2170, 0x212A, 0x1E33, 0x03FA,
                    0x054F, 0x0163, 0x03B3
            };
            for (int cp : homoglyphCodepoints) {
                homoglyphs.add((char) cp);
            }
        }

        /**
         * 判断字符是否为同形词Unicode字符
         * @param c
         * @return
         */
        boolean isHomoglyph(char c) {
            return homoglyphs.contains(c);
        }
    }

    /**
     * 构建器
     * @author yangqiong
     */
    public static class Builder {

        private boolean entropyEnabled = true;
        private boolean homoglyphEnabled = true;
        private boolean base64Enabled = true;
        private boolean repetitionEnabled = true;
        private boolean longTokenEnabled = true;
        private InjectionDetector llmJudge;

        /**
         * 启用/禁用高熵检测
         * @param enabled
         * @return
         */
        public Builder entropyEnabled(boolean enabled) {
            this.entropyEnabled = enabled;
            return this;
        }

        /**
         * 启用/禁用Unicode同形词检测
         * @param enabled
         * @return
         */
        public Builder homoglyphEnabled(boolean enabled) {
            this.homoglyphEnabled = enabled;
            return this;
        }

        /**
         * 启用/禁用Base64编码指令检测
         * @param enabled
         * @return
         */
        public Builder base64Enabled(boolean enabled) {
            this.base64Enabled = enabled;
            return this;
        }

        /**
         * 启用/禁用重复填充检测
         * @param enabled
         * @return
         */
        public Builder repetitionEnabled(boolean enabled) {
            this.repetitionEnabled = enabled;
            return this;
        }

        /**
         * 启用/禁用超长词元检测
         * @param enabled
         * @return
         */
        public Builder longTokenEnabled(boolean enabled) {
            this.longTokenEnabled = enabled;
            return this;
        }

        /**
         * 装配可选的LLM裁判检测器，对可疑内容做二次判定减少误报
         * @param llmJudge
         * @return
         */
        public Builder llmJudge(InjectionDetector llmJudge) {
            this.llmJudge = llmJudge;
            return this;
        }

        /**
         * 构建默认注入检测器
         * @return
         */
        public DefaultInjectionDetector build() {
            return new DefaultInjectionDetector(
                    entropyEnabled, homoglyphEnabled,
                    base64Enabled, repetitionEnabled,
                    longTokenEnabled, llmJudge);
        }
    }
}