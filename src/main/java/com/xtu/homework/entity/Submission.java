package com.xtu.homework.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("submission")
public class Submission {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long homeworkId;
    private Long studentId;
    private String status;        // NOT_SUBMITTED / SUBMITTED / GRADED
    private LocalDateTime openTime;
    private LocalDateTime submitTime;
    private LocalDateTime lastModifiedTime;
    private Integer durationSeconds; // 答题耗时（秒）
    private BigDecimal autoScore;    // 客观题自动评分
    private BigDecimal manualScore;  // 主观题人工评分
    private BigDecimal totalScore;
    private Boolean suspiciousFlag;  // 异常标记
}
