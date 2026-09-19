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
package com.yangqiongai.agent.harness.core.trace;

import com.yangqiongai.agent.harness.model.ContextSnapshot;

/**
 * 上下文快照监听SPI
 * <p>
 * 每次模型调用前引擎组装好上下文消息后回调，平台侧实现批量落库等处理。
 * 未注入监听器时引擎完全跳过采集，回调异常由引擎吞掉不影响主流程。
 * </p>
 * @author yangqiong
 */
public interface ContextSnapshotListener {

    /**
     * 模型调用前的上下文快照回调
     * @param snapshot
     */
    void onSnapshot(ContextSnapshot snapshot);
}
