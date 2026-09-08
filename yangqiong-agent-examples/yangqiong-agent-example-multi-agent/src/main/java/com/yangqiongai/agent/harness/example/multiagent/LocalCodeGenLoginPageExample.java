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
package com.yangqiongai.agent.harness.example.multiagent;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

import com.yangqiongai.agent.harness.HarnessRuntimeBuilder;
import com.yangqiongai.agent.harness.config.AgentApprovalMode;
import com.yangqiongai.agent.harness.core.AgentRuntime;
import com.yangqiongai.agent.harness.core.AgentRuntimeFactory;
import com.yangqiongai.agent.harness.core.event.AgentEvent;
import com.yangqiongai.agent.harness.core.event.AgentEventType;
import com.yangqiongai.agent.harness.core.message.AgentContentBlock;
import com.yangqiongai.agent.harness.core.message.AgentMessage;
import com.yangqiongai.agent.harness.core.message.AgentMessageRole;
import com.yangqiongai.agent.harness.core.message.AgentTextBlock;
import com.yangqiongai.agent.harness.core.message.AgentToolResultBlock;
import com.yangqiongai.agent.harness.core.message.AgentToolUseBlock;
import com.yangqiongai.agent.harness.core.model.AgentModel;
import com.yangqiongai.agent.harness.engine.AgentRuntimeContext;
import com.yangqiongai.agent.harness.local.model.LocalModels;
import com.yangqiongai.agent.harness.local.runtime.LocalAgentHarness;
import com.yangqiongai.agent.harness.local.runtime.LocalAgentSession;
import com.yangqiongai.agent.harness.local.runtime.LocalHarnessConfig;
import com.yangqiongai.agent.harness.local.tool.LocalFileDeleteTool;
import com.yangqiongai.agent.harness.local.tool.LocalFileEditTool;
import com.yangqiongai.agent.harness.local.tool.LocalFileGlobTool;
import com.yangqiongai.agent.harness.local.tool.LocalFileWriteTool;
import com.yangqiongai.agent.harness.local.tool.LocalSandbox;
import com.yangqiongai.agent.harness.local.tool.LocalTextDocumentParser;
import com.yangqiongai.agent.harness.subagent.orchestration.SubagentDeclaration;
import com.yangqiongai.agent.harness.tool.HarnessToolkit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 本地模式代码生成能力验证
 * <p>
 * 通过 LocalAgentHarness 一键装配本地智能体，让 Agent 使用沙箱文件工具在工作区
 * 生成 HTML 登录页面（login.html 独立于样式文件 css/style.css），运行后校验产物
 * 是否真实落盘，用于验证本地模式"代码生成并落盘"的能力闭环。
 * </p>
 * <p>
 * 覆盖三种场景：单Agent生成一套登录页并修改主题色；主Agent委派2个子Agent，
 * 同时生成两套不同主题（蓝色、绿色）的登录页，验证子Agent并行产出能力；
 * 动态生成子Agent：不手动声明，由大模型按任务自动拆解子Agent并决定主题颜色。
 * </p>
 * <p>
 * 模型信息与 RealModelTravelApprovalExample 保持一致：默认对接 DeepSeek OpenAI兼容
 * 服务（需配置 HARNESS_TEST_API_KEY），也可通过环境变量切换到本机 Ollama。
 * </p>
 * @author yangqiong
 */
public class LocalCodeGenLoginPageExample {

    private static final Logger log = LoggerFactory.getLogger(LocalCodeGenLoginPageExample.class);

    /**
     * 模型编码，支持 provider:model 前缀，与真实模型示例保持一致
     */
    private static final String MODEL_CODE = env("HARNESS_TEST_MODEL_CODE", "openai:deepseek-v4-flash");

    /**
     * API密钥（远端OpenAI兼容服务必填，本机Ollama可留空）
     */
    private static final String API_KEY = env("HARNESS_TEST_API_KEY", "");

    /**
     * API基础地址
     */
    private static final String BASE_URL = env("HARNESS_TEST_BASE_URL", "https://api.deepseek.com");

    /**
     * 请求超时秒数
     */
    private static final int TIMEOUT_SECONDS = Integer.parseInt(env("HARNESS_TEST_TIMEOUT_SECONDS", "120"));

