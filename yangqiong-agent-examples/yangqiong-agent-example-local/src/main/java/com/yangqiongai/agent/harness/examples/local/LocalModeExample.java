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
package com.yangqiongai.agent.harness.examples.local;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.yangqiongai.agent.harness.config.AgentApprovalMode;
import com.yangqiongai.agent.harness.core.event.AgentEvent;
import com.yangqiongai.agent.harness.core.event.AgentResultEvent;
import com.yangqiongai.agent.harness.core.event.ConfirmResult;
import com.yangqiongai.agent.harness.core.event.RequireUserConfirmEvent;
import com.yangqiongai.agent.harness.core.message.AgentMessage;
import com.yangqiongai.agent.harness.core.message.AgentToolUseBlock;
import com.yangqiongai.agent.harness.engine.AgentRuntimeContext;
import com.yangqiongai.agent.harness.local.model.LocalModelDiscoverer;
import com.yangqiongai.agent.harness.local.model.LocalModelEndpoint;
import com.yangqiongai.agent.harness.local.runtime.LocalAgentHarness;
import com.yangqiongai.agent.harness.local.runtime.LocalAgentResume;
import com.yangqiongai.agent.harness.local.runtime.LocalAgentSession;
import com.yangqiongai.agent.harness.local.runtime.LocalHarnessConfig;
import com.yangqiongai.agent.harness.local.workspace.LocalWorkspace;
import com.yangqiongai.agent.harness.core.message.MessageFactory;

/**
 * 本地模式一键装配示例
 * <p>
 * 演示 yangqiong-agent-local 扩展的完整接入流程：LocalModelDiscoverer 探测本机推理服务、
 * LocalAgentHarness 一键装配（SQLite存储 + 工作区 + 本地模型 + Shell工具）、同步对话、
 * 会话JSONL落盘与 resumeLatest 检查点续跑入口。模型驱动支持两种方式：
 * 配置环境变量 AI_API_KEY/AI_MODEL/AI_BASE_URL 走远端OpenAI兼容服务（无需本机安装大模型），
 * 未配置时自动探测本机 Ollama / LM Studio，探测不到端点时友好提示并退出，全程无需任何云端 API Key。
 * </p>
 * @author yangqiong
 */
public class LocalModeExample {

    /**
     * 演示数据根目录名（位于用户目录下）
     */
    private static final String DEMO_DIR_NAME = ".yangqiong-agent-local-demo";

    /**
     * 检查点恢复的作用域标识
     */
    private static final String DEMO_SCOPE_ID = "local-demo";

    /**
     * 检查点恢复的会话标识
     */
    private static final String DEMO_SESSION_ID = "demo-session";

    /**
     * 演示对话内容
     */
    private static final String CHAT_QUESTION = "请用 local_shell 工具执行 echo hello，并把命令输出结果汇报给我。";

    /**
     * 远端OpenAI兼容服务默认模型名称
     */
    private static final String DEFAULT_REMOTE_MODEL = "deepseek-chat";

    /**
     * 远端OpenAI兼容服务默认基础地址
     */
    private static final String DEFAULT_REMOTE_BASE_URL = "https://api.deepseek.com/v1";

    private LocalModeExample() {
    }

    /**
     * 示例入口
     * @param args
     */
    public static void main(String[] args) {
        // 配置了API密钥时走远端OpenAI兼容服务，否则退回本机推理服务探测
        String apiKey = System.getenv().getOrDefault("AI_API_KEY", "");
        if (apiKey == null || apiKey.isBlank()) {
            List<LocalModelEndpoint> endpoints = discoverLocalModels();
            if (endpoints.isEmpty()) {
                return;
            }
        }
        LocalHarnessConfig config = buildDemoConfig(apiKey);
        runConversation(config);
        demonstrateApprovalModes(apiKey);
        demonstrateShellClassification(apiKey);
        demonstrateResume(config);
        System.out.println();
        System.out.println("本地模式示例演示结束。");
    }

