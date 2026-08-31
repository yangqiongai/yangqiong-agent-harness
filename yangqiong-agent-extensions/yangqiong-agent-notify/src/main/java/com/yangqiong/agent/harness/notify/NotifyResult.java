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
package com.yangqiong.agent.harness.notify;

/**
 * 通知结果
 * <p>
 * 单条渠道投递的成败结论，失败时携带错误信息，供通知服务聚合返回与日志告警。
 * </p>
 * @author yangqiong
 */
public class NotifyResult {

    /**
     * 渠道类型
     */
    private final String channelType;

    /**
     * 是否投递成功
     */
    private final boolean success;

    /**
     * 失败原因，成功时为空
     */
    private final String errorMessage;

    /**
     * 构造
     * @param channelType
     * @param success
     * @param errorMessage
     */
    public NotifyResult(String channelType, boolean success, String errorMessage) {
        this.channelType = channelType;
        this.success = success;
        this.errorMessage = errorMessage;
    }

    /**
     * 构造成功结果
     * @param channelType
     * @return
     */
    public static NotifyResult success(String channelType) {
        return new NotifyResult(channelType, true, null);
    }

    /**
     * 构造失败结果
     * @param channelType
     * @param errorMessage
     * @return
     */
    public static NotifyResult failure(String channelType, String errorMessage) {
        return new NotifyResult(channelType, false, errorMessage);
    }

    /**
     * 渠道类型
     * @return
     */
    public String getChannelType() {
        return channelType;
    }

    /**
     * 是否投递成功
     * @return
     */
    public boolean isSuccess() {
        return success;
    }

    /**
     * 失败原因，成功时为空
     * @return
     */
    public String getErrorMessage() {
        return errorMessage;
    }
}
