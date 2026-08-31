-- ============================================================================
-- Agent 引擎共享存储（MyBatis-Plus/MySQL）建表脚本
-- 统一遵循规范列：scope_id / create_user / create_time / update_user / update_time
-- 字符集统一 utf8mb4，表引擎 InnoDB
-- ============================================================================

-- ---------------------------------------------------------------------------
-- 运行记录表：AgentRunStore 的权威状态源
-- run_id 全局唯一，version 为乐观锁版本（saveTransition 按版本校验更新）
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS harness_run (
    run_id                VARCHAR(64)   NOT NULL COMMENT '运行ID（全局唯一）',
    scope_id              VARCHAR(64)   NOT NULL COMMENT '租户标识',
    session_id            VARCHAR(64)   NOT NULL COMMENT '会话ID',
    user_id               VARCHAR(64)   DEFAULT NULL COMMENT '用户ID',
    agent_name            VARCHAR(128)  DEFAULT NULL COMMENT 'Agent名称',
    created_at            BIGINT        NOT NULL COMMENT '创建时间戳（毫秒）',
    updated_at            BIGINT        NOT NULL COMMENT '最近更新时间戳（毫秒）',
    version               BIGINT        NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
    state                 VARCHAR(32)   NOT NULL COMMENT '运行状态',
    error                 VARCHAR(1024) DEFAULT NULL COMMENT '失败原因',
    transitions           TEXT          DEFAULT NULL COMMENT '状态迁移历史（JSON）',
    create_user           VARCHAR(64)   DEFAULT NULL COMMENT '创建人',
    create_time           DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_user           VARCHAR(64)   DEFAULT NULL COMMENT '更新人',
    update_time           DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (run_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT 'Agent运行记录';

CREATE INDEX idx_harness_run_session ON harness_run (scope_id, session_id, created_at);

-- ---------------------------------------------------------------------------
-- 检查点表：CheckpointStore 权威读写口
-- 主键为 (run_id, version)，版本单调递增，save 时校验版本并发冲突
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS harness_checkpoint (
    run_id                VARCHAR(64)   NOT NULL COMMENT '所属运行ID',
    scope_id              VARCHAR(64)   NOT NULL COMMENT '租户标识',
    session_id            VARCHAR(64)   NOT NULL COMMENT '会话ID',
    version               BIGINT        NOT NULL COMMENT '乐观锁版本号（按runId单调递增）',
    iteration             INT           NOT NULL COMMENT '迭代轮次',
    messages              TEXT          DEFAULT NULL COMMENT '对话历史快照（JSON）',
    pending_tool_calls    TEXT          DEFAULT NULL COMMENT '待执行工具调用（JSON）',
    completed_tool_use_ids TEXT         DEFAULT NULL COMMENT '已完成工具调用ID（JSON）',
    checkpoint_time       BIGINT        NOT NULL COMMENT '快照时间戳（毫秒）',
    create_user           VARCHAR(64)   DEFAULT NULL COMMENT '创建人',
    create_time           DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_user           VARCHAR(64)   DEFAULT NULL COMMENT '更新人',
    update_time           DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (run_id, version)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT 'Agent运行检查点';

CREATE INDEX idx_harness_checkpoint_session ON harness_checkpoint (scope_id, session_id, version);

-- ---------------------------------------------------------------------------
-- 审批记录表：ApprovalStore 权威状态源
-- approval_id 唯一，tool_call_id 唯一精确匹配一次工具调用
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS harness_approval (
    approval_id           VARCHAR(64)   NOT NULL COMMENT '审批ID',
    run_id                VARCHAR(64)   NOT NULL COMMENT '所属运行ID',
    tool_call_id          VARCHAR(64)   NOT NULL COMMENT '工具调用ID（精确匹配键）',
    tool_name             VARCHAR(128)  DEFAULT NULL COMMENT '工具名称',
    scope_id              VARCHAR(64)   NOT NULL COMMENT '租户标识',
    approver_id           VARCHAR(64)   DEFAULT NULL COMMENT '审批人用户ID',
    state                 VARCHAR(32)   NOT NULL COMMENT '审批状态',
    reason                VARCHAR(1024) DEFAULT NULL COMMENT '审批理由',
    created_at            BIGINT        NOT NULL COMMENT '创建时间戳（毫秒）',
    create_user           VARCHAR(64)   DEFAULT NULL COMMENT '创建人',
    create_time           DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_user           VARCHAR(64)   DEFAULT NULL COMMENT '更新人',
    update_time           DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (approval_id),
    UNIQUE KEY uk_harness_approval_tool (tool_call_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT '人工审批记录';

CREATE INDEX idx_harness_approval_scope ON harness_approval (scope_id, state);
CREATE INDEX idx_harness_approval_run ON harness_approval (run_id);

-- ---------------------------------------------------------------------------
-- 会话摘要表：SessionMemory 保存会话运行摘要（复合键隔离）
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS harness_session_summary (
    id                    BIGINT        NOT NULL AUTO_INCREMENT COMMENT '自增主键',
    scope_id              VARCHAR(64)   NOT NULL COMMENT '租户标识',
    session_id            VARCHAR(64)   NOT NULL COMMENT '会话ID',
    summary               TEXT          DEFAULT NULL COMMENT '会话运行摘要',
    summarized_message_count INT         NOT NULL DEFAULT 0 COMMENT '已摘要到的消息序号（不含）',
    create_user           VARCHAR(64)   DEFAULT NULL COMMENT '创建人',
    create_time           DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_user           VARCHAR(64)   DEFAULT NULL COMMENT '更新人',
    update_time           DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_harness_session_summary (scope_id, session_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT '会话运行摘要';

-- ---------------------------------------------------------------------------
-- 会话情节表：SessionMemory 保存会议关键情节消息
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS harness_session_episode (
    id                    BIGINT        NOT NULL AUTO_INCREMENT COMMENT '自增主键',
    scope_id              VARCHAR(64)   NOT NULL COMMENT '租户标识',
    session_id            VARCHAR(64)   NOT NULL COMMENT '会话ID',
    message               TEXT          DEFAULT NULL COMMENT '情节消息（JSON）',
    create_user           VARCHAR(64)   DEFAULT NULL COMMENT '创建人',
    create_time           DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_user           VARCHAR(64)   DEFAULT NULL COMMENT '更新人',
    update_time           DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT '会话关键情节消息';

CREATE INDEX idx_harness_episode_session ON harness_session_episode (scope_id, session_id, id);

-- ---------------------------------------------------------------------------
-- 长期记忆表：AgentLongTermMemory 权威存储
-- id 为全局唯一记忆ID，按 (scope_id, user_id) 复合桶隔离
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS harness_long_term_memory (
    id                    VARCHAR(64)   NOT NULL COMMENT '记忆ID（全局唯一）',
    scope_id              VARCHAR(64)   NOT NULL COMMENT '租户标识',
    user_id               VARCHAR(64)   NOT NULL COMMENT '用户ID',
    session_id            VARCHAR(64)   DEFAULT NULL COMMENT '会话ID',
    content               TEXT          DEFAULT NULL COMMENT '记忆内容',
    metadata              TEXT          DEFAULT NULL COMMENT '元数据（JSON）',
    created_at            BIGINT        NOT NULL COMMENT '创建时间戳（毫秒）',
    create_user           VARCHAR(64)   DEFAULT NULL COMMENT '创建人',
    create_time           DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_user           VARCHAR(64)   DEFAULT NULL COMMENT '更新人',
    update_time           DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT '长期记忆';

CREATE INDEX idx_harness_memory_user ON harness_long_term_memory (scope_id, user_id);

-- ---------------------------------------------------------------------------
-- 工具执行记录表：ToolExecutionStore 幂等去重
-- idempotency_key 唯一，同键首次结果落地后重复记录直接复用
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS harness_tool_execution (
    idempotency_key       VARCHAR(128)  NOT NULL COMMENT '幂等键',
    result                TEXT          DEFAULT NULL COMMENT '工具结果（JSON）',
    create_user           VARCHAR(64)   DEFAULT NULL COMMENT '创建人',
    create_time           DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_user           VARCHAR(64)   DEFAULT NULL COMMENT '更新人',
    update_time           DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (idempotency_key)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT '工具执行记录';

-- ---------------------------------------------------------------------------
-- 运行锁表：RunLockStore 分布式运行归属控制
-- run_id 唯一，expires_at 为过期时间戳（毫秒），超时后其他节点可接管
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS harness_run_lock (
    run_id                VARCHAR(64)   NOT NULL COMMENT '运行ID',
    owner_node_id         VARCHAR(128)  NOT NULL COMMENT '持有节点ID',
    expires_at            BIGINT        NOT NULL COMMENT '过期时间戳（毫秒）',
    create_user           VARCHAR(64)   DEFAULT NULL COMMENT '创建人',
    create_time           DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_user           VARCHAR(64)   DEFAULT NULL COMMENT '更新人',
    update_time           DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (run_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT '运行锁';