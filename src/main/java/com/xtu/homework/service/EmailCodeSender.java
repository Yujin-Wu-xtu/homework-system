package com.xtu.homework.service;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

/**
 * 注册验证码邮件发送（QQ 邮箱 SMTP，配置见 application.yml spring.mail）
 */
@Service
@RequiredArgsConstructor
public class EmailCodeSender {

    private final JavaMailSender mailSender;

    @Value("${spring.mail.username:}")
    private String mailUsername;

    public void sendCode(String email, String code) {
        if (mailUsername == null || mailUsername.isBlank()) {
            throw new RuntimeException("邮件发送方未配置（spring.mail.username）");
        }
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(mailUsername);
        message.setTo(email);
        message.setSubject("在线作业系统 - 邮箱注册验证码");
        message.setText("您好！\n\n您的注册验证码是：" + code + "\n5 分钟内有效，请尽快完成注册。\n若您未发起注册操作，请忽略本邮件。\n\n—— 在线作业系统");
        mailSender.send(message);
    }
}
