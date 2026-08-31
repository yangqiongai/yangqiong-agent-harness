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
package com.yangqiong.agent.harness.local.runtime;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

import com.yangqiong.agent.harness.HarnessRuntimeBuilder;
import com.yangqiong.agent.harness.config.AgentApprovalMode;
import com.yangqiong.agent.harness.config.AgentPermissionContextState;
import com.yangqiong.agent.harness.config.AgentPermissionMode;
import com.yangqiong.agent.harness.core.AgentRuntime;
import com.yangqiong.agent.harness.core.message.MessageFactory;
import com.yangqiong.agent.harness.core.model.AgentModel;
import com.yangqiong.agent.harness.core.tool.AgentTool;
import com.yangqiong.agent.harness.cron.CronScheduler;
import com.yangqiong.agent.harness.cron.CronTaskRunner;
import com.yangqiong.agent.harness.cron.JsonFileCronJobStore;
import com.yangqiong.agent.harness.durable.AgentCheckpoint;
import com.yangqiong.agent.harness.engine.AgentRuntimeContext;
import com.yangqiong.agent.harness.model.embedding.EmbeddingModel;
import com.yangqiong.agent.harness.model.embedding.HashingEmbeddingModel;
import com.yangqiong.agent.harness.local.knowledge.FsKnowledgeBase;
import com.yangqiong.agent.harness.local.memory.FileLongTermMemory;
import com.yangqiong.agent.harness.local.model.LocalModelDiscoverer;
import com.yangqiong.agent.harness.local.model.LocalModelEndpoint;
import com.yangqiong.agent.harness.local.model.LocalModelEndpointType;
import com.yangqiong.agent.harness.local.model.LocalModels;
import com.yangqiong.agent.harness.local.model.OllamaEmbeddingModel;
import com.yangqiong.agent.harness.local.permission.LocalShellPolicyGate;
import com.yangqiong.agent.harness.local.store.LocalDistributedStores;
import com.yangqiong.agent.harness.local.tool.LocalFileEditTool;
import com.yangqiong.agent.harness.local.tool.LocalFileDeleteTool;
import com.yangqiong.agent.harness.local.tool.LocalFileGlobTool;
import com.yangqiong.agent.harness.local.tool.LocalFileWriteTool;
import com.yangqiong.agent.harness.local.tool.LocalSandbox;
import com.yangqiong.agent.harness.local.tool.LocalShellTool;
import com.yangqiong.agent.harness.local.tool.LocalTextDocumentParser;
import com.yangqiong.agent.harness.local.workspace.FsSkillBox;
import com.yangqiong.agent.harness.local.workspace.HealthWatchdog;
import com.yangqiong.agent.harness.local.workspace.KnowledgeMaintainer;
import com.yangqiong.agent.harness.local.workspace.LocalWorkspace;
import com.yangqiong.agent.harness.local.workspace.MemoryConsolidator;
import com.yangqiong.agent.harness.local.workspace.SessionTranscriptStore;
import com.yangqiong.agent.harness.local.workspace.SkillDistiller;
import com.yangqiong.agent.harness.local.workspace.TranscriptAutoSynchronizer;
import com.yangqiong.agent.harness.permission.AiToolApprovalGate;
import com.yangqiong.agent.harness.permission.PermissionEngine;
import com.yangqiong.agent.harness.permission.ToolPolicyGate;
import com.yangqiong.agent.harness.tool.HarnessToolkit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 本地智能体一键装配
 * <p>
 * 以单一根目录为轴的装配入口：SQLite共享存储 + 工作区骨架 + 本地模型（显式指定或自动发现，
 * 无可用端点时提示安装 Ollama/LM Studio）+ 文本嵌入（Ollama可用用之，否则降级哈希嵌入）
 * + FileToolkit（沙箱绑定工作区根目录）+ 本地Shell工具，经 HarnessRuntimeBuilder 产出可运行
 * 的 AgentRuntime；resumeLatest 在同套装配之上取最近检查点提供续跑入口。
 * 部署假设：单进程单节点运行，运行锁基于同一 SQLite 库文件在多进程间互斥，
 * 不适用于跨主机多节点共享同一存储的集群场景。
 * </p>
 * @author yangqiong
 */
