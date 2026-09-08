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
package com.yangqiongai.agent.harness.spi;

import java.util.List;
import java.util.Optional;

import com.yangqiongai.agent.harness.guardrail.GuardrailRule;
import com.yangqiongai.agent.harness.guardrail.GuardrailTarget;
import com.yangqiongai.agent.harness.guardrail.GuardrailViolation;
import com.yangqiongai.agent.harness.guardrail.InjectionDetector;

/**
 * SPI护栏检测器提供者测试实现
 * @author yangqiong
 */
public class TestDetectorProvider implements GuardrailDetectorProvider {

    @Override
    public List<InjectionDetector> provideDetectors() {
        return List.of((content, target) -> {
            String sample = content == null ? "" : content;
            if (sample.contains("blockspi")) {
                GuardrailRule rule = GuardrailRule.of("spi-detector", "blockspi",
                        GuardrailRule.GuardrailAction.BLOCK);
                return Optional.of(new GuardrailViolation(rule, target, "blockspi"));
            }
            return Optional.empty();
        });
    }
}