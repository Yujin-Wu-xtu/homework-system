package com.xtu.homework.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("audit_log")
public class AuditLog {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private String username;
    private String operation;   // CREATE / UPDATE / DELETE / LOGIN
    private String targetType;  // USER / QUESTION / HOMEWORK
    private String targetId;
    private String detail;
    private String ipAddress;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
