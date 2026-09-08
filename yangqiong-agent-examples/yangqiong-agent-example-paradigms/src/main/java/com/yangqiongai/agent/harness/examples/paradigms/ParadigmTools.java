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
package com.yangqiongai.agent.harness.examples.paradigms;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.yangqiongai.agent.harness.core.message.AgentTextBlock;
import com.yangqiongai.agent.harness.core.message.AgentToolResultBlock;
import com.yangqiongai.agent.harness.core.tool.AgentTool;
import com.yangqiongai.agent.harness.core.tool.AgentToolCallParam;

import reactor.core.publisher.Mono;

/**
 * 范式示例工具集
 * @author yangqiong
 */
public class ParadigmTools {

    private ParadigmTools() {
    }

    /**
     * 计算器工具，支持四则运算
     * @return
     */
    public static AgentTool calculator() {
        return new CalculatorTool();
    }

    /**
     * 计算器工具实现
     * @author yangqiong
     */
    public static class CalculatorTool implements AgentTool {

        @Override
        public String getName() {
            return "calculator";
        }

        @Override
        public String getDescription() {
            return "执行四则运算，输入数学表达式，返回计算结果";
        }

        @Override
        public Map<String, Object> getParameters() {
            Map<String, Object> params = new HashMap<>();
            Map<String, Object> exprSchema = new HashMap<>();
            exprSchema.put("type", "string");
            exprSchema.put("description", "数学表达式，如 2+3*4");
            params.put("expression", exprSchema);
            return params;
        }

        @Override
        public Mono<AgentToolResultBlock> callAsync(AgentToolCallParam param) {
            Object exprObj = param.getInput().get("expression");
            String expression = exprObj != null ? exprObj.toString() : "";
            String result;
            try {
                double value = new SimpleCalculator().evaluate(expression);
                if (value == (long) value) {
                    result = String.valueOf((long) value);
                } else {
                    result = String.valueOf(value);
                }
            } catch (Exception e) {
                result = "计算失败: " + e.getMessage();
            }
            AgentTextBlock textBlock = AgentTextBlock.builder().text(result).build();
            return Mono.just(AgentToolResultBlock.of(List.of(textBlock)));
        }
    }

    /**
     * 简单四则运算计算器
     * <p>
     * 支持加减乘除和括号，不依赖JDK脚本引擎。
     * </p>
     * @author yangqiong
     */
    public static class SimpleCalculator {

        /**
         * 表达式字符
         */
        private String expression;

        /**
         * 当前解析位置
         */
        private int position;

        /**
         * 求值入口
         * @param expression
         * @return
         */
        public double evaluate(String expression) {
            this.expression = expression.replaceAll("\\s", "");
            this.position = 0;
            double result = parseExpression();
            if (position < this.expression.length()) {
                throw new IllegalArgumentException("无效字符: " + this.expression.charAt(position));
            }
            return result;
        }

        /**
         * 解析加减
         */
        private double parseExpression() {
            double left = parseTerm();
            while (position < expression.length()) {
                char op = expression.charAt(position);
                if (op == '+') {
                    position++;
                    left += parseTerm();
                } else if (op == '-') {
                    position++;
                    left -= parseTerm();
                } else {
                    break;
                }
            }
            return left;
        }

        /**
         * 解析乘除
         */
        private double parseTerm() {
            double left = parseFactor();
            while (position < expression.length()) {
                char op = expression.charAt(position);
                if (op == '*') {
                    position++;
                    left *= parseFactor();
                } else if (op == '/') {
                    position++;
                    left /= parseFactor();
                } else {
                    break;
                }
            }
            return left;
        }

        /**
         * 解析因子
         */
        private double parseFactor() {
            if (position < expression.length() && expression.charAt(position) == '(') {
                position++;
                double result = parseExpression();
                if (position >= expression.length() || expression.charAt(position) != ')') {
                    throw new IllegalArgumentException("缺少右括号");
                }
                position++;
                return result;
            }
            int start = position;
            while (position < expression.length()
                    && (Character.isDigit(expression.charAt(position)) || expression.charAt(position) == '.')) {
                position++;
            }
            if (start == position) {
                throw new IllegalArgumentException("期望数字，位置: " + position);
            }
            return Double.parseDouble(expression.substring(start, position));
        }
    }
}
