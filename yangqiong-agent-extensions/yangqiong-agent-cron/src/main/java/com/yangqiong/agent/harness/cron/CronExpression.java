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
package com.yangqiong.agent.harness.cron;

import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Locale;
import java.util.Map;
import java.util.TreeSet;

/**
 * cron表达式
 * <p>
 * 支持5段（分 时 日 月 周）与6段（秒 分 时 日 月 周）两种格式；
 * 字段支持通配符、步长、范围、列表与单值，月支持JAN-DEC，
 * 周支持SUN-SAT与0-7（0与7均表示周日）。日与周同时受限时按经典cron
 * 的或语义匹配。用于计算任务的下一次触发时间。
 * </p>
 * @author yangqiong
 */
public final class CronExpression {

    /**
     * 最长搜索步数，防止无效组合导致死循环
     */
    private static final int MAX_ATTEMPTS = 100_000;

    /**
     * 月份名称映射
     */
    private static final Map<String, Integer> MONTH_NAMES = Map.ofEntries(
            Map.entry("JAN", 1), Map.entry("FEB", 2), Map.entry("MAR", 3), Map.entry("APR", 4),
            Map.entry("MAY", 5), Map.entry("JUN", 6), Map.entry("JUL", 7), Map.entry("AUG", 8),
            Map.entry("SEP", 9), Map.entry("OCT", 10), Map.entry("NOV", 11), Map.entry("DEC", 12));

    /**
     * 星期名称映射，与cron取值一致（0与7表示周日）
     */
    private static final Map<String, Integer> DOW_NAMES = Map.ofEntries(
            Map.entry("SUN", 0), Map.entry("MON", 1), Map.entry("TUE", 2), Map.entry("WED", 3),
            Map.entry("THU", 4), Map.entry("FRI", 5), Map.entry("SAT", 6));

    /**
     * 原始表达式文本
     */
    private final String expression;

    /**
     * 秒字段取值集合，6段格式才有
     */
    private final TreeSet<Integer> seconds;

    /**
     * 分钟字段取值集合
     */
    private final TreeSet<Integer> minutes;

    /**
     * 小时字段取值集合
     */
    private final TreeSet<Integer> hours;

    /**
     * 日字段取值集合
     */
    private final TreeSet<Integer> daysOfMonth;

    /**
     * 月字段取值集合
     */
    private final TreeSet<Integer> months;

    /**
     * 周字段取值集合，与Java的DayOfWeek值一致（1=周一..7=周日）
     */
    private final TreeSet<Integer> daysOfWeek;

    /**
     * 日字段是否为通配符
     */
    private final boolean dayOfMonthWildcard;

    /**
     * 周字段是否为通配符
     */
    private final boolean dayOfWeekWildcard;

    /**
     * 是否包含秒字段
     */
    private final boolean withSeconds;

    /**
     * 按表达式文本解析，5段或6段
     * @param expression
     */
    private CronExpression(String expression) {
        this.expression = expression;
        String[] parts = expression.trim().split("\\s+");
        this.withSeconds = parts.length == 6;
        int idx = 0;
        this.seconds = withSeconds ? parseField(parts[idx++], 0, 59, null) : null;
        this.minutes = parseField(parts[idx++], 0, 59, null);
        this.hours = parseField(parts[idx++], 0, 23, null);
        this.daysOfMonth = parseField(parts[idx++], 1, 31, null);
        this.months = parseField(parts[idx++], 1, 12, MONTH_NAMES);
        this.daysOfWeek = parseDayOfWeek(parts[idx]);
        this.dayOfMonthWildcard = isWildcardToken(parts[3]);
        this.dayOfWeekWildcard = isWildcardToken(parts[4]);
    }

    /**
     * 解析cron表达式
     * @param expression
     * @return
     */
    public static CronExpression parse(String expression) {
        if (expression == null || expression.isBlank()) {
            throw new IllegalArgumentException("cron表达式不能为空");
        }
        String[] parts = expression.trim().split("\\s+");
        if (parts.length != 5 && parts.length != 6) {
            throw new IllegalArgumentException("cron表达式必须为5段或6段，实际为" + parts.length + "段: " + expression);
        }
        return new CronExpression(expression.trim());
    }

    /**
     * 计算严格晚于指定时刻的下一次触发时间，使用系统默认时区
     * @param after
     * @return
     */
    public Instant nextFireAfter(Instant after) {
        return nextFireAfter(after, ZoneId.systemDefault());
    }

