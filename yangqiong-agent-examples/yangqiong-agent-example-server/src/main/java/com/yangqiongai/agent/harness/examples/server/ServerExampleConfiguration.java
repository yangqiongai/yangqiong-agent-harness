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
package com.yangqiongai.agent.harness.examples.server;

import java.nio.file.Path;

import com.yangqiongai.agent.harness.server.AgentEventSseMapper;
import com.yangqiongai.agent.harness.server.AgentSessionManager;
import com.yangqiongai.agent.harness.server.ServerConfig;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

/**
 * 服务化装配
 * <p>
 * 将 server 扩展库的控制器依赖 Bean 注册进容器，并从配置读取根目录与可选模型端点；
 * 端点留空时由 LocalAgentHarness 自动发现本机 Ollama/LM Studio 端点。
 * </p>
 * @author yangqiong
 */
@Configuration
public class ServerExampleConfiguration {

    /**
     * 服务端装配配置
     * @param rootDir
     * @param endpointBaseUrl
     * @param modelName
     * @return
     */
    @Bean
    public ServerConfig serverConfig(
            @Value("${agent.server.root-dir:./agent-data}") String rootDir,
            @Value("${agent.endpoint-base-url:}") String endpointBaseUrl,
            @Value("${agent.model-name:}") String modelName) {
        ServerConfig.Builder builder = ServerConfig.builder()
                .rootDir(Path.of(rootDir).toAbsolutePath().normalize());
        if (StringUtils.hasText(endpointBaseUrl)) {
            builder.endpointBaseUrl(endpointBaseUrl);
        }
        if (StringUtils.hasText(modelName)) {
            builder.modelName(modelName);
        }
        return builder.build();
    }

    /**
     * SSE事件映射器
     * @return
     */
    @Bean
    public AgentEventSseMapper agentEventSseMapper() {
        return new AgentEventSseMapper();
    }

    /**
     * 会话管理器，关闭时释放全部会话资源
     * @param serverConfig
     * @return
     */
    @Bean(destroyMethod = "close")
    public AgentSessionManager agentSessionManager(ServerConfig serverConfig) {
        return new AgentSessionManager(serverConfig);
    }
}