    /**
     * 模型名称
     */
    private static final String MODEL_NAME = MODEL_CODE.contains(":")
            ? MODEL_CODE.substring(MODEL_CODE.indexOf(':') + 1) : MODEL_CODE;

    /**
     * ReAct循环最大迭代次数
     */
    private static final int MAX_ITERS = 12;

    private LocalCodeGenLoginPageExample() {
    }

    /**
     * 示例入口
     * @param args
     */
    public static void main(String[] args) {
        if (!isConfigured()) {
            log.error("未配置环境变量 HARNESS_TEST_API_KEY，无法运行真实大模型示例，请先设置后再执行。");
            return;
        }
     //   runSingleAgentCodegen();
      //  runMultiAgentCodegen();
        runDynamicSubagentCodegen();
    }

    /**
     * 单Agent验证：一个Agent生成一套登录页并修改主题色
     */
    private static void runSingleAgentCodegen() {
        log.info("========== 单Agent验证：生成一套登录页 ==========");
        runOneAgent("local-codegen");
    }

    /**
     * 多Agent验证：主Agent委派2个子Agent同时生成两套不同主题的登录页
     */
    private static void runMultiAgentCodegen() {
        log.info("========== 多Agent验证：主Agent委派2个子Agent生成两套不同主题登录页 ==========");
        LocalAgentSession session = null;
        AgentRuntime coordinator = null;
        try {
            session = createSession("local-codegen-subagent");
            Path workspace = session.workspace().root();
            log.info("本地智能体会话装配完成，工作区: {}", workspace);

            AgentModel model = LocalModels.openAiCompatible(BASE_URL, MODEL_NAME, API_KEY);
            // 父级工具箱：本地文件写/改/查工具，沙箱绑定工作区根目录，子Agent继承后可直接落盘
            LocalSandbox sandbox = new LocalSandbox(workspace);
            HarnessToolkit toolkit = new HarnessToolkit(null);
            toolkit.addTool(new LocalFileWriteTool(sandbox));
            toolkit.addTool(new LocalFileEditTool(sandbox));
            toolkit.addTool(new LocalFileGlobTool(sandbox));
            toolkit.addTool(new LocalFileDeleteTool(sandbox));

            SubagentDeclaration blueAgent = SubagentDeclaration.builder()
                    .name("blue-theme-login")
                    .description("资深前端工程师，生成一套蓝色主题登录页")
                    .systemPrompt("你是资深前端工程师。请在沙箱工作区根目录下的 blue-theme 目录创建一套"
                            + "蓝色主题的登录页：创建 blue-theme/login.html 与独立的 blue-theme/css/style.css，"
                            + "主色调为蓝色系（如 #1e6fff），用 file_write 工具落盘，完成后用中文简述页面结构。")
                    .maxIterations(10)
                    .build();

            SubagentDeclaration greenAgent = SubagentDeclaration.builder()
                    .name("green-theme-login")
                    .description("资深前端工程师，生成一套绿色主题登录页")
                    .systemPrompt("你是资深前端工程师。请在沙箱工作区根目录下的 green-theme 目录创建一套"
                            + "绿色主题的登录页：创建 green-theme/login.html 与独立的 green-theme/css/style.css，"
                            + "主色调为绿色系（如 #27ae60），用 file_write 工具落盘，完成后用中文简述页面结构。")
                    .maxIterations(10)
                    .build();

            // 子代理运行时工厂：子代理复用主Agent模型与文件解析能力，文件工具从父级工具箱继承
            AgentRuntimeFactory runtimeFactory = () -> new HarnessRuntimeBuilder()
                    .model(model)
                    .enableFileToolkit(workspace.toString(), new LocalTextDocumentParser());

            coordinator = new HarnessRuntimeBuilder()
                    .name("codegen-coordinator")
                    .model(model)
                    .maxIters(MAX_ITERS)
                    .maxConcurrentToolCalls(2)
                    .systemPrompt("你是前端代码生成总协调员。收到创建多套登录页的任务后，将任务按主题分解，"
                            + "在同一轮中同时调用 agent_spawn_blue-theme-login 与 agent_spawn_green-theme-login "
                            + "两个子代理工具并行委派，每个子代理生成一套不同主题的登录页，"
                            + "全部完成后用中文汇总两套产物的结构说明。")
                    .toolkit(toolkit)
                    .enableFileToolkit(workspace.toString(), new LocalTextDocumentParser())
                    .subagentDeclarations(List.of(blueAgent, greenAgent))
                    .runtimeFactory(runtimeFactory)
                    // 子Agent在工具调用内运行，放宽工具调用超时
                    .toolCallTimeout(Duration.ofSeconds(TIMEOUT_SECONDS * 5))
                    .build();

            List<AgentEvent> events = coordinator.stream(
                    List.of(userMessage("请创建两套登录页面：一套蓝色主题、一套绿色主题，"
                            + "每套都包含 login.html 与独立的 css/style.css 样式文件。"
                            + "请委派两个子代理同时完成。")),
                    sessionContext()
            ).doOnNext(LocalCodeGenLoginPageExample::logSubagentEvent)
                    .collectList().block(Duration.ofSeconds(TIMEOUT_SECONDS * 3));

            log.info("主Agent最终汇总: {}", extractFinalText(events));
            verifySubagentFiles(workspace);
        } catch (IllegalStateException e) {
            log.error("本地智能体装配失败: {}", e.getMessage());
        } catch (Exception e) {
            log.error("多Agent代码生成验证过程出现异常", e);
        } finally {
            if (coordinator != null) {
                coordinator.close().block();
            }
            if (session != null) {
                session.close();
            }
        }
    }

