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
package com.yangqiong.agent.harness.store.redis;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import org.junit.jupiter.api.Test;

import com.yangqiong.agent.harness.core.memory.AgentLongTermMemory;
import com.yangqiong.agent.harness.core.memory.SessionMemory;
import com.yangqiong.agent.harness.core.message.AgentMessage;
import com.yangqiong.agent.harness.core.message.AgentMessageRole;
import com.yangqiong.agent.harness.core.message.AgentTextBlock;
import com.yangqiong.agent.harness.core.message.AgentToolResultBlock;
import com.yangqiong.agent.harness.durable.AgentCheckpoint;
import com.yangqiong.agent.harness.durable.AgentRunRecord;
import com.yangqiong.agent.harness.durable.AgentRunState;
import com.yangqiong.agent.harness.durable.ApprovalRecord;
import com.yangqiong.agent.harness.durable.ApprovalRecord.ApprovalState;
import com.yangqiong.agent.harness.durable.RunLockStore;
import redis.clients.jedis.JedisPooled;

/**
 * Redis共享存储单元测试
 * <p>
 * 本机6379无Redis时通过 assumeTrue 跳过，有则跑全接口覆盖。
 * </p>
 * @author yangqiong
 */
class RedisStoresTest {

    /**
     * 会话记忆单无作用域键前缀校验用固定会话
     */
    private static final String SESSION = "session-" + suffix();

    /**
     * 订阅Redis客户端
     */
    private static JedisPooled connection;

