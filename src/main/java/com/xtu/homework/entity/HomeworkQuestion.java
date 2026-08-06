package com.xtu.homework.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.math.BigDecimal;

@Data
@TableName("homework_question")
public class HomeworkQuestion {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long homeworkId;
    private Long questionId;
    private Integer sortOrder;
    private BigDecimal score;
}
