package com.xtu.homework.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;

import java.util.Properties;

/**
 * 邮件发送器配置（QQ SMTP 465 SSL）
 * 代理策略：默认纯直连（QQ SMTP 国内直连畅通，无需代理）；
 * 仅当显式设置环境变量 MAIL_PROXY_HOST（可选 MAIL_PROXY_PORT，默认 7890）时才启用代理，
 * 适配 WSL2 等直连受限的网络环境。避免空字符串代理属性导致 JavaMail 连接失败。
 */
@Configuration
public class MailConfig {

    @Bean
    public JavaMailSender javaMailSender(
            @Value("${spring.mail.host}") String host,
            @Value("${spring.mail.port}") int port,
            @Value("${spring.mail.username:}") String username,
            @Value("${spring.mail.password:}") String password,
            @Value("${spring.mail.properties.mail.smtp.auth:false}") String smtpAuth,
            @Value("${spring.mail.properties.mail.smtp.ssl.enable:false}") String smtpSsl) {
        JavaMailSenderImpl sender = new JavaMailSenderImpl();
        sender.setHost(host);
        sender.setPort(port);
        sender.setUsername(username);
        sender.setPassword(password);

        Properties props = new Properties();
        props.put("mail.smtp.auth", smtpAuth);
        props.put("mail.smtp.ssl.enable", smtpSsl);

        // 代理：仅当显式设置 MAIL_PROXY_HOST 环境变量时启用（默认纯直连）
        String proxyHost = System.getenv("MAIL_PROXY_HOST");
        if (proxyHost != null && !proxyHost.isBlank()) {
            props.put("mail.smtp.proxy.host", proxyHost.trim());
            String proxyPort = System.getenv("MAIL_PROXY_PORT");
            props.put("mail.smtp.proxy.port", proxyPort == null || proxyPort.isBlank() ? "7890" : proxyPort.trim());
        }
        sender.setJavaMailProperties(props);
        return sender;
    }
}