    /**
     * 探测本机可用的推理服务端点并打印端点与模型列表
     * @return
     */
    private static List<LocalModelEndpoint> discoverLocalModels() {
        banner("探测本地推理服务");
        List<LocalModelEndpoint> endpoints = new LocalModelDiscoverer().discover();
        if (endpoints.isEmpty()) {
            System.out.println("未发现可用的本地推理服务，可任选其一：");
            System.out.println("  方式一（本机模型）：安装并启动 Ollama（https://ollama.com），再执行: ollama pull llama3");
            System.out.println("  方式二（API Key）：设置 AI_API_KEY/AI_MODEL/AI_BASE_URL 环境变量，走远端OpenAI兼容服务");
            System.out.println("  服务就绪后重新运行本示例即可。");
            return List.of();
        }
        for (LocalModelEndpoint endpoint : endpoints) {
            System.out.println("发现端点: 类型=" + endpoint.type() + "，地址=" + endpoint.baseUrl());
            System.out.println("  可用模型: " + endpoint.models());
        }
        System.out.println("后续装配将自动使用第一个端点与第一个模型。");
        return endpoints;
    }

    /**
     * 构建本地演示装配配置，配置了API密钥时显式指定远端端点走OpenAI兼容协议
     * @param apiKey 远端服务API密钥，为空时走本机推理服务自动发现
     * @return
     */
    private static LocalHarnessConfig buildDemoConfig(String apiKey) {
        banner("构建本地装配配置");
        Path rootDir = Path.of(System.getProperty("user.home"), DEMO_DIR_NAME);
        LocalHarnessConfig.Builder builder = LocalAgentHarness.builder()
                .rootDir(rootDir)
                .shellEnabled(true)
                .shellRequireApproval(false)
                .maxIters(4)
                .systemPrompt("你是运行在本地的智能助手，请用中文简洁回答，并优先使用工具完成用户任务。")
                .scopeId(DEMO_SCOPE_ID)
                .sessionId(DEMO_SESSION_ID);
        if (apiKey != null && !apiKey.isBlank()) {
            // 远端模式：模型由环境变量覆盖，走OpenAI兼容协议，本地无大模型也可演示完整流程
            String model = System.getenv().getOrDefault("AI_MODEL", DEFAULT_REMOTE_MODEL);
            String baseUrl = System.getenv().getOrDefault("AI_BASE_URL", DEFAULT_REMOTE_BASE_URL);
            builder.endpointBaseUrl(baseUrl).modelName(model).endpointApiKey(apiKey);
            System.out.println("模型模式: 远端OpenAI兼容服务（API Key）");
            System.out.println("  端点: " + baseUrl + "，模型: " + model);
        } else {
            System.out.println("模型模式: 本机推理服务自动发现");
        }
        LocalHarnessConfig config = builder.build();
        System.out.println("存储根目录: " + config.rootDir());
        System.out.println("Shell工具: " + (config.shellEnabled() ? "已开启" : "已关闭")
                + "，ReAct最大迭代次数: " + config.maxIters());
        return config;
    }

    /**
     * 装配运行时并发起一次同步对话，随后将会话记录落盘
     * @param config
     * @return
     */
    private static void runConversation(LocalHarnessConfig config) {
        banner("装配本地智能体运行时");
        LocalAgentSession session = LocalAgentHarness.create(config);
        try {
            printWorkspaceStructure(session);
            banner("发起一次同步对话");
            AgentRuntimeContext context = AgentRuntimeContext.builder()
                    .scopeId(config.scopeId())
                    .sessionId(config.sessionId())
                    .build();
            System.out.println("用户: " + CHAT_QUESTION);
            AgentMessage reply = session.runtime()
                    .call(List.of(MessageFactory.createUserMessage(CHAT_QUESTION)), context)
                    .block();
            String answer = reply != null ? reply.getTextContent() : "(未收到回复)";
            System.out.println("Agent回复: " + answer);
            appendTranscript(session, config.sessionId(), answer);
        } finally {
            // 释放运行时底层资源与SQLite数据库连接
            session.runtime().close().block();
            session.close();
            System.out.println("首个会话已关闭，数据库连接已释放。");
        }
    }

