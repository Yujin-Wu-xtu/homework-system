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
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
