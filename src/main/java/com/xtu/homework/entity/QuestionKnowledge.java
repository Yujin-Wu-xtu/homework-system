package com.xtu.homework.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

@Data
@TableName("question_knowledge")
public class QuestionKnowledge {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long questionId;
    private Long knowledgePointId;
}
