package com.xtu.homework.dto;

import jakarta.validation.constraints.NotEmpty;
import lombok.Data;
import java.util.List;

@Data
public class SubmissionDto {
    // homeworkId 由 /homeworks/{id}/submit 路径注入（Controller 内 set），不能加 @NotNull——
    // @Valid 校验先于方法体执行，path 注入发生在校验之后，加了会导致前端未传 homeworkId 时恒 400
    private Long homeworkId;
    @NotEmpty(message = "答案列表不能为空")
    private List<AnswerItem> answers;

    @Data
    public static class AnswerItem {
        private Long questionId;
        private String answer;
    }
}