    /**
     * 打印本地工作区骨架结构
     * @param session
     * @return
     */
    private static void printWorkspaceStructure(LocalAgentSession session) {
        LocalWorkspace workspace = session.workspace();
        System.out.println("工作区根目录: " + workspace.root());
        System.out.println("  - AGENTS.md（智能体指令文件）");
        System.out.println("  - MEMORY.md（记忆文件）");
        System.out.println("  - sessions/（会话JSONL目录: " + workspace.sessionsDir() + "）");
        System.out.println("  - memory/（记忆目录）");
        System.out.println("  - skills/（技能目录）");
        System.out.println("  - knowledge/（知识目录）");
    }

    /**
     * 将本轮问答以JSONL方式追加落盘并打印会话文件行数
     * @param session
     * @param sessionId
     * @param assistantAnswer
     * @return
     */
    private static void appendTranscript(LocalAgentSession session, String sessionId, String assistantAnswer) {
        banner("会话JSONL落盘");
        Map<String, Object> userRecord = new LinkedHashMap<>();
        userRecord.put("role", "user");
        userRecord.put("content", CHAT_QUESTION);
        Map<String, Object> assistantRecord = new LinkedHashMap<>();
        assistantRecord.put("role", "assistant");
        assistantRecord.put("content", assistantAnswer);
        session.transcript().append(sessionId, userRecord);
        session.transcript().append(sessionId, assistantRecord);
        Path transcriptFile = session.workspace().sessionsDir().resolve(sessionId + ".jsonl");
        System.out.println("会话文件: " + transcriptFile);
        System.out.println("会话JSONL行数: " + session.transcript().readAll(sessionId).size());
    }

    /**
     * 演示检查点续跑入口，打印是否存在可恢复的检查点
     * @param config
     * @return
     */
    private static void demonstrateResume(LocalHarnessConfig config) {
        banner("演示resumeLatest检查点续跑入口");
        LocalAgentResume resume = LocalAgentHarness.resumeLatest(config);
        try {
            System.out.println("是否存在可恢复的检查点: " + resume.hasCheckpoint());
            resume.checkpoint().ifPresent(checkpoint -> System.out.println(
                    "最近检查点: runId=" + checkpoint.getRunId() + "，version=" + checkpoint.getVersion()));
            System.out.println("存在检查点时可调用 resume.resume() 从快照续跑事件流。");
        } finally {
            // 级联关闭底层会话与共享存储
            resume.close();
            System.out.println("恢复入口已关闭。");
        }
    }

    /**
     * 依次演示四种统一审批模式下的真实对话验证
     * <p>
     * MANUAL人工审批（暂停后批准续跑）、AUTO由AI审批模型自动判定、
     * FULL_ACCESS完全访问跳过审批、CUSTOM沿用requireApproval细粒度配置（演示拒绝）。
     * </p>
     * @param apiKey 远端服务API密钥，为空时走本机推理服务自动发现
     */
    private static void demonstrateApprovalModes(String apiKey) {
        banner("演示统一审批模式（MANUAL/AUTO/FULL_ACCESS/CUSTOM）");
        runApprovalDemo(apiKey, AgentApprovalMode.MANUAL, "approval-manual",
                "模式一 MANUAL：人工审批（暂停后批准续跑）", true);
        runApprovalDemo(apiKey, AgentApprovalMode.AUTO, "approval-auto",
                "模式二 AUTO：AI自动审批（审批模型自动判定放行或拒绝）", true);
        runApprovalDemo(apiKey, AgentApprovalMode.FULL_ACCESS, "approval-full-access",
                "模式三 FULL_ACCESS：完全访问（跳过全部审批）", true);
        runApprovalDemo(apiKey, AgentApprovalMode.CUSTOM, "approval-custom",
                "模式四 CUSTOM：自定义（requireApproval指定工具，演示人工拒绝）", false);
    }