    /**
     * 动态子Agent验证：不手动声明子Agent，启用自动编排，由大模型按任务拆解
     * 并动态生成2个子Agent，各自生成一套登录页，主题颜色由大模型决定
     */
    private static void runDynamicSubagentCodegen() {
        log.info("========== 多Agent验证：动态生成子Agent（主题颜色由大模型决定） ==========");
        LocalAgentSession session = null;
        AgentRuntime coordinator = null;
        try {
            session = createSession("local-codegen-dynamic");
            Path workspace = session.workspace().root();
            log.info("本地智能体会话装配完成，工作区: {}", workspace);

            AgentModel model = LocalModels.openAiCompatible(BASE_URL, MODEL_NAME, API_KEY);
            // 父级工具箱：本地文件写/改/查工具，沙箱绑定工作区根目录，子Agent继承后可直接落盘
            LocalSandbox sandbox = new LocalSandbox(workspace);
            HarnessToolkit toolkit = new HarnessToolkit(null);
            toolkit.addTool(new LocalFileWriteTool(sandbox));
            toolkit.addTool(new LocalFileEditTool(sandbox));
            toolkit.addTool(new LocalFileGlobTool(sandbox));
            toolkit.addTool(new LocalFileDeleteTool(sandbox));

            // 子代理运行时工厂：子代理复用主Agent模型与文件解析能力，文件工具从父级工具箱继承
            AgentRuntimeFactory runtimeFactory = () -> new HarnessRuntimeBuilder()
                    .model(model)
                    .enableFileToolkit(workspace.toString(), new LocalTextDocumentParser());

            // 不传静态声明：enableSubagents后 auto_orchestrate 工具按任务动态生成子Agent
            HarnessRuntimeBuilder coordinatorBuilder = new HarnessRuntimeBuilder()
                    .name("codegen-coordinator-dynamic")
                    .model(model)
                    .maxIters(MAX_ITERS)
                    .maxConcurrentToolCalls(2)
                    .systemPrompt("你是前端代码生成总协调员。收到任务后自主判断处理方式："
                            + "若任务可拆分为多个相对独立的子任务、适合分工协作时，调用 auto_orchestrate "
                            + "工具自动编排，将任务拆解并委派给动态生成的子Agent并行完成；"
                            + "若任务简单则直接独立完成。子Agent需写入各自独立的子目录避免互相覆盖，"
                            + "完成后用中文汇总各套产物的主题与结构说明。")
                    .toolkit(toolkit)
                    .enableFileToolkit(workspace.toString(), new LocalTextDocumentParser())
                    .enableSubagents()
                    .runtimeFactory(runtimeFactory);
            // 编排内部会运行多个子Agent（多次模型调用），放宽工具调用超时，动态子Agent数限制为2
            coordinatorBuilder.toolCallTimeout(Duration.ofSeconds(TIMEOUT_SECONDS * 5));
            coordinatorBuilder.setMaxSubagents(2);
            coordinator = coordinatorBuilder.build();

            List<AgentEvent> events = coordinator.stream(
                    List.of(userMessage("先清空登录页面历史数据，然后请生成2套不同主题的登录页面，主题颜色由你自行决定且两套明显不同。"
                            + "每套都包含 login.html 与独立的 css/style.css 样式文件，"
                            + "两套分别写到各自独立的子目录。")),
                    sessionContext()
            ).doOnNext(LocalCodeGenLoginPageExample::logSubagentEvent)
                    .collectList().block(Duration.ofSeconds(TIMEOUT_SECONDS * 3));

            log.info("主Agent最终汇总: {}", extractFinalText(events));
            verifySubagentFiles(workspace);
        } catch (IllegalStateException e) {
            log.error("本地智能体装配失败: {}", e.getMessage());
        } catch (Exception e) {
            log.error("动态子Agent代码生成验证过程出现异常", e);
        } finally {
            if (coordinator != null) {
                coordinator.close().block();
            }
            if (session != null) {
                session.close();
            }
        }
    }

