package com.xtu.homework.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class HomeworkAssignDto {
    @NotBlank(message = "作业名称不能为空")
    private String title;
    private String description;
    @NotNull(message = "教学班级ID不能为空")
    private Long teachingClassId;
    @NotNull(message = "截止时间不能为空")
    private LocalDateTime deadline;
    @NotEmpty(message = "题目列表不能为空")
    private List<QuestionItem> questions;

    @Data
    public static class QuestionItem {
        private Long questionId;
        private Integer sortOrder;
        private BigDecimal score;
    }
}
