package com.xtu.homework.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("teaching_class")
public class TeachingClass {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String name;
    private Long teacherId;
    /** 课程类型：REQUIRED=必修(专业课，按自然班动态拉学生) / ELECTIVE=选修(自由选学生，静态关系表) */
    private String courseType;
    /** 非表字段：当前教学班内学生人数（教师侧列表动态计算，前端据此过滤空教学班） */
    @TableField(exist = false)
    private Integer studentCount;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