    /**
     * 运行一个Agent完成创建与修改登录页的完整流程
     * @param rootDirName
     */
    private static void runOneAgent(String rootDirName) {
        LocalAgentSession session = null;
        try {
            session = createSession(rootDirName);
            log.info("本地智能体会话装配完成，工作区: {}", session.workspace().root());
            runCreateLoginPage(session);
            runModifyThemeColor(session);
        } catch (IllegalStateException e) {
            log.error("本地智能体装配失败: {}", e.getMessage());
        } catch (Exception e) {
            log.error("代码生成验证过程出现异常", e);
        } finally {
            if (session != null) {
                session.close();
            }
        }
    }

    /**
     * 装配本地智能体会话，工作区位于 target/{rootDirName}/workspace
     * @param rootDirName
     * @return
     */
    private static LocalAgentSession createSession(String rootDirName) {
        LocalHarnessConfig config = LocalHarnessConfig.builder()
                .rootDir(Path.of(System.getProperty("user.dir"), "target", rootDirName))
                .endpointBaseUrl(BASE_URL)
                .modelName(MODEL_NAME)
                .endpointApiKey(API_KEY)
                .maxIters(MAX_ITERS)
                .approvalMode(AgentApprovalMode.FULL_ACCESS)
                .shellEnabled(true)
                .shellRequireApproval(false)
                .systemPrompt("你是资深前端工程师。在沙箱工作区内创建完整可用的前端页面代码，"
                        + "样式建议独立成单独的css文件并正确引入，创建完成后用简洁中文说明产物。")
                .memoryFileEnabled(false)
                .knowledgeRagEnabled(false)
                .build();
        return LocalAgentHarness.create(config);
    }

    /**
     * 创建步骤：让Agent生成登录页与独立样式文件
     * @param session
     */
    private static void runCreateLoginPage(LocalAgentSession session) {
        log.info("========== 步骤1：创建登录页（login.html + css/style.css） ==========");
        String reply = callAgent(session, "请在工作区根目录下创建登录页面代码。要求：创建一个 login.html 文件，"
                + "包含用户名、密码输入框和登录按钮，并通过<link>标签引入 css/style.css 样式文件；"
                + "再创建一个 css/style.css 文件，样式文件放在独立的 css 目录下，"
                + "实现现代简洁的居中布局登录卡片样式。请使用 file_write 工具创建这两个文件，"
                + "完成后简要说明你创建的页面结构。");
        log.info("创建步骤Agent回复: {}", reply);
        verifyCreatedFiles(session.workspace().root());
    }

