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

import java.nio.charset.StandardCharsets;
import java.util.Properties;

import jakarta.mail.Authenticator;
import jakarta.mail.Message;
import jakarta.mail.MessagingException;
import jakarta.mail.PasswordAuthentication;
import jakarta.mail.Session;
import jakarta.mail.Transport;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import com.yangqiongai.agent.harness.notify.ChannelTarget;
import com.yangqiongai.agent.harness.notify.NotifyChannel;
import com.yangqiongai.agent.harness.notify.NotifyMessage;
import com.yangqiongai.agent.harness.notify.NotifyResult;

/**
 * 邮件通知渠道
 * <p>
 * 基于SMTP协议投递通知，配置项host/from/to为必填，port默认25，username与password可选开启认证，
 * ssl可开启加密连接；标题与正文按UTF-8编码。
 * </p>
 * @author yangqiong
 */
public class EmailNotifyChannel implements NotifyChannel {

    /**
     * SMTP服务器配置key
     */
    private static final String KEY_HOST = "host";

    /**
     * SMTP端口配置key
     */
    private static final String KEY_PORT = "port";

    /**
     * 认证用户名配置key
     */
    private static final String KEY_USERNAME = "username";

    /**
     * 认证密码配置key
     */
    private static final String KEY_PASSWORD = "password";

    /**
     * 发件人配置key
     */
    private static final String KEY_FROM = "from";

    /**
     * 收件人配置key，多个以逗号分隔
     */
    private static final String KEY_TO = "to";

    /**
     * 是否启用SSL配置key
     */
    private static final String KEY_SSL = "ssl";

    /**
     * 默认SMTP端口
     */
    private static final String DEFAULT_PORT = "25";

    @Override
    public String type() {
        return TYPE_EMAIL;
    }

    @Override
    public NotifyResult send(NotifyMessage message, ChannelTarget target) {
        String host = target.getProperty(KEY_HOST, null);
        String from = target.getProperty(KEY_FROM, null);
        String to = target.getProperty(KEY_TO, null);
        if (isBlank(host) || isBlank(from) || isBlank(to)) {
            return NotifyResult.failure(type(), "缺少邮件必填配置(host/from/to)");
        }
        try {
            Session session = buildSession(target, host);
            MimeMessage mime = buildMimeMessage(session, message, from, to);
            Transport.send(mime);
            return NotifyResult.success(type());
        } catch (Exception e) {
            return NotifyResult.failure(type(), e.getMessage());
        }
    }

    /**
     * 按目标配置构造邮件会话
     * @param target
     * @param host
     * @return
     */
    private Session buildSession(ChannelTarget target, String host) {
        Properties props = new Properties();
        props.put("mail.smtp.host", host);
        props.put("mail.smtp.port", target.getProperty(KEY_PORT, DEFAULT_PORT));
        boolean ssl = Boolean.parseBoolean(target.getProperty(KEY_SSL, "false"));
        props.put("mail.smtp.ssl.enable", String.valueOf(ssl));
        String username = target.getProperty(KEY_USERNAME, null);
        String password = target.getProperty(KEY_PASSWORD, null);
        boolean auth = !isBlank(username);
        props.put("mail.smtp.auth", String.valueOf(auth));
        if (auth) {
            String user = username;
            String pwd = password;
            return Session.getInstance(props, new Authenticator() {
                @Override
                protected PasswordAuthentication getPasswordAuthentication() {
                    return new PasswordAuthentication(user, pwd);
                }
            });
        }
        return Session.getInstance(props);
    }

    /**
     * 按消息构造MIME邮件
     * @param session
     * @param message
     * @param from
     * @param to
     * @return
     */
    MimeMessage buildMimeMessage(Session session, NotifyMessage message, String from, String to)
            throws MessagingException {
        MimeMessage mime = new MimeMessage(session);
        mime.setFrom(new InternetAddress(from));
        mime.setRecipients(Message.RecipientType.TO, InternetAddress.parse(to));
        mime.setSubject(message.getTitle(), StandardCharsets.UTF_8.name());
        mime.setText(message.getContent(), StandardCharsets.UTF_8.name());
        return mime;
    }

    /**
     * 判断字符串是否为空或空白
     * @param value
     * @return
     */
    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
