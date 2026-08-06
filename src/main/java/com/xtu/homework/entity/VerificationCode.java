package com.xtu.homework.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 邮箱注册验证码
 */
@Data
@TableName("verification_code")
public class VerificationCode {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String channel;
    private String target;
    private String codeHash;
    private LocalDateTime expiresAt;
    private LocalDateTime usedAt;
    private LocalDateTime lastSentAt;
    private Integer sendCountToday;
    private LocalDateTime createdAt;
}
