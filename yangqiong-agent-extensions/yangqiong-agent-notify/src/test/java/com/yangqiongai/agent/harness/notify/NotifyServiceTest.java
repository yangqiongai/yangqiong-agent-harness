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
package com.yangqiongai.agent.harness.notify;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * 通知服务测试
 * @author yangqiong
 */
class NotifyServiceTest {

    /**
     * 默认服务注册全部内置渠道
     */
    @Test
    void defaultsRegistersAllBuiltinChannels() {
        NotifyService service = NotifyService.defaults();

        assertThat(service.hasChannel(NotifyChannel.TYPE_WEBHOOK)).isTrue();
        assertThat(service.hasChannel(NotifyChannel.TYPE_FEISHU)).isTrue();
        assertThat(service.hasChannel(NotifyChannel.TYPE_DINGTALK)).isTrue();
        assertThat(service.hasChannel(NotifyChannel.TYPE_EMAIL)).isTrue();
    }

    /**
     * 无投递目标时返回空结果
     */
    @Test
    void notifyReturnsEmptyWhenNoTarget() {
        NotifyService service = new NotifyService();

        List<NotifyResult> results = service.notify(message(), NotifyConfig.builder().build());

        assertThat(results).isEmpty();
    }

    /**
     * 未注册渠道返回失败结果
     */
    @Test
    void notifyReturnsFailureForUnregisteredChannel() {
        NotifyService service = new NotifyService();
        NotifyConfig config = NotifyConfig.builder()
                .target(ChannelTarget.builder().channelType("unknown").build())
                .build();

        List<NotifyResult> results = service.notify(message(), config);

        assertThat(results).hasSize(1);
        assertThat(results.get(0).isSuccess()).isFalse();
        assertThat(results.get(0).getChannelType()).isEqualTo("unknown");
    }

    /**
     * 多渠道聚合发送且单渠道失败被隔离
     */
    @Test
    void notifyAggregatesResultsAndIsolatesFailures() {
        NotifyService service = new NotifyService()
                .register(new FakeChannel("ok", true))
                .register(new FakeChannel("bad", false));
        NotifyConfig config = NotifyConfig.builder()
                .target(ChannelTarget.builder().channelType("ok").build())
                .target(ChannelTarget.builder().channelType("bad").build())
                .build();

        List<NotifyResult> results = service.notify(message(), config);

        assertThat(results).hasSize(2);
        assertThat(results.get(0).isSuccess()).isTrue();
        assertThat(results.get(1).isSuccess()).isFalse();
    }

    /**
     * 注册同类型渠道覆盖旧实现
     */
    @Test
    void registerOverwritesSameType() {
        NotifyService service = new NotifyService()
                .register(new FakeChannel("dup", true))
                .register(new FakeChannel("dup", false));

        assertThat(service.hasChannel("dup")).isTrue();
        NotifyConfig config = NotifyConfig.builder()
                .target(ChannelTarget.builder().channelType("dup").build())
                .build();
        assertThat(service.notify(message(), config).get(0).isSuccess()).isFalse();
    }

    private NotifyMessage message() {
        return NotifyMessage.Builder.builder().title("标题").content("内容").build();
    }

    /**
     * 测试桩渠道
     * @author yangqiong
     */
    private static final class FakeChannel implements NotifyChannel {

        /**
         * 渠道类型
         */
        private final String type;

        /**
         * 是否投递成功
         */
        private final boolean success;

        FakeChannel(String type, boolean success) {
            this.type = type;
            this.success = success;
        }

        @Override
        public String type() {
            return type;
        }

        @Override
        public NotifyResult send(NotifyMessage message, ChannelTarget target) {
            return success ? NotifyResult.success(type) : NotifyResult.failure(type, "模拟失败");
        }
    }
}
