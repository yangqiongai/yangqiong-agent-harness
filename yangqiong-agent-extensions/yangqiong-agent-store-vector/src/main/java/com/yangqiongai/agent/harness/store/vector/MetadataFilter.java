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
package com.yangqiongai.agent.harness.store.vector;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 元数据过滤条件
 * <p>
 * 仅支持字段等值、IN、范围三种谓词，多条件之间为AND组合；刻意不做表达式树，
 * 保证各向量后端（Lucene标量过滤、pgvector参数化WHERE、Milvus expr、Qdrant Filter）
 * 均可低成本映射。
 * </p>
 * @author yangqiong
 */
public final class MetadataFilter {

    /**
     * 谓词类型
     */
    public enum Op {

        /**
         * 字段等值
         */
        EQ,

        /**
         * 字段取值在给定集合内
         */
        IN,

        /**
         * 大于
         */
        GT,

        /**
         * 大于等于
         */
        GTE,

        /**
         * 小于
         */
        LT,

        /**
         * 小于等于
         */
        LTE
    }

    /**
     * 单条过滤条件
     * @param field 元数据字段名
     * @param op 谓词类型
     * @param value 比较值（IN时为候选值集合）
     */
    public record Condition(String field, Op op, Object value) {

        public Condition {
            Objects.requireNonNull(field, "过滤字段不能为空");
            Objects.requireNonNull(op, "过滤谓词不能为空");
            Objects.requireNonNull(value, "过滤比较值不能为空");
        }
    }

    /**
     * 过滤条件列表（AND组合）
     */
    private final List<Condition> conditions;

    private MetadataFilter(List<Condition> conditions) {
        this.conditions = List.copyOf(conditions);
    }

    /**
     * 构建字段等值条件
     * @param field 元数据字段名
     * @param value 比较值
     * @return
     */
    public static MetadataFilter eq(String field, Object value) {
        return new MetadataFilter(List.of(new Condition(field, Op.EQ, value)));
    }

    /**
     * 构建字段IN条件
     * @param field 元数据字段名
     * @param values 候选值集合
     * @return
     */
    public static MetadataFilter in(String field, Collection<?> values) {
        Objects.requireNonNull(values, "IN候选值集合不能为空");
        return new MetadataFilter(List.of(new Condition(field, Op.IN, List.copyOf(values))));
    }

    /**
     * 构建大于条件
     * @param field 元数据字段名
     * @param value 比较值
     * @return
     */
    public static MetadataFilter gt(String field, Object value) {
        return new MetadataFilter(List.of(new Condition(field, Op.GT, value)));
    }

    /**
     * 构建大于等于条件
     * @param field 元数据字段名
     * @param value 比较值
     * @return
     */
    public static MetadataFilter gte(String field, Object value) {
        return new MetadataFilter(List.of(new Condition(field, Op.GTE, value)));
    }

    /**
     * 构建小于条件
     * @param field 元数据字段名
     * @param value 比较值
     * @return
     */
    public static MetadataFilter lt(String field, Object value) {
        return new MetadataFilter(List.of(new Condition(field, Op.LT, value)));
    }

    /**
     * 构建小于等于条件
     * @param field 元数据字段名
     * @param value 比较值
     * @return
     */
    public static MetadataFilter lte(String field, Object value) {
        return new MetadataFilter(List.of(new Condition(field, Op.LTE, value)));
    }

    /**
     * 以键值映射构建等值条件组合（保留映射迭代顺序）
     * @param equalities 键值映射，null或空时返回空过滤
     * @return
     */
    public static MetadataFilter equalities(Map<String, Object> equalities) {
        if (equalities == null || equalities.isEmpty()) {
            return new MetadataFilter(List.of());
        }
        List<Condition> conditions = new ArrayList<>(equalities.size());
        for (Map.Entry<String, Object> entry : equalities.entrySet()) {
            conditions.add(new Condition(entry.getKey(), Op.EQ, entry.getValue()));
        }
        return new MetadataFilter(conditions);
    }

    /**
     * 与另一组条件做AND组合
     * @param other 另一组过滤条件
     * @return
     */
    public MetadataFilter and(MetadataFilter other) {
        Objects.requireNonNull(other, "组合过滤条件不能为空");
        List<Condition> merged = new ArrayList<>(conditions.size() + other.conditions.size());
        merged.addAll(conditions);
        merged.addAll(other.conditions);
        return new MetadataFilter(merged);
    }

    /**
     * 获取全部过滤条件
     * @return
     */
    public List<Condition> conditions() {
        return conditions;
    }

    /**
     * 判断是否为空过滤（无条件直接放行）
     * @return
     */
    public boolean isEmpty() {
        return conditions.isEmpty();
    }

    /**
     * 校验元数据是否满足全部条件，null元数据视为不含任何字段
     * @param metadata 记录元数据
     * @return
     */
    public boolean matches(Map<String, Object> metadata) {
        for (Condition condition : conditions) {
            if (!matchCondition(metadata, condition)) {
                return false;
            }
        }
        return true;
    }

    /**
     * 校验单条条件
     * @param metadata 记录元数据
     * @param condition 过滤条件
     * @return
     */
    private boolean matchCondition(Map<String, Object> metadata, Condition condition) {
        Object actual = metadata == null ? null : metadata.get(condition.field());
        if (actual == null) {
            return false;
        }
        return switch (condition.op()) {
            case EQ -> String.valueOf(actual).equals(String.valueOf(condition.value()));
            case IN -> inValues(actual, (Collection<?>) condition.value());
            case GT -> compare(actual, condition.value()) > 0;
            case GTE -> compare(actual, condition.value()) >= 0;
            case LT -> compare(actual, condition.value()) < 0;
            case LTE -> compare(actual, condition.value()) <= 0;
        };
    }

    /**
     * 校验IN条件：按字符串形式宽容比对，与等值语义一致
     * @param actual 实际值
     * @param candidates 候选值集合
     * @return
     */
    private boolean inValues(Object actual, Collection<?> candidates) {
        String actualText = String.valueOf(actual);
        for (Object candidate : candidates) {
            if (String.valueOf(candidate).equals(actualText)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 范围比较：双方均可解析为数值时按数值比较，否则按字符串字典序比较
     * @param actual 实际值
     * @param bound 边界值
     * @return
     */
    private int compare(Object actual, Object bound) {
        BigDecimal actualNumber = toNumber(actual);
        BigDecimal boundNumber = toNumber(bound);
        if (actualNumber != null && boundNumber != null) {
            return actualNumber.compareTo(boundNumber);
        }
        return String.valueOf(actual).compareTo(String.valueOf(bound));
    }

    /**
     * 宽容解析数值，解析失败返回null
     * @param value 待解析值
     * @return
     */
    private BigDecimal toNumber(Object value) {
        if (value instanceof Number number) {
            return new BigDecimal(number.toString());
        }
        if (value instanceof String text && !text.isBlank()) {
            try {
                return new BigDecimal(text.trim());
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }
}
