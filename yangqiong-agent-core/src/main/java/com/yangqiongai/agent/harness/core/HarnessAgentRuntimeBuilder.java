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
package com.yangqiongai.agent.harness.core;

import java.time.Duration;
import java.util.Set;

import com.yangqiongai.agent.harness.config.AgentApprovalMode;
import com.yangqiongai.agent.harness.core.memory.AgentLongTermMemory;
import com.yangqiongai.agent.harness.core.memory.SessionMemory;
import com.yangqiongai.agent.harness.core.model.AgentModel;

/**
 * Agent运行时构建器（Harness特性）
 * <p>
 * 扩展高级构建器，提供AgentScope Harness特有的开关配置。
 * 仅Harness适配器实现需要支持这些配置项。
 * 所有方法均提供默认空实现，子类按需覆盖。
 * </p>
 * @author yangqiong
 */
public interface HarnessAgentRuntimeBuilder extends AdvancedAgentRuntimeBuilder {

    /**
     * 设置动态子代理启用开关（模式4 auto_orchestrate），默认启用
     * @param enabled
     * @return
     */
    default HarnessAgentRuntimeBuilder dynamicSubagentsEnabled(boolean enabled) {
        return this;
    }

    /**
     * 设置长期记忆工具启用开关（memory_search/memory_store/memory_delete），默认启用
     * @param enabled
     * @return
     */
    default HarnessAgentRuntimeBuilder memoryToolsEnabled(boolean enabled) {
        return this;
    }

    /**
     * 设置记忆自动持久化钩子启用开关，默认启用
     * @param enabled
     * @return
     */
    default HarnessAgentRuntimeBuilder memoryHooksEnabled(boolean enabled) {
        return this;
    }

    // ========== 记忆子系统SPI（L2短期记忆/L3长期记忆） ==========

    /**
     * 注入L3长期记忆存储，启用后装配记忆工具、自动持久化钩子与L3→L1检索注入
     * @param longTermMemory 长期记忆实现，null时不启用
     * @return
     */
    default HarnessAgentRuntimeBuilder longTermMemory(AgentLongTermMemory longTermMemory) {
        return this;
    }

    /**
     * 注入L2会话级短期记忆，启用后支持摘要压缩的增量摘要缓存
     * @param sessionMemory 会话记忆实现，null时不启用
     * @return
     */
    default HarnessAgentRuntimeBuilder sessionMemory(SessionMemory sessionMemory) {
        return this;
    }

    /**
     * 设置基于LLM摘要的上下文压缩开关（与截断式压缩互斥），默认关闭
     * @param enabled
     * @return
     */
    default HarnessAgentRuntimeBuilder summaryCompactionEnabled(boolean enabled) {
        return this;
    }

    /**
     * 设置L3→L1检索注入开关，仅在配置了长期记忆时生效，默认启用
     * @param enabled
     * @return
     */
    default HarnessAgentRuntimeBuilder memoryRetrievalEnabled(boolean enabled) {
        return this;
    }

    /**
     * 设置L3→L1检索注入的条目上限，默认3
     * @param topK
     * @return
     */
    default HarnessAgentRuntimeBuilder memoryRetrievalTopK(int topK) {
        return this;
    }

    // ========== 执行控制 ==========

    /**
     * 设置单次Agent执行总超时
     * @param timeout 超时时间，null时不限制
     * @return
     */
    default HarnessAgentRuntimeBuilder timeout(Duration timeout) {
        return this;
    }

    /**
     * 设置单轮推理+工具执行超时
     * @param timeout 超时时间，null时不限制
     * @return
     */
    default HarnessAgentRuntimeBuilder iterationTimeout(Duration timeout) {
        return this;
    }

    /**
     * 设置最大并行工具调用数
     * @param max 最大并行数，默认1
     * @return
     */
    default HarnessAgentRuntimeBuilder maxConcurrentToolCalls(int max) {
        return this;
    }

    /**
     * 设置单次工具调用超时，防止单个卡住的工具阻塞ReAct循环
     * @param timeout 超时时间，null或零表示不限制，默认60秒
     * @return
     */
    default HarnessAgentRuntimeBuilder toolCallTimeout(Duration timeout) {
        return this;
    }

    // ========== 工具安全 ==========

    /**
     * 设置工具白名单（仅允许调用的工具名称集合）
     * @param toolNames 工具名称集合，null或空时不限制
     * @return
     */
    default HarnessAgentRuntimeBuilder allowedTools(Set<String> toolNames) {
        return this;
    }

    /**
     * 设置工具黑名单（禁止调用的工具名称集合）
     * @param toolNames 工具名称集合，null或空时不限制
     * @return
     */
    default HarnessAgentRuntimeBuilder deniedTools(Set<String> toolNames) {
        return this;
    }

