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
package com.yangqiongai.agent.harness.notify.email;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Properties;

import jakarta.mail.Message;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import com.yangqiongai.agent.harness.notify.ChannelTarget;
import com.yangqiongai.agent.harness.notify.NotifyMessage;
import com.yangqiongai.agent.harness.notify.NotifyResult;
import org.junit.jupiter.api.Test;

/**
 * 邮件通知渠道测试
 * @author yangqiong
 */
class EmailNotifyChannelTest {

    /**
     * 缺少必填配置时返回失败结果
     */
    @Test
    void missingRequiredConfigReturnsFailure() {
        EmailNotifyChannel channel = new EmailNotifyChannel();

        NotifyResult result = channel.send(message(),
                ChannelTarget.builder().channelType("email").property("host", "smtp.x.com").build());

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getErrorMessage()).contains("host/from/to");
    }

    /**
     * 邮件字段按消息与收件人正确组装
     * @throws Exception
     */
    @Test
    void buildMimeMessageSetsSubjectFromToAndContent() throws Exception {
        EmailNotifyChannel channel = new EmailNotifyChannel();
        Session session = Session.getInstance(new Properties());

        MimeMessage mime = channel.buildMimeMessage(session, message(), "from@x.com", "to1@x.com,to2@x.com");

        assertThat(mime.getSubject()).isEqualTo("标题");
        assertThat(mime.getFrom()).hasSize(1);
        assertThat(mime.getFrom()[0].toString()).contains("from@x.com");
        assertThat(mime.getRecipients(Message.RecipientType.TO)).hasSize(2);
        assertThat(mime.getContent()).isEqualTo("内容");
    }

    private NotifyMessage message() {
        return NotifyMessage.Builder.builder().title("标题").content("内容").build();
    }
}
