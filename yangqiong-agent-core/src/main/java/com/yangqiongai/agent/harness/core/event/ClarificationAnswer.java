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
package com.yangqiongai.agent.harness.core.event;

/**
 * 用户澄清答案
 * <p>
 * 外部应用在收到 {@link RequireUserClarificationEvent} 后，
 * 通过此 record 将用户答案注入引擎，驱动 resumeWithClarification 续接执行。
 * </p>
 * @param toolCallId 对应 ask_user 工具调用的ID
 * @param answer 用户提供的答案文本
 * @author yangqiong
 */
public record ClarificationAnswer(String toolCallId, String answer) {
}