public final class LocalAgentHarness {

    private static final Logger log = LoggerFactory.getLogger(LocalAgentHarness.class);

    /**
     * 工作区默认目录名
     */
    private static final String WORKSPACE_DIR_NAME = "workspace";

    /**
     * 装配的Agent名称
     */
    private static final String AGENT_NAME = "local-agent";

    /**
     * 本地节点标识（运行锁持有者）
     */
    private static final String NODE_ID = "local-node";

    /**
     * Ollama文本嵌入默认模型名
     */
    private static final String DEFAULT_EMBEDDING_MODEL = "nomic-embed-text";

    /**
     * Shell工具最大输出字符数
     */
    private static final int SHELL_MAX_OUTPUT_LENGTH = 10000;

    /**
     * OpenAI兼容端点地址的路径后缀
     */
    private static final String OPENAI_COMPATIBLE_SUFFIX = "/v1";

    /**
     * knowledge/ 目录检索器注册名
     */
    private static final String KNOWLEDGE_RETRIEVER_NAME = "local_knowledge";

    /**
     * knowledge/ 目录RAG自动检索条数
     */
    private static final int KNOWLEDGE_RAG_TOP_K = 3;

    /**
     * knowledge/ 目录RAG自动检索注入模板前缀
     */
    private static final String KNOWLEDGE_RAG_TEMPLATE = "相关本地知识资料：\n";

    /**
     * 复盘记忆已处理标记文件名
     */
    private static final String CONSOLIDATE_MARKER_FILE = ".consolidated.jsonl";

    /**
     * 复盘记忆沉淀的默认属主用户
     */
    private static final String LOCAL_USER = "local";

    /**
     * 监控看门狗连续异常触发自愈阈值
     */
    private static final int HEALTH_ALARM_THRESHOLD = 3;

    /**
     * 监控看门狗心跳停滞超时
     */
    private static final Duration HEALTH_STALL_TIMEOUT = Duration.ofSeconds(60);

    /**
     * 监控看门狗周期自检间隔
     */
    private static final Duration HEALTH_CHECK_INTERVAL = Duration.ofSeconds(30);

    private LocalAgentHarness() {
    }

    /**
     * 创建本地装配配置构建器
     * @return
     */
    public static LocalHarnessConfig.Builder builder() {
        return LocalHarnessConfig.builder();
    }

    /**
     * 一键装配本地智能体运行时
     * @param config
     * @return
     */
    public static LocalAgentSession create(LocalHarnessConfig config) {
        Objects.requireNonNull(config, "config不能为空");
        log.info("开始装配本地智能体运行时: rootDir={}", config.rootDir());
        LocalDistributedStores stores = LocalDistributedStores.create(
                LocalWorkspace.harnessStateDir(config.rootDir()));
        try {
            LocalWorkspace workspace = new LocalWorkspace(effectiveWorkspaceDir(config)).init();
            log.info("本地工作区就绪: {}", workspace.root());

            ResolvedModel resolvedModel = resolveModel(config);
            EmbeddingModel embeddingModel = resolveEmbeddingModel(resolvedModel.ollamaEndpoint());
            log.info("本地模型确定: endpoint={}, model={}", resolvedModel.endpointBaseUrl(), resolvedModel.modelName());

            SessionTranscriptStore transcript = new SessionTranscriptStore(workspace);
            RuntimeAssembly assembly = buildRuntime(config, stores, workspace, transcript,
                    resolvedModel.model(), embeddingModel);
            AgentRuntime runtime = assembly.runtime();
            log.info("本地智能体运行时装配完成: agentName={}", runtime.getName());

            CronScheduler cronScheduler = assembly.cronScheduler();
            if (cronScheduler != null) {
                cronScheduler.start();
            }
            return new LocalAgentSession(runtime, stores, workspace, transcript, config, cronScheduler,
                    assembly.memoryConsolidator(), assembly.knowledgeMaintainer(),
                    assembly.healthWatchdog(), assembly.skillDistiller());
        } catch (RuntimeException e) {
            // 装配失败时释放已打开的数据库连接，避免句柄泄漏
            stores.close();
            throw e;
        }
    }

