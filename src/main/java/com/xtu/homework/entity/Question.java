package com.xtu.homework.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Set;

@Data
@TableName("question")
public class Question {
    /** 客观题（自动判分，需标准答案/选项）：单选/多选/判断 */
    public static final Set<String> OBJECTIVE_TYPES = Set.of("SINGLE_CHOICE", "MULTI_CHOICE", "TRUE_FALSE");
    /** 主观题（教师评分，参考答案）：问答题/应用题 */
    public static final Set<String> SUBJECTIVE_TYPES = Set.of("ESSAY", "APPLICATION");

    @TableId(type = IdType.AUTO)
    private Long id;
    private String type;          // SINGLE_CHOICE / MULTI_CHOICE / TRUE_FALSE / ESSAY / APPLICATION
    private String content;       // 题目题干（应用题可含富文本 HTML：图片/图表/公式/代码块）
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

    /** 是否客观题（自动判分、展示选项） */
    public boolean isObjective() {
        return OBJECTIVE_TYPES.contains(this.type);
    }

    /** 是否主观题（教师评分、展示参考答案输入） */
    public boolean isSubjective() {
        return SUBJECTIVE_TYPES.contains(this.type);
    }
}
