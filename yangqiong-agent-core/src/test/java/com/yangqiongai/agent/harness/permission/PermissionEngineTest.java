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
package com.yangqiongai.agent.harness.permission;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Collections;
import java.util.Set;

import com.yangqiongai.agent.harness.config.AgentPermissionContextState;
import com.yangqiongai.agent.harness.config.AgentPermissionDecision;
import com.yangqiongai.agent.harness.config.AgentPermissionMode;
import com.yangqiongai.agent.harness.config.AgentPermissionRule;
import org.junit.jupiter.api.Test;

/**
 * 权限引擎测试
 * @author yangqiong
 */
class PermissionEngineTest {

    @Test
    void shouldAllowAllWhenStateIsNull() {
        PermissionEngine engine = new PermissionEngine(null);
        assertThat(engine.allow("any_tool")).isTrue();
    }

    @Test
    void shouldAllowAllWhenModeIsNull() {
        AgentPermissionContextState state = AgentPermissionContextState.builder().build();
        PermissionEngine engine = new PermissionEngine(state);
        assertThat(engine.allow("any_tool")).isTrue();
    }

    @Test
    void shouldAllowAllInBypassMode() {
        AgentPermissionContextState state = AgentPermissionContextState.builder()
                .mode(AgentPermissionMode.BYPASS)
                .build();
        PermissionEngine engine = new PermissionEngine(state);
        assertThat(engine.allow("delete_file")).isTrue();
    }

    @Test
    void shouldAllowAllInDontAskMode() {
        AgentPermissionContextState state = AgentPermissionContextState.builder()
                .mode(AgentPermissionMode.DONT_ASK)
                .build();
        PermissionEngine engine = new PermissionEngine(state);
        assertThat(engine.allow("delete_file")).isTrue();
    }

    @Test
    void shouldAllowReadOnlyToolsInExploreMode() {
        AgentPermissionContextState state = AgentPermissionContextState.builder()
                .mode(AgentPermissionMode.EXPLORE)
                .build();
        PermissionEngine engine = new PermissionEngine(state);
        assertThat(engine.allow("read_file")).isTrue();
        assertThat(engine.allow("list_files")).isTrue();
        assertThat(engine.allow("get_data")).isTrue();
        assertThat(engine.allow("search_index")).isTrue();
        assertThat(engine.allow("query_db")).isTrue();
        assertThat(engine.allow("glob_files")).isTrue();
        assertThat(engine.allow("grep_files")).isTrue();
        assertThat(engine.allow("delete_file")).isFalse();
        assertThat(engine.allow("write_file")).isFalse();
    }

    @Test
    void shouldAllowNonDestructiveInAcceptEditsMode() {
        AgentPermissionContextState state = AgentPermissionContextState.builder()
                .mode(AgentPermissionMode.ACCEPT_EDITS)
                .build();
        PermissionEngine engine = new PermissionEngine(state);
        assertThat(engine.allow("write_file")).isTrue();
        assertThat(engine.allow("delete_file")).isFalse();
        assertThat(engine.allow("shell")).isFalse();
        assertThat(engine.allow("remove_item")).isFalse();
        assertThat(engine.allow("drop_table")).isFalse();
        assertThat(engine.allow("exec_cmd")).isFalse();
    }

    @Test
    void shouldAllowInAcceptEditsModeWhenRuleAllows() {
        AgentPermissionRule rule = AgentPermissionRule.builder()
                .toolName("delete_file")
                .mode(AgentPermissionMode.DONT_ASK)
                .build();
        AgentPermissionContextState state = AgentPermissionContextState.builder()
                .mode(AgentPermissionMode.ACCEPT_EDITS)
                .rules(Collections.singletonList(rule))
                .build();
        PermissionEngine engine = new PermissionEngine(state);
        assertThat(engine.allow("delete_file")).isTrue();
    }

    @Test
    void shouldDenyInDefaultModeWhenNoRules() {
        AgentPermissionContextState state = AgentPermissionContextState.builder()
                .mode(AgentPermissionMode.DEFAULT)
                .build();
        PermissionEngine engine = new PermissionEngine(state);
        assertThat(engine.allow("any_tool")).isTrue();
    }

    @Test
    void shouldDenyWhenRuleIsExplore() {
        AgentPermissionRule rule = AgentPermissionRule.builder()
                .toolName("delete_file")
                .mode(AgentPermissionMode.EXPLORE)
                .build();
        AgentPermissionContextState state = AgentPermissionContextState.builder()
                .mode(AgentPermissionMode.DEFAULT)
                .rules(Collections.singletonList(rule))
                .build();
        PermissionEngine engine = new PermissionEngine(state);
        assertThat(engine.allow("delete_file")).isFalse();
    }

    @Test
    void shouldHandleNullToolName() {
        AgentPermissionContextState state = AgentPermissionContextState.builder()
                .mode(AgentPermissionMode.EXPLORE)
                .build();
        PermissionEngine engine = new PermissionEngine(state);
        assertThat(engine.allow(null)).isFalse();
    }

    @Test
    void shouldReturnAskWhenToolInRequireApprovalSet() {
        PermissionEngine engine = new PermissionEngine(null, Set.of("shell", "delete_file"));
        assertThat(engine.evaluate("shell")).isEqualTo(AgentPermissionDecision.ASK);
        assertThat(engine.evaluate("delete_file")).isEqualTo(AgentPermissionDecision.ASK);
    }

    @Test
    void shouldAllowToolNotInRequireApprovalSetWhenStateIsNull() {
        PermissionEngine engine = new PermissionEngine(null, Set.of("shell"));
        assertThat(engine.evaluate("read_file")).isEqualTo(AgentPermissionDecision.ALLOW);
    }

    @Test
    void shouldPrioritizeRequireApprovalOverModeRules() {
        AgentPermissionContextState state = AgentPermissionContextState.builder()
                .mode(AgentPermissionMode.BYPASS)
                .build();
        PermissionEngine engine = new PermissionEngine(state, Set.of("shell"));
        // 即使BYPASS模式，requireApproval中的工具仍返回ASK
        assertThat(engine.evaluate("shell")).isEqualTo(AgentPermissionDecision.ASK);
        assertThat(engine.evaluate("read_file")).isEqualTo(AgentPermissionDecision.ALLOW);
    }

    @Test
    void shouldHandleEmptyRequireApprovalSet() {
        PermissionEngine engine = new PermissionEngine(null, Collections.emptySet());
        assertThat(engine.evaluate("any_tool")).isEqualTo(AgentPermissionDecision.ALLOW);
    }

    @Test
    void shouldHandleNullRequireApprovalSet() {
        PermissionEngine engine = new PermissionEngine(null, null);
        assertThat(engine.evaluate("any_tool")).isEqualTo(AgentPermissionDecision.ALLOW);
    }
}