    /**
     * 装配同 create 后按配置的 scopeId 与 sessionId 取最近检查点，返回续跑入口
     * <p>
     * 无检查点时不抛异常，返回的恢复入口 hasCheckpoint 为 false 且 resume() 为空事件流。
     * </p>
     * @param config
     * @return
     */
    public static LocalAgentResume resumeLatest(LocalHarnessConfig config) {
        Objects.requireNonNull(config, "config不能为空");
        LocalAgentSession session = create(config);
        AgentCheckpoint checkpoint = session.stores().checkpointStore()
                .latest(config.scopeId(), config.sessionId())
                .orElse(null);
        if (checkpoint == null) {
            log.warn("未找到可恢复的检查点: scopeId={}, sessionId={}", config.scopeId(), config.sessionId());
        } else {
            log.info("已定位最近检查点: runId={}, version={}", checkpoint.getRunId(), checkpoint.getVersion());
        }
        return new LocalAgentResume(session, checkpoint);
    }

    /**
     * 解析本地聊天模型：端点与模型均显式指定时直接装配（配置了apiKey的远端服务或baseUrl以 /v1 结尾
     * 走OpenAI兼容协议，否则走Ollama原生协议），否则由发现器探测取第一个端点，
     * 模型名取显式配置或端点第一个模型
     * @param config
     * @return
     */
    private static ResolvedModel resolveModel(LocalHarnessConfig config) {
        if (config.endpointBaseUrl() != null && config.modelName() != null) {
            String baseUrl = normalizeBaseUrl(config.endpointBaseUrl());
            String apiKey = config.endpointApiKey();
            // 配置了apiKey说明是远端鉴权服务，强制走OpenAI兼容协议；本地服务按baseUrl后缀判断
            boolean openAiCompatible = (apiKey != null && !apiKey.isBlank())
                    || baseUrl.endsWith(OPENAI_COMPATIBLE_SUFFIX);
            AgentModel model = openAiCompatible
                    ? LocalModels.openAiCompatible(baseUrl, config.modelName(), apiKey)
                    : LocalModels.ollama(baseUrl, config.modelName());
            log.info("使用显式指定的模型端点: {}", baseUrl);
            LocalModelEndpoint ollamaEndpoint = openAiCompatible ? null
                    : new LocalModelEndpoint(LocalModelEndpointType.OLLAMA, baseUrl, List.of());
            return new ResolvedModel(model, baseUrl, config.modelName(), ollamaEndpoint);
        }

        List<LocalModelEndpoint> endpoints = new LocalModelDiscoverer(config.extraEndpointBaseUrls()).discover();
        if (endpoints.isEmpty()) {
            throw new IllegalStateException("未发现可用的本地模型服务，请先安装并启动 Ollama（https://ollama.com）"
                    + " 或 LM Studio（https://lmstudio.ai），或通过 endpointBaseUrl 与 modelName 显式指定端点");
        }
        LocalModelEndpoint endpoint = endpoints.get(0);
        String modelName = config.modelName() != null ? config.modelName() : firstModelName(endpoint);
        AgentModel model = LocalModels.fromEndpoint(endpoint, modelName);
        LocalModelEndpoint ollamaEndpoint = endpoints.stream()
                .filter(candidate -> candidate.type() == LocalModelEndpointType.OLLAMA)
                .findFirst()
                .orElse(null);
        return new ResolvedModel(model, endpoint.baseUrl(), modelName, ollamaEndpoint);
    }

