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

import java.util.Optional;

/**
 * 注入检测器
 * <p>
 * 护栏扩展SPI：外部可提供更强的检测实现（模型分类器、向量检测、外部审核服务等），
 * 与内置规则库互补，检测结果统一汇入{@link GuardrailRuleRegistry}。
 * </p>
 * @author yangqiong
 */
public interface InjectionDetector {

    /**
     * 检测内容是否包含注入攻击
     * @param content 待检测文本
     * @param target 内容入口
     * @return 命中时返回违规描述（规则名为检测器名），未命中返回empty
     */
    Optional<GuardrailViolation> detect(String content, GuardrailTarget target);
}
