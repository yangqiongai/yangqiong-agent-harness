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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;

import org.junit.jupiter.api.Test;

/**
 * cron表达式测试
 * @author yangqiong
 */
class CronExpressionTest {

    /**
     * 测试用时区，保证结果可确定
     */
    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");

    @Test
    void parsesFiveFieldExpressionAndComputesNextFire() {
        CronExpression expr = CronExpression.parse("0 9 * * *");

        assertThat(expr.getExpression()).isEqualTo("0 9 * * *");
        assertThat(expr.isWithSeconds()).isFalse();
        assertThat(expr.nextFireAfter(at(2026, 1, 1, 8, 0, 0), ZONE))
                .isEqualTo(at(2026, 1, 1, 9, 0, 0));
    }

    @Test
    void nextFireIsStrictlyAfterStartingInstant() {
        CronExpression expr = CronExpression.parse("0 9 * * *");

        assertThat(expr.nextFireAfter(at(2026, 1, 1, 9, 0, 0), ZONE))
                .isEqualTo(at(2026, 1, 2, 9, 0, 0));
    }

    @Test
    void parsesSixFieldExpressionWithSeconds() {
        CronExpression expr = CronExpression.parse("*/5 * * * * *");

        assertThat(expr.isWithSeconds()).isTrue();
        assertThat(expr.nextFireAfter(at(2026, 1, 1, 10, 0, 2), ZONE))
                .isEqualTo(at(2026, 1, 1, 10, 0, 5));
    }

    @Test
    void supportsStepSyntax() {
        CronExpression expr = CronExpression.parse("*/15 * * * *");

        assertThat(expr.nextFireAfter(at(2026, 1, 1, 10, 7, 0), ZONE))
                .isEqualTo(at(2026, 1, 1, 10, 15, 0));
    }

    @Test
    void supportsRangeSyntax() {
        CronExpression expr = CronExpression.parse("0-30 * * * *");

        assertThat(expr.nextFireAfter(at(2026, 1, 1, 10, 35, 0), ZONE))
                .isEqualTo(at(2026, 1, 1, 11, 0, 0));
    }

    @Test
    void supportsListSyntax() {
        CronExpression expr = CronExpression.parse("1,15,30 * * * *");

        assertThat(expr.nextFireAfter(at(2026, 1, 1, 10, 16, 0), ZONE))
                .isEqualTo(at(2026, 1, 1, 10, 30, 0));
    }

    @Test
    void supportsMonthNames() {
        CronExpression expr = CronExpression.parse("0 0 1 JAN,MAR *");

        assertThat(expr.nextFireAfter(at(2026, 2, 15, 0, 0, 0), ZONE))
                .isEqualTo(at(2026, 3, 1, 0, 0, 0));
    }

    @Test
    void supportsDayOfWeekNames() {
        // 2026-01-01为周四
        CronExpression expr = CronExpression.parse("0 9 * * MON-FRI");

        assertThat(expr.nextFireAfter(at(2026, 1, 1, 10, 0, 0), ZONE))
                .isEqualTo(at(2026, 1, 2, 9, 0, 0));
        assertThat(expr.nextFireAfter(at(2026, 1, 2, 10, 0, 0), ZONE))
                .isEqualTo(at(2026, 1, 5, 9, 0, 0));
    }

    @Test
    void normalizesDowZeroAndSevenToSunday() {
        assertThat(CronExpression.parse("0 0 * * 0").nextFireAfter(at(2026, 1, 1, 12, 0, 0), ZONE))
                .isEqualTo(at(2026, 1, 4, 0, 0, 0));
        assertThat(CronExpression.parse("0 0 * * 7").nextFireAfter(at(2026, 1, 1, 12, 0, 0), ZONE))
                .isEqualTo(at(2026, 1, 4, 0, 0, 0));
    }

    @Test
    void matchesDayOfMonthOrDayOfWeekWhenBothRestricted() {
        // 日与周同时受限按或语义：1号或周日
        CronExpression expr = CronExpression.parse("0 0 1 * SUN");

        assertThat(expr.nextFireAfter(at(2026, 1, 1, 0, 0, 0), ZONE))
                .isEqualTo(at(2026, 1, 4, 0, 0, 0));
        // 周日下午之后再匹配的是下一个周日
        assertThat(expr.nextFireAfter(at(2026, 1, 4, 12, 0, 0), ZONE))
                .isEqualTo(at(2026, 1, 11, 0, 0, 0));
        // 2月1日为周日且是1号，双重命中
        assertThat(expr.nextFireAfter(at(2026, 1, 25, 12, 0, 0), ZONE))
                .isEqualTo(at(2026, 2, 1, 0, 0, 0));
    }

    @Test
    void rejectsEmptyExpression() {
        assertThatThrownBy(() -> CronExpression.parse(""))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> CronExpression.parse(null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsExpressionWithWrongFieldCount() {
        assertThatThrownBy(() -> CronExpression.parse("0 0 * *"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsOutOfRangeValue() {
        assertThatThrownBy(() -> CronExpression.parse("60 0 * * *"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> CronExpression.parse("0 0 * * 8"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    /**
     * 构造指定时刻的Instant
     * @param year
     * @param month
     * @param day
     * @param hour
     * @param minute
     * @param second
     * @return
     */
    private static Instant at(int year, int month, int day, int hour, int minute, int second) {
        return ZonedDateTime.of(year, month, day, hour, minute, second, 0, ZONE).toInstant();
    }
}