    /**
     * 解析文本嵌入：存在Ollama端点且服务可用时用Ollama嵌入（优先端点模型列表中名称含embed的模型），
     * 否则降级为零依赖的哈希嵌入
     * @param ollamaEndpoint
     * @return
     */
    private static EmbeddingModel resolveEmbeddingModel(LocalModelEndpoint ollamaEndpoint) {
        if (ollamaEndpoint != null) {
            String embeddingModelName = ollamaEndpoint.models().stream()
                    .filter(name -> name.toLowerCase(Locale.ROOT).contains("embed"))
                    .findFirst()
                    .orElse(DEFAULT_EMBEDDING_MODEL);
            OllamaEmbeddingModel candidate =
                    new OllamaEmbeddingModel(ollamaEndpoint.baseUrl(), embeddingModelName);
            if (candidate.isAvailable()) {
                log.info("使用Ollama本地文本嵌入: model={}", embeddingModelName);
                return candidate;
            }
            log.info("Ollama嵌入服务不可用，降级为哈希嵌入");
        }
        return new HashingEmbeddingModel();
    }

    /**
     * 经 HarnessRuntimeBuilder 组装运行时：注入共享存储、嵌入模型、文件工具箱、本地Shell工具、
     * 定时任务工具集，并启用工作区目录双向同步（AGENTS.md读入、技能发现、会话自动落盘）
     * @param config
     * @param stores
     * @param workspace
     * @param transcript
     * @param model
     * @param embeddingModel
     * @return
     */
    private static RuntimeAssembly buildRuntime(LocalHarnessConfig config, LocalDistributedStores stores,
                                                LocalWorkspace workspace, SessionTranscriptStore transcript,
                                                AgentModel model, EmbeddingModel embeddingModel) {
        HarnessToolkit baseToolkit = new HarnessToolkit(null);
        // 本地文件写/改/查/删工具，沙箱绑定工作区根目录，支撑代码生成类任务落盘与清理
        LocalSandbox sandbox = new LocalSandbox(workspace.root());
        baseToolkit.addTool(new LocalFileWriteTool(sandbox));
        baseToolkit.addTool(new LocalFileEditTool(sandbox));
        baseToolkit.addTool(new LocalFileGlobTool(sandbox));
        baseToolkit.addTool(new LocalFileDeleteTool(sandbox));
        if (config.shellEnabled()) {
            baseToolkit.addTool(new LocalShellTool(workspace.root(), config.shellTimeout(),
                    SHELL_MAX_OUTPUT_LENGTH));
        }
        // 运行时在 builder.build() 后才产生，用引用延迟绑定任务执行器
        AtomicReference<AgentRuntime> runtimeRef = new AtomicReference<>();
        CronScheduler cronScheduler = null;
        if (config.cronEnabled()) {
            CronTaskRunner runner = job -> runtimeRef.get().call(
                    List.of(MessageFactory.createUserMessage(job.getInstruction())),
                    AgentRuntimeContext.builder()
                            .scopeId(job.getScopeId())
                            .sessionId(job.getSessionId())
                            .build());
            Path cronStoreFile = config.cronStoreFile() != null
                    ? config.cronStoreFile() : LocalWorkspace.harnessStateDir(config.rootDir())
                            .resolve("cron").resolve("jobs.json");
            cronScheduler = CronScheduler.builder()
                    .store(new JsonFileCronJobStore(cronStoreFile))
                    .runner(runner)
                    .nodeId(NODE_ID)
                    .runLockStore(stores.runLockStore())
                    .build();
            for (AgentTool tool : cronScheduler.tools()) {
                baseToolkit.addTool(tool);
            }
        }
        HarnessRuntimeBuilder builder = new HarnessRuntimeBuilder()
                .name(AGENT_NAME)
                .model(model)
                .maxIters(config.maxIters())
                .stores(stores)
                .nodeId(NODE_ID)
                .embeddingModel(embeddingModel)
                .enableFileToolkit(workspace.root().toString(), new LocalTextDocumentParser())
                .toolkit(baseToolkit);
        if (config.workspaceSyncEnabled()) {
            wireWorkspaceSync(builder, config, workspace, transcript);
        } else if (config.systemPrompt() != null) {
            builder.systemPrompt(config.systemPrompt());
        }
        // 统一审批模式优先：MANUAL人工、AUTO AI裁判、FULL_ACCESS跳过全部、CUSTOM沿用细粒度
        if (config.approvalMode() != null) {
            builder.approvalMode(config.approvalMode());
            // AUTO模式未显式指定审批裁判模型时复用主模型作为裁判，零额外配置即可运行
            if (config.approvalMode() == AgentApprovalMode.AUTO) {
                builder.approvalJudgeModel(model);
            }
        }
        // AUTO模式时可附加审批策略描述，约束AI裁判判定口径
        if (config.aiApprovalGuidance() != null && !config.aiApprovalGuidance().isBlank()) {
            builder.aiApprovalGuidance(config.aiApprovalGuidance());
        }
        // Shell属高风险工具：启用命令内容分级审批，只读放行、危险拒绝、其余转人工确认
        if (config.shellEnabled() && config.shellRequireApproval()
                && config.approvalMode() != AgentApprovalMode.FULL_ACCESS) {
            builder.toolPolicyGate(new LocalShellPolicyGate(buildNonShellDelegate(config, model)));
        }
        SkillDistiller skillDistiller = config.skillDistillEnabled()
                ? new SkillDistiller(workspace.skillsDir()) : null;
        HealthWatchdog healthWatchdog = config.healthWatchdogEnabled()
                ? wireHealthWatchdog(builder) : null;
        if (skillDistiller != null) {
            builder.middleware(skillDistiller);
        }
        AgentRuntime runtime = builder.build();
        if (cronScheduler != null) {
            runtimeRef.set(runtime);
        }
        MemoryConsolidator memoryConsolidator = config.memoryConsolidateEnabled()
                ? new MemoryConsolidator(transcript,
                        new FileLongTermMemory(workspace.memoryDir()),
                        workspace.memoryDir().resolve(CONSOLIDATE_MARKER_FILE),
                        config.scopeId(), LOCAL_USER) : null;
        KnowledgeMaintainer knowledgeMaintainer = config.knowledgeMaintainEnabled()
                ? new KnowledgeMaintainer(workspace.knowledgeDir(), workspace.memoryDir()) : null;
        if (healthWatchdog != null) {
            healthWatchdog.start();
        }
        return new RuntimeAssembly(runtime, cronScheduler, memoryConsolidator,
                knowledgeMaintainer, healthWatchdog, skillDistiller);
    }