    /**
     * 计算严格晚于指定时刻的下一次触发时间
     * @param after
     * @param zone
     * @return
     */
    public Instant nextFireAfter(Instant after, ZoneId zone) {
        if (after == null) {
            throw new IllegalArgumentException("起始时间不能为空");
        }
        ZonedDateTime base = after.atZone(zone).withNano(0);
        if (!withSeconds) {
            base = base.withSecond(0);
        }
        // 对齐到触发粒度并保证严格晚于起始时刻
        ZonedDateTime candidate = base;
        if (!base.isAfter(after.atZone(zone))) {
            candidate = withSeconds ? base.plusSeconds(1) : base.plusMinutes(1);
        }
        // 自高位向低位逐级校正，任一字段不匹配时跳到下一个候选再整体复核
        for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
            if (!matchesMonth(candidate.getMonthValue())) {
                candidate = candidate.plusMonths(1).withDayOfMonth(1).with(LocalTime.MIN);
                continue;
            }
            if (!matchesDay(candidate.getDayOfMonth(), candidate.getDayOfWeek().getValue())) {
                candidate = candidate.plusDays(1).with(LocalTime.MIN);
                continue;
            }
            if (!matchesHour(candidate.getHour())) {
                candidate = candidate.plusHours(1).withMinute(0).withSecond(0);
                continue;
            }
            if (!matchesMinute(candidate.getMinute())) {
                candidate = candidate.plusMinutes(1).withSecond(0);
                continue;
            }
            if (withSeconds && !matchesSecond(candidate.getSecond())) {
                candidate = candidate.plusSeconds(1);
                continue;
            }
            return candidate.toInstant();
        }
        throw new IllegalStateException("无法在合理范围内计算下一次触发时间: " + expression);
    }

    /**
     * 获取原始表达式文本
     * @return
     */
    public String getExpression() {
        return expression;
    }

    /**
     * 是否包含秒字段
     * @return
     */
    public boolean isWithSeconds() {
        return withSeconds;
    }

    /**
     * 判断分钟是否匹配
     * @param minute
     * @return
     */
    private boolean matchesMinute(int minute) {
        return minutes.contains(minute);
    }

    /**
     * 判断小时是否匹配
     * @param hour
     * @return
     */
    private boolean matchesHour(int hour) {
        return hours.contains(hour);
    }

    /**
     * 判断月份是否匹配
     * @param month
     * @return
     */
    private boolean matchesMonth(int month) {
        return months.contains(month);
    }

    /**
     * 判断秒是否匹配
     * @param second
     * @return
     */
    private boolean matchesSecond(int second) {
        return seconds.contains(second);
    }

    /**
     * 判断日是否匹配，日与周同时受限时按或语义
     * @param dayOfMonth
     * @param javaDayOfWeek
     * @return
     */
    private boolean matchesDay(int dayOfMonth, int javaDayOfWeek) {
        boolean domRestricted = !dayOfMonthWildcard;
        boolean dowRestricted = !dayOfWeekWildcard;
        if (domRestricted && dowRestricted) {
            return daysOfMonth.contains(dayOfMonth) || daysOfWeek.contains(javaDayOfWeek);
        }
        if (domRestricted) {
            return daysOfMonth.contains(dayOfMonth);
        }
        if (dowRestricted) {
            return daysOfWeek.contains(javaDayOfWeek);
        }
        return true;
    }

    /**
     * 解析秒/分/时/日/月字段为取值集合
     * @param field
     * @param min
     * @param max
     * @param names
     * @return
     */
    private static TreeSet<Integer> parseField(String field, int min, int max, Map<String, Integer> names) {
        TreeSet<Integer> values = new TreeSet<>();
        for (String part : field.split(",")) {
            String rangePart = part;
            String stepPart = null;
            int slash = part.indexOf('/');
            if (slash >= 0) {
                rangePart = part.substring(0, slash);
                stepPart = part.substring(slash + 1);
            }
            int step = parseStep(stepPart);
            if ("*".equals(rangePart)) {
                addRange(values, min, max, step);
                continue;
            }
            int dash = rangePart.indexOf('-');
            if (dash >= 0) {
                int lo = parseValue(rangePart.substring(0, dash).trim(), min, max, names);
                int hi = parseValue(rangePart.substring(dash + 1).trim(), min, max, names);
                if (hi < lo) {
                    throw new IllegalArgumentException("cron范围起始大于结束: " + field);
                }
                addRange(values, lo, hi, step);
            } else {
                values.add(parseValue(rangePart.trim(), min, max, names));
            }
        }
        if (values.isEmpty()) {
            throw new IllegalArgumentException("cron字段无有效取值: " + field);
        }
        return values;
    }

    /**
     * 解析周字段并归一化（0与7均归一为7，对应周日）
     * @param field
     * @return
     */
    private static TreeSet<Integer> parseDayOfWeek(String field) {
        TreeSet<Integer> values = parseField(field, 0, 7, DOW_NAMES);
        if (values.contains(0)) {
            values.remove(0);
            values.add(7);
        }
        return values;
    }

    /**
     * 解析字段取值，名称映射优先，越界时抛异常
     * @param token
     * @param min
     * @param max
     * @param names
     * @return
     */
    private static int parseValue(String token, int min, int max, Map<String, Integer> names) {
        Integer named = names != null ? names.get(token.toUpperCase(Locale.ROOT)) : null;
        int value = named != null ? named : Integer.parseInt(token);
        if (value < min || value > max) {
            throw new IllegalArgumentException("cron字段取值越界: " + token + "（范围 " + min + "-" + max + "）");
        }
        return value;
    }

    /**
     * 解析步长，非法步长抛异常
     * @param stepPart
     * @return
     */
    private static int parseStep(String stepPart) {
        if (stepPart == null || stepPart.isBlank()) {
            return 1;
        }
        int step = Integer.parseInt(stepPart);
        if (step <= 0) {
            throw new IllegalArgumentException("cron步长必须为正数: " + stepPart);
        }
        return step;
    }

    /**
     * 向取值集合按步长填充范围
     * @param values
     * @param lo
     * @param hi
     * @param step
     */
    private static void addRange(TreeSet<Integer> values, int lo, int hi, int step) {
        for (int value = lo; value <= hi; value += step) {
            values.add(value);
        }
    }

    /**
     * 判断字段是否为通配符
     * @param field
     * @return
     */
    private static boolean isWildcardToken(String field) {
        return "*".equals(field.trim());
    }

    @Override
    public String toString() {
        return expression;
    }
}