    /**
     * 修改步骤：让Agent更换登录页主题色
     * @param session
     */
    private static void runModifyThemeColor(LocalAgentSession session) {
        log.info("========== 步骤2：修改登录页主题色 ==========");
        Path workspace = session.workspace().root();
        String beforeCss = readText(workspace.resolve("css").resolve("style.css"));
        String reply = callAgent(session, "请先使用 file_read 读取工作区根目录下的 css/style.css，"
                + "然后用 file_edit修改它的主题色，例如把主色调由蓝绿系改为暖橙色系"
                + "（如按钮与强调色换为 #e67e22），保持页面结构不变。完成后简述你修改了哪些颜色。");
        log.info("修改步骤Agent回复: {}", reply);
        verifyModifiedFiles(workspace, beforeCss);
    }

    /**
     * 执行一次Agent调用并返回最终回复文本
     * @param session
     * @param prompt
     * @return
     */
    private static String callAgent(LocalAgentSession session, String prompt) {
        AgentRuntime runtime = session.runtime();
        AgentMessage result = runtime.call(
                List.of(userMessage(prompt)),
                sessionContext()
        ).block(Duration.ofSeconds(TIMEOUT_SECONDS * 3));
        return result != null ? result.getTextContent() : "空";
    }

    /**
     * 校验创建步骤产物是否落盘，并打印工作区文件清单
     * @param workspace
     */
    private static void verifyCreatedFiles(Path workspace) {
        Path html = workspace.resolve("login.html");
        Path css = workspace.resolve("css").resolve("style.css");
        boolean htmlOk = Files.isRegularFile(html) && readContains(html, "css/style.css");
        boolean cssOk = Files.isRegularFile(css) && safeSize(css) > 0;
        log.info("校验结果 login.html 生成并引入样式: {}", htmlOk);
        log.info("校验结果 css/style.css 独立目录生成: {}", cssOk);
        printFileTree(workspace);
    }

    /**
     * 校验修改步骤后样式内容是否发生变化
     * @param workspace
     * @param beforeCss
     */
    private static void verifyModifiedFiles(Path workspace, String beforeCss) {
        Path css = workspace.resolve("css").resolve("style.css");
        String afterCss = readText(css);
        boolean modified = !afterCss.isBlank() && !afterCss.equals(beforeCss);
        log.info("校验结果 css/style.css 主题色已修改: {}", modified);
        printFileTree(workspace);
    }

    /**
     * 校验子Agent产出：扫描工作区根目录下各子目录，统计包含完整登录页（login.html + css/style.css）的套数
     * @param workspace
     */
    private static void verifySubagentFiles(Path workspace) {
        int found = 0;
        try (Stream<Path> dirs = Files.list(workspace)) {
            for (Path dir : dirs.filter(Files::isDirectory).toList()) {
                Path html = dir.resolve("login.html");
                Path css = dir.resolve("css").resolve("style.css");
                boolean ok = Files.isRegularFile(html) && readContains(html, "css/style.css")
                        && Files.isRegularFile(css) && safeSize(css) > 0;
                if (ok) {
                    found++;
                    log.info("校验结果 子Agent产物完整: {}（login.html + css/style.css）", dir.getFileName());
                }
            }
        } catch (IOException e) {
            log.warn("遍历子Agent产物失败: {}", e.getMessage());
        }
        log.info("校验结果 子Agent独立登录页套数: {}", found);
        printFileTree(workspace);
    }

    /**
     * 逐事件打印子Agent调用与主Agent输出，文本增量直接输出便于观察，
     * 工具调用与模型调用阶段打印工具名、入参与结果摘要
     * @param event
     */
    private static void logSubagentEvent(AgentEvent event) {
        if (event.getType() == AgentEventType.TEXT_BLOCK_DELTA && event.getPayload() instanceof String s) {
            System.out.print(s);
            System.out.flush();
            return;
        }
        switch (event.getType()) {
            case TOOL_CALL_START -> logToolCallStart(event);
            case TOOL_CALL_END -> logToolCallEnd(event);
            // 流式工具参数增量事件量很大，TOOL_CALL_START 已含完整工具名与入参，忽略避免刷屏
            case TOOL_CALL_DELTA -> { }
            case MODEL_CALL_START -> log.info("[模型调用] 开始");
            case MODEL_CALL_END -> log.info("[模型调用] 结束");
            default -> log.info("[事件] type={} path={}", event.getType(), event.getParentAgentPath());
        }
    }

