package com.xtu.homework.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("question")
public class Question {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String type;          // SINGLE_CHOICE / MULTI_CHOICE / TRUE_FALSE / ESSAY
    private String content;       // 题目题干
    private String correctAnswer; // 标准答案 (客观题必填，JSON格式)
    private String referenceAnswer; // 参考答案 (主观题)
    private BigDecimal score;     // 默认分值
    private String difficulty;    // EASY / MEDIUM / HARD
    private String status;        // ACTIVE / DISABLED
    private String tfidfVector;   // 预计算的TF-IDF向量（题目查重用）
    private Long creatorId;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