    /**
     * 演示Shell命令内容分级审批：常规命令自动放行、危险命令自动拒绝
     * @param apiKey 远端服务API密钥，为空时走本机推理服务自动发现
     */
    private static void demonstrateShellClassification(String apiKey) {
        banner("演示Shell命令内容分级（常规放行/危险拒绝）");
        runClassificationDemo(apiKey, "classify-safe",
                "请用 local_shell 工具执行 echo hello，并汇报输出结果。", "常规命令(echo hello)");
        runClassificationDemo(apiKey, "classify-danger",
                "请用 local_shell 工具执行 del /s /q C:\\temp\\agent-demo，并汇报结果。", "危险命令(del /s /q)");
    }

    /**
     * 按命令内容分级执行一次对话，打印是否触发人工确认与Agent最终回复
     * @param apiKey 远端服务API密钥，为空时走本机推理服务自动发现
     * @param sessionId 演示会话标识
     * @param question 引导Agent执行指定命令的提问
     * @param label 演示场景说明
     */
    private static void runClassificationDemo(String apiKey, String sessionId, String question, String label) {
        System.out.println();
        System.out.println("---------- " + label + " ----------");
        LocalHarnessConfig config = approvalDemoConfig(apiKey, AgentApprovalMode.CUSTOM, sessionId);
        LocalAgentSession session = LocalAgentHarness.create(config);
        try {
            AgentRuntimeContext context = AgentRuntimeContext.builder()
                    .scopeId(config.scopeId())
                    .sessionId(config.sessionId())
                    .build();
            System.out.println("用户: " + question);
            List<AgentEvent> events = session.runtime()
                    .stream(List.of(MessageFactory.createUserMessage(question)), context)
                    .collectList().block();
            RequireUserConfirmEvent confirm = findConfirmEvent(events);
            if (confirm != null) {
                System.out.println("触发人工确认（命令归入待人工确认分级）");
            } else {
                System.out.println("未触发人工确认（命令被分级门直接放行或拒绝）");
            }
            System.out.println("Agent回复: " + extractFinalAnswer(events));
        } finally {
            session.runtime().close().block();
            session.close();
        }
    }

    /**
     * 按指定审批模式发起一次真实对话，触发审批暂停时按演示决策注入ConfirmResult续跑
     * @param apiKey 远端服务API密钥，为空时走本机推理服务自动发现
     * @param mode 审批模式
     * @param sessionId 演示会话标识
     * @param title 演示标题
     * @param approveWhenAsked 触发人工确认时true批准续跑，false拒绝并观察智能体反应
     */
    private static void runApprovalDemo(String apiKey, AgentApprovalMode mode, String sessionId,
                                        String title, boolean approveWhenAsked) {
        System.out.println();
        System.out.println("---------- " + title + " ----------");
        LocalHarnessConfig config = approvalDemoConfig(apiKey, mode, sessionId);
        LocalAgentSession session = LocalAgentHarness.create(config);
        try {
            AgentRuntimeContext context = AgentRuntimeContext.builder()
                    .scopeId(config.scopeId())
                    .sessionId(config.sessionId())
                    .build();
            System.out.println("用户: " + CHAT_QUESTION);
            List<AgentEvent> events = session.runtime()
                    .stream(List.of(MessageFactory.createUserMessage(CHAT_QUESTION)), context)
                    .collectList().block();
            RequireUserConfirmEvent confirm = findConfirmEvent(events);
            if (confirm != null) {
                List<String> pendingTools = confirm.getPendingToolCalls().stream()
                        .map(AgentToolUseBlock::getToolName).toList();
                System.out.println("引擎已暂停等待审批，待确认工具: " + pendingTools);
                List<ConfirmResult> decisions = confirm.getPendingToolCalls().stream()
                        .map(call -> approveWhenAsked
                                ? ConfirmResult.approveCall(call.getToolUseId(), call.getToolName())
                                : ConfirmResult.denyCall(call.getToolUseId(), call.getToolName(), "演示拒绝该命令"))
                        .toList();
                System.out.println(approveWhenAsked ? "人工审批结果: 批准，继续执行工具"
                        : "人工审批结果: 拒绝，观察智能体如何回应");
                events = session.runtime().resume(decisions, context).collectList().block();
            } else {
                System.out.println("未触发人工确认（工具调用被直接放行或拒绝）");
            }
            System.out.println("Agent回复: " + extractFinalAnswer(events));
        } finally {
            // 每种模式独立会话，演示完即释放底层资源
            session.runtime().close().block();
            session.close();
        }
    }