    /**
     * 打印工具调用发起：工具名与入参
     * @param event
     */
    private static void logToolCallStart(AgentEvent event) {
        if (!(event.getPayload() instanceof List<?> calls)) {
            return;
        }
        for (Object call : calls) {
            if (call instanceof AgentToolUseBlock block) {
                log.info("[工具调用] 工具={} 入参={}", block.getToolName(),"");
            }
        }
    }

    /**
     * 打印工具执行结果：结果标识、是否成功与结果摘要
     * @param event
     */
    private static void logToolCallEnd(AgentEvent event) {
        if (!(event.getPayload() instanceof List<?> messages)) {
            return;
        }
        for (Object message : messages) {
            if (!(message instanceof AgentMessage agentMessage) || agentMessage.getContent() == null) {
                continue;
            }
            for (AgentContentBlock block : agentMessage.getContent()) {
                if (block instanceof AgentToolResultBlock result) {
                    log.info("[工具结果] useId={} 成功={} 结果: {}",
                            result.getToolUseId(), !result.isError(), truncate(result.getTextContent(), 200));
                }
            }
        }
    }

    /**
     * 截断超长文本便于日志展示
     * @param text
     * @param maxLength
     * @return
     */
    private static String truncate(String text, int maxLength) {
        if (text == null || text.length() <= maxLength) {
            return text;
        }
        return text.substring(0, maxLength) + "...";
    }

    /**
     * 从异步事件流中聚合最终答复文本
     * @param events
     * @return
     */
    private static String extractFinalText(List<AgentEvent> events) {
        if (events == null) {
            return "空";
        }
        StringBuilder sb = new StringBuilder();
        for (AgentEvent event : events) {
            if (event.getType() == AgentEventType.TEXT_BLOCK_DELTA
                    && event.getPayload() instanceof String s) {
                sb.append(s);
            }
        }
        String text = sb.toString();
        return text.isBlank() ? "空" : text;
    }

    /**
     * 打印工作区文件清单
     * @param workspace
     */
    private static void printFileTree(Path workspace) {
        log.info("工作区文件清单:");
        try (Stream<Path> paths = Files.walk(workspace)) {
            paths.filter(Files::isRegularFile)
                    .forEach(p -> log.info("  {}", workspace.relativize(p)));
        } catch (IOException e) {
            log.warn("遍历工作区失败: {}", e.getMessage());
        }
    }

    /**
     * 安全读取文件文本
     * @param file
     * @return
     */
    private static String readText(Path file) {
        try {
            return Files.readString(file);
        } catch (IOException e) {
            return "";
        }
    }

    /**
     * 判断文件文本是否包含目标片段
     * @param file
     * @param keyword
     * @return
     */
    private static boolean readContains(Path file, String keyword) {
        try {
            return Files.readString(file).contains(keyword);
        } catch (IOException e) {
            return false;
        }
    }

    /**
     * 安全读取文件字节数
     * @param file
     * @return
     */
    private static long safeSize(Path file) {
        try {
            return Files.size(file);
        } catch (IOException e) {
            return 0L;
        }
    }

    /**
     * 创建携带会话信息的上下文，供持久化运行跟踪落库使用
     * @return
     */
    private static AgentRuntimeContext sessionContext() {
        return AgentRuntimeContext.builder()
                .scopeId("local-codegen")
                .sessionId(UUID.randomUUID().toString())
                .userId("local-user")
                .build();
    }

    /**
     * 创建用户消息
     * @param text
     * @return
     */
    private static AgentMessage userMessage(String text) {
        return AgentMessage.builder()
                .role(AgentMessageRole.USER)
                .content(List.of(AgentTextBlock.builder().text(text).build()))
                .build();
    }

    /**
     * 读取环境变量
     * @param key
     * @param defaultValue
     * @return
     */
    private static String env(String key, String defaultValue) {
        String value = System.getenv(key);
        return value != null && !value.isBlank() ? value : defaultValue;
    }

    /**
     * 是否已配置API密钥
     * @return
     */
    private static boolean isConfigured() {
        return API_KEY != null && !API_KEY.isBlank();
    }
}
