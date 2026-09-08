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
package com.yangqiongai.agent.harness.example.helloworld;

import java.util.List;

import com.yangqiongai.agent.harness.HarnessRuntimeBuilder;
import com.yangqiongai.agent.harness.core.AgentRuntime;
import com.yangqiongai.agent.harness.core.message.AgentMessage;
import com.yangqiongai.agent.harness.core.model.AgentModel;
import com.yangqiongai.agent.harness.core.model.registry.AgentModelRegistry;
import com.yangqiongai.agent.harness.durable.MemoryDistributedStores;
import com.yangqiongai.agent.harness.engine.AgentRuntimeContext;
import com.yangqiongai.agent.harness.core.message.MessageFactory;
import com.yangqiongai.agent.harness.model.HarnessModelFactory;
import com.yangqiongai.agent.harness.model.HarnessModelProperties;
import com.yangqiongai.agent.harness.model.provider.AnthropicModelProvider;
import com.yangqiongai.agent.harness.model.provider.OpenAIModelProvider;

/**
 * 零Spring最简接入示例
 * <p>
 * 不依赖任何框架，仅引入 yangqiong-agent-core，演示核心引擎的最简装配与一次对话。
 * 修改下方模型默认参数（或通过环境变量覆盖）即可直接运行，支持OpenAI兼容协议的大模型：
 * deepseek（https://api.deepseek.com）、openai（https://api.openai.com/v1）等。
 * </p>
 * @author yangqiong
 */
public class HelloWorldExample {

    /**
     * 默认API密钥（请替换为你自己的密钥）
     */
    private static final String DEFAULT_API_KEY = "";

    /**
     * 默认模型名称
     */
    private static final String DEFAULT_MODEL = "deepseek-v4-flash";

    /**
     * 默认API基础地址
     */
    private static final String DEFAULT_BASE_URL = "https://api.deepseek.com";

    private HelloWorldExample() {
    }

    /**
     * 示例入口
     * @param args
     */
    public static void main(String[] args) {
        // 模型配置：可通过环境变量 AI_API_KEY/AI_MODEL/AI_BASE_URL 覆盖上方默认值
        String apiKey = System.getenv().getOrDefault("AI_API_KEY", DEFAULT_API_KEY);
        String modelName = System.getenv().getOrDefault("AI_MODEL", DEFAULT_MODEL);
        String baseUrl = System.getenv().getOrDefault("AI_BASE_URL", DEFAULT_BASE_URL);
        if (apiKey == null || apiKey.isBlank() || apiKey.equals(DEFAULT_API_KEY)) {
            printGuide();
            return;
        }
        // 装配模型：创建配置、注册表并注册协议提供方（OpenAI兼容协议，覆盖deepseek/openai等）
        HarnessModelProperties properties = new HarnessModelProperties();
        properties.setApiKey(apiKey);
        properties.setBaseUrl(baseUrl);
        properties.setModelName(modelName);
        AgentModelRegistry registry = new AgentModelRegistry(properties.getDefaultProvider());
        registry.registerProvider(new OpenAIModelProvider());
        registry.registerProvider(new AnthropicModelProvider());
        AgentModel model = new HarnessModelFactory(properties, registry)
                .getModel(properties.getModelName(), null);

        AgentRuntime runtime = new HarnessRuntimeBuilder()
                .name("hello-agent")
                .model(model)
                .systemPrompt("你是一个乐于助人的智能助手，请用中文回答。")
                .maxIters(5)
                .stores(new MemoryDistributedStores())
                .build();

        // 发起一次对话并打印回复
        AgentMessage reply = runtime.call(
                List.of(MessageFactory.createUserMessage("你好，请用一句话介绍你自己")),
                AgentRuntimeContext.empty()).block();
        System.out.println("Agent回复: " + reply.getTextContent());
        runtime.close().block();
    }

    /**
     * 未配置密钥时的装配指引
     */
    private static void printGuide() {
        System.out.println("未配置有效的API密钥，仅演示运行时装配代码。");
        System.out.println("你可以直接修改类顶部的默认参数，或通过环境变量覆盖后运行本示例：");
        System.out.println("  OpenAI: API密钥=A...  模型=gpt-4o-mini  地址=https://api.openai.com/v1");
        System.out.println("  DeepSeek: API密钥=sk-...  模型=deepseek-chat  地址=https://api.deepseek.com");
        System.out.println("Windows PowerShell 设置示例：");
        System.out.println("  $env:AI_API_KEY=\"sk-xxx\"; $env:AI_MODEL=\"deepseek-chat\"; $env:AI_BASE_URL=\"https://api.deepseek.com\"");
        System.out.println("  mvn -pl yangqiong-agent-examples/yangqiong-agent-example-hello-world exec:java"
                + " -Dexec.mainClass=helloworld.example.com.yangqiongai.agent.harness.HelloWorldExample");
    }
}