    /**
     * 构建并校验Redis可用性，不可用返回null
     * @return
     */
    private static JedisPooled jedis() {
        if (connection != null) {
            return connection;
        }
        try {
            JedisPooled pool = new JedisPooled("127.0.0.1", 6379);
            pool.ping();
            connection = pool;
            return pool;
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 生成无Redis时也要保持唯一的后缀
     * @return
     */
    private static String suffix() {
        return String.valueOf(System.nanoTime());
    }

    /**
     * 获取聚合存储，Redis不可用时跳过
     * @return
     */
    private static RedisDistributedStores stores() {
        JedisPooled pool = jedis();
        assumeTrue(pool != null, "本机Redis未运行，跳过Redis相关断言");
        return new RedisDistributedStores(pool);
    }

    /**
     * 运行锁TTL过期与续期
     */
    @Test
    void runLockTtlExpireAndRenew() throws InterruptedException {
        RedisDistributedStores stores = stores();
        RunLockStore lock = stores.runLockStore();
        String runId = "lock-" + suffix();
        assertThat(lock.tryLock(runId, "nodeA", Duration.ofSeconds(30))).isTrue();
        assertThat(lock.owner(runId)).contains("nodeA");
        assertThat(lock.tryLock(runId, "nodeB", Duration.ofSeconds(30))).isFalse();

        // 同节点重复加锁视为续期，幂等成功
        assertThat(lock.tryLock(runId, "nodeA", Duration.ofSeconds(30))).isTrue();
        lock.renew(runId, "nodeA", Duration.ofSeconds(30));

        // 非持有者无法解锁
        lock.unlock(runId, "nodeB");
        assertThat(lock.owner(runId)).contains("nodeA");
        lock.unlock(runId, "nodeA");
        assertThat(lock.owner(runId)).isEmpty();

        // 释放后可被其他节点接管
        assertThat(lock.tryLock(runId, "nodeB", Duration.ofSeconds(30))).isTrue();
        lock.unlock(runId, "nodeB");

        // 短TTL过期后可被接管
        String expireRun = "lock-expire-" + suffix();
        assertThat(lock.tryLock(expireRun, "nodeA", Duration.ofMillis(150))).isTrue();
        Thread.sleep(250);
        assertThat(lock.owner(expireRun)).isEmpty();
        assertThat(lock.tryLock(expireRun, "nodeB", Duration.ofSeconds(30))).isTrue();
    }

    /**
     * 工具执行记录SET NX幂等
     */
    @Test
    void toolExecutionIdempotency() {
        RedisDistributedStores stores = stores();
        String key = "tool-" + suffix();
        AgentToolResultBlock first = AgentToolResultBlock.of(
                List.of(AgentTextBlock.builder().text("首次结果").build()));
        AgentToolResultBlock second = AgentToolResultBlock.of(
                List.of(AgentTextBlock.builder().text("重复结果").build()));
        stores.toolExecutionStore().record(key, first);
        assertThat(stores.toolExecutionStore().isCompleted(key)).isTrue();
        assertThat(stores.toolExecutionStore().getResult(key).getTextContent()).isEqualTo("首次结果");

        // 重复记录保持首份结果不变
        stores.toolExecutionStore().record(key, second);
        assertThat(stores.toolExecutionStore().getResult(key).getTextContent()).isEqualTo("首次结果");
        assertThat(stores.toolExecutionStore().isCompleted("missing-" + suffix())).isFalse();
        assertThat(stores.toolExecutionStore().getResult("missing-" + suffix())).isNull();
    }

    /**
     * 检查点版本Lua乐观锁CAS
     */
    @Test
    void checkpointVersionCas() {
        RedisDistributedStores stores = stores();
        String runId = "cp-" + suffix();
        String scopeId = "scope-" + suffix();
        long now = System.currentTimeMillis();
        AgentCheckpoint v1 = new AgentCheckpoint(runId, scopeId, SESSION, 1,
                List.of(), List.of(), List.of(), now, 1);
        AgentCheckpoint v2 = new AgentCheckpoint(runId, scopeId, SESSION, 2,
                List.of(), List.of(), List.of(), now + 1, 2);
        stores.checkpointStore().save(v1);
        stores.checkpointStore().save(v2);

        assertThat(stores.checkpointStore().latest(scopeId, SESSION)).hasValueSatisfying(c -> assertThat(c.getVersion()).isEqualTo(2));
        assertThat(stores.checkpointStore().findByRunId(runId)).extracting(AgentCheckpoint::getVersion).containsExactly(1L, 2L);

        // 版本回退或平级保存触发冲突
        AgentCheckpoint dup = new AgentCheckpoint(runId, scopeId, SESSION, 2,
                List.of(), List.of(), List.of(), now + 2, 2);
        assertThatThrownBy(() -> stores.checkpointStore().save(dup))
                .isInstanceOf(IllegalStateException.class);

        stores.checkpointStore().clear(runId);
        assertThat(stores.checkpointStore().findByRunId(runId)).isEmpty();
        assertThat(stores.checkpointStore().latest(scopeId, SESSION)).isEmpty();
    }

    /**
     * 会话记忆摘要与情节
     */
    @Test
    void sessionSummaryAndEpisodes() {
        RedisDistributedStores stores = stores();
        SessionMemory memory = stores.sessionMemory();
        String scopeId = "scope-" + suffix();
        memory.saveSummary(scopeId, SESSION, "会话摘要内容", 5);
        assertThat(memory.loadSummary(scopeId, SESSION)).isNotNull();
        assertThat(memory.loadSummary(scopeId, SESSION).getSummary()).isEqualTo("会话摘要内容");
        assertThat(memory.loadSummary(scopeId, SESSION).getSummarizedMessageCount()).isEqualTo(5);

        AgentMessage episode = AgentMessage.builder()
                .role(AgentMessageRole.USER)
                .content(List.of(AgentTextBlock.builder().text("关键情节消息").build()))
                .build();
        memory.saveEpisode(SESSION, episode);
        assertThat(memory.listEpisodes(SESSION, 10)).hasSize(1);
        assertThat(memory.listEpisodes(SESSION, 10).get(0).getTextContent()).isEqualTo("关键情节消息");

        memory.clear(scopeId, SESSION);
        assertThat(memory.loadSummary(scopeId, SESSION)).isNull();
        memory.clear(SESSION);
        assertThat(memory.listEpisodes(SESSION, 10)).isEmpty();
    }

    /**
     * 长期记忆存储与检索
     */
    @Test
    void longTermMemoryStoreAndSearch() {
        RedisDistributedStores stores = stores();
        AgentLongTermMemory memory = stores.longTermMemory();
        String scopeId = "scope-" + suffix();
        String userId = "user-" + suffix();
        memory.store(scopeId, userId, SESSION, "长沙的天气晴朗", Map.of("tag", "weather"));
        memory.store(scopeId, userId, SESSION, "北京的交通拥堵", Map.of("tag", "traffic"));
        assertThat(memory.search(scopeId, userId, "天气", 10)).contains("长沙的天气晴朗");
        assertThat(memory.search(scopeId, userId, "交通", 10)).contains("北京的交通拥堵");
        assertThat(memory.search(scopeId, userId, "不存在的词", 10)).isEmpty();

        // 删除接口内部memoryId不可直接构造，传不存在的ID验证不抛异常
        memory.delete(scopeId, "not-exist-" + suffix());
    }

    /**
     * 运行记录创建、索引与saveTransition CAS
     */
    @Test
    void runStoreCreateAndTransition() throws InterruptedException {
        RedisDistributedStores stores = stores();
        String scopeId = "scope-" + suffix();
        String sessionId = "run-session-" + suffix();
        AgentRunRecord run = new AgentRunRecord("run-" + suffix(), scopeId, sessionId, "user-1", "助手");
        stores.runStore().create(run);
        assertThat(stores.runStore().findByRunId(run.getRunId())).isPresent();
        assertThat(stores.runStore().findBySession(scopeId, sessionId)).hasSize(1);
        assertThat(stores.runStore().findLatestBySession(scopeId, sessionId)).isPresent();

        // 重复create冲突
        assertThatThrownBy(() -> stores.runStore().create(run))
                .isInstanceOf(IllegalStateException.class);

        // 迁移到运行中并保存
        run.transitionTo(AgentRunState.RUNNING, "启动");
        stores.runStore().saveTransition(run);
        AgentRunRecord fetched = stores.runStore().findByRunId(run.getRunId()).get();
        assertThat(fetched.getState()).isEqualTo(AgentRunState.RUNNING);
        assertThat(fetched.getVersion()).isEqualTo(1L);

        // 旧的未迁移记录再次保存触发版本冲突
        AgentRunRecord stale = new AgentRunRecord(run.getRunId(), scopeId, sessionId, "user-1", "助手");
        assertThatThrownBy(() -> stores.runStore().saveTransition(stale))
                .isInstanceOf(IllegalStateException.class);

        // 等待审批的运行可被列出
        AgentRunRecord waiting = new AgentRunRecord("run-wait-" + suffix(), scopeId, "wait-session-" + suffix(), "user-2", "助手");
        stores.runStore().create(waiting);
        waiting.transitionTo(AgentRunState.RUNNING, "启动");
        waiting.transitionTo(AgentRunState.WAITING_APPROVAL, "待审批");
        stores.runStore().saveTransition(waiting);
        assertThat(stores.runStore().findWaitingApproval(scopeId))
                .extracting(AgentRunRecord::getRunId)
                .contains(waiting.getRunId());
    }

    /**
     * 审批创建、查询与幂等落定
     */
    @Test
    void approvalCreateAndResolve() {
        RedisDistributedStores stores = stores();
        String scopeId = "scope-" + suffix();
        String runId = "appr-run-" + suffix();
        String approvalId = "appr-" + suffix();
        String toolCallId = "toolcall-" + suffix();
        ApprovalRecord pending = ApprovalRecord.pending(approvalId, runId, toolCallId, "SendEmail", scopeId, "approver-1");
        stores.approvalStore().create(pending);
        assertThat(stores.approvalStore().findByApprovalId(approvalId)).contains(pending);
        assertThat(stores.approvalStore().findByToolCallId(toolCallId)).contains(pending);
        assertThat(stores.approvalStore().findPending(scopeId)).contains(pending);

        // 批准落定
        ApprovalRecord resolved = stores.approvalStore().resolve(approvalId, true, "同意");
        assertThat(resolved.getState()).isEqualTo(ApprovalState.APPROVED);
        assertThat(stores.approvalStore().findPending(scopeId)).doesNotContain(pending);
        assertThat(stores.approvalStore().findByRunId(runId))
                .extracting(ApprovalRecord::getState).containsExactly(ApprovalState.APPROVED);

        // 重复落定幂等，保持首次状态
        ApprovalRecord again = stores.approvalStore().resolve(approvalId, false, "再次驳回");
        assertThat(again.getState()).isEqualTo(ApprovalState.APPROVED);

        // 不存在的审批落定抛异常
        assertThatThrownBy(() -> stores.approvalStore().resolve("missing-" + suffix(), true, "x"))
                .isInstanceOf(IllegalStateException.class);
    }
}