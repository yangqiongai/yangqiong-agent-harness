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

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * 权限审计出口
 * <p>
 * 引擎默认提供内存实现（有界环形缓冲，仅保留最近记录），
 * 外部可替换为持久化审计日志（追加写、不可篡改存储）。
 * </p>
 * @author yangqiong
 */
public interface AuditSink {

    /**
     * 追加审计记录
     * @param record
     */
    void append(AuditRecord record);

    /**
     * 内存审计出口
     * <p>
     * 有界环形缓冲：超过容量上限时淘汰最旧记录，防止长运行服务内存无界增长。
     * 定位是"最近记录可查"，持久化审计由外部实现替换。
     * </p>
     * @author yangqiong
     */
    class InMemory implements AuditSink {

        /**
         * 最大保留审计记录数
         */
        private static final int MAX_RECORDS = 5000;

        /**
         * 审计记录环形缓冲（队首为最旧）
         */
        private final Deque<AuditRecord> records = new ArrayDeque<>();

        @Override
        public synchronized void append(AuditRecord record) {
            if (record == null) {
                return;
            }
            if (records.size() >= MAX_RECORDS) {
                records.pollFirst();
            }
            records.addLast(record);
        }

        /**
         * 获取全部审计记录快照（按写入顺序）
         * @return
         */
        public synchronized List<AuditRecord> getRecords() {
            return new ArrayList<>(records);
        }
    }
}