    /**
     * 构建审批演示装配配置，按模式附加对应的审批参数
     * @param apiKey 远端服务API密钥，为空时走本机推理服务自动发现
     * @param mode 审批模式
     * @param sessionId 演示会话标识
     * @return
     */
    private static LocalHarnessConfig approvalDemoConfig(String apiKey, AgentApprovalMode mode, String sessionId) {
        LocalHarnessConfig.Builder builder = LocalAgentHarness.builder()
                .rootDir(Path.of(System.getProperty("user.home"), DEMO_DIR_NAME))
                .shellEnabled(true)
                .maxIters(4)
                .systemPrompt("你是运行在本地的智能助手，请用中文简洁回答，并优先使用工具完成用户任务。")
                .scopeId(DEMO_SCOPE_ID)
                .sessionId(sessionId)
                .approvalMode(mode);
        // CUSTOM沿用requireApproval既有语义，shell默认纳入人工审批集合
        if (apiKey != null && !apiKey.isBlank()) {
            String model = System.getenv().getOrDefault("AI_MODEL", DEFAULT_REMOTE_MODEL);
            String baseUrl = System.getenv().getOrDefault("AI_BASE_URL", DEFAULT_REMOTE_BASE_URL);
            builder.endpointBaseUrl(baseUrl).modelName(model).endpointApiKey(apiKey);
        }
        // AUTO模式附加审批策略描述，约束审批模型的判定口径
        if (mode == AgentApprovalMode.AUTO) {
            builder.aiApprovalGuidance("echo等只读命令放行，删除与外发类操作拒绝");
        }
        return builder.build();
    }

    /**
     * 从事件流中提取最近一次待人工确认事件
     * @param events
     * @return 未触发时返回null
     */
    private static RequireUserConfirmEvent findConfirmEvent(List<AgentEvent> events) {
        if (events == null) {
            return null;
        }
        for (int i = events.size() - 1; i >= 0; i--) {
            if (events.get(i) instanceof RequireUserConfirmEvent confirm) {
                return confirm;
            }
        }
        return null;
    }

    /**
     * 从事件流中提取最终Agent结果文本
     * @param events
     * @return 未产生结果时返回占位提示
     */
    private static String extractFinalAnswer(List<AgentEvent> events) {
        if (events == null) {
            return "(未收到回复)";
        }
        for (int i = events.size() - 1; i >= 0; i--) {
            if (events.get(i) instanceof AgentResultEvent result
                    && result.getResult() != null
                    && result.getResult().getTextContent() != null) {
                return result.getResult().getTextContent();
            }
        }
        return "(未收到回复)";
    }

    /**
     * 打印步骤分隔横幅
     * @param title
     * @return
     */
    private static void banner(String title) {
        System.out.println();
        System.out.println("========== " + title + " ==========");
    }
}