    /**
     * 设置需要人工审批才能执行的工具名称集合
     * @param toolNames 工具名称集合，null或空时不限制
     * @return
     */
    default HarnessAgentRuntimeBuilder requireApproval(Set<String> toolNames) {
        return this;
    }

    /**
     * 设置统一审批模式
     * <p>
     * 四类模式的统一配置入口：MANUAL人工审批（破坏性工具暂停等待人工确认）、
     * AUTO由AI审批模型依据工具调用入参自动判定、FULL_ACCESS完全访问跳过全部审批、
     * CUSTOM沿用既有权限模式、规则、名单与策略门细粒度配置（默认）。
     * </p>
     * @param approvalMode 审批模式，null时按CUSTOM处理
     * @return
     */
    default HarnessAgentRuntimeBuilder approvalMode(AgentApprovalMode approvalMode) {
        return this;
    }

    /**
     * 设置AI自动审批（AUTO模式）的审批模型
     * @param judgeModel 审批模型，AUTO模式必填，未设置时构建报错
     * @return
     */
    default HarnessAgentRuntimeBuilder approvalJudgeModel(AgentModel judgeModel) {
        return this;
    }

    /**
     * 设置AI审批判定失败时的回退行为
     * @param fallbackAsk true回退人工确认（默认），false直接拒绝
     * @return
     */
    default HarnessAgentRuntimeBuilder aiApprovalFallbackAsk(boolean fallbackAsk) {
        return this;
    }

    /**
     * 设置AI审批附加策略描述，拼入判定提示词约束审批模型
     * @param guidance 策略描述，可空
     * @return
     */
    default HarnessAgentRuntimeBuilder aiApprovalGuidance(String guidance) {
        return this;
    }

    // ========== 错误策略 ==========

    /**
     * 设置工具失败后处理策略
     * @param strategy 策略枚举：RETRY重试 / SKIP跳过继续 / ABORT中止整个Agent
     * @return
     */
    default HarnessAgentRuntimeBuilder toolFailureStrategy(ToolFailureStrategy strategy) {
        return this;
    }

    /**
     * 设置单个工具最大重试次数
     * @param maxRetries 最大重试次数，默认0
     * @return
     */
    default HarnessAgentRuntimeBuilder maxToolRetries(int maxRetries) {
        return this;
    }

    /**
     * 设置工具结果最大字符数，超出时截断并附加截断标记，防止大结果撑爆上下文窗口
     * @param maxChars 最大字符数，0表示不限制，默认10000
     * @return
     */
    default HarnessAgentRuntimeBuilder maxToolResultChars(int maxChars) {
        return this;
    }

    /**
     * 设置最大连续工具失败次数，超过阈值时中止Agent，防止LLM反复调用同一失败工具
     * @param maxFailures 最大连续失败次数，0表示不限制，默认3
     * @return
     */
    default HarnessAgentRuntimeBuilder maxConsecutiveToolFailures(int maxFailures) {
        return this;
    }

    // ========== 上下文管理 ==========

    /**
     * 设置上下文窗口Token上限，超出时触发截断或压缩
     * @param maxTokens 最大Token数，0或不设置时由模型自行决定
     * @return
     */
    default HarnessAgentRuntimeBuilder maxContextTokens(int maxTokens) {
        return this;
    }

    /**
     * 设置历史截断策略
     * @param strategy 策略枚举：HEAD保留最早 / TAIL保留最新 / SUMMARY摘要压缩
     * @return
     */
    default HarnessAgentRuntimeBuilder historyTruncationStrategy(TruncationStrategy strategy) {
        return this;
    }

    /**
     * 设置单次Agent执行总超时模式
     * @param mode 超时模式，默认{@link TimeoutMode#WALL_CLOCK}
     * @return
     */
    default HarnessAgentRuntimeBuilder timeoutMode(TimeoutMode mode) {
        return this;
    }

    // ========== 枚举定义 ==========

    /**
     * 工具失败处理策略
     */
    enum ToolFailureStrategy {
        /** 重试工具调用 */
        RETRY,
        /** 跳过失败工具，继续推理 */
        SKIP,
        /** 中止整个Agent执行 */
        ABORT
    }

    /**
     * 历史截断策略
     */
    enum TruncationStrategy {
        /** 保留最早的消息 */
        HEAD,
        /** 保留最新的消息 */
        TAIL,
        /** 对历史消息进行摘要压缩 */
        SUMMARY
    }

    /**
     * 单次Agent执行总超时模式
     * <p>
     * 硬墙钟模式到点必断，无论模型是否持续输出；空闲模式仅在模型停顿超过超时时长才触发。
     * </p>
     */
    enum TimeoutMode {
        /** 硬墙钟总超时：到点无论是否有输出都截断并发ERROR（默认） */
        WALL_CLOCK,
        /** 空闲超时：仅在无事件发射超过超时时长时触发，持续出词不掐断 */
        IDLE
    }
}
