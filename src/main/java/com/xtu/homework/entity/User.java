package com.xtu.homework.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("sys_user")
public class User {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String username;
    private String password;
    private String realName;
    private String role;          // ADMIN / TEACHER / STUDENT
    private String phone;
    private String email;
    private Long clazzId;
    private String status;        // ACTIVE / DISABLED
    private Boolean pwdResetRequired;
    private Integer loginFailCount;
    private LocalDateTime lockedUntil;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
