package com.xtu.homework.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import java.util.List;

@Data
public class SubmissionDto {
    @NotNull(message = "作业ID不能为空")
    private Long homeworkId;
    @NotEmpty(message = "答案列表不能为空")
    private List<AnswerItem> answers;

    @Data
    public static class AnswerItem {
        private Long questionId;
        private String answer;
    }
}