    /**
     * 装配监控看门狗并注册为运行时事件监听器
     * @param builder
     * @return
     */
    private static HealthWatchdog wireHealthWatchdog(HarnessRuntimeBuilder builder) {
        HealthWatchdog watchdog = new HealthWatchdog(HEALTH_ALARM_THRESHOLD,
                HEALTH_STALL_TIMEOUT, HEALTH_CHECK_INTERVAL);
        builder.addEventListener(watchdog);
        return watchdog;
    }

    /**
     * 构建非Shell工具的委托策略门，按审批模式对齐上层既有语义
     * @param config
     * @param model
     * @return
     */
    private static ToolPolicyGate buildNonShellDelegate(LocalHarnessConfig config, AgentModel model) {
        AgentApprovalMode mode = config.approvalMode();
        if (mode == AgentApprovalMode.AUTO) {
            return new AiToolApprovalGate(model, null, null, null, true, config.aiApprovalGuidance());
        }
        if (mode == AgentApprovalMode.MANUAL) {
            PermissionEngine engine = new PermissionEngine(
                    AgentPermissionContextState.builder().mode(AgentPermissionMode.ASK).build(), null);
            return new ToolPolicyGate(engine, null, null, false, null);
        }
        return new ToolPolicyGate(null, null, null, false, null);
    }

