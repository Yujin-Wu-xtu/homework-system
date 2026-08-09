package com.xtu.homework.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("sys_user")
public class User {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String username;
    /** 密码（BCrypt 哈希）。WRITE_ONLY：仅反序列化（登录/注册/新增时接收），响应中永不输出——防止列表接口泄露密码哈希 */
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
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
