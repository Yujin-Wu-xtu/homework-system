package com.xtu.homework.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("knowledge_point")
public class KnowledgePoint {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String name;
    private String subject;
    private Long parentId;
    private String description;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