    /**
     * 启用工作区目录双向同步：AGENTS.md读入、跨会话记忆简报MEMORY.md读入、
     * 长期记忆落盘到 memory/ 目录、技能目录发现挂载、knowledge/ 构建RAG检索器、
     * 会话事件自动落盘为JSONL转录
     * @param builder
     * @param config
     * @param workspace
     * @param transcript
     */
    private static void wireWorkspaceSync(HarnessRuntimeBuilder builder, LocalHarnessConfig config,
                                          LocalWorkspace workspace, SessionTranscriptStore transcript) {
        // 记忆简报（MEMORY.md）位于系统指令与技能摘要之间，作为跨会话记忆上下文
        String agentInstructions = config.systemPrompt() != null
                ? config.systemPrompt() : workspace.readAgentsInstructions();
        String prompt = agentInstructions;
        String memoryBriefing = config.memoryFileEnabled() ? workspace.readMemory() : "";
        if (memoryBriefing != null && !memoryBriefing.isBlank()) {
            prompt = (prompt != null && !prompt.isBlank() ? prompt : "") + "\n\nMemory briefing:\n" + memoryBriefing;
        }
        com.yangqiong.agent.harness.core.skill.AgentSkillBox skillBox =
                FsSkillBox.discover(workspace.skillsDir());
        if (!skillBox.isEmpty()) {
            builder.skillBox(skillBox);
            String skillSummary = skillBox.buildSystemPrompt();
            if (!skillSummary.isBlank()) {
                prompt = (prompt != null && !prompt.isBlank() ? prompt : "") + "\n\n" + skillSummary;
            }
        }
        if (prompt != null && !prompt.isBlank()) {
            builder.systemPrompt(prompt);
        }
        // 长期记忆落盘到 memory/ 目录，替代Sqlite作为L3事实源，自动带出记忆工具与检索注入
        builder.longTermMemory(new FileLongTermMemory(workspace.memoryDir()));
        // knowledge/ 目录构建RAG检索器，非空时启用自动检索注入
        if (config.knowledgeRagEnabled()) {
            FsKnowledgeBase knowledgeBase = FsKnowledgeBase.open(workspace.knowledgeDir());
            if (!knowledgeBase.isEmpty()) {
                builder.retriever(KNOWLEDGE_RETRIEVER_NAME, knowledgeBase.retriever());
                builder.enableRagRetrieval(KNOWLEDGE_RAG_TOP_K, KNOWLEDGE_RAG_TEMPLATE);
            }
        }
        builder.addEventListener(new TranscriptAutoSynchronizer(transcript, config.sessionId()));
    }

    /**
     * 计算生效的工作区目录，未配置时默认 {rootDir}/workspace
     * @param config
     * @return
     */
    private static Path effectiveWorkspaceDir(LocalHarnessConfig config) {
        return config.workspaceDir() != null ? config.workspaceDir() : config.rootDir().resolve(WORKSPACE_DIR_NAME);
    }

    /**
     * 取端点模型列表的第一个模型名，列表为空时抛出安装提示
     * @param endpoint
     * @return
     */
    private static String firstModelName(LocalModelEndpoint endpoint) {
        if (endpoint.models().isEmpty()) {
            throw new IllegalStateException("端点 " + endpoint.baseUrl() + " 可达但未安装任何模型，"
                    + "请先拉取模型（如 ollama pull llama3）或通过 modelName 显式指定");
        }
        return endpoint.models().get(0);
    }

    /**
     * 规范化基础地址，去除尾部斜杠
     * @param baseUrl
     * @return
     */
    private static String normalizeBaseUrl(String baseUrl) {
        return baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
    }

    /**
     * 模型解析结果
     * @author yangqiong
     */
    private record ResolvedModel(AgentModel model, String endpointBaseUrl, String modelName,
                                 LocalModelEndpoint ollamaEndpoint) {
    }

    /**
     * 装配结果
     * @author yangqiong
     */
    private record RuntimeAssembly(AgentRuntime runtime, CronScheduler cronScheduler,
                                   MemoryConsolidator memoryConsolidator,
                                   KnowledgeMaintainer knowledgeMaintainer,
                                   HealthWatchdog healthWatchdog,
                                   SkillDistiller skillDistiller) {
    }
}
