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
package com.yangqiongai.agent.harness.server;

import java.nio.file.Path;

import com.yangqiongai.agent.harness.config.AgentApprovalMode;
import com.yangqiongai.agent.harness.local.runtime.LocalHarnessConfig;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 服务端装配配置测试
 * @author yangqiong
 */
class ServerConfigTest {

    @Test
    void defaultsApplyWhenNotSet() {
        ServerConfig config = ServerConfig.builder()
                .rootDir(Path.of("./data"))
                .build();

        assertThat(config.approvalMode()).isEqualTo(AgentApprovalMode.MANUAL);
        assertThat(config.defaultScopeId()).isEqualTo("server");
        assertThat(config.defaultSessionId()).isEqualTo("default-session");
        assertThat(config.shellEnabled()).isFalse();
    }

    @Test
    void toLocalConfigCarriesScopeAndSession() {
        ServerConfig config = ServerConfig.builder()
                .rootDir(Path.of("./data"))
                .defaultScopeId("noop")
                .defaultSessionId("noop")
                .endpointBaseUrl("http://localhost:11434")
                .modelName("deepseek-r1")
                .build();

        LocalHarnessConfig local = config.toLocalConfig("tenant-x", "session-y");

        assertThat(local.scopeId()).isEqualTo("tenant-x");
        assertThat(local.sessionId()).isEqualTo("session-y");
        assertThat(local.endpointBaseUrl()).isEqualTo("http://localhost:11434");
        assertThat(local.modelName()).isEqualTo("deepseek-r1");
    }

    @Test
    void sessionKeyCombinesScopeAndSession() {
        assertThat(AgentSessionManager.sessionKey("a", "b")).isEqualTo("a::b");
        assertThat(AgentSessionManager.sessionKey(null, null)).isEqualTo("::");
    }
}