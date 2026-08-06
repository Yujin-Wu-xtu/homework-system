package com.xtu.homework.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("submission_answer")
public class SubmissionAnswer {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long submissionId;
    private Long questionId;
    @TableField(exist = false)
    private String questionType;     // 非数据库字段，供前端显示
    private String studentAnswer;
    private BigDecimal score;
    private String comment;
    private Long gradedBy;
    private LocalDateTime gradedTime;
}
