package com.xtu.homework.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("homework")
public class Homework {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String title;
    private String description;
    private Long teachingClassId;
    private Long teacherId;
    private LocalDateTime deadline;
    private BigDecimal totalScore;
    private String status;        // DRAFT / PUBLISHED / CLOSED
    private Boolean questionLocked;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
